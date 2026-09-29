package gov.openpto.ingest.transform.mapper;

import gov.openpto.ingest.transform.MappingException;
import gov.openpto.ingest.transform.xml.XmlNode;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parsing helpers shared by the format mappers. */
public final class MapperSupport {

    private static final DateTimeFormatter BASIC = DateTimeFormatter.BASIC_ISO_DATE;
    private static final Pattern DOC_NUMBER = Pattern.compile("^([A-Z]{0,2})0*(\\d+)$");
    private static final Pattern DIGITS = Pattern.compile("(\\d+)");

    private MapperSupport() {
    }

    /** Parses {@code yyyyMMdd} or {@code yyyy-MM-dd}; returns {@code null} for blank, zero or invalid dates. */
    public static LocalDate date(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim();
        if (value.isEmpty() || value.chars().allMatch(c -> c == '0')) {
            return null;
        }
        try {
            if (value.length() == 8 && value.chars().allMatch(Character::isDigit)) {
                return LocalDate.parse(value, BASIC);
            }
            return LocalDate.parse(value.length() > 10 ? value.substring(0, 10) : value);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /**
     * USPTO document number + kind code to the portal form, stripping zero padding:
     * {@code 06334567}+{@code B1} → {@code US6334567B1}, {@code D0987654}+{@code S} → {@code USD987654S}.
     * Publication numbers keep all digits: {@code 20240123456}+{@code A1} → {@code US20240123456A1}.
     */
    public static String patentNumber(String docNumber, String kind) {
        if (docNumber == null || docNumber.isBlank()) {
            return null;
        }
        String number = docNumber.trim().toUpperCase(Locale.ROOT).replace(",", "").replace(" ", "");
        if (number.startsWith("US")) {
            number = number.substring(2);
        }
        Matcher m = DOC_NUMBER.matcher(number);
        if (m.matches()) {
            number = m.group(1) + m.group(2);
        }
        return "US" + number + (kind == null ? "" : kind.trim().toUpperCase(Locale.ROOT));
    }

    /** {@code 17123456} or {@code 09/123456} → {@code 17/123,456}; anything else is returned trimmed. */
    public static String applicationNumber(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String digits = raw.replaceAll("\\D", "");
        if (digits.length() == 8) {
            return digits.substring(0, 2) + "/" + digits.substring(2, 5) + "," + digits.substring(5);
        }
        return raw.trim();
    }

    /** Patent type from the application type attribute, the number prefix or the kind code. */
    public static String patentType(String applType, String docNumber, String kind) {
        if (applType != null && !applType.isBlank()) {
            String t = applType.trim().toUpperCase(Locale.ROOT);
            if (t.startsWith("UTIL")) {
                return "UTILITY";
            }
            if (t.startsWith("DESIGN")) {
                return "DESIGN";
            }
            if (t.startsWith("PLANT")) {
                return "PLANT";
            }
            if (t.startsWith("REISSUE")) {
                return "REISSUE";
            }
        }
        String n = docNumber == null ? "" : docNumber.trim().toUpperCase(Locale.ROOT);
        String k = kind == null ? "" : kind.trim().toUpperCase(Locale.ROOT);
        if (n.startsWith("D") || k.startsWith("S")) {
            return "DESIGN";
        }
        if (n.startsWith("PP") || k.startsWith("P")) {
            return "PLANT";
        }
        if (n.startsWith("RE") || k.startsWith("E")) {
            return "REISSUE";
        }
        return "UTILITY";
    }

    /** First run of digits as int, e.g. {@code CLM-00012} → 12; {@code null} if none. */
    public static Integer number(String raw) {
        if (raw == null) {
            return null;
        }
        Matcher m = DIGITS.matcher(raw);
        if (!m.find()) {
            return null;
        }
        try {
            return Integer.valueOf(m.group(1));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public static String joinName(String first, String last) {
        String f = blankToNull(first);
        String l = blankToNull(last);
        if (f == null) {
            return l;
        }
        return l == null ? f : f + " " + l;
    }

    public static <T> T require(T value, String identifier, String what) {
        if (value == null || (value instanceof String s && s.isBlank())) {
            throw new MappingException(identifier, "Missing required field: " + what);
        }
        return value;
    }

    public static String text(XmlNode node, String path) {
        return node == null ? null : node.textOf(path);
    }
}
