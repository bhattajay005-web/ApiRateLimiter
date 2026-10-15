package in.pbl.ApiRateLimiter.repository;

import in.pbl.ApiRateLimiter.entity.RateLimitTier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RateLimitTierRepository extends JpaRepository<RateLimitTier, Long> {
    Optional<RateLimitTier> findByName(String name);
}
