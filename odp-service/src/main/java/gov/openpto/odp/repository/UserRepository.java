package gov.openpto.odp.repository;

import gov.openpto.odp.model.User;
import jakarta.persistence.LockModeType;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, UUID> {

    @EntityGraph(attributePaths = "roles")
    Optional<User> findByEmail(String email);

    @EntityGraph(attributePaths = "roles")
    Optional<User> findWithRolesById(UUID id);

    boolean existsByEmail(String email);

    Page<User> findByEmailContainingIgnoreCase(String email, Pageable pageable);

    /** Serializes concurrent API-key creation for one user (max-active check). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> lockById(@Param("id") UUID id);

    /** Atomic increment so concurrent failures are all counted. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update User u set u.failedLoginCount = u.failedLoginCount + 1, u.updatedAt = :now where u.id = :id")
    int incrementFailedLogins(@Param("id") UUID id, @Param("now") Instant now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update User u set u.lockedUntil = :until, u.failedLoginCount = 0, u.updatedAt = :now
            where u.id = :id and u.failedLoginCount >= :max""")
    int lockIfThresholdReached(
            @Param("id") UUID id, @Param("max") int max, @Param("until") Instant until, @Param("now") Instant now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update User u set u.failedLoginCount = 0, u.lockedUntil = null, u.lastLoginAt = :now, u.updatedAt = :now
            where u.id = :id""")
    int recordSuccessfulLogin(@Param("id") UUID id, @Param("now") Instant now);
}
