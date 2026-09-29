package gov.openpto.fee.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import gov.openpto.fee.config.FeeConfig;
import gov.openpto.fee.domain.FeeCategory;
import gov.openpto.fee.domain.FeeSchedule;
import gov.openpto.fee.domain.FeeUnit;
import gov.openpto.fee.mapper.FeeScheduleMapper;
import gov.openpto.fee.model.FeeItemEntity;
import gov.openpto.fee.model.FeeScheduleEntity;
import gov.openpto.fee.repository.FeeScheduleRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

/** Verifies the {@code @Cacheable} proxy: the repository is hit once, later lookups come from the cache. */
@SpringJUnitConfig(FeeScheduleCatalogCachingTest.Config.class)
class FeeScheduleCatalogCachingTest {

    @Configuration
    @EnableCaching
    @Import({FeeScheduleCatalog.class, FeeScheduleMapper.class})
    static class Config {
        @Bean
        CacheManager cacheManager() {
            return new ConcurrentMapCacheManager(FeeConfig.SCHEDULE_CACHE);
        }
    }

    @MockitoBean
    private FeeScheduleRepository repository;
    @Autowired
    private FeeScheduleCatalog catalog;
    @Autowired
    private CacheManager cacheManager;

    @BeforeEach
    void clearCache() {
        catalog.evict();
    }

    @Test
    void loadAll_hitsRepositoryOnce_thenServesFromCache() {
        when(repository.findAllByOrderByEffectiveFromDesc()).thenReturn(List.of(entity()));

        List<FeeSchedule> first = catalog.loadAll();
        List<FeeSchedule> second = catalog.loadAll();

        assertThat(second).isSameAs(first);
        assertThat(first).singleElement().satisfies(s -> {
            assertThat(s.code()).isEqualTo("FY2025");
            assertThat(s.items()).extracting(i -> i.feeCode()).containsExactly("A", "B"); // sorted by display order
            assertThat(s.require("A").smallEntity()).isEqualTo(new BigDecimal("4.00"));
        });
        verify(repository, times(1)).findAllByOrderByEffectiveFromDesc();
        assertThat(cacheManager.getCache(FeeConfig.SCHEDULE_CACHE).get("all")).isNotNull();
    }

    @Test
    void evict_forcesReload() {
        when(repository.findAllByOrderByEffectiveFromDesc()).thenReturn(List.of(entity()));
        catalog.loadAll();
        catalog.evict();
        catalog.loadAll();
        verify(repository, times(2)).findAllByOrderByEffectiveFromDesc();
    }

    private static FeeScheduleEntity entity() {
        FeeScheduleEntity s = new FeeScheduleEntity();
        s.setId(1L);
        s.setCode("FY2025");
        s.setName("FY2025");
        s.setEffectiveFrom(LocalDate.of(2025, 1, 19));
        s.getItems().add(item(s, "B", 2));
        s.getItems().add(item(s, "A", 1));
        return s;
    }

    private static FeeItemEntity item(FeeScheduleEntity s, String code, int order) {
        FeeItemEntity i = new FeeItemEntity();
        i.setSchedule(s);
        i.setFeeCode(code);
        i.setDescription(code);
        i.setCategory(FeeCategory.PATENT);
        i.setGroup("G");
        i.setLargeEntity(new BigDecimal("10"));
        i.setSmallEntity(new BigDecimal("4"));
        i.setMicroEntity(new BigDecimal("2"));
        i.setUnit(FeeUnit.EACH);
        i.setDisplayOrder(order);
        return i;
    }
}
