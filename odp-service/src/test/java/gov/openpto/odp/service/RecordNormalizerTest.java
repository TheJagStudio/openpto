package gov.openpto.odp.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gov.openpto.odp.dto.CitationDto;
import gov.openpto.odp.dto.ClaimDto;
import gov.openpto.odp.dto.GoodsServiceDto;
import gov.openpto.odp.dto.PartyDto;
import gov.openpto.odp.dto.PatentUpsert;
import gov.openpto.odp.dto.TrademarkEventDto;
import gov.openpto.odp.dto.TrademarkUpsert;
import gov.openpto.odp.model.CitedBy;
import gov.openpto.odp.model.MarkType;
import gov.openpto.odp.model.PatentStatus;
import gov.openpto.odp.model.PatentType;
import gov.openpto.odp.model.RecordSource;
import gov.openpto.odp.model.TrademarkStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;

class RecordNormalizerTest {

    final RecordNormalizer normalizer =
            new RecordNormalizer(Clock.fixed(Instant.parse("2026-09-28T00:00:00Z"), ZoneOffset.UTC));

    static PatentUpsert patent(String number, String app, PatentType type, LocalDate filing, LocalDate grant,
                               List<String> cpc, String primary, List<ClaimDto> claims) {
        return new PatentUpsert(number, app, "  A title ", " ", type, null, filing, grant, null, null, primary, cpc,
                claims, null, List.of(new PartyDto(" Acme ", " ", null, "us")), List.of(new CitationDto("us1b2", CitedBy.EXAMINER)),
                null, null, "job-1", RecordSource.SEED);
    }

    @Test
    void patent_grantedUtility_derivesStatusExpirationAndPrimaryCpc() {
        PatentUpsert n = normalizer.patent(patent(" us11234567b2 ", null, PatentType.UTILITY, LocalDate.of(2015, 1, 1),
                LocalDate.of(2017, 1, 1), List.of("h01m 10/0562", "H01M4/13", "H01M10/0562"), null, null), RecordSource.INGEST);

        assertThat(n.patentNumber()).isEqualTo("US11234567B2");
        assertThat(n.status()).isEqualTo(PatentStatus.GRANTED);
        assertThat(n.expirationDate()).isEqualTo(LocalDate.of(2035, 1, 1));
        assertThat(n.primaryCpc()).isEqualTo("H01M10/0562");
        assertThat(n.cpcCodes()).containsExactly("H01M10/0562", "H01M4/13");
        assertThat(n.title()).isEqualTo("A title");
        assertThat(n.abstractText()).isNull();
        assertThat(n.claims()).isEmpty();
        assertThat(n.inventors()).isEmpty();
        assertThat(n.assignees()).containsExactly(new PartyDto("Acme", null, null, "US"));
        assertThat(n.citations()).containsExactly(new CitationDto("US1B2", CitedBy.EXAMINER));
        assertThat(n.source()).isEqualTo(RecordSource.INGEST);
    }

    @Test
    void patent_oldGrant_isExpired() {
        PatentUpsert n = normalizer.patent(patent("US1B1", null, PatentType.UTILITY, LocalDate.of(2004, 1, 1),
                LocalDate.of(2006, 1, 1), null, null, null), RecordSource.INGEST);

        assertThat(n.expirationDate()).isEqualTo(LocalDate.of(2024, 1, 1));
        assertThat(n.status()).isEqualTo(PatentStatus.EXPIRED);
    }

    @Test
    void patent_design_expiresFifteenYearsFromGrant() {
        PatentUpsert n = normalizer.patent(patent("USD1S", null, PatentType.DESIGN, LocalDate.of(2020, 1, 1),
                LocalDate.of(2021, 6, 1), null, null, null), RecordSource.INGEST);

        assertThat(n.expirationDate()).isEqualTo(LocalDate.of(2036, 6, 1));
    }

    @Test
    void patent_noGrant_isPendingWithoutExpiration_andExplicitPrimaryCpcLeads() {
        PatentUpsert n = normalizer.patent(patent("US20240000001A1", null, PatentType.UTILITY, LocalDate.of(2024, 1, 1),
                null, List.of("G06F16/245"), "g06n 3/08", null), RecordSource.INGEST);

        assertThat(n.status()).isEqualTo(PatentStatus.PENDING);
        assertThat(n.expirationDate()).isNull();
        assertThat(n.primaryCpc()).isEqualTo("G06N3/08");
        assertThat(n.cpcCodes()).containsExactly("G06N3/08", "G06F16/245");
    }

    @Test
    void patent_explicitStatusIsKept() {
        PatentUpsert p = patent("US1B1", null, PatentType.UTILITY, LocalDate.of(2020, 1, 1), null, null, null, null);
        PatentUpsert withStatus = new PatentUpsert(p.patentNumber(), null, p.title(), null, p.type(), PatentStatus.ABANDONED,
                p.filingDate(), null, null, null, null, null, null, null, null, null, null, null, null, null);

        assertThat(normalizer.patent(withStatus, RecordSource.INGEST).status()).isEqualTo(PatentStatus.ABANDONED);
    }

    @Test
    void patentKey_fallsBackToApplicationNumberDigits() {
        assertThat(RecordNormalizer.patentKey(patent(null, "17/123,456", PatentType.UTILITY, LocalDate.now(), null, null, null, null)))
                .isEqualTo("USAPP17123456");
        assertThat(RecordNormalizer.patentKey(patent(" ", " ", PatentType.UTILITY, LocalDate.now(), null, null, null, null)))
                .isNull();
        assertThat(RecordNormalizer.patentKey(patent(null, "n/a", PatentType.UTILITY, LocalDate.now(), null, null, null, null)))
                .isNull();
    }

    @Test
    void patent_withoutAnyKey_isRejected() {
        assertThatThrownBy(() -> normalizer.patent(
                patent(null, null, PatentType.UTILITY, LocalDate.now(), null, null, null, null), RecordSource.INGEST))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("patentNumber or applicationNumber");
    }

    @Test
    void patent_grantBeforeFiling_isRejected() {
        assertThatThrownBy(() -> normalizer.patent(patent("US1B1", null, PatentType.UTILITY, LocalDate.of(2020, 1, 2),
                LocalDate.of(2020, 1, 1), null, null, null), RecordSource.INGEST))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("grantDate must not be before filingDate");
    }

    @Test
    void claims_areSortedAndIndependenceDerivedFromDependsOn() {
        List<ClaimDto> out = RecordNormalizer.claims(List.of(
                new ClaimDto(3, "c3 ", true, 1),
                new ClaimDto(1, "c1", false, null),
                new ClaimDto(2, "c2", null, 1)));

        assertThat(out).containsExactly(
                new ClaimDto(1, "c1", true, null), new ClaimDto(2, "c2", false, 1), new ClaimDto(3, "c3", false, 1));
    }

    @Test
    void claims_duplicateOrForwardReference_isRejected() {
        assertThatThrownBy(() -> RecordNormalizer.claims(List.of(new ClaimDto(1, "a", true, null), new ClaimDto(1, "b", true, null))))
                .hasMessage("duplicate claim number 1");
        assertThatThrownBy(() -> RecordNormalizer.claims(List.of(new ClaimDto(1, "a", false, 2), new ClaimDto(2, "b", true, null))))
                .hasMessageContaining("does not precede it");
        assertThatThrownBy(() -> RecordNormalizer.claims(List.of(new ClaimDto(2, "b", false, 1))))
                .hasMessageContaining("claim 2 depends on claim 1");
    }

    static TrademarkUpsert trademark(LocalDate registration, List<TrademarkEventDto> events, TrademarkStatus status) {
        return new TrademarkUpsert(" 97123456 ", " ", " MARK ", null, status, LocalDate.of(2021, 1, 4), registration, null,
                " Owner ", null, null, null, List.of(new GoodsServiceDto(30, "Coffee")), events, null, null);
    }

    @Test
    void trademark_defaults_markTypeStatusAndStatusDateFromLastEvent() {
        TrademarkUpsert n = normalizer.trademark(trademark(null, List.of(
                new TrademarkEventDto(LocalDate.of(2021, 5, 1), "DOCK", "ASSIGNED"),
                new TrademarkEventDto(LocalDate.of(2021, 1, 6), "NWAP", "NEW")), null), RecordSource.INGEST);

        assertThat(n.serialNumber()).isEqualTo("97123456");
        assertThat(n.registrationNumber()).isNull();
        assertThat(n.markText()).isEqualTo("MARK");
        assertThat(n.owner()).isEqualTo("Owner");
        assertThat(n.markType()).isEqualTo(MarkType.STANDARD_CHARACTER);
        assertThat(n.status()).isEqualTo(TrademarkStatus.LIVE_PENDING);
        assertThat(n.events()).extracting(TrademarkEventDto::code).containsExactly("NWAP", "DOCK");
        assertThat(n.statusDate()).isEqualTo(LocalDate.of(2021, 5, 1));
    }

    @Test
    void trademark_registered_withoutEvents_usesRegistrationDate() {
        TrademarkUpsert n = normalizer.trademark(trademark(LocalDate.of(2022, 2, 1), null, null), RecordSource.INGEST);

        assertThat(n.status()).isEqualTo(TrademarkStatus.LIVE_REGISTERED);
        assertThat(n.statusDate()).isEqualTo(LocalDate.of(2022, 2, 1));
        assertThat(n.events()).isEmpty();
    }

    @Test
    void trademark_pendingWithoutEvents_usesFilingDate_andKeepsExplicitStatus() {
        TrademarkUpsert n = normalizer.trademark(trademark(null, null, TrademarkStatus.DEAD_ABANDONED), RecordSource.INGEST);

        assertThat(n.status()).isEqualTo(TrademarkStatus.DEAD_ABANDONED);
        assertThat(n.statusDate()).isEqualTo(LocalDate.of(2021, 1, 4));
    }

    @Test
    void trademark_registrationBeforeFiling_isRejected() {
        assertThatThrownBy(() -> normalizer.trademark(trademark(LocalDate.of(2020, 1, 1), null, null), RecordSource.INGEST))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
