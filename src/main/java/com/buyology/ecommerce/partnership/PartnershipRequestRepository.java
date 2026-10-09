package com.buyology.ecommerce.partnership;

import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;

public interface PartnershipRequestRepository extends JpaRepository<PartnershipRequest, UUID> {
    @Modifying
    @Query(value="INSERT INTO partnership_requests (id, payload, status, created_at, email_status, email_attempts, next_email_attempt) VALUES (:id, :payload, 'NEW', CURRENT_TIMESTAMP, 'PENDING', 0, CURRENT_TIMESTAMP) ON CONFLICT (id) DO NOTHING", nativeQuery=true)
    int insertOnce(@Param("id") UUID id, @Param("payload") String payload);
    Page<PartnershipRequest> findAllByOrderByCreatedAtDesc(Pageable pageable);
    @Query(value="SELECT * FROM partnership_requests WHERE email_status = 'PENDING' AND next_email_attempt <= CURRENT_TIMESTAMP ORDER BY created_at LIMIT 10 FOR UPDATE SKIP LOCKED", nativeQuery=true)
    List<PartnershipRequest> lockPendingEmails();
}
