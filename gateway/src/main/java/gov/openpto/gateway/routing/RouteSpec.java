package gov.openpto.gateway.routing;

import gov.openpto.gateway.config.GatewayProperties;

import java.util.List;

/**
 * A proxied route: id (for per-route timeouts and logs), target service key in {@code gateway.services},
 * and path patterns.
 *
 * @param stripApiKey remove the {@code api_key} query parameter before forwarding (secrets stay at the edge)
 */
public record RouteSpec(String id, String service, List<String> paths, boolean stripApiKey) {

    /** Contract route table (CONTRACTS.md, gateway section). {@code /internal/**} is handled separately (404). */
    public static final List<RouteSpec> ALL = List.of(
            new RouteSpec("odp-data", GatewayProperties.ODP, RoutePaths.ODP_DATA, true),
            new RouteSpec("odp-datasets", GatewayProperties.ODP, RoutePaths.ODP_DATASETS, true),
            new RouteSpec("odp-accounts", GatewayProperties.ODP, RoutePaths.ODP_ACCOUNTS, false),
            new RouteSpec("fees", GatewayProperties.FEES, RoutePaths.FEES, true),
            new RouteSpec("ingest", GatewayProperties.INGEST, RoutePaths.INGEST, false));
}
