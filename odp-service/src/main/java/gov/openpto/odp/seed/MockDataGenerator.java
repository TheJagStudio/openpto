package gov.openpto.odp.seed;

import static gov.openpto.odp.seed.SeedVocabulary.BENEFITS;
import static gov.openpto.odp.seed.SeedVocabulary.CITIES;
import static gov.openpto.odp.seed.SeedVocabulary.CLOSINGS;
import static gov.openpto.odp.seed.SeedVocabulary.COMPANY_COUNTRIES;
import static gov.openpto.odp.seed.SeedVocabulary.COMPANY_PREFIXES;
import static gov.openpto.odp.seed.SeedVocabulary.COMPANY_SUFFIXES;
import static gov.openpto.odp.seed.SeedVocabulary.DESIGN_OBJECTS;
import static gov.openpto.odp.seed.SeedVocabulary.DETAILS;
import static gov.openpto.odp.seed.SeedVocabulary.INVENTOR_COUNTRIES;
import static gov.openpto.odp.seed.SeedVocabulary.NAMES;
import static gov.openpto.odp.seed.SeedVocabulary.PLANT_CPC;
import static gov.openpto.odp.seed.SeedVocabulary.PLANT_CROPS;
import static gov.openpto.odp.seed.SeedVocabulary.STEPS;
import static gov.openpto.odp.seed.SeedVocabulary.TECHS;
import static gov.openpto.odp.seed.SeedVocabulary.US_NAMES;
import static gov.openpto.odp.seed.SeedVocabulary.VARIETY_WORDS;

import gov.openpto.odp.dto.CitationDto;
import gov.openpto.odp.dto.ClaimDto;
import gov.openpto.odp.dto.GoodsServiceDto;
import gov.openpto.odp.dto.PartyDto;
import gov.openpto.odp.dto.PatentUpsert;
import gov.openpto.odp.dto.TrademarkEventDto;
import gov.openpto.odp.dto.TrademarkUpsert;
import gov.openpto.odp.model.CitedBy;
import gov.openpto.odp.model.MarkType;
import gov.openpto.odp.model.PatentStatus;
import gov.openpto.odp.model.PatentType;
import gov.openpto.odp.model.RecordSource;
import gov.openpto.odp.model.TrademarkStatus;
import gov.openpto.odp.seed.SeedVocabulary.NiceClass;
import gov.openpto.odp.seed.SeedVocabulary.Place;
import gov.openpto.odp.seed.SeedVocabulary.Tech;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;

/**
 * Deterministic generator of realistic mock patents and trademarks. The same seed always yields
 * the same records (it uses {@link Random}, whose algorithm is specified, and only iterates
 * ordered collections). Dates are evaluated against the fixed {@link #AS_OF} date, so statuses do
 * not drift with the wall clock: grant after filing, expiration = filing + 20 years (design:
 * grant + 15/14 years), expired when that is before {@code AS_OF}; trademark events are
 * chronological and the status follows the last event.
 */
public final class MockDataGenerator {

    public static final LocalDate AS_OF = LocalDate.of(2026, 6, 30);

    private static final LocalDate PATENT_FROM = LocalDate.of(2003, 1, 2);
    private static final LocalDate PATENT_TO = LocalDate.of(2026, 3, 31);
    private static final LocalDate TRADEMARK_FROM = LocalDate.of(2012, 1, 3);
    private static final LocalDate TRADEMARK_TO = LocalDate.of(2026, 5, 29);
    private static final LocalDate DESIGN_TERM_CHANGE = LocalDate.of(2015, 5, 13);

    private final long seed;

    public MockDataGenerator(long seed) {
        this.seed = seed;
    }

    // =============================================================================================
    // Patents
    // =============================================================================================

    private record Company(String name, Place place, List<Integer> techs) {
    }

    /** Mutable working record; turned into an immutable {@link PatentUpsert} at the end. */
    private static final class Draft {
        int index;
        PatentType type;
        Tech tech;
        int techIndex;
        LocalDate filing;
        LocalDate grant;
        LocalDate priority;
        LocalDate expiration;
        PatentStatus status;
        boolean published = true;
        String number;
        String applicationNumber;
        String title;
        String abstractText;
        String primaryCpc;
        List<String> cpc = List.of();
        List<ClaimDto> claims = List.of();
        List<PartyDto> inventors = List.of();
        List<PartyDto> assignees = List.of();
        List<CitationDto> citations = List.of();
        String examiner;
        String artUnit;
    }

    public List<PatentUpsert> patents(int count) {
        Random rnd = new Random(seed);
        List<Company> companies = companies(rnd);
        Map<Integer, List<Company>> byTech = new HashMap<>();
        for (Company c : companies) {
            for (int t : c.techs()) {
                byTech.computeIfAbsent(t, k -> new ArrayList<>()).add(c);
            }
        }
        List<Draft> drafts = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            drafts.add(draft(rnd, i, byTech));
        }
        assignNumbers(rnd, drafts);
        assignCitations(rnd, drafts);
        return drafts.stream().map(MockDataGenerator::toUpsert).toList();
    }

    private List<Company> companies(Random rnd) {
        List<Company> companies = new ArrayList<>();
        Set<String> names = new LinkedHashSet<>();
        int target = 90;
        int attempt = 0;
        while (companies.size() < target && attempt++ < 10_000) {
            String country = weighted(rnd, COMPANY_COUNTRIES);
            int primaryTech = companies.size() % TECHS.size();
            Tech tech = TECHS.get(primaryTech);
            String name = pick(rnd, COMPANY_PREFIXES.get(country)) + " " + pick(rnd, tech.domains())
                    + pick(rnd, COMPANY_SUFFIXES.get(country));
            if (!names.add(name)) {
                continue;
            }
            List<Integer> techs = new ArrayList<>(List.of(primaryTech));
            if (rnd.nextDouble() < 0.35) {
                int second = rnd.nextInt(TECHS.size());
                if (second != primaryTech) {
                    techs.add(second);
                }
            }
            companies.add(new Company(name, pick(rnd, CITIES.get(country)), techs));
        }
        return companies;
    }

    private Draft draft(Random rnd, int index, Map<Integer, List<Company>> byTech) {
        Draft d = new Draft();
        d.index = index;
        double t = rnd.nextDouble();
        d.type = t < 0.88 ? PatentType.UTILITY : t < 0.955 ? PatentType.DESIGN : t < 0.97 ? PatentType.PLANT : PatentType.REISSUE;
        d.techIndex = rnd.nextInt(TECHS.size());
        d.tech = TECHS.get(d.techIndex);
        d.filing = skewedDate(rnd, PATENT_FROM, PATENT_TO, 0.75);
        d.priority = rnd.nextDouble() < 0.45 ? d.filing.minusDays(180 + rnd.nextInt(186)) : d.filing;

        // Prosecution outcome relative to AS_OF.
        int pendencyDays = d.type == PatentType.DESIGN ? 330 + rnd.nextInt(420) : 420 + rnd.nextInt(1050);
        LocalDate candidateGrant = nextTuesday(d.filing.plusDays(pendencyDays));
        boolean abandoned = d.type != PatentType.DESIGN && d.type != PatentType.REISSUE
                && d.filing.isBefore(AS_OF.minusYears(2)) && rnd.nextDouble() < 0.11;
        if (abandoned) {
            d.status = PatentStatus.ABANDONED;
        } else if (candidateGrant.isAfter(AS_OF)) {
            d.status = PatentStatus.PENDING;
        } else {
            d.grant = candidateGrant;
            if (d.type == PatentType.DESIGN) {
                d.expiration = d.grant.plusYears(d.filing.isBefore(DESIGN_TERM_CHANGE) ? 14 : 15);
            } else {
                d.expiration = d.filing.plusYears(20);
            }
            d.status = d.expiration.isBefore(AS_OF) ? PatentStatus.EXPIRED : PatentStatus.GRANTED;
        }
        d.published = d.type == PatentType.UTILITY && rnd.nextDouble() < 0.9;

        // Parties.
        List<Company> candidates = byTech.getOrDefault(d.techIndex, List.of());
        Company assignee = null;
        if (!candidates.isEmpty() && rnd.nextDouble() > 0.06) {
            double r = rnd.nextDouble();
            assignee = candidates.get((int) (candidates.size() * r * r));
        }
        d.assignees = assignee == null ? List.of() : new ArrayList<>(List.of(party(assignee.name(), assignee.place())));
        if (assignee != null && rnd.nextDouble() < 0.05) {
            Company co = pick(rnd, candidates);
            if (!co.name().equals(assignee.name())) {
                d.assignees.add(party(co.name(), co.place()));
            }
        }
        int inventorCount = 1 + (int) Math.min(5, Math.abs(rnd.nextGaussian() * 1.6));
        List<PartyDto> inventors = new ArrayList<>();
        Set<String> inventorNames = new LinkedHashSet<>();
        for (int i = 0; i < inventorCount; i++) {
            String country = assignee != null && rnd.nextDouble() < 0.7
                    ? assignee.place().country()
                    : weighted(rnd, INVENTOR_COUNTRIES);
            String name = personName(rnd, country);
            if (inventorNames.add(name)) {
                inventors.add(party(name, pick(rnd, CITIES.get(country))));
            }
        }
        d.inventors = inventors;
        d.examiner = personName(rnd, "US");
        d.artUnit = switch (d.type) {
            case DESIGN -> "29" + (10 + rnd.nextInt(10));
            case PLANT -> "1661";
            default -> d.tech.artUnitPrefix() + rnd.nextInt(10);
        };

        switch (d.type) {
            case DESIGN -> design(rnd, d);
            case PLANT -> plant(rnd, d);
            default -> utility(rnd, d);
        }
        return d;
    }

    private void utility(Random rnd, Draft d) {
        Tech tech = d.tech;
        String subject = pick(rnd, tech.subjects());
        String feature = pick(rnd, tech.features());
        String purpose = pick(rnd, tech.purposes());
        List<String> components = pickDistinct(rnd, tech.components(), 3);
        d.title = switch (rnd.nextInt(6)) {
            case 0 -> capitalize(subject) + " with " + stripArticle(feature);
            case 1 -> capitalize(subject) + " having " + feature;
            case 2 -> "Method and system for " + purpose;
            case 3 -> capitalize(subject) + " for " + purpose;
            case 4 -> "Method of manufacturing " + article(subject) + " " + subject;
            default -> "Systems and methods for " + purpose + " using " + stripArticle(feature);
        };
        String closing = pick(rnd, CLOSINGS);
        d.abstractText = capitalize(article(subject)) + " " + subject + " for " + purpose + " is disclosed. The " + subject
                + " includes " + components.get(0) + ", " + components.get(1) + ", and " + components.get(2)
                + ". In some embodiments, the " + subject + " incorporates " + feature + ", which "
                + pick(rnd, BENEFITS) + ". " + (closing.contains("%s") ? closing.formatted(subject) : closing);

        List<String> groups = tech.groups();
        d.primaryCpc = pick(rnd, groups);
        LinkedHashSet<String> cpc = new LinkedHashSet<>(List.of(d.primaryCpc));
        int extra = rnd.nextInt(4);
        for (int i = 0; i < extra; i++) {
            cpc.add(rnd.nextDouble() < 0.7 ? pick(rnd, groups) : pick(rnd, TECHS.get(rnd.nextInt(TECHS.size())).groups()));
        }
        d.cpc = List.copyOf(cpc);
        d.claims = claims(rnd, subject, feature, purpose, components, tech.subclass().startsWith("G"));
    }

    private List<ClaimDto> claims(
            Random rnd, String subject, String feature, String purpose, List<String> components, boolean software) {
        int n = (int) Math.round(16 + rnd.nextGaussian() * 6);
        n = Math.max(1, Math.min(30, n));
        List<Integer> independents = new ArrayList<>(List.of(1));
        if (n >= 10) {
            independents.add(n / 2 + 1);
        }
        if (n >= 18) {
            independents.add(n - 3);
        }
        List<ClaimDto> claims = new ArrayList<>(n);
        int currentIndependent = 1;
        String currentKind = "apparatus";
        for (int number = 1; number <= n; number++) {
            int independentIdx = independents.indexOf(number);
            if (independentIdx >= 0) {
                currentIndependent = number;
                currentKind = independentIdx == 0 ? "apparatus" : independentIdx == 1 ? "method" : software ? "medium" : "system";
                claims.add(new ClaimDto(number, independentClaim(currentKind, subject, feature, purpose, components), true, null));
                continue;
            }
            int base = rnd.nextDouble() < 0.7 || number - 1 == currentIndependent
                    ? currentIndependent
                    : currentIndependent + 1 + rnd.nextInt(number - currentIndependent - 1);
            String text = switch (currentKind) {
                case "method" -> "The method of claim " + base + ", further comprising " + pick(rnd, STEPS) + ".";
                case "medium" -> "The non-transitory computer-readable medium of claim " + base
                        + ", wherein the instructions further cause the processor to perform " + pick(rnd, STEPS) + ".";
                case "system" -> "The system of claim " + base + ", wherein " + pick(rnd, components)
                        + " comprises " + pick(rnd, DETAILS) + ".";
                default -> "The " + subject + " of claim " + base + ", wherein " + pick(rnd, components)
                        + " comprises " + pick(rnd, DETAILS) + ".";
            };
            claims.add(new ClaimDto(number, text, false, base));
        }
        return claims;
    }

    private static String independentClaim(String kind, String subject, String feature, String purpose, List<String> c) {
        return switch (kind) {
            case "method" -> "A method of " + purpose + ", the method comprising: providing " + c.get(0) + "; coupling "
                    + c.get(1) + " to " + stripArticle(c.get(0)) + "; and operating " + c.get(2) + " using " + feature + ".";
            case "medium" -> "A non-transitory computer-readable medium storing instructions that, when executed by a "
                    + "processor, cause the processor to: receive data from " + c.get(0) + "; process the data using "
                    + feature + "; and output a result for " + purpose + ".";
            case "system" -> "A system for " + purpose + ", comprising: " + c.get(0) + "; " + c.get(1)
                    + "; and a controller configured to operate " + c.get(2) + " based on " + feature + ".";
            default -> capitalize(article(subject)) + " " + subject + ", comprising: " + c.get(0) + "; " + c.get(1)
                    + "; and " + c.get(2) + ", wherein the " + subject + " incorporates " + feature + ".";
        };
    }

    private void design(Random rnd, Draft d) {
        String object = pick(rnd, DESIGN_OBJECTS);
        d.title = capitalize(object);
        d.abstractText = null;
        d.primaryCpc = null;
        d.cpc = List.of();
        d.claims = List.of(new ClaimDto(1, "The ornamental design for " + article(object) + " " + object
                + ", as shown and described.", true, null));
    }

    private void plant(Random rnd, Draft d) {
        int crop = rnd.nextInt(PLANT_CROPS.size());
        String variety = pick(rnd, VARIETY_WORDS) + " " + pick(rnd, VARIETY_WORDS);
        String cropName = PLANT_CROPS.get(crop);
        d.title = cropName + " named '" + variety + "'";
        d.abstractText = "A new and distinct variety of " + cropName.toLowerCase(Locale.ROOT) + " named '" + variety
                + "' is characterized by vigorous growth, " + pick(rnd, List.of("early", "late", "extended")) + " flowering, "
                + pick(rnd, List.of("disease resistance", "cold hardiness", "heat tolerance", "high yield"))
                + " and consistent reproduction by asexual propagation.";
        d.primaryCpc = PLANT_CPC.get(crop);
        d.cpc = List.of(d.primaryCpc);
        d.claims = List.of(new ClaimDto(1, "A new and distinct variety of " + cropName.toLowerCase(Locale.ROOT)
                + " as illustrated and described herein.", true, null));
    }

    /** Patent/publication/application numbers, monotonic in the relevant date so they look real. */
    private void assignNumbers(Random rnd, List<Draft> drafts) {
        List<Draft> byFiling = new ArrayList<>(drafts);
        byFiling.sort(Comparator.comparing((Draft d) -> d.filing).thenComparingInt(d -> d.index));
        Map<Integer, Integer> serialBySeries = new HashMap<>();
        for (Draft d : byFiling) {
            int series = d.type == PatentType.DESIGN ? 29 : applicationSeries(d.filing);
            int serial = serialBySeries.merge(series, 100_000 + rnd.nextInt(500), (a, b) -> a + 1 + rnd.nextInt(150));
            d.applicationNumber = "%02d/%03d,%03d".formatted(series, serial / 1000, serial % 1000);
        }

        List<Draft> granted = drafts.stream().filter(d -> d.grant != null)
                .sorted(Comparator.comparing((Draft d) -> d.grant).thenComparingInt(d -> d.index))
                .toList();
        Map<PatentType, Long> last = new HashMap<>();
        for (Draft d : granted) {
            long days = ChronoUnit.DAYS.between(LocalDate.of(2018, 6, 19), d.grant);
            long formula = switch (d.type) {
                case UTILITY -> 10_000_000L + days * 960;
                case DESIGN -> 900_000L + ChronoUnit.DAYS.between(LocalDate.of(2021, 1, 5), d.grant) * 85;
                case PLANT -> 32_000L + ChronoUnit.DAYS.between(LocalDate.of(2020, 6, 2), d.grant) * 3;
                case REISSUE -> 48_000L + ChronoUnit.DAYS.between(LocalDate.of(2020, 6, 2), d.grant) * 2;
            };
            long number = Math.max(formula, last.getOrDefault(d.type, 0L) + 1 + rnd.nextInt(3));
            last.put(d.type, number);
            d.number = switch (d.type) {
                case UTILITY -> "US" + number + (d.published ? "B2" : "B1");
                case DESIGN -> "USD" + number + "S";
                case PLANT -> "USPP" + number + "P3";
                case REISSUE -> "USRE" + number + "E";
            };
        }

        // Pending/abandoned applications are identified by their pre-grant publication number.
        Map<Integer, Integer> pubSeq = new HashMap<>();
        List<Draft> unpublished = drafts.stream().filter(d -> d.grant == null)
                .sorted(Comparator.comparing((Draft d) -> d.filing).thenComparingInt(d -> d.index))
                .toList();
        for (Draft d : unpublished) {
            int year = d.priority.plusMonths(18).getYear();
            int seq = pubSeq.merge(year, 100_000 + rnd.nextInt(1000), (a, b) -> a + 1 + rnd.nextInt(200));
            d.number = "US%d%07dA1".formatted(year, seq);
        }
    }

    /** Citations only point at seeded patents granted before this patent was filed (60% same CPC area). */
    private void assignCitations(Random rnd, List<Draft> drafts) {
        List<Draft> granted = drafts.stream().filter(d -> d.grant != null)
                .sorted(Comparator.comparing((Draft d) -> d.grant).thenComparingInt(d -> d.index))
                .toList();
        Map<Integer, List<Draft>> grantedByTech = new HashMap<>();
        for (Draft g : granted) {
            grantedByTech.computeIfAbsent(g.techIndex, k -> new ArrayList<>()).add(g);
        }
        for (Draft d : drafts) {
            if (d.type == PatentType.PLANT) {
                continue;
            }
            int eligibleAll = countGrantedBefore(granted, d.filing);
            List<Draft> sameTech = grantedByTech.getOrDefault(d.techIndex, List.of());
            int eligibleSame = countGrantedBefore(sameTech, d.filing);
            if (eligibleAll == 0) {
                continue;
            }
            int wanted = (int) Math.min(15, Math.abs(rnd.nextGaussian() * 5 + 5));
            Set<String> chosen = new TreeSet<>();
            List<CitationDto> citations = new ArrayList<>();
            for (int i = 0; i < wanted * 2 && citations.size() < wanted; i++) {
                Draft target = eligibleSame > 0 && rnd.nextDouble() < 0.6
                        ? sameTech.get(rnd.nextInt(eligibleSame))
                        : granted.get(rnd.nextInt(eligibleAll));
                if (target != d && chosen.add(target.number)) {
                    citations.add(new CitationDto(target.number, rnd.nextDouble() < 0.4 ? CitedBy.EXAMINER : CitedBy.APPLICANT));
                }
            }
            d.citations = citations;
        }
    }

    private static int countGrantedBefore(List<Draft> sortedByGrant, LocalDate date) {
        int lo = 0;
        int hi = sortedByGrant.size();
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (sortedByGrant.get(mid).grant.isBefore(date)) {
                lo = mid + 1;
            } else {
                hi = mid;
            }
        }
        return lo;
    }

    private static PatentUpsert toUpsert(Draft d) {
        return new PatentUpsert(
                d.number,
                d.applicationNumber,
                d.title,
                d.abstractText,
                d.type,
                d.status,
                d.filing,
                d.grant,
                d.priority,
                d.expiration,
                d.primaryCpc,
                d.cpc,
                d.claims,
                List.copyOf(d.inventors),
                List.copyOf(d.assignees),
                List.copyOf(d.citations),
                d.examiner,
                d.artUnit,
                null,
                RecordSource.SEED);
    }

    static int applicationSeries(LocalDate filing) {
        int y = filing.getYear();
        if (y <= 2004) {
            return 10;
        } else if (y <= 2007) {
            return 11;
        } else if (y <= 2010) {
            return 12;
        } else if (y <= 2013) {
            return 13;
        } else if (y == 2014) {
            return 14;
        } else if (y <= 2017) {
            return 15;
        } else if (y <= 2019) {
            return 16;
        } else if (y <= 2022) {
            return 17;
        } else if (y <= 2024) {
            return 18;
        }
        return 19;
    }

    // =============================================================================================
    // Trademarks
    // =============================================================================================

    private record Owner(String name, String address, boolean foreign, String country) {
    }

    public List<TrademarkUpsert> trademarks(int count) {
        Random rnd = new Random(seed * 31 + 7);
        List<Owner> owners = owners(rnd, Math.max(10, count / 3));
        List<TrademarkUpsert> drafts = new ArrayList<>(count);
        Map<Integer, Integer> serialByPrefix = new HashMap<>();
        List<LocalDate> filings = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            filings.add(skewedDate(rnd, TRADEMARK_FROM, TRADEMARK_TO, 0.8));
        }
        filings.sort(Comparator.naturalOrder());
        for (LocalDate filing : filings) {
            drafts.add(trademark(rnd, filing, owners, serialByPrefix));
        }
        return assignRegistrationNumbers(rnd, drafts);
    }

    private List<Owner> owners(Random rnd, int n) {
        List<Owner> owners = new ArrayList<>(n);
        Set<String> names = new LinkedHashSet<>();
        while (owners.size() < n) {
            boolean foreign = rnd.nextDouble() < 0.14;
            String country = foreign ? pick(rnd, List.of("DE", "FR", "JP", "GB", "KR", "CH", "NL")) : "US";
            String name;
            if (!foreign && rnd.nextDouble() < 0.15) {
                name = personName(rnd, "US");
            } else {
                NiceClass nc = pick(rnd, SeedVocabulary.NICE_CLASSES);
                name = titleCase(pick(rnd, SeedVocabulary.MARK_WORDS)) + " " + titleCase(pick(rnd, nc.words()))
                        + pick(rnd, COMPANY_SUFFIXES.get(foreign ? country : "US"));
            }
            if (names.add(name)) {
                owners.add(new Owner(name, address(rnd, country), foreign, country));
            }
        }
        return owners;
    }

    private TrademarkUpsert trademark(Random rnd, LocalDate filing, List<Owner> owners, Map<Integer, Integer> serialByPrefix) {
        double r = rnd.nextDouble();
        Owner owner = owners.get((int) (owners.size() * r * r));
        String basis;
        if (owner.foreign()) {
            basis = rnd.nextDouble() < 0.55 ? "66A" : "44E";
        } else {
            basis = rnd.nextDouble() < 0.64 ? "1A" : "1B";
        }
        NiceClass primary = pick(rnd, SeedVocabulary.NICE_CLASSES);
        Set<Integer> classes = new TreeSet<>(List.of(primary.number()));
        int extra = rnd.nextDouble() < 0.55 ? 0 : 1 + rnd.nextInt(2);
        for (int i = 0; i < extra && i < primary.related().size(); i++) {
            classes.add(primary.related().get(rnd.nextInt(primary.related().size())));
        }
        List<GoodsServiceDto> goods = classes.stream()
                .map(c -> new GoodsServiceDto(c, niceClass(c).goods()))
                .toList();

        double mt = rnd.nextDouble();
        MarkType markType = mt < 0.84 ? MarkType.STANDARD_CHARACTER : mt < 0.98 ? MarkType.DESIGN : MarkType.SOUND;
        String markText = switch (markType) {
            case SOUND -> pick(rnd, SeedVocabulary.SOUND_MARKS) + " (SOUND MARK)";
            default -> {
                String base = rnd.nextDouble() < 0.25
                        ? pick(rnd, SeedVocabulary.COINED_HEADS) + pick(rnd, SeedVocabulary.COINED_TAILS)
                        : pick(rnd, SeedVocabulary.MARK_WORDS);
                String text = rnd.nextDouble() < 0.6 ? base + " " + pick(rnd, primary.words()) : base;
                yield markType == MarkType.DESIGN ? text + " (AND DESIGN)" : text;
            }
        };

        int prefix = "66A".equals(basis) ? 79 : serialPrefix(filing);
        int serial = serialByPrefix.merge(prefix, 10_000 + rnd.nextInt(5000), (a, b) -> a + 1 + rnd.nextInt(400));
        String serialNumber = "%02d%06d".formatted(prefix, serial % 1_000_000);

        // Prosecution history (TSDR event codes), cut off at AS_OF.
        List<TrademarkEventDto> events = new ArrayList<>();
        TrademarkStatus status = TrademarkStatus.LIVE_PENDING;
        LocalDate registration = null;
        add(events, filing.plusDays(2), "NWAP", "NEW APPLICATION ENTERED");
        add(events, filing.plusDays(6 + rnd.nextInt(5)), "NWOS", "NEW APPLICATION OFFICE SUPPLIED DATA ENTERED");
        LocalDate exam = filing.plusDays(95 + rnd.nextInt(170));
        add(events, exam, "DOCK", "ASSIGNED TO EXAMINER");
        LocalDate approved = exam.plusDays(1 + rnd.nextInt(5));
        boolean done = false;
        if (rnd.nextDouble() < 0.55) {
            add(events, exam.plusDays(1), "CNRT", "NON-FINAL ACTION WRITTEN");
            add(events, exam.plusDays(1), "GNRT", "NON-FINAL ACTION E-MAILED");
            if (rnd.nextDouble() < 0.2) {
                LocalDate abandonedOn = exam.plusMonths(6).plusDays(3 + rnd.nextInt(20));
                if (add(events, abandonedOn, "ABN2", "ABANDONMENT - FAILURE TO RESPOND OR LATE RESPONSE")) {
                    add(events, abandonedOn, "MAB6", "ABANDONMENT NOTICE E-MAILED - FAILURE TO RESPOND");
                    status = TrademarkStatus.DEAD_ABANDONED;
                }
                done = true;
            } else {
                LocalDate response = exam.plusDays(30 + rnd.nextInt(150));
                add(events, response, "TROA", "TEAS RESPONSE TO OFFICE ACTION RECEIVED");
                approved = response.plusDays(20 + rnd.nextInt(40));
            }
        }
        if (!done && add(events, approved, "CNSA", "APPROVED FOR PUB - PRINCIPAL REGISTER")) {
            LocalDate publication = nextTuesday(approved.plusDays(28 + rnd.nextInt(14)));
            add(events, publication.minusDays(20), "NPUB", "NOTICE OF PUBLICATION");
            if (add(events, publication, "PUBO", "PUBLISHED FOR OPPOSITION")) {
                if ("1B".equals(basis)) {
                    LocalDate noa = nextTuesday(publication.plusWeeks(8));
                    if (add(events, noa, "NOAM", "NOA E-MAILED - SOU REQUIRED FROM APPLICANT")) {
                        if (rnd.nextDouble() < 0.25) {
                            LocalDate abandonedOn = noa.plusMonths(6).plusDays(10 + rnd.nextInt(30));
                            if (add(events, abandonedOn, "ABN6", "ABANDONMENT - NO USE STATEMENT FILED")) {
                                status = TrademarkStatus.DEAD_ABANDONED;
                            }
                        } else {
                            LocalDate sou = noa.plusDays(60 + rnd.nextInt(360));
                            if (add(events, sou, "IUAF", "USE AMENDMENT FILED")) {
                                registration = register(events, nextTuesday(sou.plusDays(60 + rnd.nextInt(60))));
                            }
                        }
                    }
                } else {
                    registration = register(events, nextTuesday(publication.plusWeeks(11 + rnd.nextInt(3))));
                }
            }
        }
        if (registration != null) {
            status = TrademarkStatus.LIVE_REGISTERED;
            LocalDate sixYears = registration.plusYears(6);
            if (sixYears.plusMonths(7).isBefore(AS_OF)) {
                if (rnd.nextDouble() < 0.12) {
                    add(events, sixYears.plusMonths(6).plusDays(rnd.nextInt(30)), "COS8", "CANCELLED SEC. 8 (6-YR)");
                    status = TrademarkStatus.DEAD_CANCELLED;
                } else {
                    add(events, sixYears.minusMonths(2).plusDays(rnd.nextInt(60)), "E8AC", "REGISTERED - SEC. 8 (6-YR) ACCEPTED");
                }
            }
        }
        events.sort(Comparator.comparing(TrademarkEventDto::date));
        String attorney = rnd.nextDouble() < 0.8 ? personName(rnd, "US") : null;
        return new TrademarkUpsert(
                serialNumber,
                registration == null ? null : "PENDING",
                markText,
                markType,
                status,
                filing,
                registration,
                events.getLast().date(),
                owner.name(),
                owner.address(),
                attorney,
                basis,
                goods,
                List.copyOf(events),
                null,
                RecordSource.SEED);
    }

    private static LocalDate register(List<TrademarkEventDto> events, LocalDate date) {
        return add(events, date, "R.PR", "REGISTERED-PRINCIPAL REGISTER") ? date : null;
    }

    /** Registration numbers are monotonic in registration date (~6,000,000 in early 2020). */
    private List<TrademarkUpsert> assignRegistrationNumbers(Random rnd, List<TrademarkUpsert> drafts) {
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < drafts.size(); i++) {
            if (drafts.get(i).registrationDate() != null) {
                order.add(i);
            }
        }
        order.sort(Comparator.comparing((Integer i) -> drafts.get(i).registrationDate()).thenComparing(i -> i));
        List<TrademarkUpsert> out = new ArrayList<>(drafts);
        long last = 0;
        for (int i : order) {
            TrademarkUpsert t = drafts.get(i);
            long formula = 6_000_000L + ChronoUnit.DAYS.between(LocalDate.of(2020, 3, 3), t.registrationDate()) * 900;
            long number = Math.max(formula, last + 1 + rnd.nextInt(5));
            last = number;
            out.set(i, new TrademarkUpsert(t.serialNumber(), String.valueOf(number), t.markText(), t.markType(), t.status(),
                    t.filingDate(), t.registrationDate(), t.statusDate(), t.owner(), t.ownerAddress(), t.attorney(),
                    t.filingBasis(), t.goodsAndServices(), t.events(), t.ingestJobId(), t.source()));
        }
        return out;
    }

    static int serialPrefix(LocalDate filing) {
        int y = filing.getYear();
        if (y <= 2013) {
            return 85;
        } else if (y <= 2015) {
            return 86;
        } else if (y <= 2017) {
            return 87;
        } else if (y <= 2019) {
            return 88;
        } else if (y == 2020) {
            return 90;
        } else if (y <= 2022) {
            return 97;
        } else if (y <= 2024) {
            return 98;
        }
        return 99;
    }

    private static NiceClass niceClass(int number) {
        return SeedVocabulary.NICE_CLASSES.stream().filter(c -> c.number() == number).findFirst().orElseThrow();
    }

    private static boolean add(List<TrademarkEventDto> events, LocalDate date, String code, String description) {
        if (date.isAfter(AS_OF)) {
            return false;
        }
        events.add(new TrademarkEventDto(date, code, description));
        return true;
    }

    private static String address(Random rnd, String country) {
        Place place = pick(rnd, CITIES.get(country));
        int number = 10 + rnd.nextInt(9000);
        String street = pick(rnd, SeedVocabulary.STREETS);
        if ("US".equals(country)) {
            return "%d %s, %s, %s %05d, US".formatted(number, street, place.city(), place.state(), 10_000 + rnd.nextInt(89_000));
        }
        return "%d %s, %s, %s".formatted(number, street, place.city(), country);
    }

    // =============================================================================================
    // Helpers
    // =============================================================================================

    private static PartyDto party(String name, Place place) {
        return new PartyDto(name, place.city(), place.state(), place.country());
    }

    private static String personName(Random rnd, String country) {
        SeedVocabulary.Names names = NAMES.getOrDefault(country, US_NAMES);
        String first = pick(rnd, names.first());
        String last = pick(rnd, names.last());
        if (("US".equals(country) || "CA".equals(country)) && rnd.nextDouble() < 0.35) {
            return first + " " + (char) ('A' + rnd.nextInt(26)) + ". " + last;
        }
        return first + " " + last;
    }

    /** A date in [from, to]; exponent &lt; 1 skews toward recent dates (filing volume grows over time). */
    private static LocalDate skewedDate(Random rnd, LocalDate from, LocalDate to, double exponent) {
        long span = ChronoUnit.DAYS.between(from, to);
        long offset = (long) (span * Math.pow(rnd.nextDouble(), exponent));
        LocalDate d = from.plusDays(offset);
        return switch (d.getDayOfWeek()) {
            case SATURDAY -> d.plusDays(2);
            case SUNDAY -> d.plusDays(1);
            default -> d;
        };
    }

    /** US patents issue and trademarks publish on Tuesdays. */
    private static LocalDate nextTuesday(LocalDate d) {
        int shift = (java.time.DayOfWeek.TUESDAY.getValue() - d.getDayOfWeek().getValue() + 7) % 7;
        return d.plusDays(shift);
    }

    private static <T> T pick(Random rnd, List<T> list) {
        return list.get(rnd.nextInt(list.size()));
    }

    private static List<String> pickDistinct(Random rnd, List<String> list, int n) {
        List<String> copy = new ArrayList<>(list);
        List<String> out = new ArrayList<>(n);
        for (int i = 0; i < n && !copy.isEmpty(); i++) {
            out.add(copy.remove(rnd.nextInt(copy.size())));
        }
        while (out.size() < n) {
            out.add(list.get(out.size() % list.size()));
        }
        return out;
    }

    private static String weighted(Random rnd, List<Map.Entry<String, Integer>> weights) {
        int total = weights.stream().mapToInt(Map.Entry::getValue).sum();
        int roll = rnd.nextInt(total);
        for (Map.Entry<String, Integer> w : weights) {
            roll -= w.getValue();
            if (roll < 0) {
                return w.getKey();
            }
        }
        return weights.getLast().getKey();
    }

    private static String article(String noun) {
        return "aeiou".indexOf(Character.toLowerCase(noun.charAt(0))) >= 0 ? "an" : "a";
    }

    private static String stripArticle(String phrase) {
        if (phrase.startsWith("a ")) {
            return phrase.substring(2);
        }
        if (phrase.startsWith("an ")) {
            return phrase.substring(3);
        }
        if (phrase.startsWith("the ")) {
            return phrase.substring(4);
        }
        return phrase;
    }

    private static String capitalize(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static String titleCase(String upper) {
        StringBuilder sb = new StringBuilder(upper.length());
        boolean start = true;
        for (char ch : upper.toCharArray()) {
            sb.append(start ? Character.toUpperCase(ch) : Character.toLowerCase(ch));
            start = ch == ' ' || ch == '-' || ch == '&';
        }
        return sb.toString();
    }
}
