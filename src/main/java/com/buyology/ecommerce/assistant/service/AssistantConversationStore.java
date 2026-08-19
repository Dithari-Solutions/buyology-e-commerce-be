package com.buyology.ecommerce.assistant.service;

import com.buyology.ecommerce.assistant.domain.AssistantConversation;
import com.buyology.ecommerce.assistant.domain.AssistantMessage;
import com.buyology.ecommerce.assistant.domain.enums.AssistantRole;
import com.buyology.ecommerce.assistant.dto.AssistantChatRequest;
import com.buyology.ecommerce.assistant.repository.AssistantConversationRepository;
import com.buyology.ecommerce.assistant.repository.AssistantMessageRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Transcript persistence and the abuse ceilings that go with it.
 *
 * <p>A separate bean from {@link AssistantService} rather than a handful of private methods on it,
 * because these are the parts that must be transactional and Spring's {@code @Transactional} only
 * takes effect through the proxy — a private or same-class call would silently run with no
 * transaction at all, which is the kind of bug that shows up as one orphaned half of a conversation
 * months later.
 *
 * <p>Keeping the writes here also keeps them <em>out</em> of the transaction that would otherwise
 * span the model call: {@link AssistantService#chat} commits the customer turn, spends several
 * seconds waiting on Claude, then commits the reply. A single transaction around all of that would
 * hold a pooled connection idle for the duration and a few dozen concurrent chats would drain the
 * pool.
 */
@Service
public class AssistantConversationStore {

    private final AssistantConversationRepository conversationRepo;
    private final AssistantMessageRepository messageRepo;
    private final int maxMessagesPerConversation;
    private final int maxConversationsPerVisitorPerDay;

    public AssistantConversationStore(
            AssistantConversationRepository conversationRepo,
            AssistantMessageRepository messageRepo,
            @Value("${assistant.max-messages-per-conversation:40}") int maxMessagesPerConversation,
            @Value("${assistant.max-conversations-per-visitor-per-day:20}") int maxConversationsPerVisitorPerDay) {
        this.conversationRepo = conversationRepo;
        this.messageRepo = messageRepo;
        this.maxMessagesPerConversation = maxMessagesPerConversation;
        this.maxConversationsPerVisitorPerDay = maxConversationsPerVisitorPerDay;
    }

    /**
     * Continues the conversation the client named, or opens a new one.
     *
     * <p>A conversation id is a bearer token for a transcript, so it is only ever accepted as
     * something to append to — the read endpoints do not serve a transcript back to the storefront.
     * Guessing one buys an attacker the right to add messages to a stranger's chat, which is
     * pointless; being able to read one would not be.
     */
    @Transactional
    public AssistantConversation resolve(AssistantChatRequest request, UUID authenticatedUserId) {
        if (request.getConversationId() != null) {
            AssistantConversation existing = conversationRepo.findById(request.getConversationId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found"));
            if (maxMessagesPerConversation > 0
                    && existing.getMessageCount() != null
                    && existing.getMessageCount() >= maxMessagesPerConversation) {
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                        "This conversation has reached its message limit. Please start a new chat.");
            }
            return existing;
        }
        enforceVisitorDailyCap(request.getVisitorId());

        AssistantConversation created = new AssistantConversation();
        created.setVisitorId(trim(request.getVisitorId(), 64));
        created.setUserId(authenticatedUserId);
        created.setLanguage(AssistantCatalogService.normaliseLanguage(request.getLanguage()));
        created.setCountryCode(upper(trim(request.getCountryCode(), 2)));
        created.setCurrency(upper(trim(request.getCurrency(), 8)));
        return conversationRepo.save(created);
    }

    /**
     * Caps how many conversations one browser may open per day.
     *
     * <p>The rate limiter caps requests per minute per IP, which stops a burst but not a browser
     * chatting steadily all afternoon — and every message is a paid model call. This is the limit
     * that bounds the daily spend. Withholding a visitor id buys no exemption worth having: the
     * per-IP limiter and the per-conversation ceiling both still apply.
     */
    private void enforceVisitorDailyCap(String visitorId) {
        if (visitorId == null || visitorId.isBlank() || maxConversationsPerVisitorPerDay <= 0) {
            return;
        }
        long recent = conversationRepo.countByVisitorIdAndCreatedAtAfter(
                visitorId.trim(), Instant.now().minus(Duration.ofDays(1)));
        if (recent >= maxConversationsPerVisitorPerDay) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "You've started a lot of chats today. Please continue an existing chat or try again tomorrow.");
        }
    }

    /** The transcript so far, oldest turn first. */
    @Transactional(readOnly = true)
    public List<AssistantMessage> history(UUID conversationId) {
        return messageRepo.findByConversationIdOrderByCreatedAtAsc(conversationId);
    }

    /**
     * Records the customer's turn and advances the message counter.
     *
     * <p>Written before the model runs, so a question is on record even when the call then fails —
     * the questions that fail are the ones most worth reading later.
     */
    @Transactional
    public void recordCustomerTurn(AssistantConversation conversation, String question) {
        messageRepo.save(new AssistantMessage(conversation, AssistantRole.CUSTOMER, question));
        conversation.setMessageCount(
                (conversation.getMessageCount() == null ? 0 : conversation.getMessageCount()) + 1);
        conversation.setLastMessageAt(Instant.now());
        conversationRepo.save(conversation);
    }

    /** Records the assistant's turn, with the ids of any products it cited. */
    @Transactional
    public void recordAssistantTurn(AssistantConversation conversation, String reply,
                                    boolean inScope, List<UUID> citedProductIds) {
        AssistantMessage message = new AssistantMessage(conversation, AssistantRole.ASSISTANT, reply);
        message.setInScope(inScope);
        if (citedProductIds != null && !citedProductIds.isEmpty()) {
            message.setProductIds(citedProductIds.stream()
                    .map(UUID::toString)
                    .reduce((a, b) -> a + "\n" + b)
                    .orElse(null));
        }
        messageRepo.save(message);
        conversation.setLastMessageAt(Instant.now());
        conversationRepo.save(conversation);
    }

    private static String trim(String value, int max) {
        if (value == null) return null;
        String cleaned = value.trim();
        if (cleaned.isEmpty()) return null;
        return cleaned.length() <= max ? cleaned : cleaned.substring(0, max);
    }

    private static String upper(String value) {
        return value == null ? null : value.toUpperCase(Locale.ROOT);
    }
}
