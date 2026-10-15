package in.pbl.ApiRateLimiter.controller;

import in.pbl.ApiRateLimiter.dto.ApiResponse;
import in.pbl.ApiRateLimiter.dto.LoginRequest;
import in.pbl.ApiRateLimiter.dto.LoginResponse;
import in.pbl.ApiRateLimiter.dto.SessionData;
import in.pbl.ApiRateLimiter.entity.User;
import in.pbl.ApiRateLimiter.repository.UserRepository;
import in.pbl.ApiRateLimiter.service.SessionService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Optional;

/**
 * Authentication and Session Management Controller.
 * 
 * Endpoints:
 * - POST /api/auth/login : Authenticates user, writes session to MySQL and caches in Redis.
 * - POST /api/auth/logout : Evicts session from Redis RAM and revokes in MySQL disk.
 * - GET  /api/auth/session: Inspects current session validity.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserRepository userRepository;
    private final SessionService sessionService;
    private final PasswordEncoder passwordEncoder;

    public AuthController(UserRepository userRepository,
                          SessionService sessionService,
                          PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.sessionService = sessionService;
        this.passwordEncoder = passwordEncoder;
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@RequestBody LoginRequest request) {
        Optional<User> userOpt;

        // Support authentication via API Key or Username + Password
        if (request.getApiKey() != null && !request.getApiKey().isBlank()) {
            userOpt = userRepository.findByApiKey(request.getApiKey());
        } else if (request.getUsername() != null && !request.getUsername().isBlank()) {
            userOpt = userRepository.findByUsername(request.getUsername());
            if (userOpt.isPresent()) {
                User user = userOpt.get();
                if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
                    return ResponseEntity.status(401)
                            .body(ApiResponse.error("Invalid username or password"));
                }
            }
        } else {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Provide either 'apiKey' or 'username' and 'password'"));
        }

        if (userOpt.isEmpty() || !userOpt.get().isActive()) {
            return ResponseEntity.status(401)
                    .body(ApiResponse.error("User not found or account is deactivated"));
        }

        User user = userOpt.get();
        // Create session: Persists into MySQL and Redis with TTL
        SessionData session = sessionService.createSession(user);

        LoginResponse response = new LoginResponse(
                session.getToken(),
                user.getUsername(),
                user.getRateLimitTier().getName(),
                user.getRateLimitTier().getMaxRequests(),
                user.getRateLimitTier().getWindowSeconds(),
                user.getRateLimitTier().getMaxConcurrentRequests(),
                Instant.ofEpochMilli(session.getExpiresAtEpochMs())
        );

        // Fetch the generated token by checking Redis/MySQL or passing through
        return ResponseEntity.ok(ApiResponse.ok("Login successful. Session established in Redis & MySQL.", response));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(@RequestHeader(value = "X-Session-Token", required = false) String sessionToken,
                                                    @RequestHeader(value = "Authorization", required = false) String authHeader) {
        String token = sessionToken;
        if (token == null && authHeader != null && authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7);
        }

        if (token != null) {
            sessionService.invalidateSession(token);
        }

        return ResponseEntity.ok(ApiResponse.ok("Session invalidated successfully.", null));
    }

    @GetMapping("/session")
    public ResponseEntity<ApiResponse<SessionData>> getSession(@RequestHeader(value = "X-Session-Token", required = false) String sessionToken) {
        if (sessionToken == null || sessionToken.isBlank()) {
            return ResponseEntity.status(401).body(ApiResponse.error("Missing session token"));
        }

        Optional<SessionData> session = sessionService.validateSession(sessionToken);
        if (session.isEmpty()) {
            return ResponseEntity.status(401).body(ApiResponse.error("Session expired or invalid"));
        }

        return ResponseEntity.ok(ApiResponse.ok("Session is active and valid.", session.get()));
    }
}
