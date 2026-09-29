package gov.openpto.ingest.storage;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;

/**
 * S3 emulation on the local file system: {@code <root>/<bucket>/<key>}. Writes go to a temp file and are
 * atomically moved into place, so readers never see partial objects. A put into a bucket listed in
 * {@code notifyingBuckets} publishes an {@link ObjectCreatedEvent} (the S3 event notification).
 */
@Slf4j
public class LocalFsObjectStorage implements ObjectStorage {

    static final String TEMP_PREFIX = ".tmp-";
    private static final String ETAG_SUFFIX = ".etag";

    private final Path root;
    private final ApplicationEventPublisher publisher;
    private final Set<String> notifyingBuckets;

    public LocalFsObjectStorage(Path root, ApplicationEventPublisher publisher, Set<String> notifyingBuckets) {
        this.root = root.toAbsolutePath().normalize();
        this.publisher = publisher;
        this.notifyingBuckets = Set.copyOf(notifyingBuckets);
    }

    public Path root() {
        return root;
    }

    @Override
    public ObjectMetadata put(String bucket, String key, InputStream data, long contentLength, String contentType)
            throws IOException {
        Path target = resolve(bucket, key);
        Files.createDirectories(target.getParent());
        Path temp = target.resolveSibling(TEMP_PREFIX + UUID.randomUUID());
        MessageDigest md5 = md5();
        long size;
        try {
            try (OutputStream out = new DigestOutputStream(Files.newOutputStream(temp), md5)) {
                size = data.transferTo(out);
            }
            if (contentLength >= 0 && size != contentLength) {
                throw new IOException("Content length mismatch: expected " + contentLength + " but got " + size);
            }
            String eTag = HexFormat.of().formatHex(md5.digest());
            move(temp, target);
            Files.writeString(etagFile(target), eTag);
            ObjectMetadata metadata = new ObjectMetadata(bucket, key, size, eTag, Instant.now());
            if (publisher != null && notifyingBuckets.contains(bucket)) {
                publisher.publishEvent(ObjectCreatedEvent.of(metadata));
            }
            return metadata;
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    @Override
    public InputStream get(String bucket, String key) throws IOException {
        Path path = resolve(bucket, key);
        try {
            return Files.newInputStream(path);
        } catch (NoSuchFileException e) {
            throw new NoSuchObjectException(bucket, key);
        }
    }

    @Override
    public Optional<ObjectMetadata> head(String bucket, String key) throws IOException {
        Path path = resolve(bucket, key);
        if (!Files.isRegularFile(path)) {
            return Optional.empty();
        }
        return Optional.of(metadata(bucket, key, path));
    }

    @Override
    public boolean delete(String bucket, String key) throws IOException {
        Path path = resolve(bucket, key);
        Files.deleteIfExists(etagFile(path));
        boolean deleted = Files.deleteIfExists(path);
        // prune empty parent folders up to the bucket (S3 has no directories)
        Path bucketDir = bucketDir(bucket);
        Path parent = path.getParent();
        while (parent != null && !parent.equals(bucketDir) && parent.startsWith(bucketDir) && Files.isDirectory(parent)) {
            try (Stream<Path> children = Files.list(parent)) {
                if (children.findAny().isPresent()) {
                    break;
                }
            }
            Files.deleteIfExists(parent);
            parent = parent.getParent();
        }
        return deleted;
    }

    @Override
    public List<ObjectMetadata> list(String bucket, String prefix) throws IOException {
        ObjectKeyValidator.validateBucket(bucket);
        ObjectKeyValidator.validatePrefix(prefix);
        Path bucketDir = bucketDir(bucket);
        List<ObjectMetadata> result = new ArrayList<>();
        if (!Files.isDirectory(bucketDir)) {
            return result;
        }
        try (Stream<Path> files = Files.walk(bucketDir)) {
            for (Path file : (Iterable<Path>) files.filter(Files::isRegularFile)::iterator) {
                String name = file.getFileName().toString();
                if (name.startsWith(TEMP_PREFIX) || name.endsWith(ETAG_SUFFIX) && name.startsWith(".")) {
                    continue;
                }
                String key = bucketDir.relativize(file).toString().replace('\\', '/');
                if (prefix == null || key.startsWith(prefix)) {
                    result.add(metadata(bucket, key, file));
                }
            }
        }
        result.sort((a, b) -> a.key().compareTo(b.key()));
        return result;
    }

    private ObjectMetadata metadata(String bucket, String key, Path path) throws IOException {
        BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);
        Path etag = etagFile(path);
        String eTag = Files.exists(etag) ? Files.readString(etag).trim() : null;
        return new ObjectMetadata(bucket, key, attrs.size(), eTag, attrs.lastModifiedTime().toInstant());
    }

    /** Resolves {@code bucket/key} and guarantees the result stays inside the bucket folder. */
    Path resolve(String bucket, String key) {
        ObjectKeyValidator.validateBucket(bucket);
        ObjectKeyValidator.validateKey(key);
        Path bucketDir = bucketDir(bucket);
        Path path = bucketDir.resolve(key).normalize();
        if (!path.startsWith(bucketDir) || path.equals(bucketDir)) {
            throw new IllegalArgumentException("Object key escapes bucket");
        }
        return path;
    }

    private Path bucketDir(String bucket) {
        return root.resolve(bucket).normalize();
    }

    private static Path etagFile(Path object) {
        return object.resolveSibling("." + object.getFileName() + ETAG_SUFFIX);
    }

    private static void move(Path from, Path to) throws IOException {
        try {
            Files.move(from, to, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static MessageDigest md5() {
        try {
            return MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}