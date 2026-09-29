package gov.openpto.gateway.identity;

/**
 * Who is calling a rate-limited route. The {@link #bucketKey()} is the rate-limit partition:
 * {@code key:<keyId>}, {@code user:<sub>} or {@code ip:<addr>} as defined in CONTRACTS.md.
 *
 * @param type      how the caller was identified
 * @param id        key id, JWT subject or client IP
 * @param tier      usage-plan tier
 * @param perMinute requests allowed per minute
 * @param perDay    requests allowed per day
 */
public record Identity(Type type, String id, Tier tier, long perMinute, long perDay) {

    /** Request attribute holding the resolved identity (read by the access log). */
    public static final String ATTRIBUTE = Identity.class.getName();

    public enum Type {
        KEY("key"), USER("user"), IP("ip");

        private final String prefix;

        Type(String prefix) {
            this.prefix = prefix;
        }

        public String prefix() {
            return prefix;
        }
    }

    public String bucketKey() {
        return type.prefix() + ":" + id;
    }

    @Override
    public String toString() {
        return bucketKey() + "(" + tier + ")";
    }
}
