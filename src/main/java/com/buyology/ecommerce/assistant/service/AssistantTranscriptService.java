package com.buyology.ecommerce.assistant.service;

import com.buyology.ecommerce.assistant.domain.AssistantConversation;
import com.buyology.ecommerce.assistant.dto.AssistantConversationSummary;
import com.buyology.ecommerce.assistant.dto.AssistantTranscriptResponse;
import com.buyology.ecommerce.assistant.repository.AssistantConversationRepository;
import com.buyology.ecommerce.assistant.repository.AssistantMessageRepository;
import com.buyology.ecommerce.common.response.PageResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Admin-side reads of what customers have been asking the assistant, and what it told them.
 *
 * <p>This is the reason transcripts are stored at all. The questions customers put to a support bot
 * are a direct readout of what the storefront fails to explain, and the answers are statements the
 * business is on the hook for — neither is visible from anywhere else.
 */
@Service
public class AssistantTranscriptService {

    /** Openers are shown in a list column; enough to recognise the conversation, no more. */
    private static final int FIRST_QUESTION_PREVIEW_CHARS = 200;

    private final AssistantConversationRepository conversationRepo;
    private final AssistantMessageRepository messageRepo;

    public AssistantTranscriptService(AssistantConversationRepository conversationRepo,
                                      AssistantMessageRepository messageRepo) {
        this.conversationRepo = conversationRepo;
        this.messageRepo = messageRepo;
    }

    /** Conversations, most recently active first. */
    @Transactional(readOnly = true)
    public PageResponse<AssistantConversationSummary> list(int page, int size) {
        Page<AssistantConversation> conversations = conversationRepo.findAllByOrderByLastMessageAtDesc(
                PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 100)));

        Map<UUID, String> openers = openingQuestions(conversations.getContent());

        List<AssistantConversationSummary> content = conversations.getContent().stream()
                .map(c -> new AssistantConversationSummary(
                        c.getId(),
                        c.getLanguage(),
                        c.getCountryCode(),
                        c.getMessageCount(),
                        openers.get(c.getId()),
                        c.getCreatedAt(),
                        c.getLastMessageAt()))
                .toList();
        return PageResponse.of(conversations, content);
    }

    /**
     * The opening question of each conversation on the page, in one query.
     *
     * <p>The rows arrive oldest first, so {@code putIfAbsent} keeps the first customer turn per
     * conversation and ignores the rest.
     */
    private Map<UUID, String> openingQuestions(List<AssistantConversation> conversations) {
        List<UUID> ids = conversations.stream().map(AssistantConversation::getId).toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<UUID, String> openers = new HashMap<>();
        for (Object[] row : messageRepo.findCustomerTurnRows(ids)) {
            UUID conversationId = (UUID) row[0];
            String content = (String) row[1];
            if (content == null || content.isBlank()) continue;
            openers.putIfAbsent(conversationId, preview(content));
        }
        return openers;
    }

    private static String preview(String content) {
        return content.length() <= FIRST_QUESTION_PREVIEW_CHARS
                ? content
                : content.substring(0, FIRST_QUESTION_PREVIEW_CHARS) + "\u2026";
    }

    /** The full transcript of one conversation. */
    @Transactional(readOnly = true)
    public AssistantTranscriptResponse transcript(UUID conversationId) {
        AssistantConversation conversation = conversationRepo.findById(conversationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found"));

        List<AssistantTranscriptResponse.Turn> turns =
                messageRepo.findByConversationIdOrderByCreatedAtAsc(conversationId).stream()
                        .map(m -> new AssistantTranscriptResponse.Turn(
                                m.getRole() == null ? null : m.getRole().name(),
                                m.getContent(),
                                m.getInScope(),
                                m.getCreatedAt()))
                        .toList();

        return new AssistantTranscriptResponse(
                conversation.getId(),
                conversation.getLanguage(),
                conversation.getCountryCode(),
                conversation.getCreatedAt(),
                conversation.getLastMessageAt(),
                turns);
    }
}
