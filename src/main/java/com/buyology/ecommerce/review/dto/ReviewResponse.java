package com.buyology.ecommerce.review.dto;

import com.buyology.ecommerce.review.domain.enums.MediaType;
import com.buyology.ecommerce.review.domain.enums.ModerationStatus;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ReviewResponse {

    private UUID id;
    private UUID productId;
    private UUID userId;
    private String userFirstName;
    private String userLastName;
    private Short rating;
    private String body;
    private Boolean isVerifiedPurchase;
    private ModerationStatus status;
    private String rejectionReason;
    private Integer helpfulCount;
    private Integer notHelpfulCount;
    private List<MediaDto> media;
    private ReviewReplyDto reply;
    private Instant createdAt;
    private Instant updatedAt;

    // =====================
    // Nested DTOs
    // =====================

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class MediaDto {
        private UUID id;
        private String url;
        private MediaType mediaType;
        private Short sortOrder;
        private Instant createdAt;

        public UUID getId() { return id; }
        public void setId(UUID id) { this.id = id; }

        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }

        public MediaType getMediaType() { return mediaType; }
        public void setMediaType(MediaType mediaType) { this.mediaType = mediaType; }

        public Short getSortOrder() { return sortOrder; }
        public void setSortOrder(Short sortOrder) { this.sortOrder = sortOrder; }

        public Instant getCreatedAt() { return createdAt; }
        public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ReviewReplyDto {
        private UUID id;
        private UUID adminId;
        private String adminFirstName;
        private String adminLastName;
        private String body;
        private Instant createdAt;
        private Instant updatedAt;

        public UUID getId() { return id; }
        public void setId(UUID id) { this.id = id; }

        public UUID getAdminId() { return adminId; }
        public void setAdminId(UUID adminId) { this.adminId = adminId; }

        public String getAdminFirstName() { return adminFirstName; }
        public void setAdminFirstName(String adminFirstName) { this.adminFirstName = adminFirstName; }

        public String getAdminLastName() { return adminLastName; }
        public void setAdminLastName(String adminLastName) { this.adminLastName = adminLastName; }

        public String getBody() { return body; }
        public void setBody(String body) { this.body = body; }

        public Instant getCreatedAt() { return createdAt; }
        public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

        public Instant getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    }

    // =====================
    // Getters & Setters
    // =====================

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getProductId() { return productId; }
    public void setProductId(UUID productId) { this.productId = productId; }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public String getUserFirstName() { return userFirstName; }
    public void setUserFirstName(String userFirstName) { this.userFirstName = userFirstName; }

    public String getUserLastName() { return userLastName; }
    public void setUserLastName(String userLastName) { this.userLastName = userLastName; }

    public Short getRating() { return rating; }
    public void setRating(Short rating) { this.rating = rating; }

    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }

    public Boolean getIsVerifiedPurchase() { return isVerifiedPurchase; }
    public void setIsVerifiedPurchase(Boolean isVerifiedPurchase) { this.isVerifiedPurchase = isVerifiedPurchase; }

    public ModerationStatus getStatus() { return status; }
    public void setStatus(ModerationStatus status) { this.status = status; }

    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }

    public Integer getHelpfulCount() { return helpfulCount; }
    public void setHelpfulCount(Integer helpfulCount) { this.helpfulCount = helpfulCount; }

    public Integer getNotHelpfulCount() { return notHelpfulCount; }
    public void setNotHelpfulCount(Integer notHelpfulCount) { this.notHelpfulCount = notHelpfulCount; }

    public List<MediaDto> getMedia() { return media; }
    public void setMedia(List<MediaDto> media) { this.media = media; }

    public ReviewReplyDto getReply() { return reply; }
    public void setReply(ReviewReplyDto reply) { this.reply = reply; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
