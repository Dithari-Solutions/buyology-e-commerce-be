package com.buyology.ecommerce.support.repository;

import com.buyology.ecommerce.support.domain.SupportTicket;
import com.buyology.ecommerce.support.domain.SupportTicketStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SupportTicketRepository extends JpaRepository<SupportTicket, UUID> {

    List<SupportTicket> findByCredentialIdOrderByCreatedAtDesc(UUID credentialId);

    Page<SupportTicket> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<SupportTicket> findByStatusOrderByCreatedAtDesc(SupportTicketStatus status, Pageable pageable);

    long countByAdminUnreadTrue();
}
