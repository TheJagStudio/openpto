package gov.openpto.fee.service;

import gov.openpto.fee.domain.FeeCategory;
import gov.openpto.fee.domain.FeeSchedule;
import gov.openpto.fee.dto.ScheduleDetailResponse;
import gov.openpto.fee.dto.ScheduleSummaryResponse;
import gov.openpto.fee.exception.NotFoundException;
import gov.openpto.fee.mapper.FeeScheduleMapper;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Schedule lookup by code or effective date (default: today in the configured zone). */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class FeeScheduleService {

    public static final String CURRENT = "current";

    private final FeeScheduleCatalog catalog;
    private final FeeScheduleMapper mapper;
    private final Clock clock;

    public LocalDate today() {
        return LocalDate.now(clock);
    }

    public List<ScheduleSummaryResponse> list() {
        LocalDate today = today();
        return catalog.loadAll().stream()
                .map(s -> mapper.toSummary(s, s.isEffectiveOn(today)))
                .toList();
    }

    /** @param code schedule code (case-insensitive) or {@code current} */
    public ScheduleDetailResponse get(String code, FeeCategory category) {
        FeeSchedule schedule = CURRENT.equalsIgnoreCase(code) ? effectiveOn(today()) : byCode(code);
        return mapper.toDetail(schedule, schedule.isEffectiveOn(today()), category);
    }

    public FeeSchedule byCode(String code) {
        String wanted = code.toUpperCase(Locale.ROOT);
        return catalog.loadAll().stream()
                .filter(s -> s.code().equals(wanted))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Fee schedule '" + code + "' not found"));
    }

    public FeeSchedule effectiveOn(LocalDate date) {
        return FeeSchedule.effectiveOn(catalog.loadAll(), date)
                .orElseThrow(() -> new NotFoundException("No fee schedule is effective on " + date));
    }
}
