package gov.openpto.ingest.exception;

import gov.openpto.ingest.web.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.jspecify.annotations.Nullable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/** Every error leaves the service as RFC 7807 {@code application/problem+json} with a {@code requestId}. */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    public static final String PROBLEM_BASE = "https://openpto.local/problems/";

    @ExceptionHandler(ApiException.class)
    ResponseEntity<Object> handleApi(ApiException ex, NativeWebRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(ex.status(), ex.getMessage());
        problem.setTitle(ex.title());
        problem.setType(URI.create(PROBLEM_BASE + slug(ex.status())));
        if (ex instanceof BadRequestException invalid && invalid.field() != null) {
            problem.setProperty("errors", List.of(Map.of("field", invalid.field(), "message", ex.getMessage())));
        }
        return build(problem, request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<Object> handleTypeMismatch(MethodArgumentTypeMismatchException ex, NativeWebRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "Parameter '" + ex.getName() + "' has an invalid value");
        problem.setTitle("Bad Request");
        problem.setProperty("errors", List.of(Map.of("field", ex.getName(), "message", "invalid value")));
        return build(problem, request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<Object> handleAccessDenied(AccessDeniedException ex, NativeWebRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "Access denied");
        problem.setTitle("Forbidden");
        return build(problem, request);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Object> handleUnexpected(Exception ex, NativeWebRequest request) {
        log.error("Unhandled error (requestId={})", MDC.get(RequestIdFilter.MDC_KEY), ex);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred. Quote the requestId when reporting it.");
        problem.setTitle("Internal Server Error");
        return build(problem, request);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                  HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail problem = ex.getBody();
        List<Map<String, String>> errors = new ArrayList<>();
        ex.getBindingResult().getFieldErrors().forEach(fe ->
                errors.add(Map.of("field", fe.getField(), "message", String.valueOf(fe.getDefaultMessage()))));
        problem.setProperty("errors", errors);
        return handleExceptionInternal(ex, problem, headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException ex,
                                                                            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail problem = ex.getBody();
        List<Map<String, String>> errors = new ArrayList<>();
        ex.getParameterValidationResults().forEach(result -> {
            String field = result.getMethodParameter().getParameterName();
            result.getResolvableErrors().forEach(err ->
                    errors.add(Map.of("field", String.valueOf(field), "message", String.valueOf(err.getDefaultMessage()))));
        });
        problem.setDetail("Validation failed");
        problem.setProperty("errors", errors);
        return handleExceptionInternal(ex, problem, headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, @Nullable Object body, HttpHeaders headers,
                                                             HttpStatusCode statusCode, WebRequest request) {
        ResponseEntity<Object> response = super.handleExceptionInternal(ex, body, headers, statusCode, request);
        if (response != null && response.getBody() instanceof ProblemDetail problem) {
            decorate(problem, request);
        }
        return response;
    }

    private ResponseEntity<Object> build(ProblemDetail problem, NativeWebRequest request) {
        decorate(problem, request);
        return ResponseEntity.status(problem.getStatus())
                .contentType(org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    static void decorate(ProblemDetail problem, WebRequest request) {
        if (request instanceof NativeWebRequest nwr) {
            HttpServletRequest servlet = nwr.getNativeRequest(HttpServletRequest.class);
            if (servlet != null && problem.getInstance() == null) {
                problem.setInstance(URI.create(servlet.getRequestURI()));
            }
        }
        problem.setProperty("requestId", MDC.get(RequestIdFilter.MDC_KEY));
    }

    private static String slug(HttpStatus status) {
        return status.name().toLowerCase().replace('_', '-');
    }
}