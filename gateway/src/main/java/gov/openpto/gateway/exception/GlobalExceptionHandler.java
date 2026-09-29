package gov.openpto.gateway.exception;

import gov.openpto.gateway.web.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * ProblemDetail for errors raised inside the dispatcher (unknown path → 404, wrong method → 405, …),
 * enriched with {@code instance} and {@code requestId}. Unexpected errors → 500 without internals.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(Exception.class)
    ResponseEntity<Object> handleUnexpected(Exception ex, WebRequest request) {
        log.error("Unhandled gateway error", ex);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred in the gateway.");
        return handleExceptionInternal(ex, problem, new HttpHeaders(), HttpStatus.INTERNAL_SERVER_ERROR, request);
    }

    @Override
    protected ResponseEntity<Object> createResponseEntity(Object body, HttpHeaders headers,
                                                          HttpStatusCode statusCode, WebRequest request) {
        if (body instanceof ProblemDetail problem && request instanceof ServletWebRequest servlet) {
            // flatten to the contract shape explicitly (no reliance on a ProblemDetail Jackson mixin)
            HttpServletRequest http = servlet.getRequest();
            Map<String, Object> flat = new LinkedHashMap<>();
            flat.put("type", problem.getType() == null ? "about:blank" : problem.getType().toString());
            HttpStatus resolved = HttpStatus.resolve(statusCode.value());
            flat.put("title", problem.getTitle() != null ? problem.getTitle()
                    : resolved != null ? resolved.getReasonPhrase() : "Error");
            flat.put("status", problem.getStatus());
            flat.put("detail", problem.getDetail());
            flat.put("instance", http.getRequestURI());
            Object requestId = http.getAttribute(RequestIdFilter.ATTRIBUTE);
            if (requestId != null) {
                flat.put("requestId", requestId.toString());
            }
            if (problem.getProperties() != null) {
                problem.getProperties().forEach(flat::putIfAbsent);
            }
            HttpHeaders out = new HttpHeaders();
            out.putAll(headers);
            out.setContentType(MediaType.APPLICATION_PROBLEM_JSON);
            return ResponseEntity.status(statusCode).headers(out).body(flat);
        }
        return super.createResponseEntity(body, headers, statusCode, request);
    }
}
