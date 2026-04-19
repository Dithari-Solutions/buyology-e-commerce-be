package com.buyology.ecommerce.b2b.repository;

import com.buyology.ecommerce.b2b.domain.B2bInquiry;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface B2bInquiryRepository extends JpaRepository<B2bInquiry, UUID> {
    List<B2bInquiry> findAllByOrderByCreatedAtDesc();
    List<B2bInquiry> findByStatusOrderByCreatedAtDesc(B2bInquiry.InquiryStatus status);
}
