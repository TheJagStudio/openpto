package gov.openpto.ingest.config;

import gov.openpto.ingest.storage.LocalFsObjectStorage;
import gov.openpto.ingest.storage.ObjectStorage;

import java.nio.file.Path;
import java.util.Set;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/** Local S3 emulation (every profile except {@code aws}). */
@Slf4j
@Configuration
@Profile("!aws")
public class StorageConfig {

    @Bean
    ObjectStorage objectStorage(AppProperties properties, ApplicationEventPublisher publisher) {
        AppProperties.Storage storage = properties.storage();
        LocalFsObjectStorage local = new LocalFsObjectStorage(Path.of(storage.root()), publisher,
                Set.of(storage.rawBucket()));
        log.info("Local object storage at {} (buckets {}, {})", local.root(), storage.rawBucket(),
                storage.processedBucket());
        return local;
    }
}