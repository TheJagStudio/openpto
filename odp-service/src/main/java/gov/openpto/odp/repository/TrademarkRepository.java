package gov.openpto.odp.repository;

import gov.openpto.odp.model.RecordSource;
import gov.openpto.odp.model.Trademark;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TrademarkRepository extends JpaRepository<Trademark, Long>, JpaSpecificationExecutor<Trademark> {

    Optional<Trademark> findBySerialNumber(String serialNumber);

    /** [status, count]. */
    @Query("select t.status, count(t) from Trademark t group by t.status order by count(t) desc")
    List<Object[]> countByStatus();

    @Query("select max(t.updatedAt) from Trademark t where t.source = :source")
    Optional<Instant> lastUpdatedAt(@Param("source") RecordSource source);
}
