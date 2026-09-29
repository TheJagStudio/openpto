package gov.openpto.fee.service;

import gov.openpto.fee.config.FeeConfig;
import gov.openpto.fee.domain.FeeSchedule;
import gov.openpto.fee.mapper.FeeScheduleMapper;
import gov.openpto.fee.repository.FeeScheduleRepository;

import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Loads every schedule (with items) as immutable domain objects and caches the list: schedules change only
 * through migrations, and there are few of them, so selection by date/code happens in memory.
 * A separate bean from {@link FeeScheduleService} so the cache proxy is not bypassed by self-invocation.
 */
@Component
@RequiredArgsConstructor
public class FeeScheduleCatalog {

    private final FeeScheduleRepository repository;
    private final FeeScheduleMapper mapper;

    @Cacheable(cacheNames = FeeConfig.SCHEDULE_CACHE, key = "'all'")
    @Transactional(readOnly = true)
    public List<FeeSchedule> loadAll() {
        return repository.findAllByOrderByEffectiveFromDesc().stream()
                .map(mapper::toDomain)
                .toList();
    }

    @CacheEvict(cacheNames = FeeConfig.SCHEDULE_CACHE, allEntries = true)
    public void evict() {
        // cache eviction only
    }
}
