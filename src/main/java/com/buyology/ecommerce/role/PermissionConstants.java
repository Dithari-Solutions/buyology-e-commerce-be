package com.buyology.ecommerce.role;

/**
 * Centralised permission code constants.
 *
 * <p>These values are stored in the {@code permissions.code} column and matched verbatim by
 * {@code hasAuthority(...)} in {@code @PreAuthorize} expressions, so they are lower case and never
 * renamed once shipped — a rename silently turns the guard into a permission nobody holds.
 *
 * <p>Every constant here must also appear in {@code RoleDataInitializer.ALL_PERMISSIONS}, otherwise the
 * row is never inserted, {@code hasAuthority} can never match it, and the endpoint it guards falls
 * through to whatever the rest of its expression allows.
 *
 * <p>Naming: {@code <module>:<resource>:<action>} or {@code <module>:<action>}. Actions are drawn from
 * read / create / update / delete / moderate / assign / export, plus a few capability-specific verbs
 * where none of those describe the operation (for example {@code promo:issue}, which mints spendable
 * value and must stay separable from {@code promo:create}).
 */
public final class PermissionConstants {

    private PermissionConstants() {}

    // ── Analytics ─────────────────────────────────────────────────────────────────
    // View website traffic: unique visitors, visits and page views on the dashboard home page.
    public static final String ANALYTICS_VISITOR_READ = "analytics:visitor:read";

    // ── AI assistant ──────────────────────────────────────────────────────────────
    // Read the storefront assistant's conversations: what customers asked and what it answered.
    // Transcripts are free text customers typed, so this is customer data and gated separately.
    public static final String ASSISTANT_CONVERSATION_READ = "assistant:conversation:read";

    // ── B2B & Procurement ─────────────────────────────────────────────────────────
    // View the countries B2B trading is enabled in, with their currency and minimum-order rules.
    public static final String B2B_COUNTRY_READ = "b2b:country:read";

    // Add or edit a B2B country (the POST endpoint is an upsert): currency, minimum order amount and
    // enabled flag.
    public static final String B2B_COUNTRY_UPDATE = "b2b:country:update";

    // Remove a B2B country, closing that market to B2B trade.
    public static final String B2B_COUNTRY_DELETE = "b2b:country:delete";

    // View outstanding and overdue B2B credit usages and the platform payback settings.
    public static final String B2B_CREDIT_READ = "b2b:credit:read";

    // Set or extend the payback deadline on an individual B2B credit usage.
    public static final String B2B_CREDIT_UPDATE = "b2b:credit:update";

    // Change the platform-wide B2B credit payback term in days.
    public static final String B2B_CREDIT_CONFIG_UPDATE = "b2b:credit:config:update";

    // View the legacy B2B inquiry queue submitted from the public contact form.
    public static final String B2B_INQUIRY_READ = "b2b:inquiry:read";

    // Change the status and admin notes on a legacy B2B inquiry.
    public static final String B2B_INQUIRY_UPDATE = "b2b:inquiry:update";

    // View active B2B memberships and their details.
    public static final String B2B_MEMBERSHIP_READ = "b2b:membership:read";

    // Edit a B2B membership (company, tier, validity), freeze or unfreeze it, and re-send the set-
    // password email.
    public static final String B2B_MEMBERSHIP_UPDATE = "b2b:membership:update";

    // Trash a B2B membership or restore a trashed one.
    public static final String B2B_MEMBERSHIP_DELETE = "b2b:membership:delete";

    // View B2B membership applications and their trade-license documents.
    public static final String B2B_MEMBERSHIP_APPLICATION_READ = "b2b:membership:application:read";

    // Convert an existing retail customer into a B2B applicant by filing an application on their
    // behalf.
    public static final String B2B_MEMBERSHIP_APPLICATION_CREATE = "b2b:membership:application:create";

    // Approve, reject or mark under review a B2B membership application (approval opens the member's
    // credit line).
    public static final String B2B_MEMBERSHIP_APPLICATION_MODERATE = "b2b:membership:application:moderate";

    // View B2B product-sourcing requests and the new-request badge count.
    public static final String B2B_PRODUCT_REQUEST_READ = "b2b:product-request:read";

    // Send a progress update message to the member who raised a product-sourcing request.
    public static final String B2B_PRODUCT_REQUEST_UPDATE = "b2b:product-request:update";

    // Advance a B2B product-sourcing request through its lifecycle (accept, source, fulfil, reject).
    public static final String B2B_PRODUCT_REQUEST_MODERATE = "b2b:product-request:moderate";

    // View B2B request-for-quote submissions, their line items and the pending-quote badge count.
    public static final String B2B_QUOTE_READ = "b2b:quote:read";

    // Respond to a B2B quote request by setting its prices or rejecting it.
    public static final String B2B_QUOTE_MODERATE = "b2b:quote:moderate";

    // Approve or reject the proof-of-payment a buyer uploaded for a bank-transfer quote.
    // Separate from :moderate because it releases the order against money we believe has landed.
    public static final String B2B_QUOTE_PAYMENT_VERIFY = "b2b:quote:payment:verify";

    // View a B2B member's wallet balance and transaction history.
    public static final String B2B_WALLET_READ = "b2b:wallet:read";

    // Add credit to a B2B member's wallet (creates spendable balance).
    public static final String B2B_WALLET_CREDIT = "b2b:wallet:credit";

    // Deduct from or manually correct a B2B member's wallet balance.
    public static final String B2B_WALLET_ADJUST = "b2b:wallet:adjust";

    // ── Catalog ───────────────────────────────────────────────────────────────────
    // View the admin product catalog: list, paginated search, status counts, product detail,
    // products by category and the trash bin.
    public static final String PRODUCT_READ = "product:read";

    // Create new products with translations, media, variants and accessories.
    public static final String PRODUCT_CREATE = "product:create";

    // Edit an existing product's fields, translations and media.
    public static final String PRODUCT_UPDATE = "product:update";

    // Publish or hide a product by switching its status between ACTIVE and INACTIVE.
    public static final String PRODUCT_MODERATE = "product:moderate";

    // Move products to the trash and restore trashed products back to the catalog.
    public static final String PRODUCT_DELETE = "product:delete";

    // Drop and rebuild the Elasticsearch product index from the database (search is degraded while
    // it runs).
    public static final String PRODUCT_REINDEX = "product:reindex";

    // Create new brands.
    public static final String PRODUCT_BRAND_CREATE = "product:brand:create";

    // Deactivate a brand.
    public static final String PRODUCT_BRAND_DELETE = "product:brand:delete";

    // Create product categories and subcategories.
    public static final String PRODUCT_CATEGORY_CREATE = "product:category:create";

    // Edit a product category's translations, parent and settings.
    public static final String PRODUCT_CATEGORY_UPDATE = "product:category:update";

    // Deactivate (soft-delete) a product category.
    public static final String PRODUCT_CATEGORY_DELETE = "product:category:delete";

    // View the global spec library (groups and options) and the spec-code registry.
    public static final String PRODUCT_SPEC_READ = "product:spec:read";

    // Create spec groups, spec options and spec codes.
    public static final String PRODUCT_SPEC_CREATE = "product:spec:create";

    // Edit spec groups, spec options and spec codes, and reorder spec options.
    public static final String PRODUCT_SPEC_UPDATE = "product:spec:update";

    // Delete spec groups, spec options and spec codes.
    public static final String PRODUCT_SPEC_DELETE = "product:spec:delete";

    // ── Couriers ──────────────────────────────────────────────────────────────────
    // View courier accounts, courier detail and the live courier map.
    public static final String COURIER_READ = "courier:read";

    // Create a courier account.
    public static final String COURIER_CREATE = "courier:create";

    // Edit a courier account, change its status/availability, and (for the courier app) read and
    // update assigned deliveries.
    public static final String COURIER_UPDATE = "courier:update";

    // Delete a courier account.
    public static final String COURIER_DELETE = "courier:delete";

    // View per-store courier profiles (Store Couriers).
    public static final String COURIER_PROFILE_READ = "courier:profile:read";

    // Create a per-store courier profile.
    public static final String COURIER_PROFILE_CREATE = "courier:profile:create";

    // Edit a per-store courier profile.
    public static final String COURIER_PROFILE_UPDATE = "courier:profile:update";

    // Delete a per-store courier profile.
    public static final String COURIER_PROFILE_DELETE = "courier:profile:delete";

    // ── Integrations ──────────────────────────────────────────────────────────────
    // View the ERPNext integration status and browse items pulled live from ERPNext.
    public static final String ERP_READ = "erp:read";

    // Import ERPNext items into our product catalogue (preview + commit). Writes to the catalogue.
    public static final String ERP_PRODUCT_IMPORT = "erp:product:import";

    // View the per-order ERPNext sync status and manually re-push a failed order.
    public static final String ERP_ORDER_SYNC = "erp:order:sync";

    // Push a synthetic test order into ERPNext. Creates real documents in the connected ERP.
    public static final String ERP_ORDER_MOCK = "erp:order:mock";

    // ── Repair (customer device-repair requests) ──────────────────────────────────
    // View incoming repair requests, their photos, status history and the unread badge count.
    public static final String REPAIR_READ = "repair:read";

    // Mark a repair device as received, price the repair and move it through its lifecycle.
    public static final String REPAIR_UPDATE = "repair:update";

    // ── Sell (customer trade-in requests) ─────────────────────────────────────────
    // View incoming trade-in requests and their details.
    public static final String SELL_READ = "sell:read";

    // Make, revise or withdraw the buy-back offer on a trade-in request and advance its status.
    public static final String SELL_UPDATE = "sell:update";

    // View the customer support-ticket queue and ticket details.
    public static final String SUPPORT_READ = "support:read";

    // Reply to support tickets and move their status (resolve/close).
    public static final String SUPPORT_UPDATE = "support:update";

    // View the Quiqup integration config, sample payloads, test order details, labels and received
    // webhook events.
    public static final String QUIQUP_READ = "quiqup:read";

    // Create a test delivery order in the Quiqup staging sandbox.
    public static final String QUIQUP_ORDER_CREATE = "quiqup:order:create";

    // Mark a Quiqup test order ready for collection (triggers a pickup).
    public static final String QUIQUP_ORDER_UPDATE = "quiqup:order:update";

    // Cancel a Quiqup test delivery order.
    public static final String QUIQUP_ORDER_CANCEL = "quiqup:order:cancel";

    // Clear the stored Quiqup webhook event log.
    public static final String QUIQUP_EVENT_DELETE = "quiqup:event:delete";

    // ── Marketing ─────────────────────────────────────────────────────────────────
    // View promo banners and their details.
    public static final String BANNER_READ = "banner:read";

    // Create a promo banner.
    public static final String BANNER_CREATE = "banner:create";

    // Edit a banner's content, artwork and display order.
    public static final String BANNER_UPDATE = "banner:update";

    // Publish or hide a banner by changing its status.
    public static final String BANNER_MODERATE = "banner:moderate";

    // Delete a banner.
    public static final String BANNER_DELETE = "banner:delete";

    // View the daily-game schedule, quiz questions and token-reward settings.
    public static final String GAME_READ = "game:read";

    // Configure which game runs on which day.
    public static final String GAME_CONFIG_UPDATE = "game:config:update";

    // Create a quiz question.
    public static final String GAME_QUIZ_CREATE = "game:quiz:create";

    // Edit an existing quiz question.
    public static final String GAME_QUIZ_UPDATE = "game:quiz:update";

    // Delete a quiz question.
    public static final String GAME_QUIZ_DELETE = "game:quiz:delete";

    // Change how many tokens players are awarded per game or quiz (creates token liability).
    public static final String GAME_REWARD_UPDATE = "game:reward:update";

    // View all news articles, including unpublished drafts.
    public static final String NEWSLETTER_ARTICLE_READ = "newsletter:article:read";

    // Write a news article draft.
    public static final String NEWSLETTER_ARTICLE_CREATE = "newsletter:article:create";

    // Publish a news article and optionally email it to every newsletter subscriber.
    public static final String NEWSLETTER_ARTICLE_MODERATE = "newsletter:article:moderate";

    // View newsletter subscriber statistics.
    public static final String NEWSLETTER_SUBSCRIBER_READ = "newsletter:subscriber:read";

    // View promo codes, their redemption/usage history and the token-redemption settings.
    public static final String PROMO_READ = "promo:read";

    // Create a new promo code (creates financial liability).
    public static final String PROMO_CREATE = "promo:create";

    // Edit an existing promo code's discount, limits and validity.
    public static final String PROMO_UPDATE = "promo:update";

    // Deactivate/delete a promo code.
    public static final String PROMO_DELETE = "promo:delete";

    // Put spendable codes in customers' hands: blast a promo to customers or mint a personal coupon
    // for one user.
    public static final String PROMO_ISSUE = "promo:issue";

    // Change the token-redemption exchange rate (how many tokens buy what discount).
    public static final String PROMO_CONFIG_UPDATE = "promo:config:update";

    // View stories and story details in the admin dashboard.
    public static final String STORY_READ = "story:read";

    // Create a story, including requesting presigned media upload URLs.
    public static final String STORY_CREATE = "story:create";

    // Edit an existing story: add media and change its display order.
    public static final String STORY_UPDATE = "story:update";

    // Activate or deactivate a story so it shows or hides on the storefront.
    public static final String STORY_MODERATE = "story:moderate";

    // Delete a story or remove a media file from a story.
    public static final String STORY_DELETE = "story:delete";

    // ── Orders ────────────────────────────────────────────────────────────────────
    // View orders in the admin dashboard — list, filters, full detail and delivery proof photos.
    public static final String ORDER_READ = "order:read";

    // Move an order through its status lifecycle, including cancelling it.
    public static final String ORDER_STATUS_UPDATE = "order:status:update";

    // Add tracking events, set the carrier tracking code and upload pickup/drop-off proof photos.
    public static final String ORDER_TRACKING_UPDATE = "order:tracking:update";

    // Assign one of the store's couriers to an express order.
    public static final String ORDER_COURIER_ASSIGN = "order:courier:assign";

    // ── Payments, Refunds & Payouts ───────────────────────────────────────────────
    // Issue a full or partial refund against a successful payment transaction directly at the
    // payment gateway.
    public static final String PAYMENT_REFUND = "payment:refund";

    // View supplier payout requests — list, filters and detail.
    public static final String PAYOUT_READ = "payout:read";

    // Reject a supplier payout request.
    public static final String PAYOUT_MODERATE = "payout:moderate";

    // Mark a supplier payout request as paid — confirms money has left the company.
    public static final String PAYOUT_PAYOUT = "payout:payout";

    // View a supplier's stored payout bank account details.
    public static final String PAYOUT_ACCOUNT_READ = "payout:account:read";

    // View customer refund requests — list, filters and full detail.
    public static final String REFUND_READ = "refund:read";

    // Approve or reject a customer refund request.
    public static final String REFUND_MODERATE = "refund:moderate";

    // Mark returned goods as received for a refund request (store fulfilment step).
    public static final String REFUND_UPDATE = "refund:update";

    // Release the money for an approved refund request back to the customer.
    public static final String REFUND_PAYOUT = "refund:payout";

    // View the global refund policy settings (window, courier pickup fee, rules).
    public static final String REFUND_SETTING_READ = "refund:setting:read";

    // Change the global refund policy settings.
    public static final String REFUND_SETTING_UPDATE = "refund:setting:update";

    // ── Revenue ───────────────────────────────────────────────────────────────────
    // View platform and supplier revenue reports in the dashboard.
    public static final String REVENUE_READ = "revenue:read";

    // Generate a revenue export file (CSV/XLSX) containing full financial data.
    public static final String REVENUE_EXPORT = "revenue:export";

    // View the revenue export history and download previously generated export files.
    public static final String REVENUE_EXPORT_READ = "revenue:export:read";

    // ── Reviews & Q&A ─────────────────────────────────────────────────────────────
    // View customer product reviews in the admin dashboard — list, filters and detail.
    public static final String REVIEW_READ = "review:read";

    // Approve, reject or hide a customer review.
    public static final String REVIEW_MODERATE = "review:moderate";

    // Delete a customer review.
    public static final String REVIEW_DELETE = "review:delete";

    // Post an official store reply to a customer review.
    public static final String REVIEW_REPLY_CREATE = "review:reply:create";

    // Edit an existing official reply to a customer review.
    public static final String REVIEW_REPLY_UPDATE = "review:reply:update";

    // Delete an official reply to a customer review.
    public static final String REVIEW_REPLY_DELETE = "review:reply:delete";

    // View customer product questions in the admin dashboard — list, filters and detail.
    public static final String QUESTION_READ = "question:read";

    // Approve, reject or hide a customer product question.
    public static final String QUESTION_MODERATE = "question:moderate";

    // Delete a customer product question.
    public static final String QUESTION_DELETE = "question:delete";

    // Post an official answer to a customer product question.
    public static final String QUESTION_ANSWER_CREATE = "question:answer:create";

    // Edit an existing official answer.
    public static final String QUESTION_ANSWER_UPDATE = "question:answer:update";

    // Show or hide an official answer on the storefront.
    public static final String QUESTION_ANSWER_TOGGLE = "question:answer:toggle";

    // Delete an official answer.
    public static final String QUESTION_ANSWER_DELETE = "question:answer:delete";

    // ── Roles & Permissions ───────────────────────────────────────────────────────
    // View the permission catalog and the direct permissions granted to a user.
    public static final String PERMISSION_READ = "permission:read";

    // Create a new permission code.
    public static final String PERMISSION_CREATE = "permission:create";

    // Edit an existing permission code or its description.
    public static final String PERMISSION_UPDATE = "permission:update";

    // Delete a permission code.
    public static final String PERMISSION_DELETE = "permission:delete";

    // View roles, the permissions attached to them, and who holds each role.
    public static final String ROLE_READ = "role:read";

    // Create a new role.
    public static final String ROLE_CREATE = "role:create";

    // Rename or edit an existing role.
    public static final String ROLE_UPDATE = "role:update";

    // Delete a role.
    public static final String ROLE_DELETE = "role:delete";

    // Edit a role's permission matrix (add, remove or replace its permissions).
    public static final String ROLE_PERMISSION_ASSIGN = "role:permission:assign";

    // Grant or revoke a role on a specific user.
    public static final String USER_ROLE_ASSIGN = "user:role:assign";

    // Grant or revoke a direct ALLOW/DENY permission on a specific user.
    public static final String USER_PERMISSION_ASSIGN = "user:permission:assign";

    // ── Settings & Reference Data ─────────────────────────────────────────────────
    // View the platform country reference list, including inactive countries.
    public static final String COUNTRY_READ = "country:read";

    // Add a new country (market) with its currency.
    public static final String COUNTRY_CREATE = "country:create";

    // Edit a country's name, currency, active state and B2B market toggle.
    public static final String COUNTRY_UPDATE = "country:update";

    // Deactivate a country, closing that market across the platform.
    public static final String COUNTRY_DELETE = "country:delete";

    // ── Stores ────────────────────────────────────────────────────────────────────
    // View stores in the dashboard — list, detail and lookup by slug.
    public static final String STORE_READ = "store:read";

    // Edit a store's details, banner and translations.
    public static final String STORE_UPDATE = "store:update";

    // Create a new store.
    public static final String STORE_CREATE = "store:create";

    // Soft-delete a store, removing it from the platform.
    public static final String STORE_DELETE = "store:delete";

    // View a store's branches and their weekly operating hours.
    public static final String STORE_LOCATION_READ = "store:location:read";

    // Add a new branch (location) to a store.
    public static final String STORE_LOCATION_CREATE = "store:location:create";

    // Edit a branch's details and its weekly operating hours (including removing an hours row).
    public static final String STORE_LOCATION_UPDATE = "store:location:update";

    // Deactivate a store branch so it no longer serves customers.
    public static final String STORE_LOCATION_DELETE = "store:location:delete";

    // View the products and variants assigned to a store, with their store-level price and stock.
    public static final String STORE_PRODUCT_READ = "store:product:read";

    // Assign a catalog product or variant to a store.
    public static final String STORE_PRODUCT_ASSIGN = "store:product:assign";

    // Edit a store product's or store variant's price, stock and flags.
    public static final String STORE_PRODUCT_UPDATE = "store:product:update";

    // Remove a product or variant from a store.
    public static final String STORE_PRODUCT_REMOVE = "store:product:remove";

    // View the admins attached to a store.
    public static final String STORE_ADMIN_READ = "store:admin:read";

    // Attach an admin account to a store.
    public static final String STORE_ADMIN_ASSIGN = "store:admin:assign";

    // Edit a store-admin assignment.
    public static final String STORE_ADMIN_UPDATE = "store:admin:update";

    // Detach an admin account from a store.
    public static final String STORE_ADMIN_REMOVE = "store:admin:remove";

    // ── Suppliers ─────────────────────────────────────────────────────────────────
    // View supplier applications and supplier accounts, including their documents and linked stores.
    public static final String SUPPLIER_APPLICATION_READ = "supplier:application:read";

    // Approve, reject, edit, freeze, delete, restore or re-invite a supplier, and link/unlink
    // supplier stores.
    public static final String SUPPLIER_APPLICATION_REVIEW = "supplier:application:review";

    // View the supplier product moderation queue and approve or reject submitted supplier products.
    public static final String SUPPLIER_PRODUCT_APPROVE = "supplier:product:approve";

    // View supplier-initiated product change requests (edit/delete/restore) awaiting review.
    public static final String SUPPLIER_PRODUCT_CHANGE_READ = "supplier:product:change:read";

    // Approve or reject supplier-initiated product change requests.
    public static final String SUPPLIER_PRODUCT_CHANGE_MODERATE = "supplier:product:change:moderate";

    // Supplier portal: view own products, own trash, own change requests and own product reviews.
    public static final String SUPPLIER_PRODUCT_READ = "supplier:product:read";

    // Supplier portal: submit new products for approval.
    public static final String SUPPLIER_PRODUCT_CREATE = "supplier:product:create";

    // Supplier portal: edit, publish, draft, delete, restore or request edits to own products.
    public static final String SUPPLIER_PRODUCT_UPDATE = "supplier:product:update";

    // Supplier portal: view own sales analytics and export own revenue.
    public static final String SUPPLIER_ANALYTICS_READ = "supplier:analytics:read";

    // Supplier portal: view the stores the supplier is linked to.
    public static final String SUPPLIER_STORE_READ = "supplier:store:read";

    // Supplier portal: view the signed-in supplier's own business profile.
    public static final String SUPPLIER_ACCOUNT_READ = "supplier:account:read";

    // Supplier portal: freeze or unfreeze the signed-in supplier's own account.
    public static final String SUPPLIER_ACCOUNT_UPDATE = "supplier:account:update";

    // Supplier portal: close (soft-delete) the signed-in supplier's own account.
    public static final String SUPPLIER_ACCOUNT_DELETE = "supplier:account:delete";

    // Supplier portal: view orders that contain the supplier's own items.
    public static final String SUPPLIER_ORDER_READ = "supplier:order:read";

    // Supplier portal: advance the fulfilment status of an order containing the supplier's own
    // items.
    public static final String SUPPLIER_ORDER_UPDATE = "supplier:order:update";

    // Supplier portal: view refund requests affecting the supplier's own products (read-only).
    public static final String SUPPLIER_REFUND_READ = "supplier:refund:read";

    // Supplier portal: view own payout account, payout eligibility and payout history.
    public static final String SUPPLIER_PAYOUT_READ = "supplier:payout:read";

    // Supplier portal: submit a new payout request for the available balance.
    public static final String SUPPLIER_PAYOUT_CREATE = "supplier:payout:create";

    // Supplier portal: add or change own payout bank account details.
    public static final String SUPPLIER_PAYOUT_UPDATE = "supplier:payout:update";

    // ── Users & Admins ────────────────────────────────────────────────────────────
    // View the customer/user list and open a user's full detail (profile, favorites, active cart).
    public static final String USER_READ = "user:read";

    // Suspend or restore a user account, including the bulk inactivity sweep.
    public static final String USER_BLOCK = "user:block";

    // View saved/favorite products across all users or for one user.
    public static final String USER_FAVORITE_READ = "user:favorite:read";

    // Promote an existing storefront account to an admin account and assign it roles (privilege
    // escalation).
    public static final String USER_PROMOTE = "user:promote";

    // Wipe another user's two-factor enrollment and recovery codes for lost-device recovery.
    public static final String USER_MFA_RESET = "user:mfa:reset";

    // View the admin-account list and look up which account holds an email address.
    public static final String USER_ADMIN_READ = "user:admin:read";

    // Provision a new admin account with a password and roles (includes the /auth/admin/signup +
    // verify-otp flow).
    public static final String USER_ADMIN_CREATE = "user:admin:create";
}
