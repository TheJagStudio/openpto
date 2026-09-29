package gov.openpto.ingest.transform.lambda;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Object key conventions shared by the service and the function.
 * Raw objects: {@code jobs/{jobId}/{fileName}}; processed objects: {@code jobs/{jobId}/{fileName}.json}.
 */
public final class ObjectKeys {

    private static final Pattern JOB_KEY = Pattern.compile("^jobs/([0-9a-fA-F-]{36})/.+");

    private ObjectKeys() {
    }

    public static String rawKey(String jobId, String safeFileName) {
        return "jobs/" + jobId + "/" + safeFileName;
    }

    public static String outputKey(String rawKey) {
        return rawKey + ".json";
    }

    /** The job id encoded in a key, or {@code null} for keys that do not follow the convention. */
    public static String jobId(String key) {
        if (key == null) {
            return null;
        }
        Matcher m = JOB_KEY.matcher(key);
        return m.matches() ? m.group(1).toLowerCase() : null;
    }
}