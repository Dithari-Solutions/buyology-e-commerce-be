package com.buyology.ecommerce.common.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID> {

    Page<AuditEvent> findByActorIdOrderByCreatedAtDesc(UUID actorId, Pageable pageable);

    Page<AuditEvent> findByEntityTypeAndEntityIdOrderByCreatedAtDesc(String entityType, String entityId, Pageable pageable);

    Page<AuditEvent> findByActionOrderByCreatedAtDesc(String action, Pageable pageable);
}
