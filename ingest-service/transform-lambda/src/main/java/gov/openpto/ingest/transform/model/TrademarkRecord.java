package gov.openpto.ingest.transform.model;

import java.time.LocalDate;
import java.util.List;

/**
 * Neutral trademark record, shaped like odp-service {@code TrademarkUpsert}
 * ({@code POST /internal/v1/trademarks/bulk-upsert}, keyed by {@code serialNumber}).
 */
public record TrademarkRecord(
        String serialNumber,
        String registrationNumber,
        String markText,
        String markType,
        String status,
        String statusCode,
        LocalDate filingDate,
        LocalDate registrationDate,
        LocalDate statusDate,
        String owner,
        String ownerAddress,
        String attorney,
        String filingBasis,
        List<Integer> niceClasses,
        List<GoodsAndServices> goodsAndServices,
        List<TrademarkEvent> events,
        String ingestJobId,
        String source) {
}