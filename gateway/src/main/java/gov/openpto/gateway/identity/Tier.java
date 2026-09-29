package gov.openpto.gateway.identity;

import gov.openpto.gateway.config.GatewayProperties.TierLimit;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

/** Usage-plan tiers from CONTRACTS.md (the AWS API Gateway "usage plan" equivalent). */
public enum Tier {
    ANONYMOUS, FREE, WEB, ADMIN;

    /** Contract limits; application.yml may override them. */
    public static Map<Tier, TierLimit> contractDefaults() {
        Map<Tier, TierLimit> m = new EnumMap<>(Tier.class);
        m.put(ANONYMOUS, new TierLimit(30, 1_000));
        m.put(FREE, new TierLimit(120, 20_000));
        m.put(WEB, new TierLimit(300, 50_000));
        m.put(ADMIN, new TierLimit(1_200, 1_000_000));
        return m;
    }

    /** Parses a tier name returned by odp-service; unknown or missing names fall back to {@link #FREE}. */
    public static Tier fromApiKeyTier(String name) {
        if (name == null || name.isBlank()) {
            return FREE;
        }
        try {
            return valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return FREE;
        }
    }
}
