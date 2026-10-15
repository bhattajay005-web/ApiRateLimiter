package in.pbl.ApiRateLimiter.repository;

import in.pbl.ApiRateLimiter.entity.RequestAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RequestAuditLogRepository extends JpaRepository<RequestAuditLog, Long> {
    List<RequestAuditLog> findTop50ByOrderByRequestTimestampDesc();
    List<RequestAuditLog> findByUserIdOrderByRequestTimestampDesc(Long userId);
    long countByStatus(String status);
}
