package gov.openpto.ingest.transform;

import static org.assertj.core.api.Assertions.assertThat;

import gov.openpto.ingest.transform.model.Citation;
import gov.openpto.ingest.transform.model.Party;
import gov.openpto.ingest.transform.model.PatentRecord;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

class PatdocTransformTest {

    @Test
    void patdocSample_threeLegacyDocuments() throws Exception {
        TestSupport.Result result = TestSupport.run(TestSupport.sample("pg020101-patdoc-sample.xml"));

        assertThat(result.summary().format()).isEqualTo(DocumentFormat.PATDOC_LEGACY);
        assertThat(result.summary().recordsOk()).isEqualTo(3);
        assertThat(result.summary().recordsFailed()).isZero();
        assertThat(result.records()).extracting(r -> ((PatentRecord) r).patentNumber())
                .containsExactly("US6334567B1", "US6334568B2", "USD453921S1");
    }

    @Test
    void patdoc_fieldLevelMapping() throws Exception {
        PatentRecord p = TestSupport.run(TestSupport.sample("pg020101-patdoc-sample.xml")).record(0);

        assertThat(p.applicationNumber()).isEqualTo("09/512,384");
        assertThat(p.filingDate()).isEqualTo(LocalDate.of(2000, 2, 24));
        assertThat(p.grantDate()).isEqualTo(LocalDate.of(2002, 1, 1));
        assertThat(p.title()).isEqualTo("Packet router with weighted fair queue scheduling");
        assertThat(p.type()).isEqualTo("UTILITY");
        assertThat(p.status()).isEqualTo("GRANTED");
        assertThat(p.inventors()).containsExactly(
                new Party("Gregory Almeida", "San Jose", "CA", "US"),
                new Party("Yuki Tanabe", "Cupertino", "CA", "US"));
        assertThat(p.assignees()).containsExactly(new Party("Bayline Networks, Inc.", "San Jose", "CA", "US"));
        assertThat(p.examiner()).isEqualTo("Diane Okonkwo");
        assertThat(p.artUnit()).isEqualTo("2662");
        assertThat(p.abstractText()).startsWith("A router assigns each flow a weight");
        assertThat(p.citations()).containsExactly(
                new Citation("US5572678A", "EXAMINER"),
                new Citation("US5905730A", "APPLICANT"));
        assertThat(p.claims()).hasSize(2);
        assertThat(p.claims().get(1).dependsOn()).isEqualTo(1);
        assertThat(p.claims().get(1).text()).isEqualTo("2. The router of claim 1, wherein voice traffic is assigned the highest weight.");
        assertThat(p.expirationDate()).isEqualTo(LocalDate.of(2020, 2, 24));
        assertThat(p.cpcCodes()).isEmpty();
    }

    @Test
    void patdoc_foreignPartiesPriorityAndDesign() throws Exception {
        TestSupport.Result result = TestSupport.run(TestSupport.sample("pg020101-patdoc-sample.xml"));
        PatentRecord second = result.record(1);
        PatentRecord design = result.record(2);

        assertThat(second.priorityDate()).isEqualTo(LocalDate.of(1999, 7, 22));
        assertThat(second.inventors()).containsExactly(new Party("Klaus Brandt", "Bremen", null, "DE"));
        assertThat(second.abstractText()).contains("brewer's hot rinse cycle");
        assertThat(design.type()).isEqualTo("DESIGN");
        assertThat(design.applicationNumber()).isEqualTo("29/118,220");
        assertThat(design.expirationDate()).isEqualTo(LocalDate.of(2016, 1, 1));
        assertThat(design.abstractText()).isNull();
    }
}