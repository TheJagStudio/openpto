package gov.openpto.odp.repository;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;

/** Explicitly typed, null-safe PreparedStatement setters (avoids driver parameter-type lookups). */
final class Jdbc {

    private Jdbc() {

    }

    static void string(PreparedStatement ps, int i, String v) throws SQLException {
        if (v == null) {
            ps.setNull(i, Types.VARCHAR);
        } else {
            ps.setString(i, v);
        }
    }

    static void date(PreparedStatement ps, int i, LocalDate v) throws SQLException {
        if (v == null) {
            ps.setNull(i, Types.DATE);
        } else {
            ps.setObject(i, v, Types.DATE);
        }
    }

    static void integer(PreparedStatement ps, int i, Integer v) throws SQLException {
        if (v == null) {
            ps.setNull(i, Types.INTEGER);
        } else {
            ps.setInt(i, v);
        }
    }

    static void enumName(PreparedStatement ps, int i, Enum<?> v) throws SQLException {
        string(ps, i, v == null ? null : v.name());
    }
}
