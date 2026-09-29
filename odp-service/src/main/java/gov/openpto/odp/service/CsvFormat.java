package gov.openpto.odp.service;

import java.util.Collection;
import java.util.stream.Collectors;

/** RFC 4180 cell encoding with spreadsheet formula-injection protection. */
final class CsvFormat {

    private CsvFormat() {

    }

    static String row(Object... cells) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(cell(cells[i]));
        }
        return sb.append("\r\n").toString();
    }

    static String cell(Object value) {
        if (value == null) {
            return "";
        }
        String s = value instanceof Collection<?> c
                ? c.stream().map(String::valueOf).collect(Collectors.joining("; "))
                : String.valueOf(value);
        if (!s.isEmpty() && "=+-@\t\r".indexOf(s.charAt(0)) >= 0) {
            s = "'" + s;
        }
        boolean quote = s.indexOf(',') >= 0 || s.indexOf('"') >= 0 || s.indexOf('\n') >= 0 || s.indexOf('\r') >= 0
                || (!s.isEmpty() && (Character.isWhitespace(s.charAt(0)) || Character.isWhitespace(s.charAt(s.length() - 1))));
        return quote ? '"' + s.replace("\"", "\"\"") + '"' : s;
    }
}
