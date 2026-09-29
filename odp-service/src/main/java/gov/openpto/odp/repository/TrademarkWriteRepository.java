package gov.openpto.odp.repository;

import gov.openpto.odp.dto.GoodsServiceDto;
import gov.openpto.odp.dto.TrademarkEventDto;
import gov.openpto.odp.dto.TrademarkUpsert;
import gov.openpto.odp.model.RecordSource;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** JDBC write path for trademarks (seed batch insert + ingest upsert). Callers own the transaction. */
@Repository
@RequiredArgsConstructor
public class TrademarkWriteRepository {

    private static final String COLUMNS = """
            serial_number, registration_number, mark_text, mark_type, status, filing_date, registration_date,
            status_date, owner_name, owner_address, attorney, filing_basis, nice_classes, goods_text, source,
            ingest_job_id""";

    private static final String UPSERT_SQL = "INSERT INTO trademarks (" + COLUMNS + """
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (serial_number) DO UPDATE SET
                registration_number = EXCLUDED.registration_number, mark_text = EXCLUDED.mark_text,
                mark_type = EXCLUDED.mark_type, status = EXCLUDED.status, filing_date = EXCLUDED.filing_date,
                registration_date = EXCLUDED.registration_date, status_date = EXCLUDED.status_date,
                owner_name = EXCLUDED.owner_name, owner_address = EXCLUDED.owner_address,
                attorney = EXCLUDED.attorney, filing_basis = EXCLUDED.filing_basis,
                nice_classes = EXCLUDED.nice_classes, goods_text = EXCLUDED.goods_text,
                source = EXCLUDED.source, ingest_job_id = EXCLUDED.ingest_job_id,
                updated_at = now(), version = trademarks.version + 1
            RETURNING id, (xmax = 0) AS inserted""";

    private static final String INSERT_WITH_ID_SQL = "INSERT INTO trademarks (id, " + COLUMNS + """
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""";

    private final JdbcTemplate jdbc;

    public UpsertOutcome upsert(TrademarkUpsert t, RecordSource source) {
        UpsertOutcome outcome = jdbc.query(
                con -> {
                    PreparedStatement ps = con.prepareStatement(UPSERT_SQL);
                    bindParent(ps, 1, t, source);
                    return ps;
                },
                rs -> {
                    rs.next();
                    return new UpsertOutcome(rs.getLong("id"), rs.getBoolean("inserted"));
                });
        if (outcome == null) {
            throw new IllegalStateException("Upsert returned no row for " + t.serialNumber());
        }
        if (!outcome.inserted()) {
            jdbc.update("DELETE FROM trademark_goods_services WHERE trademark_id = ?", outcome.id());
            jdbc.update("DELETE FROM trademark_events WHERE trademark_id = ?", outcome.id());
        }
        insertChildren(List.of(new Keyed(outcome.id(), t)));
        return outcome;
    }

    public void insertBatch(List<TrademarkUpsert> trademarks, RecordSource source) {
        if (trademarks.isEmpty()) {
            return;
        }
        List<Long> ids = jdbc.queryForList(
                "SELECT nextval('trademarks_id_seq') FROM generate_series(1, ?)", Long.class, trademarks.size());
        List<Keyed> keyed = new ArrayList<>(trademarks.size());
        for (int i = 0; i < trademarks.size(); i++) {
            keyed.add(new Keyed(ids.get(i), trademarks.get(i)));
        }
        jdbc.batchUpdate(INSERT_WITH_ID_SQL, keyed, 1000, (ps, k) -> {
            ps.setLong(1, k.id());
            bindParent(ps, 2, k.trademark(), source);
        });
        insertChildren(keyed);
    }

    private void insertChildren(List<Keyed> keyed) {
        List<Object[]> goods = new ArrayList<>();
        List<Object[]> events = new ArrayList<>();
        for (Keyed k : keyed) {
            List<GoodsServiceDto> gs = k.trademark().goodsAndServices();
            for (int i = 0; i < gs.size(); i++) {
                goods.add(new Object[]{k.id(), i, gs.get(i).niceClass(), gs.get(i).description()});
            }
            List<TrademarkEventDto> ev = k.trademark().events();
            for (int i = 0; i < ev.size(); i++) {
                events.add(new Object[]{k.id(), i, ev.get(i)});
            }
        }
        if (!goods.isEmpty()) {
            jdbc.batchUpdate(
                    "INSERT INTO trademark_goods_services (trademark_id, seq, nice_class, description) VALUES (?, ?, ?, ?)",
                    goods, 1000, (ps, r) -> {
                        ps.setLong(1, (Long) r[0]);
                        ps.setInt(2, (Integer) r[1]);
                        ps.setInt(3, (Integer) r[2]);
                        ps.setString(4, (String) r[3]);
                    });
        }
        if (!events.isEmpty()) {
            jdbc.batchUpdate(
                    "INSERT INTO trademark_events (trademark_id, seq, event_date, code, description) VALUES (?, ?, ?, ?, ?)",
                    events, 1000, (ps, r) -> {
                        TrademarkEventDto e = (TrademarkEventDto) r[2];
                        ps.setLong(1, (Long) r[0]);
                        ps.setInt(2, (Integer) r[1]);
                        Jdbc.date(ps, 3, e.date());
                        ps.setString(4, e.code());
                        ps.setString(5, e.description());
                    });
        }
    }

    private static void bindParent(PreparedStatement ps, int start, TrademarkUpsert t, RecordSource source)
            throws SQLException {
        int i = start;
        ps.setString(i++, t.serialNumber());
        Jdbc.string(ps, i++, t.registrationNumber());
        ps.setString(i++, t.markText());
        Jdbc.enumName(ps, i++, t.markType());
        Jdbc.enumName(ps, i++, t.status());
        Jdbc.date(ps, i++, t.filingDate());
        Jdbc.date(ps, i++, t.registrationDate());
        Jdbc.date(ps, i++, t.statusDate());
        Jdbc.string(ps, i++, t.owner());
        Jdbc.string(ps, i++, t.ownerAddress());
        Jdbc.string(ps, i++, t.attorney());
        Jdbc.string(ps, i++, t.filingBasis());
        ps.setArray(i++, ps.getConnection().createArrayOf("integer", niceClasses(t.goodsAndServices())));
        Jdbc.string(ps, i++, goodsText(t.goodsAndServices()));
        ps.setString(i++, source.name());
        Jdbc.string(ps, i, t.ingestJobId());
    }

    static Integer[] niceClasses(List<GoodsServiceDto> goods) {
        return goods.stream().map(GoodsServiceDto::niceClass).distinct().sorted().toArray(Integer[]::new);
    }

    static String goodsText(List<GoodsServiceDto> goods) {
        return goods.isEmpty() ? null : goods.stream().map(GoodsServiceDto::description).collect(Collectors.joining(" ; "));
    }

    private record Keyed(long id, TrademarkUpsert trademark) {
    }
}
