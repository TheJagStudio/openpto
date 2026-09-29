package gov.openpto.ingest.storage;

import java.util.regex.Pattern;

/** Rejects bucket names and keys that could escape the bucket folder (path traversal) or are not S3-safe. */
public final class ObjectKeyValidator {

    private static final Pattern BUCKET = Pattern.compile("^[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]$");
    private static final Pattern KEY = Pattern.compile("^[A-Za-z0-9!_.*'()/-]{1,1024}$");

    private ObjectKeyValidator() {
    }

    public static void validateBucket(String bucket) {
        if (bucket == null || !BUCKET.matcher(bucket).matches() || bucket.contains("..")) {
            throw new IllegalArgumentException("Invalid bucket name: " + bucket);
        }
    }

    public static void validateKey(String key) {
        if (key == null || !KEY.matcher(key).matches() || key.startsWith("/") || key.endsWith("/")
                || key.contains("//")) {
            throw new IllegalArgumentException("Invalid object key");
        }
        for (String segment : key.split("/")) {
            // ".", ".." (traversal) and dot-files (reserved for temp files / etag sidecars) are not allowed
            if (segment.startsWith(".")) {
                throw new IllegalArgumentException("Invalid object key");
            }
        }
    }

    public static void validatePrefix(String prefix) {
        if (prefix != null && !prefix.isEmpty()) {
            String probe = prefix.endsWith("/") ? prefix + "x" : prefix;
            validateKey(probe);
        }
    }
}