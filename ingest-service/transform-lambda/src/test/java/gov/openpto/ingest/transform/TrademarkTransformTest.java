package gov.openpto.ingest.transform;

import static org.assertj.core.api.Assertions.assertThat;

import gov.openpto.ingest.transform.model.GoodsAndServices;
import gov.openpto.ingest.transform.model.TrademarkEvent;
import gov.openpto.ingest.transform.model.TrademarkRecord;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

class TrademarkTransformTest {

    @Test
    void trademarkSample_fiveCaseFiles() throws Exception {
        TestSupport.Result result = TestSupport.run(TestSupport.sample("apc240102-trademark-sample.xml"));

        assertThat(result.summary().format()).isEqualTo(DocumentFormat.TRADEMARK_DAILY);
        assertThat(result.summary().documents()).isEqualTo(1);
        assertThat(result.summary().recordsOk()).isEqualTo(5);
        assertThat(result.records()).extracting(r -> ((TrademarkRecord) r).serialNumber())
                .containsExactly("97123456", "98234567", "90876543", "79345678", "88765432");
        assertThat(result.records()).extracting(r -> ((TrademarkRecord) r).status())
                .containsExactly("LIVE_REGISTERED", "LIVE_PENDING", "DEAD_ABANDONED", "LIVE_REGISTERED", "DEAD_CANCELLED");
        assertThat(result.records()).extracting(r -> ((TrademarkRecord) r).filingBasis())
                .containsExactly("1A", "1B", "1B", "66A", "1A");
        assertThat(result.records()).extracting(r -> ((TrademarkRecord) r).markType())
                .containsExactly("STANDARD_CHARACTER", "STANDARD_CHARACTER", "DESIGN", "DESIGN", "SOUND");
    }

    @Test
    void trademark_fieldLevelMapping() throws Exception {
        TrademarkRecord t = TestSupport.run(TestSupport.sample("apc240102-trademark-sample.xml")).record(0);

        assertThat(t.registrationNumber()).isEqualTo("7123456");
        assertThat(t.markText()).isEqualTo("BLUE HERON ROASTERS");
        assertThat(t.statusCode()).isEqualTo("700");
        assertThat(t.filingDate()).isEqualTo(LocalDate.of(2021, 11, 15));
        assertThat(t.registrationDate()).isEqualTo(LocalDate.of(2023, 7, 4));
        assertThat(t.statusDate()).isEqualTo(LocalDate.of(2023, 7, 4));
        assertThat(t.owner()).isEqualTo("Blue Heron Coffee Company, LLC");
        assertThat(t.ownerAddress()).isEqualTo("112 Waterfront Way, Suite 4, Portland OR 97209, US");
        assertThat(t.attorney()).isEqualTo("Priya Raman");
        assertThat(t.niceClasses()).containsExactly(30, 43);
        assertThat(t.goodsAndServices()).containsExactly(
                new GoodsAndServices(30, "Coffee; coffee beans; ground coffee; tea"),
                new GoodsAndServices(43, "Cafe and coffee house services"));
        assertThat(t.events()).hasSize(4);
        assertThat(t.events().get(0)).isEqualTo(new TrademarkEvent(LocalDate.of(2021, 11, 18), "NWAP", "NEW APPLICATION ENTERED"));
        assertThat(t.events().get(3).code()).isEqualTo("R.PR");
        assertThat(t.ingestJobId()).isEqualTo("job-1");
    }

    @Test
    void trademark_zeroRegistrationNumberIsNull() throws Exception {
        TrademarkRecord t = TestSupport.run(TestSupport.sample("apc240102-trademark-sample.xml")).record(1);

        assertThat(t.registrationNumber()).isNull();
        assertThat(t.registrationDate()).isNull();
        assertThat(t.niceClasses()).containsExactly(9);
        assertThat(t.ownerAddress()).isEqualTo("77 Greenhouse Row, Durham NC 27701, US");
    }

    @Test
    void trademark_caseFileMissingMarkText_isRecordErrorOthersContinue() throws Exception {
        String xml = """
                <?xml version="1.0"?>
                <trademark-applications-daily><application-information><file-segments><action-keys>
                <case-file><serial-number>97000001</serial-number><case-file-header><filing-date>20240101</filing-date></case-file-header></case-file>
                <case-file><serial-number>97000002</serial-number><case-file-header><filing-date>20240101</filing-date>
                <mark-identification>OK MARK</mark-identification></case-file-header></case-file>
                </action-keys></file-segments></application-information></trademark-applications-daily>
                """;
        TestSupport.Result result = TestSupport.run(xml);

        assertThat(result.summary().recordsOk()).isEqualTo(1);
        assertThat(result.summary().recordsFailed()).isEqualTo(1);
        assertThat(result.summary().errors()).singleElement().satisfies(e -> {
            assertThat(e.index()).isZero();
            assertThat(e.identifier()).isEqualTo("97000001");
            assertThat(e.message()).contains("mark-identification");
        });
        assertThat(((TrademarkRecord) result.record(0)).niceClasses()).isEmpty();
    }
}