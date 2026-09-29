package gov.openpto.odp.service;

import gov.openpto.odp.config.AppProperties;
import gov.openpto.odp.dto.PatentFilter;
import gov.openpto.odp.dto.TrademarkFilter;
import gov.openpto.odp.exception.BadRequestException;
import gov.openpto.odp.repository.ExportRepository;
import gov.openpto.odp.repository.ExportRepository.PatentRow;
import gov.openpto.odp.repository.ExportRepository.TrademarkRow;
import gov.openpto.odp.repository.spec.PatentSpecifications;
import gov.openpto.odp.repository.spec.SqlFilters;
import gov.openpto.odp.repository.spec.SqlFilters.SqlQuery;
import gov.openpto.odp.repository.spec.TrademarkSpecifications;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.json.JsonMapper;

/**
 * Streamed bulk exports. Rows flow from a server-side cursor straight to the response stream
 * (JSON array or CSV); at no point are the (up to 10,000) records held in memory together.
 * Arguments are validated eagerly so errors become a normal 400 before streaming starts.
 */
@Slf4j
@Service
public class ExportService {

    public enum Format {
        JSON,
        CSV
    }

    private static final String[] PATENT_HEADER = {
            "patentNumber", "applicationNumber", "title", "type", "status", "filingDate", "grantDate",
            "expirationDate", "primaryCpc", "assignees", "inventors", "source", "abstract"
    };
    private static final String[] TRADEMARK_HEADER = {
            "serialNumber", "registrationNumber", "markText", "markType", "status", "filingDate",
            "registrationDate", "statusDate", "owner", "filingBasis", "niceClasses", "source"
    };

    private final ExportRepository exports;
    private final JsonMapper jsonMapper;
    private final AppProperties.Export props;
    private final TransactionTemplate readOnlyTx;

    public ExportService(
            ExportRepository exports,
            JsonMapper jsonMapper,
            AppProperties.Export props,
            PlatformTransactionManager transactionManager) {
        this.exports = exports;
        this.jsonMapper = jsonMapper;
        this.props = props;
        this.readOnlyTx = new TransactionTemplate(transactionManager);
        this.readOnlyTx.setReadOnly(true);
    }

    public StreamingResponseBody patents(PatentFilter filter, String sort, Integer limit, Format format) {
        SqlQuery query = SqlFilters.patents(filter, PatentSpecifications.sort(sort, filter));
        int max = resolveLimit(limit);
        return out -> stream(out, format, PATENT_HEADER, sink -> exports.streamPatents(query, max, sink), ExportService::patentCells);
    }

    public StreamingResponseBody trademarks(TrademarkFilter filter, String sort, Integer limit, Format format) {
        SqlQuery query = SqlFilters.trademarks(filter, TrademarkSpecifications.sort(sort, filter));
        int max = resolveLimit(limit);
        return out -> stream(out, format, TRADEMARK_HEADER, sink -> exports.streamTrademarks(query, max, sink), ExportService::trademarkCells);
    }

    int resolveLimit(Integer requested) {
        if (requested == null) {
            return props.maxRows();
        }
        if (requested < 1 || requested > props.maxRows()) {
            throw new BadRequestException("limit must be between 1 and " + props.maxRows());
        }
        return requested;
    }

    private <R> void stream(
            OutputStream out,
            Format format,
            String[] header,
            Consumer<Consumer<R>> source,
            java.util.function.Function<R, Object[]> cells) throws IOException {
        long started = System.nanoTime();
        int[] count = {0};
        try {
            if (format == Format.JSON) {
                JsonGenerator generator = jsonMapper.createGenerator(out);
                generator.writeStartArray();
                readOnlyTx.executeWithoutResult(tx -> source.accept(row -> {
                    generator.writePOJO(row);
                    count[0]++;
                }));
                generator.writeEndArray();
                generator.flush();
            } else {
                Writer writer = new BufferedWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8), 64 * 1024);
                writer.write(CsvFormat.row((Object[]) header));
                readOnlyTx.executeWithoutResult(tx -> source.accept(row -> {
                    try {
                        writer.write(CsvFormat.row(cells.apply(row)));
                        count[0]++;
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                }));
                writer.flush();
            }
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
        log.info("Exported {} rows as {} in {} ms", count[0], format, (System.nanoTime() - started) / 1_000_000);
    }

    private static Object[] patentCells(PatentRow r) {
        return new Object[]{
                r.patentNumber(), r.applicationNumber(), r.title(), r.type(), r.status(), r.filingDate(), r.grantDate(),
                r.expirationDate(), r.primaryCpc(), r.assignees(), r.inventors(), r.source(), r.abstractText()
        };
    }

    private static Object[] trademarkCells(TrademarkRow r) {
        return new Object[]{
                r.serialNumber(), r.registrationNumber(), r.markText(), r.markType(), r.status(), r.filingDate(),
                r.registrationDate(), r.statusDate(), r.owner(), r.filingBasis(), r.niceClasses(), r.source()
        };
    }
}
