package in.pbl.ApiRateLimiter.repository;

import in.pbl.ApiRateLimiter.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByApiKey(String apiKey);
    Optional<User> findByUsername(String username);
    boolean existsByUsername(String username);
    boolean existsByApiKey(String apiKey);
}
