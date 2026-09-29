package gov.openpto.ingest.transform;

import static org.assertj.core.api.Assertions.assertThat;

import gov.openpto.ingest.transform.model.Party;
import gov.openpto.ingest.transform.model.PatentRecord;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

class ApplicationPublicationTransformTest {

    @Test
    void publicationSample_threeDocuments() throws Exception {
        TestSupport.Result result = TestSupport.run(TestSupport.sample("ipa240418-sample.xml"));

        assertThat(result.summary().format()).isEqualTo(DocumentFormat.US_PATENT_APPLICATION);
        assertThat(result.summary().recordsOk()).isEqualTo(3);
        assertThat(result.records()).extracting(r -> ((PatentRecord) r).patentNumber())
                .containsExactly("US20240123456A1", "US20240123457A1", "US20240123458A1");
    }

    @Test
    void publication_fieldLevelMapping() throws Exception {
        PatentRecord p = TestSupport.run(TestSupport.sample("ipa240418-sample.xml")).record(0);

        assertThat(p.applicationNumber()).isEqualTo("18/234,567");
        assertThat(p.status()).isEqualTo("PENDING");
        assertThat(p.grantDate()).isNull();
        assertThat(p.expirationDate()).isNull();
        assertThat(p.publicationDate()).isEqualTo(LocalDate.of(2024, 4, 18));
        assertThat(p.filingDate()).isEqualTo(LocalDate.of(2023, 10, 16));
        assertThat(p.primaryCpc()).isEqualTo("G06N 3/08");
        assertThat(p.assignees()).containsExactly(new Party("Quillfeather Compute, Inc.", "Seattle", "WA", "US"));
        assertThat(p.inventors()).extracting(Party::name).containsExactly("Meera Iyer");
        assertThat(p.claims()).hasSize(2);
        assertThat(p.claims().get(1).dependsOn()).isEqualTo(1);
    }

    @Test
    void publication_foreignPriorityAndNoAssignee() throws Exception {
        TestSupport.Result result = TestSupport.run(TestSupport.sample("ipa240418-sample.xml"));
        PatentRecord second = result.record(1);
        PatentRecord third = result.record(2);

        assertThat(second.priorityDate()).isEqualTo(LocalDate.of(2022, 11, 4));
        assertThat(second.assignees()).extracting(Party::country).containsExactly("NO");
        assertThat(third.assignees()).isEmpty();
        assertThat(third.citations()).isEmpty();
    }
}