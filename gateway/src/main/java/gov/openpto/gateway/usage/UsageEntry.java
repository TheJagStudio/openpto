package gov.openpto.gateway.usage;

import java.time.LocalDate;

/** One element of {@code { entries:[{ keyId, date, count }] }}. */
public record UsageEntry(String keyId, LocalDate date, long count) {
}
