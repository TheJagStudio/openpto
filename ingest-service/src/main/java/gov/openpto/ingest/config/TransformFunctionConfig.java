package gov.openpto.ingest.config;

import com.amazonaws.services.lambda.runtime.events.S3Event;
import gov.openpto.ingest.storage.ObjectStorage;
import gov.openpto.ingest.storage.StorageObjectIO;
import gov.openpto.ingest.transform.lambda.LambdaHandler;
import gov.openpto.ingest.transform.lambda.LocalContext;
import gov.openpto.ingest.transform.lambda.TransformResult;

import java.util.function.Function;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Hosts the transform-lambda locally. The very same {@link LambdaHandler} that AWS Lambda would invoke is
 * wired to the file-system buckets and exposed as a Spring Cloud Function ({@code transform}).
 */
@Slf4j
@Configuration
public class TransformFunctionConfig {

    public static final String FUNCTION_NAME = "openpto-transform";
    private static final int LAMBDA_TIMEOUT_MS = 15 * 60 * 1000;

    @Bean
    LambdaHandler transformLambdaHandler(ObjectStorage storage, AppProperties properties) {
        StorageObjectIO io = new StorageObjectIO(storage);
        return new LambdaHandler(io, io, properties.storage().processedBucket());
    }

    @Bean
    Function<S3Event, TransformResult> transform(LambdaHandler transformLambdaHandler) {
        return event -> transformLambdaHandler.handleRequest(event,
                new LocalContext(FUNCTION_NAME, LAMBDA_TIMEOUT_MS, message -> log.info("[lambda] {}", message)));
    }
}