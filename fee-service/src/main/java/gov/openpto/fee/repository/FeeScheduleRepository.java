package gov.openpto.fee.repository;

import gov.openpto.fee.model.FeeScheduleEntity;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FeeScheduleRepository extends JpaRepository<FeeScheduleEntity, Long> {

    /** All schedules with their items in one query (newest first). */
    @EntityGraph(attributePaths = "items")
    List<FeeScheduleEntity> findAllByOrderByEffectiveFromDesc();
}
