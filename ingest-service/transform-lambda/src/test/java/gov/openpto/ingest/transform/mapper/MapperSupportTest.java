package gov.openpto.ingest.transform.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gov.openpto.ingest.transform.DocumentFormat;
import gov.openpto.ingest.transform.MappingException;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

class MapperSupportTest {

    @Test
    void date_parsesBasicAndIsoAndRejectsGarbage() {
        assertThat(MapperSupport.date("20250107")).isEqualTo(LocalDate.of(2025, 1, 7));
        assertThat(MapperSupport.date("2025-01-07")).isEqualTo(LocalDate.of(2025, 1, 7));
        assertThat(MapperSupport.date("2025-01-07T10:00:00Z")).isEqualTo(LocalDate.of(2025, 1, 7));
        assertThat(MapperSupport.date("19961100")).isNull();
        assertThat(MapperSupport.date("00000000")).isNull();
        assertThat(MapperSupport.date(" ")).isNull();
        assertThat(MapperSupport.date(null)).isNull();
        assertThat(MapperSupport.date("soon")).isNull();
    }

    @Test
    void patentNumber_normalisesPaddingPrefixesAndKind() {
        assertThat(MapperSupport.patentNumber("06334567", "B1")).isEqualTo("US6334567B1");
        assertThat(MapperSupport.patentNumber("D0453921", "S1")).isEqualTo("USD453921S1");
        assertThat(MapperSupport.patentNumber("PP012345", "P2")).isEqualTo("USPP12345P2");
        assertThat(MapperSupport.patentNumber("RE038123", "E")).isEqualTo("USRE38123E");
        assertThat(MapperSupport.patentNumber("20240123456", "a1")).isEqualTo("US20240123456A1");
        assertThat(MapperSupport.patentNumber("US 9,876,543", null)).isEqualTo("US9876543");
        assertThat(MapperSupport.patentNumber("", "B1")).isNull();
        assertThat(MapperSupport.patentNumber(null, "B1")).isNull();
    }

    @Test
    void applicationNumber_formatsSeriesCode() {
        assertThat(MapperSupport.applicationNumber("17123456")).isEqualTo("17/123,456");
        assertThat(MapperSupport.applicationNumber("09/512384")).isEqualTo("09/512,384");
        assertThat(MapperSupport.applicationNumber("PCT/US2020/1")).isEqualTo("PCT/US2020/1");
        assertThat(MapperSupport.applicationNumber(" ")).isNull();
    }

    @Test
    void patentType_fromAttributeNumberOrKind() {
        assertThat(MapperSupport.patentType("utility", "1", "B2")).isEqualTo("UTILITY");
        assertThat(MapperSupport.patentType("design", "1", "B2")).isEqualTo("DESIGN");
        assertThat(MapperSupport.patentType("plant", "1", "B2")).isEqualTo("PLANT");
        assertThat(MapperSupport.patentType("reissue", "1", "B2")).isEqualTo("REISSUE");
        assertThat(MapperSupport.patentType("other", "D0123", null)).isEqualTo("DESIGN");
        assertThat(MapperSupport.patentType(null, "PP123", null)).isEqualTo("PLANT");
        assertThat(MapperSupport.patentType(null, "RE123", null)).isEqualTo("REISSUE");
        assertThat(MapperSupport.patentType(null, "123", "E1")).isEqualTo("REISSUE");
        assertThat(MapperSupport.patentType(null, null, null)).isEqualTo("UTILITY");
    }

    @Test
    void numberJoinNameAndRequire() {
        assertThat(MapperSupport.number("CLM-00012")).isEqualTo(12);
        assertThat(MapperSupport.number("none")).isNull();
        assertThat(MapperSupport.number(null)).isNull();
        assertThat(MapperSupport.number("99999999999999")).isNull();
        assertThat(MapperSupport.joinName("Ann", "Lee")).isEqualTo("Ann Lee");
        assertThat(MapperSupport.joinName(null, "Lee")).isEqualTo("Lee");
        assertThat(MapperSupport.joinName("Ann", " ")).isEqualTo("Ann");
        assertThat(MapperSupport.blankToNull(" x ")).isEqualTo("x");
        assertThatThrownBy(() -> MapperSupport.require(" ", "id", "title"))
                .isInstanceOf(MappingException.class)
                .hasMessage("Missing required field: title")
                .extracting(e -> ((MappingException) e).identifier()).isEqualTo("id");
        assertThat(MapperSupport.text(null, "a")).isNull();
    }

    @Test
    void trademarkStatusAndMarkTypeTables() {
        assertThat(TrademarkCaseFileMapper.status("800")).isEqualTo("LIVE_REGISTERED");
        assertThat(TrademarkCaseFileMapper.status("618")).isEqualTo("DEAD_ABANDONED");
        assertThat(TrademarkCaseFileMapper.status("900")).isEqualTo("DEAD_CANCELLED");
        assertThat(TrademarkCaseFileMapper.status("686")).isEqualTo("LIVE_PENDING");
        assertThat(TrademarkCaseFileMapper.status(null)).isEqualTo("LIVE_PENDING");
        assertThat(TrademarkCaseFileMapper.markType(null)).isEqualTo("STANDARD_CHARACTER");
        assertThat(TrademarkCaseFileMapper.markType("2000")).isEqualTo("DESIGN");
    }

    @Test
    void formatDetectionAndMapperFactory() {
        assertThat(DocumentFormat.fromRootElement("us-patent-application-publication")).isEqualTo(DocumentFormat.US_PATENT_APPLICATION);
        assertThat(DocumentFormat.fromRootElement("PATDOC")).isEqualTo(DocumentFormat.PATDOC_LEGACY);
        assertThat(DocumentFormat.fromRootElement(null)).isEqualTo(DocumentFormat.UNKNOWN);
        assertThat(DocumentFormat.TRADEMARK_DAILY.target()).isEqualTo(DocumentFormat.Target.TRADEMARKS);
        assertThat(RecordMapper.forFormat(DocumentFormat.PATDOC_LEGACY)).isInstanceOf(PatdocMapper.class);
        assertThatThrownBy(() -> RecordMapper.forFormat(DocumentFormat.UNKNOWN)).isInstanceOf(IllegalArgumentException.class);
    }
}