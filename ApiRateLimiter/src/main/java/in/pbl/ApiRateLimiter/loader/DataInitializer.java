package in.pbl.ApiRateLimiter.loader;

import in.pbl.ApiRateLimiter.entity.RateLimitTier;
import in.pbl.ApiRateLimiter.entity.User;
import in.pbl.ApiRateLimiter.repository.RateLimitTierRepository;
import in.pbl.ApiRateLimiter.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seeds initial Rate Limit Tiers and Demo Users into MySQL database on application boot.
 */
@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final RateLimitTierRepository tierRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(RateLimitTierRepository tierRepository,
                           UserRepository userRepository,
                           PasswordEncoder passwordEncoder) {
        this.tierRepository = tierRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        // Seed Tiers
        RateLimitTier freeTier = tierRepository.findByName("FREE").orElseGet(() ->
                tierRepository.save(new RateLimitTier("FREE", 5, 60, 2))
        );

        RateLimitTier proTier = tierRepository.findByName("PRO").orElseGet(() ->
                tierRepository.save(new RateLimitTier("PRO", 20, 60, 5))
        );

        RateLimitTier enterpriseTier = tierRepository.findByName("ENTERPRISE").orElseGet(() ->
                tierRepository.save(new RateLimitTier("ENTERPRISE", 100, 60, 20))
        );

        // Seed Users
        if (!userRepository.existsByUsername("alice")) {
            userRepository.save(new User(
                    "alice",
                    "alice@example.com",
                    passwordEncoder.encode("password123"),
                    "key_alice_free_123",
                    freeTier
            ));
        }

        if (!userRepository.existsByUsername("bob")) {
            userRepository.save(new User(
                    "bob",
                    "bob@example.com",
                    passwordEncoder.encode("password123"),
                    "key_bob_pro_456",
                    proTier
            ));
        }

        if (!userRepository.existsByUsername("charlie")) {
            userRepository.save(new User(
                    "charlie",
                    "charlie@example.com",
                    passwordEncoder.encode("password123"),
                    "key_charlie_ent_789",
                    enterpriseTier
            ));
        }

        log.info("Database initialized successfully with default Rate Limit Tiers and Users.");
    }
}
