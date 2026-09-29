package gov.openpto.odp.repository;

import gov.openpto.odp.model.ApiKey;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ApiKeyRepository extends JpaRepository<ApiKey, UUID> {

    @Query("select k from ApiKey k where k.user.id = :userId order by k.createdAt desc")
    List<ApiKey> findAllForUser(@Param("userId") UUID userId);

    @Query("select k from ApiKey k where k.id = :id and k.user.id = :userId")
    Optional<ApiKey> findForUser(@Param("id") UUID id, @Param("userId") UUID userId);

    @Query("select count(k) from ApiKey k where k.user.id = :userId and k.revokedAt is null")
    long countActiveForUser(@Param("userId") UUID userId);

    /** Hot verify path: one unique-index lookup, no entity hydration. */
    @Query("""
            select new gov.openpto.odp.repository.ApiKeyLookup(k.id, u.id, k.tier, k.revokedAt, u.enabled)
            from ApiKey k join k.user u where k.keyHash = :hash""")
    Optional<ApiKeyLookup> lookupByHash(@Param("hash") String hash);

    @Query("""
            select new gov.openpto.odp.repository.ApiKeyCounts(
                k.user.id, count(k), sum(case when k.revokedAt is null then 1L else 0L end))
            from ApiKey k where k.user.id in :userIds group by k.user.id""")
    List<ApiKeyCounts> countsForUsers(@Param("userIds") Collection<UUID> userIds);
}
