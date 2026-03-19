package com.buyology.ecommerce.review.dto;

import com.buyology.ecommerce.review.domain.enums.ModerationStatus;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class QuestionResponse {

    private UUID id;
    private UUID productId;
    private UUID userId;
    private String userFirstName;
    private String userLastName;
    private String body;
    private ModerationStatus status;
    private Integer helpfulCount;
    private QuestionAnswerDto answer;
    private Instant createdAt;
    private Instant updatedAt;

    // =====================
    // Nested DTO
    // =====================

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class QuestionAnswerDto {
        private UUID id;
        private UUID adminId;
        private String adminFirstName;
        private String adminLastName;
        private String body;
        private Boolean isActive;
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

        public Boolean getIsActive() { return isActive; }
        public void setIsActive(Boolean isActive) { this.isActive = isActive; }

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

    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }

    public ModerationStatus getStatus() { return status; }
    public void setStatus(ModerationStatus status) { this.status = status; }

    public Integer getHelpfulCount() { return helpfulCount; }
    public void setHelpfulCount(Integer helpfulCount) { this.helpfulCount = helpfulCount; }

    public QuestionAnswerDto getAnswer() { return answer; }
    public void setAnswer(QuestionAnswerDto answer) { this.answer = answer; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
