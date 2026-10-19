package com.buyology.ecommerce.partnership;

import com.buyology.ecommerce.customeremail.service.CustomerEmailGuard;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import jakarta.mail.internet.InternetAddress;
import java.util.*;

@Service
public class PartnershipInvitationService {
    private final PartnershipInvitationRepository repository;
    private final PartnershipInvitationTemplate template;
    private final CustomerEmailGuard guard;
    private final TransactionTemplate transactions;
    public PartnershipInvitationService(PartnershipInvitationRepository repository, PartnershipInvitationTemplate template,
            CustomerEmailGuard guard, PlatformTransactionManager manager) {
        this.repository = repository; this.template = template; this.guard = guard;
        this.transactions = new TransactionTemplate(manager);
    }
    public record Detail(UUID id, java.time.Instant createdAt, String templateVersion,
            List<PartnershipInvitationRepository.Delivery> recipients) {}
    public Detail submit(UUID id, UUID admin, String rawEmails, String version) {
        if (id == null || admin == null) throw new IllegalArgumentException("An invitation reference and administrator are required");
        List<String> emails = normalize(rawEmails);
        if (!PartnershipInvitationTemplate.VERSION.equals(version)) throw new ResponseStatusException(HttpStatus.CONFLICT, "The email template changed. Refresh the preview before submitting.");
        return transactions.execute(tx -> {
            boolean created = repository.insert(id, admin, template.preview()) == 1;
            if (created) {
                guard.checkCampaign(admin, emails.size());
                repository.addRecipients(id, emails);
            }
            var batch = repository.find(id).orElseThrow();
            var recipients = repository.deliveries(id);
            if (!batch.createdBy().equals(admin) || !batch.templateVersion().equals(version)
                    || !recipients.stream().map(PartnershipInvitationRepository.Delivery::email).sorted().toList().equals(emails)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "This invitation reference belongs to a different submission.");
            }
            return new Detail(batch.id(), batch.createdAt(), batch.templateVersion(), recipients);
        });
    }
    public Detail get(UUID id) {
        var batch = repository.find(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invitation batch not found"));
        return new Detail(batch.id(), batch.createdAt(), batch.templateVersion(), repository.deliveries(id));
    }
    public List<Detail> recent() { return repository.recent().stream().map(this::get).toList(); }

    static List<String> normalize(String input) {
        if (input == null || input.isBlank() || input.length() > 20_000) throw new IllegalArgumentException("Enter a comma-separated list of email addresses");
        TreeSet<String> result = new TreeSet<>();
        for (String part : input.split(",", -1)) {
            String email = part.trim().toLowerCase(Locale.ROOT);
            if (email.isEmpty()) throw new IllegalArgumentException("Remove empty entries between commas");
            try {
                // Only a single mailbox, without display names, header breaks or multiple recipients.
                if (email.length() > 254 || !email.matches("[a-z0-9.!#$%&'*+/=?^_`{|}~-]+@[a-z0-9](?:[a-z0-9.-]*[a-z0-9])?\\.[a-z]{2,63}")) throw new IllegalArgumentException();
                new InternetAddress(email, true).validate();
            } catch (Exception invalid) { throw new IllegalArgumentException("Invalid email address: " + email.replaceAll("[\\r\\n]", " ")); }
            result.add(email);
            if (result.size() > PartnershipInvitationTemplate.MAX_RECIPIENTS) throw new IllegalArgumentException("Enter at most 50 unique email addresses per invitation");
        }
        return List.copyOf(result);
    }
}
