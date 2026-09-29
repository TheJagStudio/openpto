package gov.openpto.odp.repository.spec;

import static gov.openpto.odp.repository.spec.SpecSupport.hasText;

import gov.openpto.odp.dto.PatentFilter;
import gov.openpto.odp.dto.TrademarkFilter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Parameterized SQL equivalents of the search specifications, for the JDBC streaming exports.
 * Only whitelisted column names are ever concatenated; every user value is a bind parameter.
 */
public final class SqlFilters {

    private static final Map<String, String> PATENT_SORT_COLUMNS =
            Map.of("grantDate", "p.grant_date", "filingDate", "p.filing_date", "patentNumber", "p.patent_number");
    private static final Map<String, String> TRADEMARK_SORT_COLUMNS = Map.of(
            "filingDate", "t.filing_date", "registrationDate", "t.registration_date", "serialNumber", "t.serial_number");
    private static final String TSQUERY = "websearch_to_tsquery('english', ?)";

    private SqlFilters() {
    }

    /** A WHERE clause (without the keyword; {@code TRUE} when unfiltered) + ORDER BY with ordered bind parameters. */
    public record SqlQuery(String where, String orderBy, List<Object> params) {
    }

    public static SqlQuery patents(PatentFilter f, SearchSort sort) {
        List<String> conds = new ArrayList<>();
        List<Object> params = new ArrayList<>();
        if (f.hasQuery()) {
            conds.add("p.search_vector @@ " + TSQUERY);
            params.add(f.q().trim());
        }
        if (f.type() != null) {
            conds.add("p.type = ?");
            params.add(f.type().name());
        }
        if (f.status() != null) {
            conds.add("p.status = ?");
            params.add(f.status().name());
        }
        if (hasText(f.cpc())) {
            conds.add("EXISTS (SELECT 1 FROM patent_cpc c WHERE c.patent_id = p.id AND c.code LIKE ? ESCAPE '\\')");
            params.add(SpecSupport.prefixPattern(PatentSpecifications.normalizeCpc(f.cpc())));
        }
        if (hasText(f.assignee())) {
            conds.add("EXISTS (SELECT 1 FROM patent_assignees a WHERE a.patent_id = p.id AND lower(a.name) LIKE ? ESCAPE '\\')");
            params.add(SpecSupport.containsPattern(f.assignee()));
        }
        if (hasText(f.inventor())) {
            conds.add("EXISTS (SELECT 1 FROM patent_inventors i WHERE i.patent_id = p.id AND lower(i.name) LIKE ? ESCAPE '\\')");
            params.add(SpecSupport.containsPattern(f.inventor()));
        }
        range(conds, params, "p.filing_date", f.filedFrom(), f.filedTo());
        range(conds, params, "p.grant_date", f.grantedFrom(), f.grantedTo());

        String orderBy;
        if (sort.isRelevance() && f.hasQuery()) {
            orderBy = "ts_rank_cd(p.search_vector, " + TSQUERY + ") DESC, p.id DESC";
            params.add(f.q().trim());
        } else {
            orderBy = column(PATENT_SORT_COLUMNS, sort, "p.filing_date") + ", p.id DESC";
        }
        return new SqlQuery(conds.isEmpty() ? "TRUE" : String.join(" AND ", conds), orderBy, params);
    }

    public static SqlQuery trademarks(TrademarkFilter f, SearchSort sort) {
        List<String> conds = new ArrayList<>();
        List<Object> params = new ArrayList<>();
        if (f.hasQuery()) {
            conds.add("t.search_vector @@ " + TSQUERY);
            params.add(f.q().trim());
        }
        if (f.status() != null) {
            conds.add("t.status = ?");
            params.add(f.status().name());
        }
        if (f.niceClass() != null) {
            conds.add("t.nice_classes @> ARRAY[?]::integer[]");
            params.add(f.niceClass());
        }
        if (hasText(f.owner())) {
            conds.add("lower(t.owner_name) LIKE ? ESCAPE '\\'");
            params.add(SpecSupport.containsPattern(f.owner()));
        }
        range(conds, params, "t.filing_date", f.filedFrom(), f.filedTo());

        String orderBy;
        if (sort.isRelevance() && f.hasQuery()) {
            orderBy = "ts_rank_cd(t.search_vector, " + TSQUERY + ") DESC, t.id DESC";
            params.add(f.q().trim());
        } else {
            orderBy = column(TRADEMARK_SORT_COLUMNS, sort, "t.filing_date") + ", t.id DESC";
        }
        return new SqlQuery(conds.isEmpty() ? "TRUE" : String.join(" AND ", conds), orderBy, params);
    }

    private static void range(List<String> conds, List<Object> params, String column, Object from, Object to) {
        if (from != null) {
            conds.add(column + " >= ?");
            params.add(from);
        }
        if (to != null) {
            conds.add(column + " <= ?");
            params.add(to);
        }
    }

    private static String column(Map<String, String> columns, SearchSort sort, String fallback) {
        String col = columns.getOrDefault(sort.field(), fallback);
        return col + (sort.ascending() ? " ASC" : " DESC") + " NULLS LAST";
    }
}
