package gov.openpto.gateway.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Builds RFC 7807 bodies for errors the gateway itself originates (401/404/429/502/503/504) in the shared
 * contract shape {@code { type, title, status, detail, instance, requestId }}. Used from servlet filters,
 * route filters and the security entry point, so every gateway error looks the same.
 */
@Component
public class ProblemResponses {

    private final JsonMapper jsonMapper;

    public ProblemResponses(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    public static Map<String, Object> body(HttpStatus status, String detail, String instance, Object requestId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "about:blank");
        body.put("title", status.getReasonPhrase());
        body.put("status", status.value());
        body.put("detail", detail);
        body.put("instance", instance);
        if (requestId != null) {
            body.put("requestId", requestId.toString());
        }
        return body;
    }

    /** Writes a problem response directly to the servlet response (filters, entry points). */
    public void write(HttpServletRequest request, HttpServletResponse response, HttpStatus status, String detail)
            throws IOException {
        if (response.isCommitted()) {
            return;
        }
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        Object requestId = request.getAttribute(RequestIdFilter.ATTRIBUTE);
        jsonMapper.writeValue(response.getOutputStream(),
                body(status, detail, request.getRequestURI(), requestId));
    }

    /** Builds a problem {@link ServerResponse} for functional route handlers. */
    public static ServerResponse serverResponse(ServerRequest request, HttpStatus status, String detail) {
        return serverResponse(request, status, detail, Map.of());
    }

    public static ServerResponse serverResponse(ServerRequest request, HttpStatus status, String detail,
                                                Map<String, String> headers) {
        Object requestId = request.attribute(RequestIdFilter.ATTRIBUTE).orElse(null);
        return ServerResponse.status(status)
                .headers(h -> headers.forEach(h::set))
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(body(status, detail, request.servletRequest().getRequestURI(), requestId));
    }
}
