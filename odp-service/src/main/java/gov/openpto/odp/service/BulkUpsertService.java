package gov.openpto.odp.service;

import gov.openpto.odp.config.AppConfig;
import gov.openpto.odp.dto.BulkUpsertResponse;
import gov.openpto.odp.dto.PatentUpsert;
import gov.openpto.odp.dto.TrademarkUpsert;
import gov.openpto.odp.exception.BadRequestException;
import gov.openpto.odp.model.RecordSource;
import gov.openpto.odp.repository.PatentWriteRepository;
import gov.openpto.odp.repository.TrademarkWriteRepository;
import gov.openpto.odp.repository.UpsertOutcome;
import jakarta.validation.Validator;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Idempotent bulk upserts from ingest-service. Each record is parsed, validated, normalized and
 * written in its own short transaction, so one bad record (malformed JSON value, constraint
 * violation, inconsistent dates, database rejection) lands in {@code failed[]} without affecting
 * the rest of the batch. Child collections are replaced on update.
 */
@Slf4j
@Service
public class BulkUpsertService {

    public static final int MAX_BATCH = 1000;

    private final PatentWriteRepository patentWriter;
    private final TrademarkWriteRepository trademarkWriter;
    private final RecordNormalizer normalizer;
    private final Validator validator;
    private final CacheManager cacheManager;
    private final JsonMapper jsonMapper;
    private final TransactionTemplate perRecordTx;

    public BulkUpsertService(
            PatentWriteRepository patentWriter,
            TrademarkWriteRepository trademarkWriter,
            RecordNormalizer normalizer,
            Validator validator,
            CacheManager cacheManager,
            JsonMapper jsonMapper,
            PlatformTransactionManager transactionManager) {
        this.patentWriter = patentWriter;
        this.trademarkWriter = trademarkWriter;
        this.normalizer = normalizer;
        this.validator = validator;
        this.cacheManager = cacheManager;
        this.jsonMapper = jsonMapper;
        this.perRecordTx = new TransactionTemplate(transactionManager);
        this.perRecordTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** @param records {@link PatentUpsert} instances or raw {@link JsonNode}s (parsed per record) */
    public BulkUpsertResponse upsertPatents(List<?> records) {
        return run(records, PatentUpsert.class, RecordNormalizer::patentKey,
                r -> patentWriter.upsert(normalizer.patent(r, RecordSource.INGEST), RecordSource.INGEST),
                "patentNumber", "applicationNumber");
    }

    /** @param records {@link TrademarkUpsert} instances or raw {@link JsonNode}s (parsed per record) */
    public BulkUpsertResponse upsertTrademarks(List<?> records) {
        return run(records, TrademarkUpsert.class, TrademarkUpsert::serialNumber,
                r -> trademarkWriter.upsert(normalizer.trademark(r, RecordSource.INGEST), RecordSource.INGEST),
                "serialNumber");
    }

    private <T> BulkUpsertResponse run(
            List<?> records, Class<T> type, Function<T, String> keyFn, Function<T, UpsertOutcome> write, String... rawKeyFields) {
        if (records == null) {
            throw new BadRequestException("Request body must be a JSON array");
        }
        if (records.size() > MAX_BATCH) {
            throw new BadRequestException("At most " + MAX_BATCH + " records per request");
        }
        int inserted = 0;
        int updated = 0;
        List<BulkUpsertResponse.Failure> failed = new ArrayList<>();
        for (int i = 0; i < records.size(); i++) {
            Object raw = records.get(i);
            String id = rawKey(raw, rawKeyFields, "#" + i);
            try {
                T record = parse(raw, type);
                String key = keyFn.apply(record);
                if (key != null && !key.isBlank()) {
                    id = key.trim();
                }
                String violations = violations(record);
                if (violations != null) {
                    failed.add(new BulkUpsertResponse.Failure(id, violations));
                    continue;
                }
                UpsertOutcome outcome = perRecordTx.execute(status -> write.apply(record));
                if (outcome != null && outcome.inserted()) {
                    inserted++;
                } else {
                    updated++;
                }
            } catch (IllegalArgumentException e) {
                failed.add(new BulkUpsertResponse.Failure(id, e.getMessage()));
            } catch (DataAccessException e) {
                String cause = firstLine(e.getMostSpecificCause().getMessage());
                log.warn("Bulk upsert record {} rejected by the database: {}", id, cause);
                failed.add(new BulkUpsertResponse.Failure(id, "rejected by the database: " + cause));
            }
        }
        if (inserted + updated > 0) {
            evictCaches();
        }
        log.info("Bulk upsert of {}: {} inserted, {} updated, {} failed", type.getSimpleName(), inserted, updated, failed.size());
        return new BulkUpsertResponse(inserted, updated, failed);
    }

    private <T> T parse(Object raw, Class<T> type) {
        if (type.isInstance(raw)) {
            return type.cast(raw);
        }
        if (raw instanceof JsonNode node && node.isObject()) {
            try {
                return jsonMapper.treeToValue(node, type);
            } catch (JacksonException e) {
                throw new IllegalArgumentException("invalid " + type.getSimpleName() + ": " + firstLine(e.getOriginalMessage()));
            }
        }
        throw new IllegalArgumentException("record must be a JSON object");
    }

    private static String rawKey(Object raw, String[] fields, String fallback) {
        if (raw instanceof JsonNode node && node.isObject()) {
            for (String field : fields) {
                JsonNode value = node.get(field);
                if (value != null && value.isString() && !value.asString().isBlank()) {
                    return value.asString().trim();
                }
            }
        }
        return fallback;
    }

    private <T> String violations(T record) {
        var violations = validator.validate(record);
        if (violations.isEmpty()) {
            return null;
        }
        return violations.stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .sorted()
                .collect(Collectors.joining("; "));
    }

    private void evictCaches() {
        for (String name : List.of(AppConfig.STATS_CACHE, AppConfig.FACETS_CACHE)) {
            Cache cache = cacheManager.getCache(name);
            if (cache != null) {
                cache.clear();
            }
        }
    }

    private static String firstLine(String message) {
        if (message == null) {
            return "unknown error";
        }
        String line = message.lines().findFirst().orElse(message);
        return line.length() > 300 ? line.substring(0, 300) : line;
    }
}
