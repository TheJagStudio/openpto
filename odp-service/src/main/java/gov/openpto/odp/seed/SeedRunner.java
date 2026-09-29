package gov.openpto.odp.seed;

import gov.openpto.odp.config.AppProperties;
import gov.openpto.odp.dto.PatentUpsert;
import gov.openpto.odp.dto.TrademarkUpsert;
import gov.openpto.odp.model.RecordSource;
import gov.openpto.odp.repository.PatentRepository;
import gov.openpto.odp.repository.PatentWriteRepository;
import gov.openpto.odp.repository.TrademarkRepository;
import gov.openpto.odp.repository.TrademarkWriteRepository;
import gov.openpto.odp.service.RecordNormalizer;

import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.cache.CacheManager;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Seeds mock data on startup when {@code app.seed.enabled=true} (on in the dev profile) and the
 * respective table is empty. Uses JDBC batch inserts in chunks of 1,000 records.
 */
@Slf4j
@Component
@Order(10)
@ConditionalOnBooleanProperty("app.seed.enabled")
@RequiredArgsConstructor
public class SeedRunner implements ApplicationRunner {

    private static final int CHUNK = 1000;

    private final AppProperties.Seed props;
    private final PatentRepository patents;
    private final TrademarkRepository trademarks;
    private final PatentWriteRepository patentWriter;
    private final TrademarkWriteRepository trademarkWriter;
    private final RecordNormalizer normalizer;
    private final PlatformTransactionManager transactionManager;
    private final CacheManager cacheManager;

    @Override
    public void run(ApplicationArguments args) {
        MockDataGenerator generator = new MockDataGenerator(props.randomSeed());
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        boolean seeded = false;
        if (patents.count() == 0) {
            long start = System.nanoTime();
            List<PatentUpsert> records = generator.patents(props.patents()).stream()
                    .map(p -> normalizer.patent(p, RecordSource.SEED))
                    .toList();
            long generated = System.nanoTime();
            for (int i = 0; i < records.size(); i += CHUNK) {
                List<PatentUpsert> chunk = records.subList(i, Math.min(records.size(), i + CHUNK));
                tx.executeWithoutResult(s -> patentWriter.insertBatch(chunk, RecordSource.SEED));
            }
            log.info("Seeded {} patents (generate {} ms, insert {} ms)", records.size(),
                    (generated - start) / 1_000_000, (System.nanoTime() - generated) / 1_000_000);
            seeded = true;
        } else {
            log.info("Patents table not empty; skipping patent seed");
        }
        if (trademarks.count() == 0) {
            long start = System.nanoTime();
            List<TrademarkUpsert> records = generator.trademarks(props.trademarks()).stream()
                    .map(t -> normalizer.trademark(t, RecordSource.SEED))
                    .toList();
            long generated = System.nanoTime();
            for (int i = 0; i < records.size(); i += CHUNK) {
                List<TrademarkUpsert> chunk = records.subList(i, Math.min(records.size(), i + CHUNK));
                tx.executeWithoutResult(s -> trademarkWriter.insertBatch(chunk, RecordSource.SEED));
            }
            log.info("Seeded {} trademarks (generate {} ms, insert {} ms)", records.size(),
                    (generated - start) / 1_000_000, (System.nanoTime() - generated) / 1_000_000);
            seeded = true;
        } else {
            log.info("Trademarks table not empty; skipping trademark seed");
        }
        if (seeded) {
            cacheManager.getCacheNames().forEach(name -> {
                var cache = cacheManager.getCache(name);
                if (cache != null) {
                    cache.clear();
                }
            });
        }
    }
}
