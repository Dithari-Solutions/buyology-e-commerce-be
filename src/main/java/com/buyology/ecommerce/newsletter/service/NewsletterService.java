package com.buyology.ecommerce.newsletter.service;

import com.buyology.ecommerce.common.service.EmailService;
import com.buyology.ecommerce.common.utils.FileValidationUtils;
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
                                             MultipartFile image, List<MultipartFile> gallery) {
        NewsArticle article = new NewsArticle();
        article.setTitle(req.getTitle());
        article.setSummary(req.getSummary());
        article.setContent(req.getContent());
        article.setCreatedBy(adminId);

        article.setSlug(uniqueSlug(req.getTitle()));

        if (image != null && !image.isEmpty()) {
            FileValidationUtils.validateImage(image);
            String key = "news/" + UUID.randomUUID() + "/" + image.getOriginalFilename();
            // Capture the returned key — watermarking may change the extension (e.g. WebP→PNG).
            String storedKey = contaboObjectService.uploadFile(key, image);
            article.setImageKey(storedKey);
        }

        if (gallery != null && !gallery.isEmpty()) {
            List<String> keys = new ArrayList<>();
            for (MultipartFile g : gallery) {
                if (g == null || g.isEmpty()) continue;
                FileValidationUtils.validateImage(g);
                keys.add(contaboObjectService.uploadFile(
                        "news/" + UUID.randomUUID() + "/" + g.getOriginalFilename(), g));
            }
            if (!keys.isEmpty()) article.setGalleryKeys(String.join("\n", keys));
        }

        return toResponse(articleRepo.save(article));
    }

    /**
     * A readable, unique URL segment.
     *
     * <p>Suffixed with a short random block rather than checked-and-retried: two articles can
     * legitimately share a title ("December giveaway" every year), and a slug collision at save
     * time would surface as a constraint violation on a unique index rather than anything a
     * publisher could act on.
     */
    private String uniqueSlug(String title) {
        String base = (title == null ? "" : title).toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9\\s-]", "")
                .replaceAll("\\s+", "-")
                .replaceAll("-{2,}", "-")
                .replaceAll("^-|-$", "");
        if (base.isBlank()) base = "article";
        if (base.length() > 280) base = base.substring(0, 280);
        String slug = base + "-" + UUID.randomUUID().toString().substring(0, 8);
        return articleRepo.findBySlug(slug).isPresent()
                ? base + "-" + UUID.randomUUID().toString().substring(0, 8)
                : slug;
    }

    /** One published article by its slug — what the public detail page reads. */
    public NewsArticleResponse getPublishedBySlug(String slug) {
        NewsArticle article = articleRepo.findBySlug(slug)
                .filter(a -> a.getStatus() == NewsArticle.ArticleStatus.PUBLISHED)
                .orElseThrow(() -> new NoSuchElementException("Article not found: " + slug));
        return toResponse(article);
    }

    /**
     * How many articles were published after a given moment.
     *
     * <p>Drives the header badge. The client holds the timestamp it last read, so the server keeps
     * no per-visitor state for something that only needs to say "there are three you have not
     * seen" — and it works for signed-out visitors, who are most of the audience for news.
     */
    public long countPublishedSince(Instant since) {
        return articleRepo.countByStatusAndPublishedAtAfter(
                NewsArticle.ArticleStatus.PUBLISHED,
                since == null ? Instant.EPOCH : since);
    }

    /**
     * Edits an article in place.
     *
     * <p>The slug deliberately does not follow the title. Once an article is published its URL has
     * been shared, indexed and linked; regenerating the slug on every title fix would break all of
     * that silently. A typo in a headline is worth less than a working link.
     *
     * <p>Images are replaced only when a new file is supplied — sending the form without one keeps
     * what is already there, so fixing a sentence does not require re-uploading the artwork.
     */
    @Transactional
    public NewsArticleResponse updateArticle(UUID id, CreateNewsArticleRequest req,
                                             MultipartFile image, List<MultipartFile> gallery) {
        NewsArticle article = articleRepo.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Article not found: " + id));

        article.setTitle(req.getTitle());
        article.setSummary(req.getSummary());
        article.setContent(req.getContent());

        if (image != null && !image.isEmpty()) {
            FileValidationUtils.validateImage(image);
            article.setImageKey(contaboObjectService.uploadFile(
                    "news/" + UUID.randomUUID() + "/" + image.getOriginalFilename(), image));
        }

        if (gallery != null && !gallery.isEmpty()) {
            List<String> keys = new ArrayList<>();
            for (MultipartFile g : gallery) {
                if (g == null || g.isEmpty()) continue;
                FileValidationUtils.validateImage(g);
                keys.add(contaboObjectService.uploadFile(
                        "news/" + UUID.randomUUID() + "/" + g.getOriginalFilename(), g));
            }
            // Supplying a gallery REPLACES the old one; that is what "choose these images" means.
            if (!keys.isEmpty()) article.setGalleryKeys(String.join("\n", keys));
        }

        return toResponse(articleRepo.save(article));
    }

    /**
     * Removes an article.
     *
     * <p>A published article that has already gone out by email cannot be unsent, so deleting one
     * takes it off the site without pretending it was never sent. The stored images are left in
     * object storage rather than deleted: they may be referenced from an email already in
     * somebody's inbox, and an orphaned file costs less than a broken image in a newsletter.
     */
    @Transactional
    public void deleteArticle(UUID id) {
        NewsArticle article = articleRepo.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Article not found: " + id));
        articleRepo.delete(article);
        log.warn("[NEWS] Article '{}' ({}) deleted", article.getTitle(), id);
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
        r.setSlug(a.getSlug());
        if (a.getImageKey() != null) {
            try { r.setImageUrl(contaboObjectService.getPresignedUrl(a.getImageKey())); }
            catch (Exception ignored) {}
        }
        if (a.getGalleryKeys() != null && !a.getGalleryKeys().isBlank()) {
            List<String> urls = new ArrayList<>();
            for (String key : a.getGalleryKeys().split("\n")) {
                if (key.isBlank()) continue;
                // One bad key must not cost the article its whole gallery.
                try { urls.add(contaboObjectService.getPresignedUrl(key.trim())); }
                catch (Exception ignored) {}
            }
            r.setGalleryUrls(urls);
        }
        return r;
    }
}
