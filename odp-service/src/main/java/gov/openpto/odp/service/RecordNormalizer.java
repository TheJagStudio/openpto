package gov.openpto.odp.service;

import gov.openpto.odp.dto.CitationDto;
import gov.openpto.odp.dto.ClaimDto;
import gov.openpto.odp.dto.PartyDto;
import gov.openpto.odp.dto.PatentUpsert;
import gov.openpto.odp.dto.TrademarkEventDto;
import gov.openpto.odp.dto.TrademarkUpsert;
import gov.openpto.odp.model.MarkType;
import gov.openpto.odp.model.PatentStatus;
import gov.openpto.odp.model.PatentType;
import gov.openpto.odp.model.RecordSource;
import gov.openpto.odp.model.TrademarkStatus;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Canonicalizes incoming patent/trademark records before they are written: fills derived fields
 * (natural key, status, expiration, primary CPC), normalizes codes, and rejects records whose
 * internal consistency cannot be repaired ({@link IllegalArgumentException} with a readable reason).
 */
@Component
@RequiredArgsConstructor
public class RecordNormalizer {

    private final Clock clock;

    public PatentUpsert patent(PatentUpsert p, RecordSource source) {
        String number = patentKey(p);
        if (number == null) {
            throw new IllegalArgumentException("patentNumber or applicationNumber is required");
        }
        if (p.grantDate() != null && p.grantDate().isBefore(p.filingDate())) {
            throw new IllegalArgumentException("grantDate must not be before filingDate");
        }
        List<ClaimDto> claims = claims(p.claims());
        LinkedHashSet<String> cpc = new LinkedHashSet<>();
        String primary = blankToNull(p.primaryCpc()) == null ? null : cpcCode(p.primaryCpc());
        if (primary != null) {
            cpc.add(primary);
        }
        nullSafe(p.cpcCodes()).stream().map(RecordNormalizer::cpcCode).forEach(cpc::add);
        if (primary == null && !cpc.isEmpty()) {
            primary = cpc.getFirst();
        }
        LocalDate expiration = p.expirationDate() != null ? p.expirationDate() : expiration(p.type(), p.filingDate(), p.grantDate());
        PatentStatus status = p.status() != null ? p.status() : patentStatus(p.grantDate(), expiration);
        List<CitationDto> citations = nullSafe(p.citations()).stream()
                .map(c -> new CitationDto(c.patentNumber().trim().toUpperCase(Locale.ROOT), c.citedBy()))
                .toList();
        return new PatentUpsert(
                number,
                blankToNull(p.applicationNumber()),
                p.title().trim(),
                blankToNull(p.abstractText()),
                p.type(),
                status,
                p.filingDate(),
                p.grantDate(),
                p.priorityDate(),
                expiration,
                primary,
                List.copyOf(cpc),
                claims,
                parties(p.inventors()),
                parties(p.assignees()),
                citations,
                blankToNull(p.examiner()),
                blankToNull(p.artUnit()),
                blankToNull(p.ingestJobId()),
                source);
    }

    public TrademarkUpsert trademark(TrademarkUpsert t, RecordSource source) {
        if (t.registrationDate() != null && t.registrationDate().isBefore(t.filingDate())) {
            throw new IllegalArgumentException("registrationDate must not be before filingDate");
        }
        List<TrademarkEventDto> events = nullSafe(t.events()).stream()
                .sorted(Comparator.comparing(TrademarkEventDto::date))
                .toList();
        TrademarkStatus status = t.status() != null
                ? t.status()
                : t.registrationDate() != null ? TrademarkStatus.LIVE_REGISTERED : TrademarkStatus.LIVE_PENDING;
        LocalDate statusDate = t.statusDate() != null
                ? t.statusDate()
                : !events.isEmpty() ? events.getLast().date()
                : t.registrationDate() != null ? t.registrationDate() : t.filingDate();
        return new TrademarkUpsert(
                t.serialNumber().trim(),
                blankToNull(t.registrationNumber()),
                t.markText().trim(),
                t.markType() != null ? t.markType() : MarkType.STANDARD_CHARACTER,
                status,
                t.filingDate(),
                t.registrationDate(),
                statusDate,
                blankToNull(t.owner()),
                blankToNull(t.ownerAddress()),
                blankToNull(t.attorney()),
                blankToNull(t.filingBasis()),
                List.copyOf(nullSafe(t.goodsAndServices())),
                events,
                blankToNull(t.ingestJobId()),
                source);
    }

    /** The natural key: {@code patentNumber}, else {@code USAPP} + the application number digits. */
    public static String patentKey(PatentUpsert p) {
        String number = blankToNull(p.patentNumber());
        if (number != null) {
            return number.toUpperCase(Locale.ROOT);
        }
        String app = blankToNull(p.applicationNumber());
        if (app == null) {
            return null;
        }
        String digits = app.replaceAll("\\D", "");
        return digits.isEmpty() ? null : "USAPP" + digits;
    }

    static LocalDate expiration(PatentType type, LocalDate filing, LocalDate grant) {
        if (grant == null) {
            return null;
        }
        return type == PatentType.DESIGN ? grant.plusYears(15) : filing.plusYears(20);
    }

    PatentStatus patentStatus(LocalDate grant, LocalDate expiration) {
        if (grant == null) {
            return PatentStatus.PENDING;
        }
        return expiration != null && expiration.isBefore(LocalDate.now(clock)) ? PatentStatus.EXPIRED : PatentStatus.GRANTED;
    }

    /** Sorted by number; unique numbers; dependents must reference an earlier claim. */
    static List<ClaimDto> claims(List<ClaimDto> input) {
        List<ClaimDto> sorted = new ArrayList<>(nullSafe(input));
        sorted.sort(Comparator.comparing(ClaimDto::number));
        Set<Integer> seen = new HashSet<>();
        List<ClaimDto> out = new ArrayList<>(sorted.size());
        for (ClaimDto c : sorted) {
            if (!seen.add(c.number())) {
                throw new IllegalArgumentException("duplicate claim number " + c.number());
            }
            Integer dependsOn = c.dependsOn();
            if (dependsOn != null && (dependsOn >= c.number() || !seen.contains(dependsOn))) {
                throw new IllegalArgumentException("claim " + c.number() + " depends on claim " + dependsOn + " which does not precede it");
            }
            out.add(new ClaimDto(c.number(), c.text().trim(), dependsOn == null, dependsOn));
        }
        return out;
    }

    static String cpcCode(String code) {
        return code.replace(" ", "").toUpperCase(Locale.ROOT);
    }

    private static List<PartyDto> parties(List<PartyDto> input) {
        return nullSafe(input).stream()
                .map(p -> new PartyDto(
                        p.name().trim(),
                        blankToNull(p.city()),
                        blankToNull(p.state()),
                        p.country() == null ? null : p.country().toUpperCase(Locale.ROOT)))
                .toList();
    }

    private static <T> List<T> nullSafe(List<T> list) {
        return list == null ? List.of() : list;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
