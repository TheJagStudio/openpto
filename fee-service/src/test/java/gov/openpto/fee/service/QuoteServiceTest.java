package gov.openpto.fee.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import gov.openpto.fee.domain.ApplicationType;
import gov.openpto.fee.domain.EntitySize;
import gov.openpto.fee.domain.RuleViolation;
import gov.openpto.fee.domain.TrademarkFilingType;
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
import jakarta.validation.Validation;
import jakarta.validation.Validator;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class QuoteServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2025, 6, 1);
    private static final Instant NOW = Instant.parse("2025-06-01T15:00:00Z");
    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    @Mock
    private SavedQuoteRepository repository;
    @Mock
    private FeeCalculationService calculations;

    private final JsonMapper json = JsonMapper.builder().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();
    private QuoteService service;

    @BeforeEach
    void setUp() {
        service = new QuoteService(repository, calculations, json, VALIDATOR, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static FeeQuoteResponse quote(String total, EntitySize entity) {
        return new FeeQuoteResponse("FY2025", "FY2025 name", entity, List.of(), List.of(), new BigDecimal(total), "USD",
                List.of("note"), List.of());
    }

    @Test
    void create_patentFiling_pinsDate_persistsNormalizedRequest_andServerTotal() {
        when(calculations.dateOrToday(null)).thenReturn(TODAY);
        when(calculations.patentFiling(any())).thenReturn(quote("1680.00", EntitySize.SMALL));

        // A client-supplied "total" is ignored: only the request fields are read.
        SavedQuoteResponse saved = service.create(new CreateQuoteRequest(QuoteKind.PATENT_FILING, json.readTree(
                "{\"applicationType\":\"UTILITY\",\"entitySize\":\"SMALL\",\"totalClaims\":25,\"independentClaims\":5,\"total\":1}"),
                "  My quote  "));

        ArgumentCaptor<SavedQuoteEntity> entity = ArgumentCaptor.forClass(SavedQuoteEntity.class);
        verify(repository).save(entity.capture());
        SavedQuoteEntity e = entity.getValue();
        assertThat(e.getKind()).isEqualTo(QuoteKind.PATENT_FILING);
        assertThat(e.getLabel()).isEqualTo("My quote");
        assertThat(e.getTotal()).isEqualTo(new BigDecimal("1680.00"));
        assertThat(e.getScheduleCode()).isEqualTo("FY2025");
        assertThat(e.getCreatedAt()).isEqualTo(NOW);
        assertThat(e.getRequestJson()).contains("\"filingDate\":\"2025-06-01\"").doesNotContain("\"total\"");

        assertThat(saved.id()).isEqualTo(e.getId());
        assertThat(saved.total()).isEqualTo(new BigDecimal("1680.00"));
        assertThat(saved.request().get("filingDate").asString()).isEqualTo("2025-06-01");
        assertThat(saved.maintenance()).isNull();

        ArgumentCaptor<PatentFilingRequest> req = ArgumentCaptor.forClass(PatentFilingRequest.class);
        verify(calculations).patentFiling(req.capture());
        assertThat(req.getValue().filingDate()).isEqualTo(TODAY);
        assertThat(req.getValue().applicationType()).isEqualTo(ApplicationType.UTILITY);
    }

    @Test
    void create_maintenance_attachesWindows_andUsesPayableNowAsQuote() {
        LocalDate asOf = LocalDate.of(2025, 2, 1);
        when(calculations.dateOrToday(asOf)).thenReturn(asOf);
        MaintenanceResponse m = new MaintenanceResponse(List.of(), false, "FY2025", "n", EntitySize.MICRO,
                LocalDate.of(2021, 6, 15), asOf, quote("538.00", EntitySize.MICRO));
        when(calculations.maintenance(any())).thenReturn(m);

        SavedQuoteResponse saved = service.create(new CreateQuoteRequest(QuoteKind.MAINTENANCE, json.readTree(
                "{\"entitySize\":\"MICRO\",\"grantDate\":\"2021-06-15\",\"asOfDate\":\"2025-02-01\"}"), null));

        assertThat(saved.total()).isEqualTo(new BigDecimal("538.00"));
        assertThat(saved.maintenance()).isSameAs(m);
        assertThat(saved.label()).isNull();
    }

    @Test
    void create_nestedValidationErrors_arePrefixedWithRequest() {
        assertThatThrownBy(() -> service.create(new CreateQuoteRequest(QuoteKind.PATENT_FILING,
                json.readTree("{\"applicationType\":\"UTILITY\",\"totalClaims\":501}"), null)))
                .isInstanceOfSatisfying(InvalidRequestException.class, ex -> assertThat(ex.errors())
                        .extracting(RuleViolation::field)
                        .containsExactly("request.entitySize", "request.totalClaims"));
        verify(repository, never()).save(any());
    }

    @Test
    void create_wrongShapeForKind_isRejected() {
        assertThatThrownBy(() -> service.create(new CreateQuoteRequest(QuoteKind.TRADEMARK,
                json.readTree("{\"filingType\":\"SPACESHIP\",\"numberOfClasses\":1}"), null)))
                .isInstanceOfSatisfying(InvalidRequestException.class, ex -> assertThat(ex.errors())
                        .containsExactly(new RuleViolation("request", "is not a valid TrademarkFeeRequest")));
    }

    @Test
    void get_recomputesFromStoredRequest() {
        UUID id = UUID.randomUUID();
        SavedQuoteEntity e = new SavedQuoteEntity();
        e.setId(id);
        e.setKind(QuoteKind.TRADEMARK);
        e.setRequestJson("{\"filingType\":\"SECTION_8\",\"numberOfClasses\":3,\"filingDate\":\"2024-05-01\"}");
        e.setScheduleCode("FY2023");
        e.setTotal(new BigDecimal("999.99")); // stale denormalized value is never returned
        e.setCreatedAt(NOW);
        when(repository.findById(id)).thenReturn(Optional.of(e));
        when(calculations.trademark(any())).thenReturn(quote("675.00", null));

        SavedQuoteResponse r = service.get(id);

        assertThat(r.total()).isEqualTo(new BigDecimal("675.00"));
        assertThat(r.createdAt()).isEqualTo(NOW);
        ArgumentCaptor<TrademarkFeeRequest> req = ArgumentCaptor.forClass(TrademarkFeeRequest.class);
        verify(calculations).trademark(req.capture());
        assertThat(req.getValue().filingType()).isEqualTo(TrademarkFilingType.SECTION_8);
        assertThat(req.getValue().filingDate()).isEqualTo(LocalDate.of(2024, 5, 1));
    }

    @Test
    void get_unknownId_isNotFound() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.get(id)).isInstanceOf(NotFoundException.class).hasMessageContaining(id.toString());
    }

    @Test
    void maintenanceRequest_paidStagesPatternIsValidated() {
        assertThatThrownBy(() -> service.create(new CreateQuoteRequest(QuoteKind.MAINTENANCE, json.readTree(
                "{\"entitySize\":\"LARGE\",\"grantDate\":\"2021-06-15\",\"paidStages\":[\"4.5\"]}"), null)))
                .isInstanceOfSatisfying(InvalidRequestException.class, ex ->
                        assertThat(ex.errors()).singleElement().satisfies(v -> assertThat(v.field()).startsWith("request.paidStages")));
        assertThat(MaintenanceRequest.class).isNotNull();
    }
}
