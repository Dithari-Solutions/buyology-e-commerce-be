package com.buyology.ecommerce.common.audit;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.UUID;

/**
 * Records sensitive actions for forensic / compliance purposes.
 * Call sites use {@link #log(String, String, String, String, String)} with structured action codes
 * (e.g. {@code AUTH_LOGIN_SUCCESS}, {@code MEMBERSHIP_FREEZE}, {@code SUPPLIER_TRASH}).
 *
 * Writes are async so audit never blocks the request hot path.
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditEventRepository repo;

    public AuditService(AuditEventRepository repo) {
        this.repo = repo;
    }

    @Async
    public void log(String action, String entityType, String entityId, String outcome, String metadata) {
        try {
            AuditEvent event = new AuditEvent();
            event.setAction(action);
            event.setEntityType(entityType);
            event.setEntityId(entityId);
            event.setOutcome(outcome);
            event.setMetadata(metadata);
            event.setActorId(currentActorId());
            HttpServletRequest req = currentRequest();
            if (req != null) {
                event.setIpAddress(extractIp(req));
                String ua = req.getHeader("User-Agent");
                if (ua != null && ua.length() > 500) ua = ua.substring(0, 500);
                event.setUserAgent(ua);
            }
            repo.save(event);
        } catch (Exception e) {
            log.warn("AuditService write failed for action={}: {}", action, e.getMessage());
        }
    }

    public void logSuccess(String action, String entityType, String entityId) {
        log(action, entityType, entityId, "SUCCESS", null);
    }

    public void logFailure(String action, String entityType, String entityId, String reason) {
        log(action, entityType, entityId, "FAILURE", reason);
    }

    private UUID currentActorId() {
        try {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof UUID id) return id;
        } catch (Exception ignored) {}
        return null;
    }

    private HttpServletRequest currentRequest() {
        try {
            var attrs = RequestContextHolder.getRequestAttributes();
            if (attrs instanceof ServletRequestAttributes sra) return sra.getRequest();
        } catch (Exception ignored) {}
        return null;
    }

    private String extractIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) return xff.split(",")[0].strip();
        return request.getRemoteAddr();
    }
}
