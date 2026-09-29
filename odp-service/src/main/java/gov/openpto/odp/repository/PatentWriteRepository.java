package gov.openpto.odp.repository;

import gov.openpto.odp.dto.CitationDto;
import gov.openpto.odp.dto.ClaimDto;
import gov.openpto.odp.dto.PartyDto;
import gov.openpto.odp.dto.PatentUpsert;
import gov.openpto.odp.model.RecordSource;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * JDBC write path for patents: batch inserts for the seed, {@code INSERT ... ON CONFLICT} upserts
 * for ingest. Child collections are replaced wholesale. Callers own the transaction.
 * Records passed here must already be normalized (non-null patentNumber, status, lists).
 */
@Repository
@RequiredArgsConstructor
public class PatentWriteRepository {

    private static final String COLUMNS = """
            patent_number, application_number, title, abstract_text, type, status, filing_date, grant_date,
            priority_date, expiration_date, primary_cpc, cpc_section, examiner, art_unit, assignee_text,
            inventor_text, source, ingest_job_id""";

    private static final String UPSERT_SQL = "INSERT INTO patents (" + COLUMNS + """
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (patent_number) DO UPDATE SET
                application_number = EXCLUDED.application_number, title = EXCLUDED.title,
                abstract_text = EXCLUDED.abstract_text, type = EXCLUDED.type, status = EXCLUDED.status,
                filing_date = EXCLUDED.filing_date, grant_date = EXCLUDED.grant_date,
                priority_date = EXCLUDED.priority_date, expiration_date = EXCLUDED.expiration_date,
                primary_cpc = EXCLUDED.primary_cpc, cpc_section = EXCLUDED.cpc_section,
                examiner = EXCLUDED.examiner, art_unit = EXCLUDED.art_unit,
                assignee_text = EXCLUDED.assignee_text, inventor_text = EXCLUDED.inventor_text,
                source = EXCLUDED.source, ingest_job_id = EXCLUDED.ingest_job_id,
                updated_at = now(), version = patents.version + 1
            RETURNING id, (xmax = 0) AS inserted""";

    private static final String INSERT_WITH_ID_SQL = "INSERT INTO patents (id, " + COLUMNS + """
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""";

    private final JdbcTemplate jdbc;

    public UpsertOutcome upsert(PatentUpsert p, RecordSource source) {
        UpsertOutcome outcome = jdbc.query(
                con -> {
                    PreparedStatement ps = con.prepareStatement(UPSERT_SQL);
                    bindParent(ps, 1, p, source);
                    return ps;
                },
                rs -> {
                    rs.next();
                    return new UpsertOutcome(rs.getLong("id"), rs.getBoolean("inserted"));
                });
        if (outcome == null) {
            throw new IllegalStateException("Upsert returned no row for " + p.patentNumber());
        }
        if (!outcome.inserted()) {
            deleteChildren(outcome.id());
        }
        insertChildren(List.of(new Keyed(outcome.id(), p)));
        return outcome;
    }

    /** Fast path for empty tables (seed): ids pre-allocated from the sequence, everything batched. */
    public void insertBatch(List<PatentUpsert> patents, RecordSource source) {
        if (patents.isEmpty()) {
            return;
        }
        List<Long> ids = jdbc.queryForList(
                "SELECT nextval('patents_id_seq') FROM generate_series(1, ?)", Long.class, patents.size());
        List<Keyed> keyed = new ArrayList<>(patents.size());
        for (int i = 0; i < patents.size(); i++) {
            keyed.add(new Keyed(ids.get(i), patents.get(i)));
        }
        jdbc.batchUpdate(INSERT_WITH_ID_SQL, keyed, 1000, (ps, k) -> {
            ps.setLong(1, k.id());
            bindParent(ps, 2, k.patent(), source);
        });
        insertChildren(keyed);
    }

    private void deleteChildren(long id) {
        for (String table : List.of("patent_claims", "patent_inventors", "patent_assignees", "patent_cpc", "patent_citations")) {
            jdbc.update("DELETE FROM " + table + " WHERE patent_id = ?", id);
        }
    }

    private void insertChildren(List<Keyed> keyed) {
        List<Object[]> claims = new ArrayList<>();
        List<Object[]> inventors = new ArrayList<>();
        List<Object[]> assignees = new ArrayList<>();
        List<Object[]> cpc = new ArrayList<>();
        List<Object[]> citations = new ArrayList<>();
        for (Keyed k : keyed) {
            PatentUpsert p = k.patent();
            for (ClaimDto c : p.claims()) {
                claims.add(new Object[]{k.id(), c.number(), c.text(), c.dependsOn() == null, c.dependsOn()});
            }
            addParties(inventors, k.id(), p.inventors());
            addParties(assignees, k.id(), p.assignees());
            for (int i = 0; i < p.cpcCodes().size(); i++) {
                cpc.add(new Object[]{k.id(), i, p.cpcCodes().get(i)});
            }
            for (int i = 0; i < p.citations().size(); i++) {
                CitationDto c = p.citations().get(i);
                citations.add(new Object[]{k.id(), i, c.patentNumber(), c.citedBy().name()});
            }
        }
        batch("INSERT INTO patent_claims (patent_id, claim_number, text, independent, depends_on) VALUES (?, ?, ?, ?, ?)",
                claims, (ps, r) -> {
                    ps.setLong(1, (Long) r[0]);
                    ps.setInt(2, (Integer) r[1]);
                    ps.setString(3, (String) r[2]);
                    ps.setBoolean(4, (Boolean) r[3]);
                    Jdbc.integer(ps, 5, (Integer) r[4]);
                });
        String partySql = " (patent_id, seq, name, city, state, country) VALUES (?, ?, ?, ?, ?, ?)";
        batch("INSERT INTO patent_inventors" + partySql, inventors, PatentWriteRepository::bindParty);
        batch("INSERT INTO patent_assignees" + partySql, assignees, PatentWriteRepository::bindParty);
        batch("INSERT INTO patent_cpc (patent_id, seq, code) VALUES (?, ?, ?)", cpc, (ps, r) -> {
            ps.setLong(1, (Long) r[0]);
            ps.setInt(2, (Integer) r[1]);
            ps.setString(3, (String) r[2]);
        });
        batch("INSERT INTO patent_citations (patent_id, seq, cited_patent_number, cited_by) VALUES (?, ?, ?, ?)",
                citations, (ps, r) -> {
                    ps.setLong(1, (Long) r[0]);
                    ps.setInt(2, (Integer) r[1]);
                    ps.setString(3, (String) r[2]);
                    ps.setString(4, (String) r[3]);
                });
    }

    private void batch(String sql, List<Object[]> rows, org.springframework.jdbc.core.ParameterizedPreparedStatementSetter<Object[]> setter) {
        if (!rows.isEmpty()) {
            jdbc.batchUpdate(sql, rows, 1000, setter);
        }
    }

    private static void addParties(List<Object[]> out, long id, List<PartyDto> parties) {
        for (int i = 0; i < parties.size(); i++) {
            PartyDto p = parties.get(i);
            out.add(new Object[]{id, i, p.name(), p.city(), p.state(), p.country()});
        }
    }

    private static void bindParty(PreparedStatement ps, Object[] r) throws SQLException {
        ps.setLong(1, (Long) r[0]);
        ps.setInt(2, (Integer) r[1]);
        ps.setString(3, (String) r[2]);
        Jdbc.string(ps, 4, (String) r[3]);
        Jdbc.string(ps, 5, (String) r[4]);
        Jdbc.string(ps, 6, (String) r[5]);
    }

    private static void bindParent(PreparedStatement ps, int start, PatentUpsert p, RecordSource source) throws SQLException {
        int i = start;
        ps.setString(i++, p.patentNumber());
        Jdbc.string(ps, i++, p.applicationNumber());
        ps.setString(i++, p.title());
        Jdbc.string(ps, i++, p.abstractText());
        Jdbc.enumName(ps, i++, p.type());
        Jdbc.enumName(ps, i++, p.status());
        Jdbc.date(ps, i++, p.filingDate());
        Jdbc.date(ps, i++, p.grantDate());
        Jdbc.date(ps, i++, p.priorityDate());
        Jdbc.date(ps, i++, p.expirationDate());
        Jdbc.string(ps, i++, p.primaryCpc());
        Jdbc.string(ps, i++, p.primaryCpc() == null || p.primaryCpc().isEmpty() ? null : p.primaryCpc().substring(0, 1));
        Jdbc.string(ps, i++, p.examiner());
        Jdbc.string(ps, i++, p.artUnit());
        Jdbc.string(ps, i++, joinNames(p.assignees()));
        Jdbc.string(ps, i++, joinNames(p.inventors()));
        ps.setString(i++, source.name());
        if (p.ingestJobId() == null) {
            ps.setNull(i, Types.VARCHAR);
        } else {
            ps.setString(i, p.ingestJobId());
        }
    }

    static String joinNames(List<PartyDto> parties) {
        return parties.isEmpty() ? null : parties.stream().map(PartyDto::name).collect(Collectors.joining("; "));
    }

    private record Keyed(long id, PatentUpsert patent) {
    }
}
