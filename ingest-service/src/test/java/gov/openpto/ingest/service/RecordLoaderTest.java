package gov.openpto.ingest.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import gov.openpto.ingest.TestFixtures;
import gov.openpto.ingest.client.BulkUpsertResponse;
import gov.openpto.ingest.client.OdpClient;
import gov.openpto.ingest.client.OdpClientException;
import gov.openpto.ingest.storage.LocalFsObjectStorage;
import gov.openpto.ingest.transform.DocumentFormat;
import gov.openpto.ingest.transform.RecordError;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class RecordLoaderTest {

    @TempDir
    Path root;

    private LocalFsObjectStorage storage;
    private OdpClient odp;
    private RecordLoader loader;

    @BeforeEach
    void setUp() {
        storage = new LocalFsObjectStorage(root, null, Set.of());
        odp = mock(OdpClient.class);
        loader = new RecordLoader(storage, odp, JsonMapper.shared(), TestFixtures.properties(root));
    }

    private void storeRecords(int count, String keyField) throws IOException {
        StringBuilder json = new StringBuilder("[\n");
        for (int i = 0; i < count; i++) {
            json.append(i == 0 ? "" : ",\n").append("{\"").append(keyField).append("\":\"ID").append(i).append("\"}");
        }
        json.append("\n]\n");
        storage.put("openpto-processed", "jobs/j/out.json",
                new ByteArrayInputStream(json.toString().getBytes(StandardCharsets.UTF_8)), -1, "application/json");
    }

    @Test
    @SuppressWarnings("unchecked")
    void load_1203Records_sentInBatchesOf500() throws IOException {
        storeRecords(1203, "patentNumber");
        when(odp.bulkUpsert(eq(DocumentFormat.Target.PATENTS), anyList())).thenAnswer(inv -> {
            List<JsonNode> batch = inv.getArgument(1);
            return new BulkUpsertResponse(batch.size(), 0, List.of());
        });

        LoadOutcome outcome = loader.load(DocumentFormat.US_PATENT_GRANT, "openpto-processed", "jobs/j/out.json");

        ArgumentCaptor<List<JsonNode>> batches = ArgumentCaptor.forClass(List.class);
        verify(odp, times(3)).bulkUpsert(eq(DocumentFormat.Target.PATENTS), batches.capture());
        List<Integer> sizes = new ArrayList<>();
        batches.getAllValues().forEach(b -> sizes.add(b.size()));
        assertThat(sizes).containsExactly(500, 500, 203);
        assertThat(batches.getAllValues().get(1).get(0).get("patentNumber").asString()).isEqualTo("ID500");
        assertThat(outcome).isEqualTo(new LoadOutcome(1203, 0, List.of()));
    }

    @Test
    void load_failedIdsMappedToRecordIndex() throws IOException {
        storeRecords(502, "serialNumber");
        when(odp.bulkUpsert(eq(DocumentFormat.Target.TRADEMARKS), anyList()))
                .thenReturn(new BulkUpsertResponse(499, 0, List.of(new BulkUpsertResponse.Failure("ID7", "dup"))))
                .thenReturn(new BulkUpsertResponse(1, 0, List.of(new BulkUpsertResponse.Failure("ID501", "bad"),
                        new BulkUpsertResponse.Failure("unknown", "?"))));

        LoadOutcome outcome = loader.load(DocumentFormat.TRADEMARK_DAILY, "openpto-processed", "jobs/j/out.json");

        assertThat(outcome.loaded()).isEqualTo(500);
        assertThat(outcome.failed()).isEqualTo(3);
        assertThat(outcome.errors()).containsExactly(new RecordError(7, "ID7", "dup"),
                new RecordError(501, "ID501", "bad"), new RecordError(500, "unknown", "?"));
    }

    @Test
    void load_rejectedBatchMarksRecordsFailedAndContinues() throws IOException {
        storeRecords(3, "patentNumber");
        when(odp.bulkUpsert(eq(DocumentFormat.Target.PATENTS), anyList()))
                .thenThrow(new OdpClientException("odp-service rejected the batch: 400", null, false));

        LoadOutcome outcome = loader.load(DocumentFormat.US_PATENT_APPLICATION, "openpto-processed", "jobs/j/out.json");

        assertThat(outcome.loaded()).isZero();
        assertThat(outcome.failed()).isEqualTo(3);
        assertThat(outcome.errors()).extracting(RecordError::identifier).containsExactly("ID0", "ID1", "ID2");
    }

    @Test
    void load_odpUnavailable_abortsWithPartialProgress() throws IOException {
        storeRecords(700, "patentNumber");
        when(odp.bulkUpsert(eq(DocumentFormat.Target.PATENTS), anyList()))
                .thenReturn(new BulkUpsertResponse(500, 0, List.of()))
                .thenThrow(new OdpClientException("odp-service unavailable after 3 attempts", null, true));

        assertThatThrownBy(() -> loader.load(DocumentFormat.PATDOC_LEGACY, "openpto-processed", "jobs/j/out.json"))
                .isInstanceOfSatisfying(LoadAbortedException.class,
                        e -> assertThat(e.partial().loaded()).isEqualTo(500));
    }

    @Test
    void load_rejectsNonArrayAndUnknownFormat() throws IOException {
        storage.put("openpto-processed", "bad.json", new ByteArrayInputStream("{}".getBytes(StandardCharsets.UTF_8)), -1, null);

        assertThatThrownBy(() -> loader.load(DocumentFormat.US_PATENT_GRANT, "openpto-processed", "bad.json"))
                .isInstanceOf(IOException.class).hasMessageContaining("not a JSON array");
        assertThatThrownBy(() -> loader.load(DocumentFormat.UNKNOWN, "openpto-processed", "bad.json"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void identifier_fallsBackToApplicationNumber() {
        JsonNode node = JsonMapper.shared().readTree("{\"patentNumber\":null,\"applicationNumber\":\"17/123,456\"}");
        assertThat(RecordLoader.identifier(node, DocumentFormat.Target.PATENTS)).isEqualTo("17/123,456");
        assertThat(RecordLoader.identifier(node, DocumentFormat.Target.TRADEMARKS)).isNull();
    }
}