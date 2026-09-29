package gov.openpto.ingest.transform.mapper;

import static gov.openpto.ingest.transform.mapper.MapperSupport.blankToNull;
import static gov.openpto.ingest.transform.mapper.MapperSupport.date;
import static gov.openpto.ingest.transform.mapper.MapperSupport.number;
import static gov.openpto.ingest.transform.mapper.MapperSupport.require;
import static gov.openpto.ingest.transform.mapper.MapperSupport.text;

import gov.openpto.ingest.transform.model.GoodsAndServices;
import gov.openpto.ingest.transform.model.PatentRecord;
import gov.openpto.ingest.transform.model.TrademarkEvent;
import gov.openpto.ingest.transform.model.TrademarkRecord;
import gov.openpto.ingest.transform.xml.XmlNode;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Maps one {@code <case-file>} of the USPTO {@code trademark-applications-daily} XML. */
public final class TrademarkCaseFileMapper implements RecordMapper {

    @Override
    public String identify(XmlNode caseFile) {
        return text(caseFile, "serial-number");
    }

    @Override
    public TrademarkRecord map(XmlNode caseFile, String ingestJobId) {
        String serial = require(text(caseFile, "serial-number"), null, "serial-number");
        XmlNode header = caseFile.child("case-file-header");
        String markText = require(text(header, "mark-identification"), serial, "case-file-header/mark-identification");
        String filingDateRaw = text(header, "filing-date");
        var filingDate = require(date(filingDateRaw), serial, "case-file-header/filing-date");
        String statusCode = text(header, "status-code");

        List<GoodsAndServices> goods = goodsAndServices(caseFile);
        XmlNode owner = currentOwner(caseFile);
        return new TrademarkRecord(
                serial,
                registrationNumber(text(caseFile, "registration-number")),
                markText,
                markType(text(header, "mark-drawing-code")),
                status(statusCode),
                statusCode,
                filingDate,
                date(text(header, "registration-date")),
                date(text(header, "status-date")),
                text(owner, "party-name"),
                ownerAddress(owner),
                text(header, "attorney-name"),
                filingBasis(header),
                niceClasses(caseFile, goods),
                goods,
                events(caseFile),
                ingestJobId,
                PatentRecord.SOURCE_INGEST);
    }

    private static String registrationNumber(String raw) {
        String value = blankToNull(raw);
        return value == null || value.chars().allMatch(c -> c == '0') ? null : value;
    }

    /**
     * USPTO status codes: 700/800 registered (800 = renewed), 600–618 abandoned, 710–799 and 900s cancelled
     * or expired; everything else is a live pending application.
     */
    static String status(String statusCode) {
        Integer code = number(statusCode);
        if (code == null) {
            return "LIVE_PENDING";
        }
        if (code == 700 || code == 800) {
            return "LIVE_REGISTERED";
        }
        if (code >= 600 && code <= 618) {
            return "DEAD_ABANDONED";
        }
        if ((code > 700 && code < 800) || code >= 900) {
            return "DEAD_CANCELLED";
        }
        return "LIVE_PENDING";
    }

    /** Mark drawing code: 4xxx standard characters, 6xxx sound (and other non-visual), otherwise design. */
    static String markType(String drawingCode) {
        String code = blankToNull(drawingCode);
        if (code == null) {
            return "STANDARD_CHARACTER";
        }
        return switch (code.charAt(0)) {
            case '4' -> "STANDARD_CHARACTER";
            case '6' -> "SOUND";
            default -> "DESIGN";
        };
    }

    private static String filingBasis(XmlNode header) {
        if (header == null) {
            return "1A";
        }
        if (flag(header, "filing-basis-current-66a-in") || flag(header, "filing-basis-filed-as-66a-in")) {
            return "66A";
        }
        if (flag(header, "filing-basis-current-44e-in") || flag(header, "filing-basis-filed-as-44e-in")) {
            return "44E";
        }
        if (flag(header, "filing-basis-current-44d-in") || flag(header, "filing-basis-filed-as-44d-in")) {
            return "44D";
        }
        if (flag(header, "intent-to-use-current-in") || flag(header, "filing-basis-filed-as-intent-to-use-in")) {
            return "1B";
        }
        return "1A";
    }

    private static boolean flag(XmlNode header, String name) {
        String value = text(header, name);
        return value != null && (value.equalsIgnoreCase("T") || value.equalsIgnoreCase("true") || value.equals("1"));
    }

    private static List<GoodsAndServices> goodsAndServices(XmlNode caseFile) {
        List<GoodsAndServices> result = new ArrayList<>();
        for (XmlNode statement : caseFile.findAll("case-file-statements/case-file-statement")) {
            String typeCode = text(statement, "type-code");
            String description = text(statement, "text");
            if (typeCode == null || description == null || !typeCode.toUpperCase(Locale.ROOT).startsWith("GS")) {
                continue;
            }
            Integer niceClass = typeCode.length() >= 5 ? number(typeCode.substring(2, 5)) : null;
            result.add(new GoodsAndServices(niceClass, description));
        }
        return result;
    }

    private static List<Integer> niceClasses(XmlNode caseFile, List<GoodsAndServices> goods) {
        TreeSet<Integer> classes = new TreeSet<>();
        for (XmlNode classification : caseFile.findAll("classifications/classification")) {
            Integer code = number(text(classification, "international-code"));
            if (code != null && code >= 1 && code <= 45) {
                classes.add(code);
            }
        }
        if (classes.isEmpty()) {
            goods.stream().map(GoodsAndServices::niceClass)
                    .filter(c -> c != null && c >= 1 && c <= 45)
                    .forEach(classes::add);
        }
        return new ArrayList<>(classes);
    }

    private static List<TrademarkEvent> events(XmlNode caseFile) {
        return caseFile.findAll("case-file-event-statements/case-file-event-statement").stream()
                .map(e -> new TrademarkEvent(date(text(e, "date")), text(e, "code"), text(e, "description-text")))
                .filter(e -> e.date() != null || e.code() != null)
                .sorted(Comparator.comparing(TrademarkEvent::date, Comparator.nullsLast(Comparator.naturalOrder())))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    /** The owner entry with the highest party-type (later owners, e.g. 30 registrant, supersede 10 applicant). */
    private static XmlNode currentOwner(XmlNode caseFile) {
        XmlNode best = null;
        int bestType = Integer.MIN_VALUE;
        for (XmlNode owner : caseFile.findAll("case-file-owners/case-file-owner")) {
            Integer type = number(text(owner, "party-type"));
            int t = type == null ? 0 : type;
            if (best == null || t > bestType) {
                best = owner;
                bestType = t;
            }
        }
        return best;
    }

    private static String ownerAddress(XmlNode owner) {
        if (owner == null) {
            return null;
        }
        String cityLine = Stream.of(text(owner, "city"), text(owner, "state"), text(owner, "postcode"))
                .filter(s -> s != null)
                .collect(Collectors.joining(" "));
        String joined = Stream.of(text(owner, "address-1"), text(owner, "address-2"), blankToNull(cityLine),
                        text(owner, "country"))
                .filter(s -> s != null)
                .collect(Collectors.joining(", "));
        return blankToNull(joined);
    }
}
