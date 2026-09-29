package gov.openpto.gateway.web;

import gov.openpto.gateway.identity.Identity;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * First filter in the chain: assigns the correlation id and writes one access-log line per request.
 * <ul>
 *   <li>Accepts an inbound {@code X-Request-Id} only if it is short and log-safe, otherwise generates a UUID.</li>
 *   <li>Stores it as a request attribute (forwarded downstream by {@code GatewayRequestHeadersFilter}),
 *       in the MDC and on the response.</li>
 *   <li>Logs {@code method path status ms identity requestId} on logger {@code gov.openpto.gateway.access}.</li>
 * </ul>
 */
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String ATTRIBUTE = RequestIdFilter.class.getName() + ".requestId";

    private static final Logger accessLog = LoggerFactory.getLogger("gov.openpto.gateway.access");
    private static final Pattern SAFE_ID = Pattern.compile("^[A-Za-z0-9._:-]{8,128}$");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long start = System.nanoTime();
        String requestId = resolve(request.getHeader(GatewayHeaders.REQUEST_ID));
        request.setAttribute(ATTRIBUTE, requestId);
        response.setHeader(GatewayHeaders.REQUEST_ID, requestId);
        MDC.put(GatewayHeaders.MDC_REQUEST_ID, requestId);
        try {
            chain.doFilter(request, response);
        } finally {
            long ms = (System.nanoTime() - start) / 1_000_000;
            if (accessLog.isInfoEnabled()) {
                Object identity = request.getAttribute(Identity.ATTRIBUTE);
                accessLog.info("{} {} {} {}ms identity={} requestId={}", request.getMethod(),
                        request.getRequestURI(), response.getStatus(), ms,
                        identity instanceof Identity id ? id.bucketKey() : "-", requestId);
            }
            MDC.remove(GatewayHeaders.MDC_REQUEST_ID);
        }
    }

    static String resolve(String inbound) {
        if (inbound != null && SAFE_ID.matcher(inbound).matches()) {
            return inbound;
        }
        return UUID.randomUUID().toString();
    }
}
