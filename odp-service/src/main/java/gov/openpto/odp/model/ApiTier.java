package gov.openpto.odp.model;

/** Rate-limit tiers (enforced by the gateway, reported by the internal verify endpoint). */
public enum ApiTier {
    ANONYMOUS(30, 1_000),
    FREE(120, 20_000),
    WEB(300, 50_000),
    ADMIN(1_200, 1_000_000);

    private final int perMinute;
    private final int perDay;

    ApiTier(int perMinute, int perDay) {
        this.perMinute = perMinute;
        this.perDay = perDay;
    }

    public int perMinute() {
        return perMinute;
    }

    public int perDay() {
        return perDay;
    }
}
