package gov.openpto.fee.service;

import gov.openpto.fee.domain.RuleViolation;
import gov.openpto.fee.dto.CreateQuoteRequest;
import gov.openpto.fee.dto.FeeQuoteResponse;
import gov.openpto.fee.dto.MaintenanceRequest;
import gov.openpto.fee.dto.MaintenanceResponse;
import gov.openpto.fee.dto.PatentFilingRequest;
import gov.openpto.fee.dto.QuoteKind;
import gov.openpto.fee.dto.SavedQuoteResponse;
import gov.openpto.fee.dto.TrademarkFeeRequest;
import gov.openpto.fee.exception.InvalidRequestException;
import gov.openpto.fee.exception.NotFoundException;
import gov.openpto.fee.model.SavedQuoteEntity;
import gov.openpto.fee.repository.SavedQuoteRepository;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Saved quotes. Only the normalized request (defaults applied, schedule date pinned) is stored; the quote is
 * recomputed from it on every read, so stored totals can never drift from the rules and client-supplied
 * totals are never trusted.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class QuoteService {

    private final SavedQuoteRepository repository;
    private final FeeCalculationService calculations;
    private final JsonMapper jsonMapper;
    private final Validator validator;
    private final Clock clock;

    @Transactional
    public SavedQuoteResponse create(CreateQuoteRequest command) {
        Object normalized = normalize(command.kind(), command.request());
        Computed computed = compute(command.kind(), normalized);

        SavedQuoteEntity entity = new SavedQuoteEntity();
        entity.setId(UUID.randomUUID());
        entity.setKind(command.kind());
        entity.setLabel(command.label() == null || command.label().isBlank() ? null : command.label().strip());
        entity.setRequestJson(jsonMapper.writeValueAsString(normalized));
        entity.setScheduleCode(computed.quote().scheduleCode());
        entity.setTotal(computed.quote().total());
        entity.setCreatedAt(Instant.now(clock));
        repository.save(entity);
        return toResponse(entity, normalized, computed);
    }

    public SavedQuoteResponse get(UUID id) {
        SavedQuoteEntity entity = repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Quote " + id + " not found"));
        Object request = jsonMapper.readValue(entity.getRequestJson(), requestType(entity.getKind()));
        return toResponse(entity, request, compute(entity.getKind(), request));
    }

    /** Parses, validates and pins the schedule date so a later read recomputes against the same schedule. */
    Object normalize(QuoteKind kind, JsonNode node) {
        Object parsed;
        try {
            parsed = jsonMapper.treeToValue(node, requestType(kind));
        } catch (JacksonException ex) {
            throw new InvalidRequestException("Malformed " + kind + " request",
                    List.of(new RuleViolation("request", "is not a valid " + requestType(kind).getSimpleName())));
        }
        Set<ConstraintViolation<Object>> violations = validator.validate(parsed);
        if (!violations.isEmpty()) {
            List<RuleViolation> errors = violations.stream()
                    .map(v -> new RuleViolation("request." + v.getPropertyPath(), v.getMessage()))
                    .sorted(Comparator.comparing(RuleViolation::field))
                    .toList();
            throw new InvalidRequestException("Request validation failed (" + errors.size() + " error(s)).", errors);
        }
        return switch (parsed) {
            case PatentFilingRequest r -> r.withFilingDate(calculations.dateOrToday(r.filingDate()));
            case MaintenanceRequest r -> r.withAsOfDate(calculations.dateOrToday(r.asOfDate()));
            case TrademarkFeeRequest r -> r.withFilingDate(calculations.dateOrToday(r.filingDate()));
            default -> throw new IllegalStateException("Unsupported request " + parsed.getClass());
        };
    }

    private Computed compute(QuoteKind kind, Object request) {
        return switch (kind) {
            case PATENT_FILING -> new Computed(calculations.patentFiling((PatentFilingRequest) request), null);
            case TRADEMARK -> new Computed(calculations.trademark((TrademarkFeeRequest) request), null);
            case MAINTENANCE -> {
                MaintenanceResponse m = calculations.maintenance((MaintenanceRequest) request);
                yield new Computed(m.payableNow(), m);
            }
        };
    }

    private static Class<?> requestType(QuoteKind kind) {
        return switch (kind) {
            case PATENT_FILING -> PatentFilingRequest.class;
            case MAINTENANCE -> MaintenanceRequest.class;
            case TRADEMARK -> TrademarkFeeRequest.class;
        };
    }

    private SavedQuoteResponse toResponse(SavedQuoteEntity e, Object request, Computed c) {
        FeeQuoteResponse q = c.quote();
        return new SavedQuoteResponse(e.getId(), e.getKind(), e.getLabel(), jsonMapper.valueToTree(request),
                q.scheduleCode(), q.scheduleName(), q.entitySize(), q.lineItems(), q.subtotals(), q.total(),
                q.currency(), q.notes(), q.warnings(), c.maintenance(), e.getCreatedAt());
    }

    private record Computed(FeeQuoteResponse quote, MaintenanceResponse maintenance) {
    }
}
