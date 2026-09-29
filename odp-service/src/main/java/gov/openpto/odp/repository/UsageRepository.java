package gov.openpto.odp.repository;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Daily per-key request counters (upsert-add) and per-user aggregations. */
@Repository
@RequiredArgsConstructor
public class UsageRepository {

    private final JdbcTemplate jdbc;

    public record DailyCount(UUID keyId, LocalDate date, long count) {
    }

    public record DateTotal(LocalDate date, long requests) {
    }

    public record KeyTotal(UUID keyId, String name, long requests) {
    }

    /**
     * Adds counts to {@code api_usage_daily}; rows for unknown key ids are silently skipped
     * (the INSERT ... SELECT yields no row), so one stale key cannot fail the gateway's flush.
     *
     * @return number of entries applied
     */
    public int addDailyCounts(List<DailyCount> counts) {
        if (counts.isEmpty()) {
            return 0;
        }
        int[][] results = jdbc.batchUpdate("""
                        INSERT INTO api_usage_daily (key_id, usage_date, request_count)
                        SELECT k.id, ?, ? FROM api_keys k WHERE k.id = ?
                        ON CONFLICT (key_id, usage_date)
                        DO UPDATE SET request_count = api_usage_daily.request_count + EXCLUDED.request_count""",
                counts, 500, (ps, c) -> {
                    ps.setDate(1, Date.valueOf(c.date()));
                    ps.setLong(2, c.count());
                    ps.setObject(3, c.keyId());
                });
        int applied = 0;
        for (int[] batch : results) {
            for (int r : batch) {
                applied += r > 0 || r == java.sql.Statement.SUCCESS_NO_INFO ? 1 : 0;
            }
        }
        return applied;
    }

    /** Bumps the lifetime counter and last-used timestamp of each key. */
    public void bumpKeys(Map<UUID, Long> totals, Instant usedAt) {
        if (totals.isEmpty()) {
            return;
        }
        List<Map.Entry<UUID, Long>> entries = totals.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList();
        jdbc.batchUpdate("""
                        UPDATE api_keys
                        SET request_count = request_count + ?,
                            last_used_at = GREATEST(COALESCE(last_used_at, ?), ?)
                        WHERE id = ?""",
                entries, 500, (ps, e) -> {
                    Timestamp ts = Timestamp.from(usedAt);
                    ps.setLong(1, e.getValue());
                    ps.setTimestamp(2, ts);
                    ps.setTimestamp(3, ts);
                    ps.setObject(4, e.getKey());
                });
    }

    public List<DateTotal> dailyTotalsForUser(UUID userId, LocalDate from, LocalDate to) {
        return jdbc.query("""
                        SELECT u.usage_date, SUM(u.request_count) AS requests
                        FROM api_usage_daily u JOIN api_keys k ON k.id = u.key_id
                        WHERE k.user_id = ? AND u.usage_date BETWEEN ? AND ?
                        GROUP BY u.usage_date ORDER BY u.usage_date""",
                (rs, n) -> new DateTotal(rs.getDate(1).toLocalDate(), rs.getLong(2)),
                userId, Date.valueOf(from), Date.valueOf(to));
    }

    public List<KeyTotal> keyTotalsForUser(UUID userId, LocalDate from, LocalDate to) {
        return jdbc.query("""
                        SELECT k.id, k.name, COALESCE(SUM(u.request_count), 0) AS requests
                        FROM api_keys k
                        LEFT JOIN api_usage_daily u ON u.key_id = k.id AND u.usage_date BETWEEN ? AND ?
                        WHERE k.user_id = ?
                        GROUP BY k.id, k.name, k.created_at
                        ORDER BY requests DESC, k.created_at DESC""",
                (rs, n) -> new KeyTotal(rs.getObject(1, UUID.class), rs.getString(2), rs.getLong(3)),
                Date.valueOf(from), Date.valueOf(to), userId);
    }
}
