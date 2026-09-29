package gov.openpto.ingest.service;

import static gov.openpto.ingest.TestFixtures.ALICE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import gov.openpto.ingest.TestFixtures;
import gov.openpto.ingest.dto.IngestJobResponse;
import gov.openpto.ingest.exception.InvalidUploadException;
import gov.openpto.ingest.model.IngestJob;
import gov.openpto.ingest.model.IngestJobStage;
import gov.openpto.ingest.model.JobStatus;
import gov.openpto.ingest.repository.IngestJobRepository;
import gov.openpto.ingest.repository.IngestJobStageRepository;
import gov.openpto.ingest.storage.LocalFsObjectStorage;
import gov.openpto.ingest.storage.ObjectCreatedEvent;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

class UploadServiceTest {

    static final String XML = "<?xml version=\"1.0\"?>\n<us-patent-grant/>\n";

    @TempDir
    Path root;

    private IngestJobRepository jobs;
    private IngestJobStageRepository stages;
    private final List<Object> events = new ArrayList<>();
    private UploadService service;

    @BeforeEach
    void setUp() {
        jobs = mock(IngestJobRepository.class);
        stages = mock(IngestJobStageRepository.class);
        when(jobs.save(any(IngestJob.class))).thenAnswer(inv -> inv.getArgument(0));
        ApplicationEventPublisher publisher = events::add;
        LocalFsObjectStorage storage = new LocalFsObjectStorage(root, publisher, Set.of("openpto-raw"));
        service = new UploadService(jobs, stages, storage, new TransactionTemplate(mock(PlatformTransactionManager.class)),
                TestFixtures.properties(root));
    }

    static MockMultipartFile file(String name, byte[] content) {
        return new MockMultipartFile("files", name, "application/octet-stream", content);
    }

    static byte[] zip(String... namesAndContents) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            for (int i = 0; i < namesAndContents.length; i += 2) {
                zip.putNextEntry(new ZipEntry(namesAndContents[i]));
                zip.write(namesAndContents[i + 1].getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return bytes.toByteArray();
    }

    @Test
    void upload_xml_createsQueuedJobStoresRawObjectAndPublishesEvent() throws IOException {
        List<IngestJobResponse> jobsCreated = service.upload(List.of(file("my grant (1).xml", XML.getBytes())), ALICE);

        assertThat(jobsCreated).singleElement().satisfies(j -> {
            assertThat(j.status()).isEqualTo(JobStatus.QUEUED);
            assertThat(j.fileName()).isEqualTo("my grant (1).xml");
            assertThat(j.ownerId()).isEqualTo(ALICE.id());
            assertThat(j.rawObjectKey()).isEqualTo("jobs/" + j.id() + "/my_grant__1_.xml");
            assertThat(Files.readString(root.resolve("openpto-raw").resolve(j.rawObjectKey()))).isEqualTo(XML);
        });
        assertThat(events).singleElement().isInstanceOfSatisfying(ObjectCreatedEvent.class,
                e -> assertThat(e.key()).isEqualTo(jobsCreated.get(0).rawObjectKey()));
        verify(stages).save(any(IngestJobStage.class));
    }

    @Test
    void upload_zip_createsOneJobPerXmlEntry() throws IOException {
        byte[] archive = zip("a.xml", XML, "nested/b.XML", XML, "readme.txt", "hi", "__MACOSX/._a.xml", "junk", "dir/", "");

        List<IngestJobResponse> created = service.upload(List.of(file("batch.zip", archive)), ALICE);

        assertThat(created).extracting(IngestJobResponse::fileName).containsExactly("batch.zip/a.xml", "batch.zip/nested/b.XML");
        assertThat(created).extracting(IngestJobResponse::sizeBytes).containsOnly((long) XML.length());
        assertThat(events).hasSize(2);
    }

    @Test
    void upload_rejectsCountTypeEmptyAndSniffMismatch() throws IOException {
        List<MultipartFile> eleven = new ArrayList<>();
        for (int i = 0; i < 11; i++) {
            eleven.add(file(i + ".xml", XML.getBytes()));
        }
        assertThatThrownBy(() -> service.upload(List.of(), ALICE)).hasMessageContaining("At least one file");
        assertThatThrownBy(() -> service.upload(null, ALICE)).isInstanceOf(InvalidUploadException.class);
        assertThatThrownBy(() -> service.upload(eleven, ALICE)).hasMessageContaining("At most 10");
        assertThatThrownBy(() -> service.upload(List.of(file("a.pdf", XML.getBytes())), ALICE))
                .hasMessageContaining("only .xml or .zip");
        assertThatThrownBy(() -> service.upload(List.of(file("a.xml", new byte[0])), ALICE))
                .hasMessageContaining("empty");
        assertThatThrownBy(() -> service.upload(List.of(file("a.xml", "%PDF-1.7".getBytes())), ALICE))
                .hasMessageContaining("not XML");
        assertThatThrownBy(() -> service.upload(List.of(file("a.zip", XML.getBytes())), ALICE))
                .hasMessageContaining("not a zip");
        assertThatThrownBy(() -> service.upload(List.of(file("a.xml", zip("a.xml", XML))), ALICE))
                .hasMessageContaining("not XML");
        assertThat(events).isEmpty();
        assertThat(root.resolve("openpto-raw")).doesNotExist();
    }

    @Test
    void upload_zipSlipEntry_isRejectedBeforeAnyJobIsCreated() throws IOException {
        byte[] evil = zip("good.xml", XML, "../../etc/evil.xml", XML);

        assertThatThrownBy(() -> service.upload(List.of(file("good.xml", XML.getBytes()), file("evil.zip", evil)), ALICE))
                .isInstanceOf(InvalidUploadException.class).hasMessageContaining("zip slip");
        assertThat(events).isEmpty();
    }

    @Test
    void upload_zipWithoutXml_isRejected() throws IOException {
        assertThatThrownBy(() -> service.upload(List.of(file("docs.zip", zip("a.txt", "x"))), ALICE))
                .hasMessageContaining("no .xml files");
    }

    @Test
    void names_areSanitised() {
        assertThat(UploadService.displayName(null)).isEqualTo("upload.xml");
        assertThat(UploadService.displayName("C:\\temp\\x.xml")).isEqualTo("x.xml");
        assertThat(UploadService.displayName("../../y.xml")).isEqualTo("y.xml");
        assertThat(UploadService.safeKeyName("..\u00e9vil name.xml")).isEqualTo("vil_name.xml");
        assertThat(UploadService.safeKeyName("...")).isEqualTo("upload.xml");
        assertThat(UploadService.safeKeyName("a".repeat(200) + ".xml")).hasSize(120).endsWith(".xml");
    }
}