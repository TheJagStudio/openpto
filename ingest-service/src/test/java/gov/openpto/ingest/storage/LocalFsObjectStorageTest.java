package gov.openpto.ingest.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

class LocalFsObjectStorageTest {

    @TempDir
    Path root;

    private ApplicationEventPublisher publisher;
    private LocalFsObjectStorage storage;

    @BeforeEach
    void setUp() {
        publisher = mock(ApplicationEventPublisher.class);
        storage = new LocalFsObjectStorage(root, publisher, Set.of("openpto-raw"));
    }

    private static InputStream bytes(String s) {
        return new ByteArrayInputStream(s.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void put_get_head_list_delete_roundTrip() throws IOException {
        ObjectMetadata meta = storage.put("openpto-processed", "jobs/a/b.json", bytes("hello"), 5, "application/json");

        assertThat(meta.size()).isEqualTo(5);
        assertThat(meta.eTag()).isEqualTo("5d41402abc4b2a76b9719d911017c592"); // md5("hello")
        assertThat(Files.readString(root.resolve("openpto-processed/jobs/a/b.json"))).isEqualTo("hello");
        try (InputStream in = storage.get("openpto-processed", "jobs/a/b.json")) {
            assertThat(new String(in.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("hello");
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        assertThat(storage.stream("openpto-processed", "jobs/a/b.json", out)).isEqualTo(5);
        assertThat(storage.head("openpto-processed", "jobs/a/b.json")).get()
                .extracting(ObjectMetadata::eTag).isEqualTo(meta.eTag());
        assertThat(storage.list("openpto-processed", "jobs/")).extracting(ObjectMetadata::key).containsExactly("jobs/a/b.json");
        assertThat(storage.list("openpto-processed", "other/")).isEmpty();
        assertThat(storage.list("empty-bucket", null)).isEmpty();

        assertThat(storage.delete("openpto-processed", "jobs/a/b.json")).isTrue();
        assertThat(storage.delete("openpto-processed", "jobs/a/b.json")).isFalse();
        assertThat(storage.head("openpto-processed", "jobs/a/b.json")).isEmpty();
        assertThat(root.resolve("openpto-processed/jobs")).doesNotExist();
        verify(publisher, never()).publishEvent(any());
    }

    @Test
    void put_intoNotifyingBucket_publishesS3StyleEvent() throws IOException {
        storage.put("openpto-raw", "jobs/x/file.xml", bytes("<a/>"), -1, "application/xml");

        ArgumentCaptor<Object> event = ArgumentCaptor.forClass(Object.class);
        verify(publisher).publishEvent(event.capture());
        assertThat(event.getValue()).isInstanceOfSatisfying(ObjectCreatedEvent.class, e -> {
            assertThat(e.bucket()).isEqualTo("openpto-raw");
            assertThat(e.key()).isEqualTo("jobs/x/file.xml");
            assertThat(e.size()).isEqualTo(4);
            assertThat(e.eTag()).hasSize(32);
        });
    }

    @Test
    void put_lengthMismatch_failsAndLeavesNoObjectOrTempFile() {
        assertThatThrownBy(() -> storage.put("openpto-processed", "k.json", bytes("abc"), 10, "application/json"))
                .isInstanceOf(IOException.class).hasMessageContaining("mismatch");

        assertThat(root.resolve("openpto-processed/k.json")).doesNotExist();
        assertThat(root.resolve("openpto-processed").toFile().list()).isEmpty();
    }

    @Test
    void get_missingObject_throwsNoSuchObject() {
        assertThatThrownBy(() -> storage.get("openpto-raw", "nope.xml")).isInstanceOf(NoSuchObjectException.class)
                .hasMessageContaining("NoSuchKey");
    }

    @ParameterizedTest
    @ValueSource(strings = {"../escape.txt", "a/../../escape.txt", "/abs.txt", "a//b", "a\\..\\b", "C:/x", "a/.hidden",
            "dir/", "", "a/./b"})
    void pathTraversalAndUnsafeKeys_areRejected(String key) {
        assertThatThrownBy(() -> storage.put("openpto-raw", key, bytes("x"), 1, "text/plain"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> storage.get("openpto-raw", key)).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"..", "UPPER", "a", "bad_bucket", "../raw"})
    void invalidBuckets_areRejected(String bucket) {
        assertThatThrownBy(() -> storage.head(bucket, "k")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void list_ignoresTempAndSidecarFiles() throws IOException {
        storage.put("openpto-processed", "a.json", bytes("1"), 1, null);
        Files.writeString(root.resolve("openpto-processed/.tmp-123"), "partial");

        assertThat(storage.list("openpto-processed", "")).extracting(ObjectMetadata::key).containsExactly("a.json");
    }

    @Test
    void storageObjectIO_writesOnCloseAndReadsBack() throws IOException {
        StorageObjectIO io = new StorageObjectIO(storage);
        try (OutputStream out = io.create("openpto-processed", "jobs/j/out.json", "application/json")) {
            out.write("[1]".getBytes(StandardCharsets.UTF_8));
            assertThat(storage.head("openpto-processed", "jobs/j/out.json")).isEmpty();
        }
        try (InputStream in = io.open("openpto-processed", "jobs/j/out.json")) {
            assertThat(new String(in.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("[1]");
        }
    }
}