package gov.openpto.ingest.service;

import gov.openpto.ingest.config.AppProperties;
import gov.openpto.ingest.dto.IngestJobResponse;
import gov.openpto.ingest.exception.InvalidUploadException;
import gov.openpto.ingest.model.IngestJob;
import gov.openpto.ingest.model.IngestJobStage;
import gov.openpto.ingest.model.JobStatus;
import gov.openpto.ingest.model.StageName;
import gov.openpto.ingest.model.StageStatus;
import gov.openpto.ingest.repository.IngestJobRepository;
import gov.openpto.ingest.repository.IngestJobStageRepository;
import gov.openpto.ingest.storage.ObjectMetadata;
import gov.openpto.ingest.storage.ObjectStorage;
import gov.openpto.ingest.transform.lambda.ObjectKeys;
import gov.openpto.ingest.web.CurrentUser;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Stream;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

/**
 * Validates uploads (count, extension, size, sniffed content, safe zip extraction), then — in one transaction —
 * creates one job per XML file and puts it into the raw bucket. The put raises the S3-style ObjectCreated event,
 * which the pipeline only sees after commit.
 */
@Slf4j
@Service
public class UploadService {

    private final IngestJobRepository jobs;
    private final IngestJobStageRepository stages;
    private final ObjectStorage storage;
    private final TransactionTemplate tx;
    private final AppProperties.Ingest limits;
    private final String rawBucket;
    private final ZipExtractor zipExtractor;

    public UploadService(IngestJobRepository jobs, IngestJobStageRepository stages, ObjectStorage storage,
                         TransactionTemplate tx, AppProperties properties) {
        this.jobs = jobs;
        this.stages = stages;
        this.storage = storage;
        this.tx = tx;
        this.limits = properties.ingest();
        this.rawBucket = properties.storage().rawBucket();
        this.zipExtractor = new ZipExtractor(limits.maxZipEntries(), limits.maxFileBytes(), limits.maxZipTotalBytes(),
                limits.maxCompressionRatio());
    }

    /** One XML payload to become a job. */
    private record Prepared(String displayName, long size, Source source) {
    }

    @FunctionalInterface
    private interface Source {
        InputStream open() throws IOException;
    }

    public List<IngestJobResponse> upload(List<MultipartFile> files, CurrentUser user) {
        if (files == null || files.isEmpty()) {
            throw new InvalidUploadException("files", "At least one file is required");
        }
        if (files.size() > limits.maxFiles()) {
            throw new InvalidUploadException("files", "At most " + limits.maxFiles() + " files per upload");
        }
        Path workDir = null;
        try {
            List<Prepared> prepared = new ArrayList<>();
            for (MultipartFile file : files) {
                String name = displayName(file.getOriginalFilename());
                if (file.isEmpty()) {
                    throw new InvalidUploadException("files", name + ": file is empty");
                }
                if (file.getSize() > limits.maxFileBytes()) {
                    throw new InvalidUploadException("files", name + ": file exceeds " + limits.maxFileBytes() + " bytes");
                }
                String lower = name.toLowerCase(Locale.ROOT);
                ContentSniffer.Kind kind;
                try (InputStream in = file.getInputStream()) {
                    kind = ContentSniffer.sniff(in);
                }
                if (lower.endsWith(".xml")) {
                    if (kind != ContentSniffer.Kind.XML) {
                        throw new InvalidUploadException("files", name + ": content is not XML");
                    }
                    prepared.add(new Prepared(name, file.getSize(), file::getInputStream));
                } else if (lower.endsWith(".zip")) {
                    if (kind != ContentSniffer.Kind.ZIP) {
                        throw new InvalidUploadException("files", name + ": content is not a zip archive");
                    }
                    if (workDir == null) {
                        workDir = Files.createTempDirectory("ingest-upload-");
                    }
                    Path zip = workDir.resolve("upload-" + prepared.size() + ".zip");
                    file.transferTo(zip);
                    Path entriesDir = Files.createDirectory(workDir.resolve("entries-" + prepared.size()));
                    for (ZipExtractor.Entry entry : zipExtractor.extract(zip, entriesDir, name)) {
                        prepared.add(new Prepared(truncate(name + "/" + entry.name(), 512), entry.size(),
                                () -> Files.newInputStream(entry.file())));
                    }
                } else {
                    throw new InvalidUploadException("files", name + ": only .xml or .zip files are accepted");
                }
            }
            if (prepared.size() > limits.maxZipEntries()) {
                throw new InvalidUploadException("files", "Upload expands to more than " + limits.maxZipEntries()
                        + " XML files");
            }
            List<IngestJob> created = tx.execute(status -> createJobs(prepared, user));
            return created.stream().map(job -> JobMapper.toResponse(job, List.of(), List.of())).toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not store upload", e);
        } finally {
            deleteQuietly(workDir);
        }
    }

    private List<IngestJob> createJobs(List<Prepared> prepared, CurrentUser user) {
        List<IngestJob> created = new ArrayList<>();
        for (Prepared item : prepared) {
            UUID id = UUID.randomUUID();
            IngestJob job = new IngestJob();
            job.setId(id);
            job.setOwnerId(user.id());
            job.setFileName(item.displayName());
            job.setSizeBytes(item.size());
            job.setStatus(JobStatus.QUEUED);
            job.setRawBucket(rawBucket);
            job.setRawObjectKey(ObjectKeys.rawKey(id.toString(), safeKeyName(item.displayName())));
            job = jobs.save(job);
            try (InputStream in = item.source().open()) {
                ObjectMetadata stored = storage.put(rawBucket, job.getRawObjectKey(), in, item.size(), "application/xml");
                job.setRawEtag(stored.eTag());
            } catch (IOException e) {
                throw new UncheckedIOException("Could not store " + item.displayName(), e);
            }
            stages.save(new IngestJobStage(id, StageName.UPLOADED, StageStatus.COMPLETED,
                    "Stored in s3://" + rawBucket + "/" + job.getRawObjectKey()));
            created.add(jobs.save(job));
            log.info("Job {} queued for {} ({} bytes) by {}", id, item.displayName(), item.size(), user.id());
        }
        return created;
    }

    static String displayName(String original) {
        if (original == null || original.isBlank()) {
            return "upload.xml";
        }
        String base = ZipExtractor.baseName(original.trim());
        return truncate(base.isBlank() ? "upload.xml" : base, 255);
    }

    /** S3-safe object key segment derived from the display name (never trusted as a path). */
    static String safeKeyName(String displayName) {
        String base = ZipExtractor.baseName(displayName);
        String safe = base.replaceAll("[^A-Za-z0-9._-]", "_").replaceAll("^[._]+", "");
        if (safe.isEmpty()) {
            safe = "upload.xml";
        }
        if (safe.length() > 120) {
            safe = safe.substring(safe.length() - 120);
        }
        return safe;
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }

    private static void deleteQuietly(Path dir) {
        if (dir == null) {
            return;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                    // best effort
                }
            });
        } catch (IOException ignored) {
            // best effort
        }
    }
}