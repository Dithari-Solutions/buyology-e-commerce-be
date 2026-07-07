package com.buyology.ecommerce.b2b.productrequest.domain;

/**
 * Lifecycle of a B2B product-sourcing request (a member asks Buyology to source a product
 * that isn't in the catalog).
 *
 * NEW          — just submitted by the member; awaiting procurement triage.
 * UNDER_REVIEW — procurement is evaluating the request.
 * IN_PROGRESS  — procurement is actively sourcing the product.
 * COMPLETED    — sourcing finished (product found / added / handed off).
 * REJECTED     — procurement declined to source the product.
 */
public enum B2bProductRequestStatus {
    NEW,
    UNDER_REVIEW,
    IN_PROGRESS,
    COMPLETED,
    REJECTED
}
