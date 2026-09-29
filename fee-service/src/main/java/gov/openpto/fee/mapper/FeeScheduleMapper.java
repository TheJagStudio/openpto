package gov.openpto.fee.mapper;

import gov.openpto.fee.domain.FeeCategory;
import gov.openpto.fee.domain.FeeRate;
import gov.openpto.fee.domain.FeeSchedule;
import gov.openpto.fee.dto.FeeItemResponse;
import gov.openpto.fee.dto.ScheduleDetailResponse;
import gov.openpto.fee.dto.ScheduleSummaryResponse;
import gov.openpto.fee.model.FeeItemEntity;
import gov.openpto.fee.model.FeeScheduleEntity;

import java.util.Comparator;

import org.springframework.stereotype.Component;

/** Entity -> domain and domain -> DTO mapping for fee schedules. */
@Component
public class FeeScheduleMapper {

    public FeeSchedule toDomain(FeeScheduleEntity entity) {
        return new FeeSchedule(entity.getId(), entity.getCode(), entity.getName(), entity.getEffectiveFrom(),
                entity.getEffectiveTo(), entity.getItems().stream()
                .sorted(Comparator.comparing(FeeItemEntity::getDisplayOrder))
                .map(this::toDomain)
                .toList());
    }

    public FeeRate toDomain(FeeItemEntity item) {
        return new FeeRate(item.getFeeCode(), item.getDescription(), item.getCategory(), item.getGroup(),
                item.getLargeEntity(), item.getSmallEntity(), item.getMicroEntity(), item.getUnit());
    }

    public ScheduleSummaryResponse toSummary(FeeSchedule schedule, boolean current) {
        return new ScheduleSummaryResponse(schedule.id(), schedule.code(), schedule.name(), schedule.effectiveFrom(),
                schedule.effectiveTo(), current);
    }

    /** @param category optional filter; null returns all items */
    public ScheduleDetailResponse toDetail(FeeSchedule schedule, boolean current, FeeCategory category) {
        return new ScheduleDetailResponse(schedule.id(), schedule.code(), schedule.name(), schedule.effectiveFrom(),
                schedule.effectiveTo(), current, schedule.items().stream()
                .filter(i -> category == null || i.category() == category)
                .map(FeeScheduleMapper::toItem)
                .toList());
    }

    private static FeeItemResponse toItem(FeeRate rate) {
        return new FeeItemResponse(rate.feeCode(), rate.description(), rate.category(), rate.group(),
                rate.largeEntity(), rate.smallEntity(), rate.microEntity(), rate.unit());
    }
}
