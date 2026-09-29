package gov.openpto.ingest.config;

import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;

import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Bounded executor that plays the role of the Lambda service: at most {@code maxPoolSize} transforms run
 * concurrently, {@code queueCapacity} wait; when full the caller runs the job itself (back-pressure instead
 * of dropping events). On shutdown in-flight jobs are allowed to finish ({@code shutdownWait}).
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    public static final String INGEST_EXECUTOR = "ingestExecutor";

    @Bean(name = INGEST_EXECUTOR)
    ThreadPoolTaskExecutor ingestExecutor(AppProperties properties) {
        AppProperties.Ingest ingest = properties.ingest();
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("ingest-");
        executor.setCorePoolSize(ingest.corePoolSize());
        executor.setMaxPoolSize(ingest.maxPoolSize());
        executor.setQueueCapacity(ingest.queueCapacity());
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds((int) ingest.shutdownWait().toSeconds());
        executor.setTaskDecorator(mdcPropagation());
        return executor;
    }

    static TaskDecorator mdcPropagation() {
        return runnable -> {
            Map<String, String> context = MDC.getCopyOfContextMap();
            return () -> {
                Map<String, String> previous = MDC.getCopyOfContextMap();
                if (context != null) {
                    MDC.setContextMap(context);
                }
                try {
                    runnable.run();
                } finally {
                    if (previous != null) {
                        MDC.setContextMap(previous);
                    } else {
                        MDC.clear();
                    }
                }
            };
        };
    }
}