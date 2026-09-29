package gov.openpto.odp.seed;

import static org.assertj.core.api.Assertions.assertThat;

import gov.openpto.odp.dto.CitationDto;
import gov.openpto.odp.dto.ClaimDto;
import gov.openpto.odp.dto.PatentUpsert;
import gov.openpto.odp.dto.TrademarkEventDto;
import gov.openpto.odp.dto.TrademarkUpsert;
import gov.openpto.odp.model.PatentStatus;
import gov.openpto.odp.model.PatentType;
import gov.openpto.odp.model.RecordSource;
import gov.openpto.odp.model.TrademarkStatus;
import gov.openpto.odp.service.RecordNormalizer;
import jakarta.validation.Validation;
import jakarta.validation.Validator;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class MockDataGeneratorTest {

    static List<PatentUpsert> patents;
    static List<TrademarkUpsert> trademarks;

    @BeforeAll
    static void generate() {
        MockDataGenerator generator = new MockDataGenerator(20250119L);
        patents = generator.patents(2000);
        trademarks = generator.trademarks(800);
    }

    @Test
    void sameSeed_producesIdenticalData_differentSeedDiffers() {
        MockDataGenerator again = new MockDataGenerator(20250119L);
        assertThat(again.patents(2000)).isEqualTo(patents);
        assertThat(again.trademarks(800)).isEqualTo(trademarks);
        assertThat(new MockDataGenerator(7L).patents(50)).isNotEqualTo(patents.subList(0, 50));
    }

    @Test
    void patents_haveUniqueNumbersAndRealisticShape() {
        assertThat(patents).extracting(PatentUpsert::patentNumber).doesNotHaveDuplicates();
        assertThat(patents).extracting(PatentUpsert::applicationNumber).doesNotHaveDuplicates()
                .allMatch(a -> a.matches("^\\d{2}/\\d{3},\\d{3}$"));
        assertThat(patents).allSatisfy(p -> {
            assertThat(p.source()).isEqualTo(RecordSource.SEED);
            assertThat(p.title()).isNotBlank();
            assertThat(p.claims()).hasSizeBetween(1, 30);
            assertThat(p.inventors()).isNotEmpty();
        });
        Map<PatentType, Long> byType = patents.stream().collect(Collectors.groupingBy(PatentUpsert::type, Collectors.counting()));
        assertThat(byType).containsKeys(PatentType.UTILITY, PatentType.DESIGN, PatentType.PLANT, PatentType.REISSUE);
        Set<String> sections = patents.stream().filter(p -> p.primaryCpc() != null)
                .map(p -> p.primaryCpc().substring(0, 1)).collect(Collectors.toSet());
        assertThat(sections).containsAll(List.of("A", "B", "C", "D", "E", "F", "G", "H"));
        assertThat(patents).anyMatch(p -> p.title().toLowerCase().contains("battery"));
    }

    @Test
    void patents_datesAndStatusesAreConsistent() {
        for (PatentUpsert p : patents) {
            assertThat(p.priorityDate()).isBeforeOrEqualTo(p.filingDate());
            switch (p.status()) {
                case GRANTED, EXPIRED -> {
                    assertThat(p.grantDate()).isAfter(p.filingDate()).isBeforeOrEqualTo(MockDataGenerator.AS_OF);
                    if (p.type() == PatentType.DESIGN) {
                        assertThat(p.expirationDate()).isIn(p.grantDate().plusYears(14), p.grantDate().plusYears(15));
                    } else {
                        assertThat(p.expirationDate()).isEqualTo(p.filingDate().plusYears(20));
                    }
                    assertThat(p.status() == PatentStatus.EXPIRED).isEqualTo(p.expirationDate().isBefore(MockDataGenerator.AS_OF));
                }
                case PENDING, ABANDONED -> {
                    assertThat(p.grantDate()).isNull();
                    assertThat(p.patentNumber()).endsWith("A1");
                }
            }
        }
        assertThat(patents).extracting(PatentUpsert::status).contains(PatentStatus.values());
    }

    @Test
    void claims_dependOnEarlierClaims_andSurviveNormalization() {
        RecordNormalizer normalizer = new RecordNormalizer(Clock.fixed(MockDataGenerator.AS_OF.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC));
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        for (PatentUpsert p : patents) {
            assertThat(p.claims().getFirst().independent()).isTrue();
            for (ClaimDto c : p.claims()) {
                if (c.dependsOn() != null) {
                    assertThat(c.dependsOn()).isLessThan(c.number());
                    assertThat(c.text()).contains("of claim " + c.dependsOn());
                }
            }
            PatentUpsert normalized = normalizer.patent(p, RecordSource.SEED);
            assertThat(normalized.status()).isEqualTo(p.status());
            assertThat(validator.validate(p)).isEmpty();
        }
    }

    @Test
    void citations_pointAtSeededPatentsGrantedBeforeFiling() {
        Map<String, PatentUpsert> byNumber = patents.stream().collect(Collectors.toMap(PatentUpsert::patentNumber, Function.identity()));
        long withCitations = 0;
        for (PatentUpsert p : patents) {
            Set<String> seen = new HashSet<>();
            for (CitationDto c : p.citations()) {
                PatentUpsert cited = byNumber.get(c.patentNumber());
                assertThat(cited).as("cited %s exists", c.patentNumber()).isNotNull();
                assertThat(cited.grantDate()).isBefore(p.filingDate());
                assertThat(seen.add(c.patentNumber())).isTrue();
            }
            withCitations += p.citations().isEmpty() ? 0 : 1;
        }
        assertThat(withCitations).isGreaterThan(patents.size() / 2);
    }

    @Test
    void trademarks_haveConsistentHistories() {
        assertThat(trademarks).extracting(TrademarkUpsert::serialNumber).doesNotHaveDuplicates()
                .allMatch(s -> s.matches("^\\d{8}$"));
        assertThat(trademarks).filteredOn(t -> t.registrationNumber() != null)
                .extracting(TrademarkUpsert::registrationNumber).doesNotHaveDuplicates();
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        for (TrademarkUpsert t : trademarks) {
            assertThat(validator.validate(t)).isEmpty();
            assertThat(t.events()).isSortedAccordingTo(Comparator.comparing(TrademarkEventDto::date));
            assertThat(t.events().getFirst().code()).isEqualTo("NWAP");
            assertThat(t.events()).allSatisfy(e -> assertThat(e.date()).isBeforeOrEqualTo(MockDataGenerator.AS_OF));
            assertThat(t.statusDate()).isEqualTo(t.events().getLast().date());
            assertThat(t.goodsAndServices()).isNotEmpty().allSatisfy(g -> assertThat(g.niceClass()).isBetween(1, 45));
            List<String> codes = t.events().stream().map(TrademarkEventDto::code).toList();
            switch (t.status()) {
                case LIVE_REGISTERED -> {
                    assertThat(codes).contains("R.PR");
                    assertThat(t.registrationDate()).isAfter(t.filingDate());
                    assertThat(t.registrationNumber()).isNotNull();
                }
                case DEAD_CANCELLED -> assertThat(codes).contains("R.PR", "COS8");
                case DEAD_ABANDONED -> assertThat(codes).anyMatch(c -> c.startsWith("ABN"));
                case LIVE_PENDING -> assertThat(t.registrationDate()).isNull();
            }
            if ("66A".equals(t.filingBasis())) {
                assertThat(t.serialNumber()).startsWith("79");
            }
        }
        assertThat(trademarks).extracting(TrademarkUpsert::status).contains(TrademarkStatus.values());
    }
}
