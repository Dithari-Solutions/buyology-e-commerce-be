package com.buyology.ecommerce.role;

/**
 * Centralised permission code constants.
 * These values are stored in the permissions.code column and used in @PreAuthorize expressions.
 */
public final class PermissionConstants {

    private PermissionConstants() {}

    // ── Reviews ──────────────────────────────────────────────────────────────
    public static final String REVIEW_READ         = "review:read";
    public static final String REVIEW_MODERATE     = "review:moderate";
    public static final String REVIEW_REPLY_CREATE = "review:reply:create";
    public static final String REVIEW_REPLY_UPDATE = "review:reply:update";
    public static final String REVIEW_REPLY_DELETE = "review:reply:delete";
    public static final String REVIEW_DELETE       = "review:delete";

    // ── Questions ─────────────────────────────────────────────────────────────
    public static final String QUESTION_READ          = "question:read";
    public static final String QUESTION_MODERATE      = "question:moderate";
    public static final String QUESTION_ANSWER_CREATE = "question:answer:create";
    public static final String QUESTION_ANSWER_UPDATE = "question:answer:update";
    public static final String QUESTION_ANSWER_TOGGLE = "question:answer:toggle";
    public static final String QUESTION_ANSWER_DELETE = "question:answer:delete";
    public static final String QUESTION_DELETE        = "question:delete";

    // ── Courier ───────────────────────────────────────────────────────────────
    public static final String COURIER_READ   = "courier:read";
    public static final String COURIER_CREATE = "courier:create";
    public static final String COURIER_UPDATE = "courier:update";
    public static final String COURIER_DELETE = "courier:delete";

    // ── Store ─────────────────────────────────────────────────────────────────
    public static final String STORE_READ           = "store:read";
    public static final String STORE_UPDATE         = "store:update";
    public static final String STORE_PRODUCT_READ   = "store:product:read";
    public static final String STORE_PRODUCT_ASSIGN = "store:product:assign";
    public static final String STORE_PRODUCT_UPDATE = "store:product:update";
    public static final String STORE_PRODUCT_REMOVE = "store:product:remove";
    public static final String STORE_ADMIN_READ     = "store:admin:read";
    public static final String STORE_ADMIN_ASSIGN   = "store:admin:assign";
    public static final String STORE_ADMIN_UPDATE   = "store:admin:update";
    public static final String STORE_ADMIN_REMOVE   = "store:admin:remove";
}
