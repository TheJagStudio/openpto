package gov.openpto.ingest.transform.model;

import java.time.LocalDate;

/** Prosecution history entry (TSDR "events"). */
public record TrademarkEvent(LocalDate date, String code, String description) {
}