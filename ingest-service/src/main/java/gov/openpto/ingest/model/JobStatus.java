package gov.openpto.ingest.model;

import java.util.EnumSet;
import java.util.Set;

/** Job lifecycle: QUEUED → PARSING → TRANSFORMED → LOADING → COMPLETED | PARTIAL | FAILED. */
public enum JobStatus {
    QUEUED, PARSING, TRANSFORMED, LOADING, COMPLETED, PARTIAL, FAILED;

    /** States a job can be stuck in when the process dies. */
    public static final Set<JobStatus> IN_FLIGHT = EnumSet.of(QUEUED, PARSING, TRANSFORMED, LOADING);

    public boolean isRetryable() {
        return this == FAILED || this == PARTIAL;
    }

    public boolean isTerminal() {
        return this == COMPLETED || this == PARTIAL || this == FAILED;
    }
}