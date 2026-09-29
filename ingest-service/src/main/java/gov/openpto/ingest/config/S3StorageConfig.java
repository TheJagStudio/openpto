package gov.openpto.ingest.config;

import gov.openpto.ingest.storage.ObjectStorage;
import gov.openpto.ingest.storage.S3ObjectStorage;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.services.s3.S3Client;

/** Amazon S3 (profile {@code aws}); credentials and region come from the default provider chain / task role. */
@Configuration
@Profile("aws")
public class S3StorageConfig {

    @Bean(destroyMethod = "close")
    S3Client s3Client() {
        return S3Client.create();
    }

    @Bean
    ObjectStorage objectStorage(S3Client s3Client) {
        return new S3ObjectStorage(s3Client);
    }
}