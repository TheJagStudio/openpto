package gov.openpto.fee.domain;

/** Fee codes used by the calculators; they exist in every seeded schedule (see the V2 migration). */
public final class FeeCodes {

    public static final String UTIL_FILING = "UTIL_FILING";
    public static final String UTIL_SEARCH = "UTIL_SEARCH";
    public static final String UTIL_EXAM = "UTIL_EXAM";
    public static final String DESIGN_FILING = "DESIGN_FILING";
    public static final String DESIGN_SEARCH = "DESIGN_SEARCH";
    public static final String DESIGN_EXAM = "DESIGN_EXAM";
    public static final String PLANT_FILING = "PLANT_FILING";
    public static final String PLANT_SEARCH = "PLANT_SEARCH";
    public static final String PLANT_EXAM = "PLANT_EXAM";
    public static final String REISSUE_FILING = "REISSUE_FILING";
    public static final String REISSUE_SEARCH = "REISSUE_SEARCH";
    public static final String REISSUE_EXAM = "REISSUE_EXAM";
    public static final String PROV_FILING = "PROV_FILING";
    public static final String CLAIM_INDEP_OVER_3 = "CLAIM_INDEP_OVER_3";
    public static final String CLAIM_OVER_20 = "CLAIM_OVER_20";
    public static final String CLAIM_MULTI_DEP = "CLAIM_MULTI_DEP";
    public static final String APP_SIZE = "APP_SIZE";
    public static final String NON_ELECTRONIC = "NON_ELECTRONIC";
    public static final String LATE_SURCHARGE = "LATE_SURCHARGE";
    public static final String RCE_FIRST = "RCE_FIRST";
    public static final String RCE_SUBSEQUENT = "RCE_SUBSEQUENT";
    public static final String TRACK_ONE = "TRACK_ONE";
    public static final String MAINT_3_5 = "MAINT_3_5";
    public static final String MAINT_7_5 = "MAINT_7_5";
    public static final String MAINT_11_5 = "MAINT_11_5";
    public static final String MAINT_SURCHARGE = "MAINT_SURCHARGE";

    public static final String TM_BASE_APP = "TM_BASE_APP";
    public static final String TM_INSUFFICIENT_INFO = "TM_INSUFFICIENT_INFO";
    public static final String TM_FREE_FORM = "TM_FREE_FORM";
    public static final String TM_EXTRA_CHARS = "TM_EXTRA_CHARS";
    public static final String TM_SOU = "TM_SOU";
    public static final String TM_EXT_SOU = "TM_EXT_SOU";
    public static final String TM_SEC8 = "TM_SEC8";
    public static final String TM_SEC15 = "TM_SEC15";
    public static final String TM_SEC9 = "TM_SEC9";
    public static final String TM_SEC8_GRACE = "TM_SEC8_GRACE";
    public static final String TM_SEC9_GRACE = "TM_SEC9_GRACE";

    private FeeCodes() {
    }

    /** Extension-of-time fee code for 1..5 months. */
    public static String extension(int months) {
        if (months < 1 || months > 5) {
            throw new IllegalArgumentException("Extension months must be 1..5: " + months);
        }
        return "EXT_" + months + "M";
    }
}
