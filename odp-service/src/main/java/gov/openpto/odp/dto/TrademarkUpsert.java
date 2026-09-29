package gov.openpto.odp.dto;

import gov.openpto.odp.model.MarkType;
import gov.openpto.odp.model.RecordSource;
import gov.openpto.odp.model.TrademarkStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/** Trademark record accepted by the internal bulk upsert; keyed by {@code serialNumber}. */
@Schema(description = "Trademark upsert record")
public record TrademarkUpsert(
        @Schema(example = "97123456") @NotBlank @Pattern(regexp = "^[0-9]{6,12}$", message = "must be 6-12 digits")
        String serialNumber,
        @Schema(example = "7012345") @Size(max = 16) String registrationNumber,
        @Schema(example = "BLUE HERON ROASTERS") @NotBlank @Size(max = 500) String markText,
        MarkType markType,
        TrademarkStatus status,
        @NotNull LocalDate filingDate,
        LocalDate registrationDate,
        LocalDate statusDate,
        @Size(max = 300) String owner,
        @Size(max = 500) String ownerAddress,
        @Size(max = 200) String attorney,
        @Pattern(regexp = "^(1A|1B|44D|44E|66A)$", message = "must be one of 1A, 1B, 44D, 44E, 66A") String filingBasis,
        @Size(max = 100) List<@Valid @NotNull GoodsServiceDto> goodsAndServices,
        @Size(max = 500) List<@Valid @NotNull TrademarkEventDto> events,
        @Size(max = 64) String ingestJobId,
        @Schema(example = "INGEST", description = "Ignored on the internal endpoint (always INGEST)") RecordSource source) {
}
