package gov.openpto.gateway.config;

import gov.openpto.gateway.routing.RoutePaths;
import gov.openpto.gateway.web.GatewayHeaders;
import gov.openpto.gateway.web.ProblemResponses;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

import java.time.Duration;
import java.util.List;

/**
 * Gateway security. The gateway does not double-authenticate: proxied routes are {@code permitAll} here
 * and each downstream enforces its own auth. The exception is {@code /api/v1/ingest/**} (except samples),
 * where the gateway also requires a valid JWT, mirroring an API Gateway JWT authorizer.
 * The bearer-token resolver only looks at ingest and actuator paths, so a stale token sent to a public
 * route (e.g. login) is ignored here; on data routes the identity filter validates it instead.
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
public class SecurityConfig {

    private static final List<PathPattern> JWT_PATHS = List.of(
            PathPatternParser.defaultInstance.parse("/api/v1/ingest/**"),
            PathPatternParser.defaultInstance.parse("/actuator/**"));
    private static final List<PathPattern> JWT_EXEMPT = RoutePaths.INGEST_PUBLIC.stream()
            .map(PathPatternParser.defaultInstance::parse).toList();

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, ProblemResponses problems,
                                            BearerTokenResolver bearerTokenResolver) throws Exception {
        AuthenticationEntryPoint entryPoint = (request, response, ex) -> {
            response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
            problems.write(request, response, HttpStatus.UNAUTHORIZED,
                    "A valid bearer token is required for this resource.");
        };
        AccessDeniedHandler deniedHandler = (request, response, ex) ->
                problems.write(request, response, HttpStatus.FORBIDDEN, "You are not allowed to access this resource.");

        http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(b -> b.disable())
                .formLogin(f -> f.disable())
                .logout(l -> l.disable())
                .requestCache(c -> c.disable())
                .headers(h -> h
                        .referrerPolicy(r -> r.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
                        .contentSecurityPolicy(csp -> csp.policyDirectives(
                                "default-src 'self'; img-src 'self' data:; style-src 'self' 'unsafe-inline'; "
                                        + "frame-ancestors 'none'")))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers("/actuator/**").hasRole("ADMIN")
                        .requestMatchers(RoutePaths.INGEST_PUBLIC.toArray(String[]::new)).permitAll()
                        .requestMatchers(RoutePaths.INGEST.toArray(String[]::new)).authenticated()
                        .anyRequest().permitAll())
                .oauth2ResourceServer(o -> o
                        .bearerTokenResolver(bearerTokenResolver)
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(deniedHandler)
                        .jwt(j -> j.jwtAuthenticationConverter(jwtAuthenticationConverter())))
                .exceptionHandling(e -> e.authenticationEntryPoint(entryPoint).accessDeniedHandler(deniedHandler));
        return http.build();
    }

    /** Resolves bearer tokens only where the gateway itself authenticates (ingest, actuator). */
    @Bean
    BearerTokenResolver bearerTokenResolver() {
        DefaultBearerTokenResolver delegate = new DefaultBearerTokenResolver();
        return request -> requiresJwt(request) ? delegate.resolve(request) : null;
    }

    static boolean requiresJwt(HttpServletRequest request) {
        var path = RoutePaths.pathWithinApplication(request);
        return JWT_PATHS.stream().anyMatch(p -> p.matches(path)) && JWT_EXEMPT.stream().noneMatch(p -> p.matches(path));
    }

    /** Claim {@code roles} → {@code ROLE_<role>} (contract identity section). */
    static JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("roles");
        authorities.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }

    /** RS256 tokens issued by odp-service; keys fetched (and cached) from its JWKS endpoint; issuer checked. */
    @Bean
    JwtDecoder jwtDecoder(@Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}") String jwkSetUri,
                          GatewayProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(properties.jwtIssuer()));
        return decoder;
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(GatewayProperties properties) {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(properties.corsAllowedOrigins());
        cors.setAllowedMethods(List.of("GET", "HEAD", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cors.setAllowedHeaders(List.of("*"));
        cors.setExposedHeaders(List.of(GatewayHeaders.REQUEST_ID, GatewayHeaders.RATE_LIMIT_LIMIT,
                GatewayHeaders.RATE_LIMIT_REMAINING, GatewayHeaders.RATE_LIMIT_RESET, GatewayHeaders.RETRY_AFTER,
                HttpHeaders.CONTENT_DISPOSITION, HttpHeaders.LOCATION));
        cors.setAllowCredentials(false);
        cors.setMaxAge(Duration.ofHours(1));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cors);
        return source;
    }
}
