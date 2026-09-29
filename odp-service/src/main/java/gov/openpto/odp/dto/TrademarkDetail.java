package gov.openpto.odp.dto;

import gov.openpto.odp.model.MarkType;
import gov.openpto.odp.model.RecordSource;
import gov.openpto.odp.model.TrademarkStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.List;

@Schema(description = "TSDR-style trademark record")
public record TrademarkDetail(
        @Schema(example = "97123456") String serialNumber,
        @Schema(example = "7012345") String registrationNumber,
        @Schema(example = "BLUE HERON ROASTERS") String markText,
        MarkType markType,
        TrademarkStatus status,
        LocalDate filingDate,
        LocalDate registrationDate,
        @Schema(example = "Blue Heron Coffee Co.") String owner,
        List<Integer> niceClasses,
        List<GoodsServiceDto> goodsAndServices,
        @Schema(example = "1200 Harbor Way, Seattle, WA 98101, US") String ownerAddress,
        @Schema(example = "Jordan P. Ellis") String attorney,
        @Schema(example = "1A", allowableValues = {"1A", "1B", "44D", "44E", "66A"}) String filingBasis,
        List<TrademarkEventDto> events,
        LocalDate statusDate,
        RecordSource source,
        String ingestJobId) {
}
