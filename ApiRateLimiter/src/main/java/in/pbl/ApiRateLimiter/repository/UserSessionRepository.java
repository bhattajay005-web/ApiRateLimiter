package in.pbl.ApiRateLimiter.repository;

import in.pbl.ApiRateLimiter.entity.UserSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserSessionRepository extends JpaRepository<UserSession, Long> {
    Optional<UserSession> findByToken(String token);

    List<UserSession> findByUserIdAndIsRevokedFalse(Long userId);

    @Modifying
    @Query("UPDATE UserSession s SET s.isRevoked = true WHERE s.user.id = :userId")
    int revokeAllSessionsForUser(@Param("userId") Long userId);

    @Modifying
    @Query("DELETE FROM UserSession s WHERE s.expiresAt < :now")
    int deleteExpiredSessions(@Param("now") Instant now);
}
