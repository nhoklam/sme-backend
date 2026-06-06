package sme.backend.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import sme.backend.entity.SecurityAuditLog;
import sme.backend.repository.SecurityAuditLogRepository;

import java.util.UUID;

@Service
@Slf4j
public class SecurityAuditService {

    @Autowired
    private SecurityAuditLogRepository repository;

    @Transactional(propagation = Propagation.REQUIRES_NEW, noRollbackFor = Exception.class)
    public void logEvent(String username, String eventType, String ipAddress, String userAgent, String details) {
        try {
            SecurityAuditLog auditLog = SecurityAuditLog.builder()
                    .id(UUID.randomUUID())
                    .username(username != null ? username : "UNKNOWN")
                    .eventType(eventType)
                    .ipAddress(ipAddress)
                    .userAgent(userAgent)
                    .details(details)
                    .build();
            repository.saveAndFlush(auditLog);
        } catch (Exception e) {
            log.error("SECURITY_WARNING: Failed to save security audit log. EventType: {}, Username: {}. Error: {}", eventType, username, e.getMessage());
        }
    }

    public void logLoginSuccess(String username, String ipAddress, String userAgent) {
        logEvent(username, "LOGIN_SUCCESS", ipAddress, userAgent, "User logged in successfully");
    }

    public void logLoginFailed(String username, String ipAddress, String userAgent, String details) {
        logEvent(username, "LOGIN_FAILED", ipAddress, userAgent, details);
    }

    public void logAccountLocked(String username, String ipAddress, String userAgent, String details) {
        logEvent(username, "ACCOUNT_LOCKED", ipAddress, userAgent, details);
    }
    
    public void logAccountUnlocked(String username, String ipAddress, String userAgent, String details) {
        logEvent(username, "ACCOUNT_UNLOCKED", ipAddress, userAgent, details);
    }
}
