package gov.openpto.ingest.service;

import gov.openpto.ingest.client.BulkUpsertResponse;
import gov.openpto.ingest.client.OdpClient;
import gov.openpto.ingest.client.OdpClientException;
import gov.openpto.ingest.config.AppProperties;
import gov.openpto.ingest.storage.ObjectStorage;
import gov.openpto.ingest.transform.DocumentFormat;
import gov.openpto.ingest.transform.RecordError;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectReader;
import tools.jackson.databind.json.JsonMapper;

/**
 * Streams the processed JSON array from the processed bucket and loads it into odp-service in batches
 * (default 500). A batch rejected with 4xx marks its records failed and loading continues; odp being
 * unavailable after retries aborts the load ({@link LoadAbortedException}).
 */
@Slf4j
@Service
public class RecordLoader {

    private final ObjectStorage storage;
    private final OdpClient odp;
    private final JsonMapper jsonMapper;
    private final ObjectReader nodeReader;
    private final int batchSize;
    private final int maxErrors;

    public RecordLoader(ObjectStorage storage, OdpClient odp, JsonMapper jsonMapper, AppProperties properties) {
        this.storage = storage;
        this.odp = odp;
        this.jsonMapper = jsonMapper;
        // one array element at a time: the rest of the array is not "trailing garbage"
        this.nodeReader = jsonMapper.readerFor(JsonNode.class).without(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
        this.batchSize = Math.max(1, properties.odp().batchSize());
        this.maxErrors = properties.ingest().maxStoredErrors();
    }

    public LoadOutcome load(DocumentFormat format, String bucket, String key) throws IOException {
        DocumentFormat.Target target = format.target();
        if (target == null) {
            throw new IllegalArgumentException("Cannot load records of format " + format);
        }
        Progress progress = new Progress();
        try (InputStream in = storage.get(bucket, key); JsonParser parser = jsonMapper.createParser(in)) {
            if (parser.nextToken() != JsonToken.START_ARRAY) {
                throw new IOException("Processed object s3://" + bucket + "/" + key + " is not a JSON array");
            }
            List<JsonNode> batch = new ArrayList<>(batchSize);
            while (parser.nextToken() == JsonToken.START_OBJECT) {
                batch.add(nodeReader.readValue(parser));
                if (batch.size() == batchSize) {
                    send(target, batch, progress);
                    batch = new ArrayList<>(batchSize);
                }
            }
            if (!batch.isEmpty()) {
                send(target, batch, progress);
            }
        }
        return progress.outcome();
    }

    private void send(DocumentFormat.Target target, List<JsonNode> batch, Progress progress) {
        int offset = progress.sent;
        try {
            BulkUpsertResponse response = odp.bulkUpsert(target, batch);
            progress.loaded += response.loaded();
            for (BulkUpsertResponse.Failure failure : response.failed()) {
                progress.fail(offset + indexOf(batch, target, failure.id()), failure.id(), failure.error());
            }
        } catch (OdpClientException e) {
            if (e.retriesExhausted()) {
                throw new LoadAbortedException(e.getMessage(), progress.outcome(), e);
            }
            log.warn("odp-service rejected batch at offset {} ({} records): {}", offset, batch.size(), e.getMessage());
            for (int i = 0; i < batch.size(); i++) {
                progress.fail(offset + i, identifier(batch.get(i), target), e.getMessage());
            }
        }
        progress.sent += batch.size();
    }

    private static int indexOf(List<JsonNode> batch, DocumentFormat.Target target, String id) {
        for (int i = 0; i < batch.size(); i++) {
            if (id != null && id.equals(identifier(batch.get(i), target))) {
                return i;
            }
        }
        return 0;
    }

    static String identifier(JsonNode record, DocumentFormat.Target target) {
        String field = target == DocumentFormat.Target.TRADEMARKS ? "serialNumber" : "patentNumber";
        JsonNode value = record.get(field);
        if ((value == null || value.isNull()) && target == DocumentFormat.Target.PATENTS) {
            value = record.get("applicationNumber");
        }
        return value == null || value.isNull() ? null : value.asString();
    }

    private final class Progress {
        private int sent;
        private int loaded;
        private int failed;
        private final List<RecordError> errors = new ArrayList<>();

        void fail(int index, String identifier, String message) {
            failed++;
            if (errors.size() < maxErrors) {
                errors.add(new RecordError(index, identifier, message));
            }
        }

        LoadOutcome outcome() {
            return new LoadOutcome(loaded, failed, List.copyOf(errors));
        }
    }
}