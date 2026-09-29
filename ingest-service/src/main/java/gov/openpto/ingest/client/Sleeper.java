package gov.openpto.ingest.client;

import java.time.Duration;

/** Back-off sleep, injectable so tests do not wait. */
@FunctionalInterface
public interface Sleeper {

    Sleeper THREAD = duration -> Thread.sleep(duration.toMillis());

    void sleep(Duration duration) throws InterruptedException;
}