package gov.openpto.odp.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import gov.openpto.odp.dto.UsageReportRequest;
import gov.openpto.odp.dto.UsageResponse;
import gov.openpto.odp.exception.BadRequestException;
import gov.openpto.odp.repository.UsageRepository;
import gov.openpto.odp.repository.UsageRepository.DailyCount;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UsageServiceTest {

    static final Instant NOW = Instant.parse("2026-09-28T12:00:00Z");
    static final LocalDate TODAY = LocalDate.of(2026, 9, 28);

    @Mock
    UsageRepository repo;

    UsageService service;

    @BeforeEach
    void setUp() {
        service = new UsageService(repo, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    @SuppressWarnings("unchecked")
    void record_mergesDuplicatesSkipsZeroAndBumpsKeys() {
        UUID a = UUID.fromString("00000000-0000-0000-0000-00000000000a");
        UUID b = UUID.fromString("00000000-0000-0000-0000-00000000000b");
        given(repo.addDailyCounts(anyList())).willReturn(3);

        int applied = service.record(new UsageReportRequest(List.of(
                new UsageReportRequest.Entry(a, TODAY, 5L),
                new UsageReportRequest.Entry(a, TODAY, 7L),
                new UsageReportRequest.Entry(a, TODAY.minusDays(1), 1L),
                new UsageReportRequest.Entry(b, TODAY, 2L),
                new UsageReportRequest.Entry(b, TODAY, 0L))));

        ArgumentCaptor<List<DailyCount>> counts = ArgumentCaptor.forClass(List.class);
        verify(repo).addDailyCounts(counts.capture());
        assertThat(counts.getValue()).containsExactly(
                new DailyCount(a, TODAY.minusDays(1), 1),
                new DailyCount(a, TODAY, 12),
                new DailyCount(b, TODAY, 2));
        ArgumentCaptor<Map<UUID, Long>> totals = ArgumentCaptor.forClass(Map.class);
        verify(repo).bumpKeys(totals.capture(), org.mockito.ArgumentMatchers.eq(NOW));
        assertThat(totals.getValue()).isEqualTo(Map.of(a, 13L, b, 2L));
        assertThat(applied).isEqualTo(3);
    }

    @Test
    void forUser_zeroFillsDaysAndTotals() {
        UUID user = UUID.randomUUID();
        UUID key = UUID.randomUUID();
        given(repo.dailyTotalsForUser(user, TODAY.minusDays(2), TODAY)).willReturn(List.of(
                new UsageRepository.DateTotal(TODAY.minusDays(2), 4), new UsageRepository.DateTotal(TODAY, 6)));
        given(repo.keyTotalsForUser(user, TODAY.minusDays(2), TODAY))
                .willReturn(List.of(new UsageRepository.KeyTotal(key, "nb", 10)));

        UsageResponse r = service.forUser(user, 3);

        assertThat(r.totalRequests()).isEqualTo(10);
        assertThat(r.daily()).extracting(UsageResponse.DailyUsage::requests).containsExactly(4L, 0L, 6L);
        assertThat(r.daily()).extracting(UsageResponse.DailyUsage::date).containsExactly(TODAY.minusDays(2), TODAY.minusDays(1), TODAY);
        assertThat(r.byKey()).containsExactly(new UsageResponse.KeyUsage(key, "nb", 10));
    }

    @Test
    void forUser_daysOutOfRange_throwsBadRequest() {
        assertThatThrownBy(() -> service.forUser(UUID.randomUUID(), 0)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.forUser(UUID.randomUUID(), 366)).isInstanceOf(BadRequestException.class);
    }
}
