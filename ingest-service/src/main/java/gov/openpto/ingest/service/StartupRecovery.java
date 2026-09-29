package gov.openpto.ingest.service;

import gov.openpto.ingest.config.AppProperties;

import java.time.Duration;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Events live in memory, so jobs caught in flight by a restart would hang forever; fail them instead. */
@Slf4j
@Component
public class StartupRecovery {

    private final JobStateService jobState;
    private final Duration stuckAfter;

    public StartupRecovery(JobStateService jobState, AppProperties properties) {
        this.jobState = jobState;
        this.stuckAfter = properties.ingest().stuckAfter();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recover() {
        int recovered = jobState.recoverStuck(stuckAfter);
        if (recovered > 0) {
            log.warn("Marked {} interrupted ingest job(s) as FAILED; they can be retried", recovered);
        }
    }
}