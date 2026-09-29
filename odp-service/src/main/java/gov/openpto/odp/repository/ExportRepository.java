package gov.openpto.odp.repository;

import com.fasterxml.jackson.annotation.JsonProperty;
import gov.openpto.odp.repository.spec.SqlFilters.SqlQuery;

import java.sql.Array;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Cursor-based (fetch size 500) row streaming for the bulk dataset exports. Must be called inside
 * a transaction so the PostgreSQL driver uses a server-side cursor instead of buffering all rows.
 */
@Repository
@RequiredArgsConstructor
public class ExportRepository {

    private static final int FETCH_SIZE = 500;

    public record PatentRow(
            String patentNumber,
            String applicationNumber,
            String title,
            String type,
            String status,
            LocalDate filingDate,
            LocalDate grantDate,
            LocalDate expirationDate,
            String primaryCpc,
            List<String> assignees,
            List<String> inventors,
            @JsonProperty("abstract") String abstractText,
            String source) {
    }

    public record TrademarkRow(
            String serialNumber,
            String registrationNumber,
            String markText,
            String markType,
            String status,
            LocalDate filingDate,
            LocalDate registrationDate,
            LocalDate statusDate,
            String owner,
            String filingBasis,
            List<Integer> niceClasses,
            String source) {
    }

    private final JdbcTemplate jdbc;

    public void streamPatents(SqlQuery q, int limit, Consumer<PatentRow> sink) {
        String sql = """
                SELECT p.patent_number, p.application_number, p.title, p.type, p.status, p.filing_date,
                       p.grant_date, p.expiration_date, p.primary_cpc, p.abstract_text, p.source,
                       ARRAY(SELECT a.name FROM patent_assignees a WHERE a.patent_id = p.id ORDER BY a.seq) AS assignees,
                       ARRAY(SELECT i.name FROM patent_inventors i WHERE i.patent_id = p.id ORDER BY i.seq) AS inventors
                FROM patents p
                WHERE %s
                ORDER BY %s
                LIMIT ?""".formatted(q.where(), q.orderBy());
        stream(sql, q.params(), limit, rs -> sink.accept(new PatentRow(
                rs.getString("patent_number"),
                rs.getString("application_number"),
                rs.getString("title"),
                rs.getString("type"),
                rs.getString("status"),
                localDate(rs, "filing_date"),
                localDate(rs, "grant_date"),
                localDate(rs, "expiration_date"),
                rs.getString("primary_cpc"),
                stringList(rs.getArray("assignees")),
                stringList(rs.getArray("inventors")),
                rs.getString("abstract_text"),
                rs.getString("source"))));
    }

    public void streamTrademarks(SqlQuery q, int limit, Consumer<TrademarkRow> sink) {
        String sql = """
                SELECT t.serial_number, t.registration_number, t.mark_text, t.mark_type, t.status, t.filing_date,
                       t.registration_date, t.status_date, t.owner_name, t.filing_basis, t.nice_classes, t.source
                FROM trademarks t
                WHERE %s
                ORDER BY %s
                LIMIT ?""".formatted(q.where(), q.orderBy());
        stream(sql, q.params(), limit, rs -> sink.accept(new TrademarkRow(
                rs.getString("serial_number"),
                rs.getString("registration_number"),
                rs.getString("mark_text"),
                rs.getString("mark_type"),
                rs.getString("status"),
                localDate(rs, "filing_date"),
                localDate(rs, "registration_date"),
                localDate(rs, "status_date"),
                rs.getString("owner_name"),
                rs.getString("filing_basis"),
                intList(rs.getArray("nice_classes")),
                rs.getString("source"))));
    }

    @FunctionalInterface
    private interface RowConsumer {
        void accept(ResultSet rs) throws SQLException;
    }

    private void stream(String sql, List<Object> params, int limit, RowConsumer consumer) {
        jdbc.query(
                con -> {
                    PreparedStatement ps = con.prepareStatement(sql, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
                    ps.setFetchSize(FETCH_SIZE);
                    int i = 1;
                    for (Object p : params) {
                        if (p instanceof LocalDate d) {
                            ps.setDate(i++, Date.valueOf(d));
                        } else if (p instanceof Integer n) {
                            ps.setInt(i++, n);
                        } else {
                            ps.setString(i++, (String) p);
                        }
                    }
                    ps.setInt(i, limit);
                    return ps;
                },
                consumer::accept);
    }

    private static LocalDate localDate(ResultSet rs, String column) throws SQLException {
        Date d = rs.getDate(column);
        return d == null ? null : d.toLocalDate();
    }

    private static List<String> stringList(Array array) throws SQLException {
        return array == null ? List.of() : Arrays.stream((Object[]) array.getArray()).map(String::valueOf).toList();
    }

    private static List<Integer> intList(Array array) throws SQLException {
        return array == null ? List.of() : Arrays.stream((Object[]) array.getArray()).map(o -> ((Number) o).intValue()).toList();
    }
}
