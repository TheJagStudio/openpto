package gov.openpto.odp.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import gov.openpto.odp.config.AppProperties;
import gov.openpto.odp.dto.PatentFilter;
import gov.openpto.odp.dto.TrademarkFilter;
import gov.openpto.odp.exception.BadRequestException;
import gov.openpto.odp.repository.ExportRepository;
import gov.openpto.odp.repository.ExportRepository.PatentRow;
import gov.openpto.odp.repository.ExportRepository.TrademarkRow;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class ExportServiceTest {

    static final JsonMapper JSON = JsonMapper.builder().build();

    ExportRepository repo = mock(ExportRepository.class);
    PlatformTransactionManager tx = mock(PlatformTransactionManager.class);
    ExportService service;

    static final PatentRow ROW = new PatentRow("US1B2", "17/123,456", "Widget, improved", "UTILITY", "GRANTED",
            LocalDate.of(2020, 1, 2), LocalDate.of(2022, 3, 4), LocalDate.of(2040, 1, 2), "H01M10/05",
            List.of("Acme Inc.", "Beta LLC"), List.of("=cmd()"), "Line one\nwith \"quotes\"", "SEED");

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        when(tx.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        service = new ExportService(repo, JSON, new AppProperties.Export(10_000), tx);
        doAnswer(inv -> {
            Consumer<PatentRow> sink = inv.getArgument(2);
            sink.accept(ROW);
            sink.accept(ROW);
            return null;
        }).when(repo).streamPatents(any(), eq(10_000), any(Consumer.class));
        doAnswer(inv -> {
            Consumer<TrademarkRow> sink = inv.getArgument(2);
            sink.accept(new TrademarkRow("97123456", null, "MARK", "STANDARD_CHARACTER", "LIVE_PENDING",
                    LocalDate.of(2021, 1, 1), null, LocalDate.of(2021, 2, 1), "Owner", "1A", List.of(30, 43), "SEED"));
            return null;
        }).when(repo).streamTrademarks(any(), eq(5), any(Consumer.class));
    }

    @Test
    void patentsCsv_writesHeaderAndEscapedRows() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        service.patents(PatentFilter.empty(), null, null, ExportService.Format.CSV).writeTo(out);

        String[] lines = out.toString(StandardCharsets.UTF_8).split("\r\n");
        assertThat(lines[0]).startsWith("patentNumber,applicationNumber,title,type,status");
        assertThat(lines[1]).isEqualTo("US1B2,\"17/123,456\",\"Widget, improved\",UTILITY,GRANTED,2020-01-02,2022-03-04,"
                + "2040-01-02,H01M10/05,Acme Inc.; Beta LLC,'=cmd(),SEED,\"Line one\nwith \"\"quotes\"\"\"");
        assertThat(lines).hasSize(3);
    }

    @Test
    void patentsJson_streamsArrayWithAbstractField() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        service.patents(PatentFilter.empty(), "patentNumber,asc", null, ExportService.Format.JSON).writeTo(out);

        JsonNode array = JSON.readTree(out.toString(StandardCharsets.UTF_8));
        assertThat(array.isArray()).isTrue();
        assertThat(array.size()).isEqualTo(2);
        assertThat(array.get(0).get("abstract").asString()).startsWith("Line one");
        assertThat(array.get(0).get("filingDate").asString()).isEqualTo("2020-01-02");
        assertThat(array.get(0).get("assignees").size()).isEqualTo(2);
    }

    @Test
    void trademarksCsv_withLimit() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        service.trademarks(TrademarkFilter.empty(), null, 5, ExportService.Format.CSV).writeTo(out);

        assertThat(out.toString(StandardCharsets.UTF_8))
                .contains("97123456,,MARK,STANDARD_CHARACTER,LIVE_PENDING,2021-01-01,,2021-02-01,Owner,1A,30; 43,SEED");
    }

    @Test
    void trademarksJson() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        service.trademarks(TrademarkFilter.empty(), null, 5, ExportService.Format.JSON).writeTo(out);

        assertThat(JSON.readTree(out.toByteArray()).get(0).get("niceClasses").size()).isEqualTo(2);
    }

    @Test
    void invalidLimitOrSort_failsEagerlyBeforeStreaming() {
        assertThatThrownBy(() -> service.patents(PatentFilter.empty(), null, 0, ExportService.Format.CSV))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.patents(PatentFilter.empty(), null, 10_001, ExportService.Format.CSV))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.trademarks(TrademarkFilter.empty(), "markText,asc", null, ExportService.Format.CSV))
                .isInstanceOf(BadRequestException.class);
        verify(repo, never()).streamPatents(any(), org.mockito.ArgumentMatchers.anyInt(), any());
    }

    @Test
    void csvCell_handlesNullsListsAndFormulaInjection() {
        assertThat(CsvFormat.cell(null)).isEmpty();
        assertThat(CsvFormat.cell("+1")).isEqualTo("'+1");
        assertThat(CsvFormat.cell("@SUM")).isEqualTo("'@SUM");
        assertThat(CsvFormat.cell(" padded")).isEqualTo("\" padded\"");
        assertThat(CsvFormat.cell(List.of(1, 2))).isEqualTo("1; 2");
        assertThat(CsvFormat.row("a", null, 3)).isEqualTo("a,,3\r\n");
    }
}
