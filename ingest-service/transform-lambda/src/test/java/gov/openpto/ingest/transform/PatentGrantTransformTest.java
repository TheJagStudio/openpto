package gov.openpto.ingest.transform;

import static org.assertj.core.api.Assertions.assertThat;

import gov.openpto.ingest.transform.model.Citation;
import gov.openpto.ingest.transform.model.Claim;
import gov.openpto.ingest.transform.model.Party;
import gov.openpto.ingest.transform.model.PatentRecord;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

class PatentGrantTransformTest {

    @Test
    void grantSample_fiveConcatenatedDocuments_allMapped() throws Exception {
        TestSupport.Result result = TestSupport.run(TestSupport.sample("ipg250107-sample.xml"));

        assertThat(result.summary().format()).isEqualTo(DocumentFormat.US_PATENT_GRANT);
        assertThat(result.summary().documents()).isEqualTo(5);
        assertThat(result.summary().recordsOk()).isEqualTo(5);
        assertThat(result.summary().recordsFailed()).isZero();
        assertThat(result.records()).extracting(r -> ((PatentRecord) r).patentNumber())
                .containsExactly("US12345601B2", "US12345602B2", "US12345603B1", "US12345604B2", "US12345605B2");
    }

    @Test
    void grantSample_firstDocument_fieldLevelMapping() throws Exception {
        PatentRecord p = TestSupport.run(TestSupport.sample("ipg250107-sample.xml")).record(0);

        assertThat(p.applicationNumber()).isEqualTo("17/482,913");
        assertThat(p.title()).isEqualTo("Adaptive query caching for distributed ledger indexes");
        assertThat(p.type()).isEqualTo("UTILITY");
        assertThat(p.status()).isEqualTo("GRANTED");
        assertThat(p.filingDate()).isEqualTo(LocalDate.of(2021, 9, 23));
        assertThat(p.grantDate()).isEqualTo(LocalDate.of(2025, 1, 7));
        assertThat(p.priorityDate()).isEqualTo(LocalDate.of(2020, 9, 21));
        assertThat(p.expirationDate()).isEqualTo(LocalDate.of(2041, 9, 23).plusDays(212));
        assertThat(p.primaryCpc()).isEqualTo("G06F 16/2455");
        assertThat(p.cpcCodes()).containsExactly("G06F 16/2455", "G06F 16/9535");
        assertThat(p.abstractText()).startsWith("A query cache for a distributed ledger index")
                .doesNotContain("\n").doesNotContain("  ");
        assertThat(p.inventors()).containsExactly(
                new Party("Elena Marchetti", "Austin", "TX", "US"),
                new Party("Kwame Osei", "Round Rock", "TX", "US"));
        assertThat(p.assignees()).containsExactly(new Party("Northwind Ledger Systems, Inc.", "Austin", "TX", "US"));
        assertThat(p.examiner()).isEqualTo("Hiro Nakamura");
        assertThat(p.artUnit()).isEqualTo("2161");
        assertThat(p.citations()).containsExactly(
                new Citation("US9876543B2", "EXAMINER"),
                new Citation("US10234567B1", "APPLICANT"));
        assertThat(p.ingestJobId()).isEqualTo("job-1");
        assertThat(p.source()).isEqualTo("INGEST");
    }

    @Test
    void grantSample_claimsWithDependencies() throws Exception {
        PatentRecord p = TestSupport.run(TestSupport.sample("ipg250107-sample.xml")).record(0);

        assertThat(p.claims()).hasSize(3);
        Claim first = p.claims().get(0);
        assertThat(first.number()).isEqualTo(1);
        assertThat(first.independent()).isTrue();
        assertThat(first.dependsOn()).isNull();
        assertThat(first.text()).startsWith("1. A method comprising: receiving a range query");
        assertThat(p.claims().get(1)).satisfies(c -> {
            assertThat(c.dependsOn()).isEqualTo(1);
            assertThat(c.independent()).isFalse();
            assertThat(c.text()).contains("The method of claim 1, further comprising");
        });
        assertThat(p.claims().get(2).dependsOn()).isEqualTo(2);
    }

    @Test
    void grantSample_foreignCitationAndSubscriptText() throws Exception {
        PatentRecord p = TestSupport.run(TestSupport.sample("ipg250107-sample.xml")).record(1);

        assertThat(p.citations()).contains(new Citation("JP2016123456A", "EXAMINER"));
        assertThat(p.abstractText()).contains("SpO2 accuracy");
        assertThat(p.expirationDate()).isEqualTo(LocalDate.of(2042, 1, 14));
    }

    @Test
    void grantSample_characterReferencesDecoded() throws Exception {
        PatentRecord p = TestSupport.run(TestSupport.sample("ipg250107-sample.xml")).record(2);

        assertThat(p.abstractText()).contains("more than 250% without delamination");
        assertThat(p.inventors()).extracting(Party::country).containsExactly("US", "PL");
        assertThat(p.cpcCodes()).containsExactly("H01M 10/0525", "Y02E 60/10");
    }
}