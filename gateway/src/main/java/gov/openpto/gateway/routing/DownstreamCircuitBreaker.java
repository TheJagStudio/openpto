package gov.openpto.gateway.routing;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongSupplier;

/**
 * "Circuit-breaking lite": after {@code threshold} consecutive transport failures (connect refused,
 * timeout) the circuit opens and requests fail fast with 503 for {@code openDuration}. After that a
 * request is let through (half-open); success closes the circuit, another failure re-opens it.
 * HTTP error statuses from the downstream are responses, not failures.
 */
public class DownstreamCircuitBreaker {

    public enum State {CLOSED, OPEN, HALF_OPEN}

    private final int threshold;
    private final long openNanos;
    private final LongSupplier nanoClock;
    private final AtomicInteger consecutiveFailures = new AtomicInteger();
    private volatile boolean tripped;
    private volatile long openUntilNanos;

    public DownstreamCircuitBreaker(int threshold, Duration openDuration) {
        this(threshold, openDuration, System::nanoTime);
    }

    DownstreamCircuitBreaker(int threshold, Duration openDuration, LongSupplier nanoClock) {
        this.threshold = threshold;
        this.openNanos = openDuration.toNanos();
        this.nanoClock = nanoClock;
    }

    public State state() {
        if (!tripped) {
            return State.CLOSED;
        }
        return nanoClock.getAsLong() - openUntilNanos < 0 ? State.OPEN : State.HALF_OPEN;
    }

    public boolean allowRequest() {
        return state() != State.OPEN;
    }

    /** Whole seconds until the circuit half-opens (at least 1 while open). */
    public long retryAfterSeconds() {
        long remaining = openUntilNanos - nanoClock.getAsLong();
        return Math.max(1, Math.ceilDiv(remaining, TimeUnit.SECONDS.toNanos(1)));
    }

    public void onSuccess() {
        consecutiveFailures.set(0);
        tripped = false;
    }

    public void onFailure() {
        if (consecutiveFailures.incrementAndGet() >= threshold) {
            openUntilNanos = nanoClock.getAsLong() + openNanos;
            tripped = true;
        }
    }
}
