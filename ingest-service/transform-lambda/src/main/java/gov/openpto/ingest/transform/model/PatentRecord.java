package gov.openpto.ingest.transform.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;
import java.util.List;

/**
 * Neutral patent record, shaped like odp-service {@code PatentUpsert}
 * ({@code POST /internal/v1/patents/bulk-upsert}, keyed by {@code patentNumber}).
 */
public record PatentRecord(
        String patentNumber,
        String applicationNumber,
        String title,
        String type,
        String status,
        LocalDate filingDate,
        LocalDate grantDate,
        LocalDate publicationDate,
        String primaryCpc,
        List<String> cpcCodes,
        @JsonProperty("abstract") String abstractText,
        List<Claim> claims,
        List<Citation> citations,
        List<Party> inventors,
        List<Party> assignees,
        LocalDate priorityDate,
        LocalDate expirationDate,
        String examiner,
        String artUnit,
        String ingestJobId,
        String source) {

    public static final String SOURCE_INGEST = "INGEST";
}