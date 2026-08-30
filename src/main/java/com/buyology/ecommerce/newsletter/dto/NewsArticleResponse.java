package com.buyology.ecommerce.newsletter.dto;

import com.buyology.ecommerce.newsletter.domain.NewsArticle;
import java.time.Instant;
import java.util.UUID;

public class NewsArticleResponse {
    private UUID id;
    private String title;
    private String summary;
    private String content;
    private NewsArticle.ArticleStatus status;
    private String imageUrl;
    /** Readable URL segment — the public detail page is /news/{slug}. */
    private String slug;
    /** Presigned URLs for the extra images, in the order they were uploaded. */
    private java.util.List<String> galleryUrls = java.util.List.of();
    private Instant publishedAt;
    private Instant createdAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public NewsArticle.ArticleStatus getStatus() { return status; }
    public void setStatus(NewsArticle.ArticleStatus status) { this.status = status; }
    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }
    public java.util.List<String> getGalleryUrls() { return galleryUrls; }
    public void setGalleryUrls(java.util.List<String> galleryUrls) { this.galleryUrls = galleryUrls; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public Instant getPublishedAt() { return publishedAt; }
    public void setPublishedAt(Instant publishedAt) { this.publishedAt = publishedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
