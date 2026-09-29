package gov.openpto.odp.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

/**
 * Generates API keys: {@code opto_} + 40 base62 characters from {@link SecureRandom} (~238 bits).
 * Only the SHA-256 hex digest and the first 9 characters ({@code opto_xxxx}) are persisted.
 */
@Component
public class ApiKeyGenerator {

    public static final String PREFIX = "opto_";
    public static final int RANDOM_LENGTH = 40;
    public static final int DISPLAY_PREFIX_LENGTH = 9;
    private static final char[] BASE62 = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz".toCharArray();
    private static final Pattern FORMAT = Pattern.compile("^opto_[0-9A-Za-z]{40}$");

    private final SecureRandom random = new SecureRandom();

    public record GeneratedKey(String plaintext, String hash, String prefix) {

        @Override
        public String toString() {
            return "GeneratedKey[prefix=" + prefix + "]";
        }
    }

    public GeneratedKey generate() {
        StringBuilder sb = new StringBuilder(PREFIX.length() + RANDOM_LENGTH).append(PREFIX);
        for (int i = 0; i < RANDOM_LENGTH; i++) {
            sb.append(BASE62[random.nextInt(BASE62.length)]);
        }
        String plaintext = sb.toString();
        return new GeneratedKey(plaintext, sha256Hex(plaintext), plaintext.substring(0, DISPLAY_PREFIX_LENGTH));
    }

    /** Cheap syntactic check so garbage never reaches the database. */
    public static boolean looksLikeKey(String candidate) {
        return candidate != null && FORMAT.matcher(candidate).matches();
    }

    public static String sha256Hex(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
