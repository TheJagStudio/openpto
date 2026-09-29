package gov.openpto.odp.repository;

import gov.openpto.odp.model.Patent;
import gov.openpto.odp.model.RecordSource;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PatentRepository extends JpaRepository<Patent, Long>, JpaSpecificationExecutor<Patent> {

    Optional<Patent> findByPatentNumber(String patentNumber);

    /** [year, count] ordered by year. */
    @Query("select extract(year from p.filingDate), count(p) from Patent p group by extract(year from p.filingDate) order by 1")
    List<Object[]> countByFilingYear();

    @Query("select max(p.updatedAt) from Patent p where p.source = :source")
    Optional<Instant> lastUpdatedAt(@Param("source") RecordSource source);
}
