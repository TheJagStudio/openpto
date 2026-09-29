package gov.openpto.fee.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * In-memory copies of the seeded schedules (V2 migration) so the pure core is testable without a DB.
 * {@code FeeServiceIntegrationTest} asserts that the database seed matches these amounts exactly.
 */
public final class TestSchedules {

    public static final LocalDate FY2025_START = LocalDate.of(2025, 1, 19);
    public static final LocalDate FY2023_START = LocalDate.of(2022, 12, 29);
    public static final LocalDate FY2023_END = LocalDate.of(2025, 1, 18);

    private static final FeeSchedule FY2025 = build(1L, "FY2025", "FY2025 (test)", FY2025_START, null, new String[]{
            "UTIL_FILING Filing 350 140 70 EACH",
            "UTIL_SEARCH Search 770 308 154 EACH",
            "UTIL_EXAM Examination 880 352 176 EACH",
            "DESIGN_FILING Filing 300 120 60 EACH",
            "DESIGN_SEARCH Search 160 64 32 EACH",
            "DESIGN_EXAM Examination 640 256 128 EACH",
            "PLANT_FILING Filing 350 140 70 EACH",
            "PLANT_SEARCH Search 480 192 96 EACH",
            "PLANT_EXAM Examination 700 280 140 EACH",
            "REISSUE_FILING Filing 350 140 70 EACH",
            "REISSUE_SEARCH Search 770 308 154 EACH",
            "REISSUE_EXAM Examination 2860 1144 572 EACH",
            "PROV_FILING Filing 325 130 65 EACH",
            "CLAIM_INDEP_OVER_3 Excess_claims 600 240 120 PER_CLAIM",
            "CLAIM_OVER_20 Excess_claims 200 80 40 PER_CLAIM",
            "CLAIM_MULTI_DEP Excess_claims 925 370 185 EACH",
            "APP_SIZE Application_size 440 176 88 PER_50_SHEETS",
            "NON_ELECTRONIC Surcharges 400 200 200 EACH",
            "LATE_SURCHARGE Surcharges 160 64 32 EACH",
            "EXT_1M Extensions_of_time 235 94 47 EACH",
            "EXT_2M Extensions_of_time 530 212 106 EACH",
            "EXT_3M Extensions_of_time 1260 504 252 EACH",
            "EXT_4M Extensions_of_time 2010 804 402 EACH",
            "EXT_5M Extensions_of_time 2760 1104 552 EACH",
            "RCE_FIRST Continued_examination 1500 600 300 EACH",
            "RCE_SUBSEQUENT Continued_examination 2860 1144 572 EACH",
            "TRACK_ONE Prioritized_examination 4515 1806 903 EACH",
            "MAINT_3_5 Maintenance 2150 860 430 EACH",
            "MAINT_7_5 Maintenance 4040 1616 808 EACH",
            "MAINT_11_5 Maintenance 8280 3312 1656 EACH",
            "MAINT_SURCHARGE Maintenance 540 216 108 EACH",
            "TM_BASE_APP Application 350 350 350 PER_CLASS",
            "TM_INSUFFICIENT_INFO Application 100 100 100 PER_CLASS",
            "TM_FREE_FORM Application 200 200 200 PER_CLASS",
            "TM_EXTRA_CHARS Application 200 200 200 PER_CLASS",
            "TM_SOU Intent_to_use 150 150 150 PER_CLASS",
            "TM_EXT_SOU Intent_to_use 125 125 125 PER_CLASS",
            "TM_SEC8 Post-registration 325 325 325 PER_CLASS",
            "TM_SEC15 Post-registration 250 250 250 PER_CLASS",
            "TM_SEC9 Post-registration 325 325 325 PER_CLASS",
            "TM_SEC8_GRACE Post-registration 100 100 100 PER_CLASS",
            "TM_SEC9_GRACE Post-registration 100 100 100 PER_CLASS",
    });

    private static final FeeSchedule FY2023 = build(2L, "FY2023", "FY2023 (test)", FY2023_START, FY2023_END, new String[]{
            "UTIL_FILING Filing 320 128 64 EACH",
            "UTIL_SEARCH Search 700 280 140 EACH",
            "UTIL_EXAM Examination 800 320 160 EACH",
            "DESIGN_FILING Filing 280 112 56 EACH",
            "DESIGN_SEARCH Search 160 64 32 EACH",
            "DESIGN_EXAM Examination 640 256 128 EACH",
            "PLANT_FILING Filing 320 128 64 EACH",
            "PLANT_SEARCH Search 440 176 88 EACH",
            "PLANT_EXAM Examination 660 264 132 EACH",
            "REISSUE_FILING Filing 320 128 64 EACH",
            "REISSUE_SEARCH Search 700 280 140 EACH",
            "REISSUE_EXAM Examination 2480 992 496 EACH",
            "PROV_FILING Filing 300 120 60 EACH",
            "CLAIM_INDEP_OVER_3 Excess_claims 480 192 96 PER_CLAIM",
            "CLAIM_OVER_20 Excess_claims 100 40 20 PER_CLAIM",
            "CLAIM_MULTI_DEP Excess_claims 860 344 172 EACH",
            "APP_SIZE Application_size 420 168 84 PER_50_SHEETS",
            "NON_ELECTRONIC Surcharges 400 200 200 EACH",
            "LATE_SURCHARGE Surcharges 160 64 32 EACH",
            "EXT_1M Extensions_of_time 220 88 44 EACH",
            "EXT_2M Extensions_of_time 640 256 128 EACH",
            "EXT_3M Extensions_of_time 1480 592 296 EACH",
            "EXT_4M Extensions_of_time 2320 928 464 EACH",
            "EXT_5M Extensions_of_time 3160 1264 632 EACH",
            "RCE_FIRST Continued_examination 1360 544 272 EACH",
            "RCE_SUBSEQUENT Continued_examination 2000 800 400 EACH",
            "TRACK_ONE Prioritized_examination 4515 1806 903 EACH",
            "MAINT_3_5 Maintenance 2000 800 400 EACH",
            "MAINT_7_5 Maintenance 3760 1504 752 EACH",
            "MAINT_11_5 Maintenance 7700 3080 1540 EACH",
            "MAINT_SURCHARGE Maintenance 500 200 100 EACH",
            "TM_BASE_APP Application 350 350 350 PER_CLASS",
            "TM_SOU Intent_to_use 100 100 100 PER_CLASS",
            "TM_EXT_SOU Intent_to_use 125 125 125 PER_CLASS",
            "TM_SEC8 Post-registration 225 225 225 PER_CLASS",
            "TM_SEC15 Post-registration 200 200 200 PER_CLASS",
            "TM_SEC9 Post-registration 300 300 300 PER_CLASS",
            "TM_SEC8_GRACE Post-registration 100 100 100 PER_CLASS",
            "TM_SEC9_GRACE Post-registration 100 100 100 PER_CLASS",
    });

    private TestSchedules() {
    }

    public static FeeSchedule fy2025() {
        return FY2025;
    }

    public static FeeSchedule fy2023() {
        return FY2023;
    }

    public static List<FeeSchedule> all() {
        return List.of(FY2025, FY2023);
    }

    private static FeeSchedule build(Long id, String code, String name, LocalDate from, LocalDate to, String[] rows) {
        List<FeeRate> items = new ArrayList<>();
        for (String row : rows) {
            String[] f = row.split(" ");
            FeeCategory category = f[0].startsWith("TM_") ? FeeCategory.TRADEMARK : FeeCategory.PATENT;
            items.add(new FeeRate(f[0], f[0].toLowerCase(), category, f[1].replace('_', ' '),
                    new BigDecimal(f[2]), new BigDecimal(f[3]), new BigDecimal(f[4]), FeeUnit.valueOf(f[5])));
        }
        return new FeeSchedule(id, code, name, from, to, items);
    }
}
