package gov.openpto.odp.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import gov.openpto.odp.config.AppConfig;
import gov.openpto.odp.dto.BulkUpsertResponse;
import gov.openpto.odp.dto.PatentUpsert;
import gov.openpto.odp.dto.TrademarkUpsert;
import gov.openpto.odp.exception.BadRequestException;
import gov.openpto.odp.model.PatentType;
import gov.openpto.odp.model.RecordSource;
import gov.openpto.odp.repository.PatentWriteRepository;
import gov.openpto.odp.repository.TrademarkWriteRepository;
import gov.openpto.odp.repository.UpsertOutcome;
import jakarta.validation.Validation;
import jakarta.validation.Validator;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class BulkUpsertServiceTest {

    static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();
    static final JsonMapper JSON = JsonMapper.builder().build();

    PatentWriteRepository patentWriter = mock(PatentWriteRepository.class);
    TrademarkWriteRepository trademarkWriter = mock(TrademarkWriteRepository.class);
    PlatformTransactionManager txManager = mock(PlatformTransactionManager.class);
    CacheManager caches = new ConcurrentMapCacheManager(AppConfig.STATS_CACHE, AppConfig.FACETS_CACHE);
    BulkUpsertService service;

    @BeforeEach
    void setUp() {
        when(txManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        service = new BulkUpsertService(patentWriter, trademarkWriter, new RecordNormalizer(Clock.systemUTC()), VALIDATOR,
                caches, JSON, txManager);
        caches.getCache(AppConfig.STATS_CACHE).put("all", "cached");
    }

    static PatentUpsert patent(String number, String title) {
        return new PatentUpsert(number, null, title, null, PatentType.UTILITY, null, LocalDate.of(2020, 1, 1), null, null,
                null, null, null, null, null, null, null, null, null, "job", RecordSource.INGEST);
    }

    @Test
    void upsertPatents_countsInsertsAndUpdates_isolatesFailures_andEvictsCaches() {
        given(patentWriter.upsert(any(), eq(RecordSource.INGEST)))
                .willReturn(new UpsertOutcome(1, true))
                .willReturn(new UpsertOutcome(2, false))
                .willThrow(new DataIntegrityViolationException("value too long\nDetail: ..."));

        BulkUpsertResponse r = service.upsertPatents(List.of(
                patent("US1B1", "one"), patent("US2B1", "two"), patent("US3B1", " "), patent("US4B1", "four")));

        assertThat(r.inserted()).isEqualTo(1);
        assertThat(r.updated()).isEqualTo(1);
        assertThat(r.failed()).containsExactly(
                new BulkUpsertResponse.Failure("US3B1", "title: must not be blank"),
                new BulkUpsertResponse.Failure("US4B1", "rejected by the database: value too long"));
        assertThat(caches.getCache(AppConfig.STATS_CACHE).get("all")).isNull();
    }

    @Test
    void upsertPatents_normalizesBeforeWriting_andForcesIngestSource() {
        given(patentWriter.upsert(any(), eq(RecordSource.INGEST))).willReturn(new UpsertOutcome(1, true));

        service.upsertPatents(List.of(patent("us9b1", "t")));

        verify(patentWriter).upsert(org.mockito.ArgumentMatchers.argThat(p ->
                p.patentNumber().equals("US9B1") && p.source() == RecordSource.INGEST && p.claims().isEmpty()), eq(RecordSource.INGEST));
    }

    @Test
    void upsertPatents_rawJson_parsesPerRecordAndReportsMalformedOnes() {
        List<JsonNode> nodes = List.of(
                JSON.readTree("{\"patentNumber\":\"US1B1\",\"title\":\"ok\",\"type\":\"UTILITY\",\"filingDate\":\"2020-01-01\"}"),
                JSON.readTree("{\"patentNumber\":\"US2B1\",\"title\":\"bad\",\"type\":\"ROCKET\",\"filingDate\":\"2020-01-01\"}"),
                JSON.readTree("{\"applicationNumber\":\"17/000,001\",\"title\":\"bad date\",\"type\":\"UTILITY\",\"filingDate\":\"yesterday\"}"),
                JSON.readTree("42"),
                JSON.readTree("{\"title\":\"no key\",\"type\":\"UTILITY\",\"filingDate\":\"2020-01-01\"}"));
        given(patentWriter.upsert(any(), eq(RecordSource.INGEST))).willReturn(new UpsertOutcome(1, true));

        BulkUpsertResponse r = service.upsertPatents(nodes);

        assertThat(r.inserted()).isEqualTo(1);
        assertThat(r.failed()).extracting(BulkUpsertResponse.Failure::id).containsExactly("US2B1", "17/000,001", "#3", "#4");
        assertThat(r.failed().get(0).error()).startsWith("invalid PatentUpsert");
        assertThat(r.failed().get(2).error()).isEqualTo("record must be a JSON object");
        assertThat(r.failed().get(3).error()).contains("patentNumber or applicationNumber");
    }

    @Test
    void upsertPatents_nullElement_isReportedByIndex() {
        List<Object> records = new ArrayList<>();
        records.add(null);
        BulkUpsertResponse r = service.upsertPatents(records);

        assertThat(r.failed()).containsExactly(new BulkUpsertResponse.Failure("#0", "record must be a JSON object"));
        assertThat(caches.getCache(AppConfig.STATS_CACHE).get("all")).isNotNull();
    }

    @Test
    void upsert_tooManyOrNull_isBadRequest() {
        assertThatThrownBy(() -> service.upsertPatents(Collections.nCopies(1001, patent("US1B1", "t"))))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.upsertTrademarks(null)).isInstanceOf(BadRequestException.class);
        verify(patentWriter, never()).upsert(any(), any());
    }

    @Test
    void upsertTrademarks_validatesAndWrites() {
        given(trademarkWriter.upsert(any(), eq(RecordSource.INGEST))).willReturn(new UpsertOutcome(5, false));
        TrademarkUpsert ok = new TrademarkUpsert("97123456", null, "MARK", null, null, LocalDate.of(2021, 1, 1), null, null,
                null, null, null, "1A", null, null, null, null);
        TrademarkUpsert badBasis = new TrademarkUpsert("97123457", null, "MARK", null, null, LocalDate.of(2021, 1, 1), null,
                null, null, null, null, "2A", null, null, null, null);

        BulkUpsertResponse r = service.upsertTrademarks(List.of(ok, badBasis));

        assertThat(r.updated()).isEqualTo(1);
        assertThat(r.failed()).singleElement().satisfies(f -> {
            assertThat(f.id()).isEqualTo("97123457");
            assertThat(f.error()).startsWith("filingBasis:");
        });
    }
}
