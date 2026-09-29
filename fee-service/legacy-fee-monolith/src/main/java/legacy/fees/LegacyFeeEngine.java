package legacy.fees;

import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Vector;

/**
 * FeeEngine - fee calculation from the PTO "monolith" (originally written for JDK 1.4, patched every
 * fee year by editing the literals below).
 *
 * Kept verbatim (FY2025 amounts) as the ORACLE for the fee-service characterization tests. Do not fix,
 * do not refactor: the point is to capture exactly what the old system charged, bugs included.
 *
 * Entity codes: "L" large, "S" small, "M" micro.
 * App types:    1 utility, 2 design, 3 plant, 4 provisional, 5 reissue.
 * RCE:          0 none, 1 first, 2 second and subsequent.
 * Returns -1 for any request the old UI would have rejected.
 */
public class LegacyFeeEngine {

    public static final String LARGE = "L";
    public static final String SMALL = "S";
    public static final String MICRO = "M";

    public static final int UTIL = 1;
    public static final int DESIGN = 2;
    public static final int PLANT = 3;
    public static final int PROV = 4;
    public static final int REISSUE = 5;

    public static final int ST_NOT_OPEN = 0;
    public static final int ST_OPEN = 1;
    public static final int ST_GRACE = 2;
    public static final int ST_PASSED = 3;

    public static final double ERROR = -1.0;

    private LegacyFeeEngine() {
    }

    /**
     * Total patent filing fee in dollars (rounded to cents), or -1 if the request is invalid.
     */
    public static double calcFilingFee(int appType, String entity, int claims, int indep, boolean multiDep,
                                       int sheets, boolean electronic, boolean late, int extMonths, int rce,
                                       boolean trackOne) {
        double total = 0.0;
        double factor = 0.0;

        if (entity == null) {
            return ERROR;
        }
        if (entity.equals("L")) {
            factor = 1.0;
        } else if (entity.equals("S")) {
            factor = 0.4;
        } else if (entity.equals("M")) {
            factor = 0.2;
        } else {
            return ERROR;
        }

        if (claims < 0 || claims > 500 || indep < 0 || indep > 100 || indep > claims) {
            return ERROR;
        }
        if (sheets < 0 || sheets > 10000 || extMonths < 0 || extMonths > 5 || rce < 0 || rce > 2) {
            return ERROR;
        }

        if (trackOne) {
            if (appType == DESIGN || appType == PLANT || appType == PROV) {
                return ERROR;
            }
            if (indep > 4 || claims > 30) {
                return ERROR;
            }
        }

        if (appType == UTIL) {
            total = total + 350 * factor;
            total = total + 770 * factor;
            total = total + 880 * factor;
            if (claims > 20) {
                total = total + (claims - 20) * 200 * factor;
            }
            if (indep > 3) {
                total = total + (indep - 3) * 600 * factor;
            }
            if (multiDep) {
                total = total + 925 * factor;
            }
        } else if (appType == DESIGN) {
            total = total + 300 * factor;
            total = total + 160 * factor;
            total = total + 640 * factor;
        } else if (appType == PLANT) {
            total = total + 350 * factor;
            total = total + 480 * factor;
            total = total + 700 * factor;
        } else if (appType == PROV) {
            total = total + 325 * factor;
        } else if (appType == REISSUE) {
            total = total + 350 * factor;
            total = total + 770 * factor;
            total = total + 2860 * factor;
            if (claims > 20) {
                total = total + (claims - 20) * 200 * factor;
            }
            if (indep > 3) {
                // reissue: "over original count" - old UI always used 3
                total = total + (indep - 3) * 600 * factor;
            }
            if (multiDep) {
                total = total + 925 * factor;
            }
        } else {
            return ERROR;
        }

        // application size fee: each 50 sheets or fraction thereof over 100
        if (sheets > 100) {
            int blocks = (sheets - 100) / 50 + 1;
            total = total + blocks * 440 * factor;
        }

        // paper filing - utility only; micro pays the small rate (not 20%!)
        if (!electronic && appType == UTIL) {
            if (entity.equals("L")) {
                total = total + 400;
            } else {
                total = total + 200;
            }
        }

        if (late) {
            total = total + 160 * factor;
        }

        if (extMonths > 0 && appType != PROV) {
            if (extMonths == 1) {
                total = total + 235 * factor;
            } else if (extMonths == 2) {
                total = total + 530 * factor;
            } else if (extMonths == 3) {
                total = total + 1260 * factor;
            } else if (extMonths == 4) {
                total = total + 2010 * factor;
            } else {
                total = total + 2760 * factor;
            }
        }

        if (rce > 0 && appType != DESIGN && appType != PROV) {
            if (rce == 1) {
                total = total + 1500 * factor;
            } else {
                total = total + 2860 * factor;
            }
        }

        if (trackOne) {
            total = total + 4515 * factor;
        }

        return Math.round(total * 100.0) / 100.0;
    }

    /**
     * Maintenance fee windows. Returns a Vector of double[4] per stage (3.5, 7.5, 11.5):
     * { status, fee, surcharge, payableOnAsOf (or -1 if it cannot be paid on asOf) }.
     * Returns null for bad input.
     */
    public static Vector calcMaintenance(String entity, Date grant, Date asOf) {
        double factor;
        if (grant == null || asOf == null || entity == null) {
            return null;
        }
        if (entity.equals("L")) {
            factor = 1.0;
        } else if (entity.equals("S")) {
            factor = 0.4;
        } else if (entity.equals("M")) {
            factor = 0.2;
        } else {
            return null;
        }
        if (asOf.before(grant)) {
            return null;
        }

        int[] openMonths = {36, 84, 132};
        int[] dueMonths = {42, 90, 138};
        double[] fees = {2150, 4040, 8280};
        double surcharge = Math.round(540 * factor * 100.0) / 100.0;

        Vector result = new Vector();
        for (int i = 0; i < 3; i++) {
            Calendar c = new GregorianCalendar();
            c.setTime(grant);
            c.add(Calendar.MONTH, openMonths[i]);
            Date open = c.getTime();

            c.setTime(grant);
            c.add(Calendar.MONTH, dueMonths[i]);
            Date due = c.getTime();

            c.setTime(grant);
            c.add(Calendar.MONTH, dueMonths[i] + 6);
            Date graceEnd = c.getTime();

            double fee = Math.round(fees[i] * factor * 100.0) / 100.0;
            double[] row = new double[4];
            row[1] = fee;
            row[2] = surcharge;
            if (asOf.before(open)) {
                row[0] = ST_NOT_OPEN;
                row[3] = -1;
            } else if (!asOf.after(due)) {
                row[0] = ST_OPEN;
                row[3] = fee;
            } else if (!asOf.after(graceEnd)) {
                row[0] = ST_GRACE;
                row[3] = Math.round((fee + surcharge) * 100.0) / 100.0;
            } else {
                row[0] = ST_PASSED;
                row[3] = -1;
            }
            result.addElement(row);
        }
        return result;
    }
}
