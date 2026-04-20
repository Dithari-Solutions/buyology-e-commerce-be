package com.buyology.ecommerce.newsletter.service;

import com.buyology.ecommerce.common.service.EmailService;
import com.buyology.ecommerce.infrastructure.external.ContaboObjectService;
import com.buyology.ecommerce.newsletter.domain.NewsArticle;
import com.buyology.ecommerce.newsletter.domain.NewsletterSubscriber;
import com.buyology.ecommerce.newsletter.dto.CreateNewsArticleRequest;
import com.buyology.ecommerce.newsletter.dto.NewsArticleResponse;
import com.buyology.ecommerce.newsletter.repository.NewsArticleRepository;
import com.buyology.ecommerce.newsletter.repository.NewsletterSubscriberRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class NewsletterService {

    private static final Logger log = LoggerFactory.getLogger(NewsletterService.class);

    private final NewsletterSubscriberRepository subscriberRepo;
    private final NewsArticleRepository articleRepo;
    private final EmailService emailService;
    private final ContaboObjectService contaboObjectService;

    @Value("${app.base-url:https://buyology.online}")
    private String baseUrl;

    public NewsletterService(NewsletterSubscriberRepository subscriberRepo,
                             NewsArticleRepository articleRepo,
                             EmailService emailService,
                             ContaboObjectService contaboObjectService) {
        this.subscriberRepo = subscriberRepo;
        this.articleRepo = articleRepo;
        this.emailService = emailService;
        this.contaboObjectService = contaboObjectService;
    }

    // ── Subscription ─────────────────────────────────────────────────────────

    @Transactional
    public String subscribe(String email) {
        String normalised = email.trim().toLowerCase();
        Optional<NewsletterSubscriber> existing = subscriberRepo.findByEmailIgnoreCase(normalised);
        if (existing.isPresent()) {
            if (!existing.get().isActive()) {
                NewsletterSubscriber sub = existing.get();
                sub.setActive(true);
                subscriberRepo.save(sub);
                sendConfirmationEmail(sub);
                return "Subscription reactivated successfully";
            }
            return "Already subscribed";
        }
        NewsletterSubscriber sub = new NewsletterSubscriber();
        sub.setEmail(normalised);
        sub.setActive(true);
        subscriberRepo.save(sub);
        sendConfirmationEmail(sub);
        return "Subscribed successfully";
    }

    private void sendConfirmationEmail(NewsletterSubscriber sub) {
        try {
            String unsubUrl = baseUrl + "/api/newsletter/unsubscribe?token=" + sub.getUnsubscribeToken();
            emailService.sendNewsletterSubscriptionEmail(sub.getEmail(), unsubUrl);
        } catch (Exception e) {
            log.warn("Failed to send subscription confirmation to {}: {}", sub.getEmail(), e.getMessage());
        }
    }

    @Transactional
    public String unsubscribe(UUID token) {
        return subscriberRepo.findByUnsubscribeToken(token).map(sub -> {
            sub.setActive(false);
            subscriberRepo.save(sub);
            return "Unsubscribed successfully";
        }).orElse("Invalid unsubscribe token");
    }

    public long countSubscribers() {
        return subscriberRepo.countByIsActiveTrue();
    }

    public List<NewsletterSubscriber> listSubscribers() {
        return subscriberRepo.findAllByIsActiveTrue();
    }

    // ── News articles ─────────────────────────────────────────────────────────

    @Transactional
    public NewsArticleResponse createArticle(CreateNewsArticleRequest req, UUID adminId,
                                             MultipartFile image) {
        NewsArticle article = new NewsArticle();
        article.setTitle(req.getTitle());
        article.setSummary(req.getSummary());
        article.setContent(req.getContent());
        article.setCreatedBy(adminId);

        if (image != null && !image.isEmpty()) {
            String key = "news/" + UUID.randomUUID() + "/" + image.getOriginalFilename();
            contaboObjectService.uploadFile(key, image);
            article.setImageKey(key);
        }

        return toResponse(articleRepo.save(article));
    }

    public List<NewsArticleResponse> listAllArticles() {
        return articleRepo.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public List<NewsArticleResponse> listPublishedArticles() {
        return articleRepo.findAllByStatusOrderByPublishedAtDesc(NewsArticle.ArticleStatus.PUBLISHED)
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Transactional
    @Async
    public void publishAndSend(UUID articleId, boolean sendToSubscribers) {
        NewsArticle article = articleRepo.findById(articleId)
                .orElseThrow(() -> new NoSuchElementException("Article not found: " + articleId));
        article.setStatus(NewsArticle.ArticleStatus.PUBLISHED);
        article.setPublishedAt(Instant.now());
        articleRepo.save(article);

        if (sendToSubscribers) {
            List<NewsletterSubscriber> subscribers = subscriberRepo.findAllByIsActiveTrue();
            for (NewsletterSubscriber sub : subscribers) {
                try {
                    String unsubUrl = baseUrl + "/api/newsletter/unsubscribe?token=" + sub.getUnsubscribeToken();
                    emailService.sendNewsletterEmail(sub.getEmail(), article.getTitle(),
                            article.getContent(), unsubUrl);
                } catch (Exception e) {
                    log.warn("Failed to send newsletter to {}: {}", sub.getEmail(), e.getMessage());
                }
            }
            log.info("Newsletter '{}' sent to {} subscribers", article.getTitle(), subscribers.size());
        }
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private NewsArticleResponse toResponse(NewsArticle a) {
        NewsArticleResponse r = new NewsArticleResponse();
        r.setId(a.getId());
        r.setTitle(a.getTitle());
        r.setSummary(a.getSummary());
        r.setContent(a.getContent());
        r.setStatus(a.getStatus());
        r.setPublishedAt(a.getPublishedAt());
        r.setCreatedAt(a.getCreatedAt());
        if (a.getImageKey() != null) {
            try { r.setImageUrl(contaboObjectService.getPresignedUrl(a.getImageKey())); }
            catch (Exception ignored) {}
        }
        return r;
    }
}
