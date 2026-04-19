package com.buyology.ecommerce.b2b.service;

import com.buyology.ecommerce.b2b.domain.B2bInquiry;
import com.buyology.ecommerce.b2b.dto.B2bInquiryRequest;
import com.buyology.ecommerce.b2b.dto.B2bInquiryResponse;
import com.buyology.ecommerce.b2b.repository.B2bInquiryRepository;
import com.buyology.ecommerce.common.service.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class B2bInquiryService {

    private static final Logger log = LoggerFactory.getLogger(B2bInquiryService.class);

    private final B2bInquiryRepository inquiryRepo;
    private final EmailService emailService;

    @Value("${app.admin-email:firdovsirz@gmail.com}")
    private String adminEmail;

    public B2bInquiryService(B2bInquiryRepository inquiryRepo, EmailService emailService) {
        this.inquiryRepo = inquiryRepo;
        this.emailService = emailService;
    }

    @Transactional
    public B2bInquiryResponse submit(B2bInquiryRequest req) {
        B2bInquiry inquiry = new B2bInquiry();
        inquiry.setCompany(req.getCompany());
        inquiry.setContactPerson(req.getContact());
        inquiry.setEmail(req.getEmail());
        inquiry.setPhone(req.getPhone());
        inquiry.setQuantity(req.getQuantity());
        inquiry.setMessage(req.getMessage());
        inquiry = inquiryRepo.save(inquiry);

        try {
            emailService.sendB2bInquiryNotification(adminEmail, inquiry.getCompany(),
                    inquiry.getContactPerson(), inquiry.getEmail(), inquiry.getPhone(),
                    inquiry.getQuantity(), inquiry.getMessage());
        } catch (Exception e) {
            log.warn("B2B admin notification failed: {}", e.getMessage());
        }

        return toResponse(inquiry);
    }

    public List<B2bInquiryResponse> listAll() {
        return inquiryRepo.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toResponse).collect(Collectors.toList());
    }

    @Transactional
    public B2bInquiryResponse updateStatus(UUID id, String status, String adminNotes) {
        B2bInquiry inquiry = inquiryRepo.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Inquiry not found: " + id));
        inquiry.setStatus(B2bInquiry.InquiryStatus.valueOf(status.toUpperCase()));
        if (adminNotes != null) inquiry.setAdminNotes(adminNotes);
        return toResponse(inquiryRepo.save(inquiry));
    }

    private B2bInquiryResponse toResponse(B2bInquiry i) {
        B2bInquiryResponse r = new B2bInquiryResponse();
        r.setId(i.getId());
        r.setCompany(i.getCompany());
        r.setContactPerson(i.getContactPerson());
        r.setEmail(i.getEmail());
        r.setPhone(i.getPhone());
        r.setQuantity(i.getQuantity());
        r.setMessage(i.getMessage());
        r.setStatus(i.getStatus());
        r.setAdminNotes(i.getAdminNotes());
        r.setCreatedAt(i.getCreatedAt());
        return r;
    }
}
