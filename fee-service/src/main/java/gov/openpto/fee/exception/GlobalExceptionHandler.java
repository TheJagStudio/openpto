package gov.openpto.fee.exception;

import gov.openpto.fee.config.RequestIdFilter;
import gov.openpto.fee.domain.FeeRuleViolationException;
import gov.openpto.fee.domain.RuleViolation;

import java.net.URI;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * RFC 7807 errors: {@code { type, title, status, detail, instance, requestId, errors? }}. Never leaks stack
 * traces; unexpected errors are logged with the request id and returned as a generic 500.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final URI VALIDATION_TYPE = URI.create("https://openpto.local/problems/validation-error");
    private static final URI RULE_TYPE = URI.create("https://openpto.local/problems/fee-rule-violation");
    private static final URI NOT_FOUND_TYPE = URI.create("https://openpto.local/problems/not-found");

    @ExceptionHandler(NotFoundException.class)
    ResponseEntity<Object> handleNotFound(NotFoundException ex, WebRequest request) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        pd.setType(NOT_FOUND_TYPE);
        pd.setTitle("Not Found");
        return handleExceptionInternal(ex, pd, new HttpHeaders(), HttpStatus.NOT_FOUND, request);
    }

    @ExceptionHandler(FeeRuleViolationException.class)
    ResponseEntity<Object> handleRuleViolation(FeeRuleViolationException ex, WebRequest request) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        pd.setType(RULE_TYPE);
        pd.setTitle("Fee rule violation");
        pd.setProperty("errors", toErrors(ex.violations()));
        return handleExceptionInternal(ex, pd, new HttpHeaders(), HttpStatus.BAD_REQUEST, request);
    }

    @ExceptionHandler(InvalidRequestException.class)
    ResponseEntity<Object> handleInvalidRequest(InvalidRequestException ex, WebRequest request) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        pd.setType(VALIDATION_TYPE);
        pd.setTitle("Validation failed");
        pd.setProperty("errors", toErrors(ex.errors()));
        return handleExceptionInternal(ex, pd, new HttpHeaders(), HttpStatus.BAD_REQUEST, request);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Object> handleUnexpected(Exception ex, WebRequest request) {
        log.error("Unhandled error (requestId={})", MDC.get(RequestIdFilter.MDC_KEY), ex);
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred. Quote the requestId when reporting it.");
        pd.setTitle("Internal Server Error");
        return handleExceptionInternal(ex, pd, new HttpHeaders(), HttpStatus.INTERNAL_SERVER_ERROR, request);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                  HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<FieldErrorResponse> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(GlobalExceptionHandler::toError)
                .toList();
        List<FieldErrorResponse> global = ex.getBindingResult().getGlobalErrors().stream()
                .map(e -> new FieldErrorResponse(e.getObjectName(), e.getDefaultMessage()))
                .toList();
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "Request validation failed (" + (errors.size() + global.size()) + " error(s)).");
        pd.setType(VALIDATION_TYPE);
        pd.setTitle("Validation failed");
        pd.setProperty("errors", java.util.stream.Stream.concat(errors.stream(), global.stream()).toList());
        return handleExceptionInternal(ex, pd, headers, HttpStatus.BAD_REQUEST, request);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException ex,
                                                                            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<FieldErrorResponse> errors = ex.getParameterValidationResults().stream()
                .flatMap(r -> r.getResolvableErrors().stream()
                        .map(e -> new FieldErrorResponse(r.getMethodParameter().getParameterName(), e.getDefaultMessage())))
                .toList();
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Request validation failed.");
        pd.setType(VALIDATION_TYPE);
        pd.setTitle("Validation failed");
        pd.setProperty("errors", errors);
        return handleExceptionInternal(ex, pd, headers, HttpStatus.BAD_REQUEST, request);
    }

    /** Every ProblemDetail (ours and Spring's built-in ones) gets the correlation id. */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
                                                             HttpStatusCode statusCode, WebRequest request) {
        ResponseEntity<Object> response = super.handleExceptionInternal(ex, body, headers, statusCode, request);
        if (response != null && response.getBody() instanceof ProblemDetail pd) {
            String requestId = MDC.get(RequestIdFilter.MDC_KEY);
            if (requestId != null) {
                pd.setProperty("requestId", requestId);
            }
        }
        return response;
    }

    private static FieldErrorResponse toError(FieldError e) {
        return new FieldErrorResponse(e.getField(), e.getDefaultMessage());
    }

    private static List<FieldErrorResponse> toErrors(List<RuleViolation> violations) {
        return violations.stream().map(v -> new FieldErrorResponse(v.field(), v.message())).toList();
    }
}
