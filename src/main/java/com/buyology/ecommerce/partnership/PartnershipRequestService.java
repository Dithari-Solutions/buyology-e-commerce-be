package com.buyology.ecommerce.partnership;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.buyology.ecommerce.common.service.EmailService;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.*;

@Service
public class PartnershipRequestService {
    private final PartnershipRequestRepository repository;
    private final ObjectMapper mapper;
    private final EmailService emails;
    public PartnershipRequestService(PartnershipRequestRepository repository, ObjectMapper mapper, EmailService emails) {
        this.repository=repository; this.mapper=mapper; this.emails=emails;
    }
    public record Receipt(UUID id) {}
    public record Detail(UUID id, PartnershipApplication application, String status, String adminNotes, Instant createdAt, String emailStatus, int emailAttempts) {}
    public record RequestPage(List<Detail> content, long totalElements, int totalPages, int number) {}
    @Transactional
    public Receipt submit(PartnershipApplication application) {
        // A retry after a lost response must neither duplicate the request nor send a second email.
        String payload=encode(application);
        repository.insertOnce(application.requestId(), payload);
        PartnershipRequest saved=repository.findById(application.requestId()).orElseThrow();
        if (!decode(saved.getPayload()).equals(application)) throw new ResponseStatusException(HttpStatus.CONFLICT, "This submission reference was already used. Reload the form to submit a new application.");
        return new Receipt(saved.getId());
    }
    @Transactional(readOnly=true)
    public RequestPage list(int page) {
        Page<PartnershipRequest> result=repository.findAllByOrderByCreatedAtDesc(PageRequest.of(Math.max(0,page),25));
        return new RequestPage(result.getContent().stream().map(this::detail).toList(), result.getTotalElements(), result.getTotalPages(), result.getNumber());
    }
    @Transactional
    public Detail update(UUID id, String status, String notes) {
        if (!Set.of("NEW","REVIEWED","RESPONDED").contains(status)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid status");
        PartnershipRequest request=repository.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Partnership request not found"));
        request.setStatus(status); request.setAdminNotes(notes);
        return detail(request);
    }
    @Transactional
    public void sendPendingEmails() {
        for (PartnershipRequest request : repository.lockPendingEmails()) {
            PartnershipApplication app=decode(request.getPayload());
            request.setEmailAttempts(request.getEmailAttempts()+1);
            boolean sent=emails.sendPartnershipReceivedEmail(app.email(),app.name(),app.company(),request.getId().toString());
            request.setEmailStatus(sent ? "SENT" : request.getEmailAttempts() >= 5 ? "FAILED" : "PENDING");
            request.setNextEmailAttempt(Instant.now().plusSeconds(60L * (1L << request.getEmailAttempts())));
        }
    }
    private Detail detail(PartnershipRequest request) { return new Detail(request.getId(),decode(request.getPayload()),request.getStatus(),request.getAdminNotes(),request.getCreatedAt(),request.getEmailStatus(),request.getEmailAttempts()); }
    private String encode(PartnershipApplication application) {
        try { return mapper.writeValueAsString(application); } catch (JsonProcessingException e) { throw new IllegalStateException("Unable to serialize application",e); }
    }
    private PartnershipApplication decode(String payload) {
        try { return mapper.readValue(payload,PartnershipApplication.class); } catch (JsonProcessingException e) { throw new IllegalStateException("Unable to read application",e); }
    }
}
