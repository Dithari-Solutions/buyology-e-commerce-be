package com.buyology.ecommerce.role;

import com.buyology.ecommerce.role.domain.Permission;
import com.buyology.ecommerce.role.domain.Role;
import com.buyology.ecommerce.role.domain.RolePermission;
import com.buyology.ecommerce.role.domain.RolePermissionId;
import com.buyology.ecommerce.role.domain.UserRole;
import com.buyology.ecommerce.role.domain.UserRoleId;
import com.buyology.ecommerce.role.repository.PermissionRepository;
import com.buyology.ecommerce.role.repository.RolePermissionRepository;
import com.buyology.ecommerce.role.repository.RoleRepository;
import com.buyology.ecommerce.role.repository.UserRoleRepository;
import com.buyology.ecommerce.user.domain.Users;
import com.buyology.ecommerce.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Seeds predefined roles and permissions on startup (idempotent).
 *
 * Roles and their starter access:
 *   CUSTOMER_SUPPORT — review & questions module
 *   COURIER_ADMIN    — courier module (no delete)
 *   STORE_ADMIN      — store product assignment for their store
 *   SUPERADMIN       — full access to all modules
 *
 * <p>The permission lists below are <em>starting points</em>, applied when a role row is first
 * created. Afterwards the RBAC console owns them: this class will not re-grant, and never revokes.
 * SUPERADMIN alone is kept in sync with the full permission set on every boot.
 */
@Component
public class RoleDataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(RoleDataInitializer.class);

    // ── Role names referenced in code ────────────────────────────────────────

    private static final String ADMIN = "ADMIN";
    private static final String SUPERADMIN = "SUPERADMIN";
    private static final String SUPPLIER = "SUPPLIER";

    // ── Permission code lists per role ─────────────────────────────────────────

    /**
     * Every code in the catalog. SUPERADMIN is re-synced to this on every boot and is locked against
     * editing, so it stays the recovery path for any permission mistake made in the console.
     */
    private static final List<String> ALL_PERMISSIONS = List.of(
            PermissionConstants.ANALYTICS_VISITOR_READ,
            PermissionConstants.ASSISTANT_CONVERSATION_READ,
            PermissionConstants.B2B_COUNTRY_READ,
            PermissionConstants.B2B_COUNTRY_UPDATE,
            PermissionConstants.B2B_COUNTRY_DELETE,
            PermissionConstants.B2B_CREDIT_READ,
            PermissionConstants.B2B_CREDIT_UPDATE,
            PermissionConstants.B2B_CREDIT_CONFIG_UPDATE,
            PermissionConstants.B2B_INQUIRY_READ,
            PermissionConstants.B2B_INQUIRY_UPDATE,
            PermissionConstants.B2B_MEMBERSHIP_READ,
            PermissionConstants.B2B_MEMBERSHIP_UPDATE,
            PermissionConstants.B2B_MEMBERSHIP_DELETE,
            PermissionConstants.B2B_MEMBERSHIP_APPLICATION_READ,
            PermissionConstants.B2B_MEMBERSHIP_APPLICATION_CREATE,
            PermissionConstants.B2B_MEMBERSHIP_APPLICATION_MODERATE,
            PermissionConstants.B2B_PRODUCT_REQUEST_READ,
            PermissionConstants.B2B_PRODUCT_REQUEST_UPDATE,
            PermissionConstants.B2B_PRODUCT_REQUEST_MODERATE,
            PermissionConstants.B2B_QUOTE_READ,
            PermissionConstants.B2B_QUOTE_MODERATE,
            PermissionConstants.B2B_QUOTE_PAYMENT_VERIFY,
            PermissionConstants.B2B_WALLET_READ,
            PermissionConstants.B2B_WALLET_CREDIT,
            PermissionConstants.B2B_WALLET_ADJUST,
            PermissionConstants.PRODUCT_READ,
            PermissionConstants.PRODUCT_CREATE,
            PermissionConstants.PRODUCT_UPDATE,
            PermissionConstants.PRODUCT_MODERATE,
            PermissionConstants.PRODUCT_DELETE,
            PermissionConstants.PRODUCT_REINDEX,
            PermissionConstants.PRODUCT_BRAND_CREATE,
            PermissionConstants.PRODUCT_BRAND_DELETE,
            PermissionConstants.PRODUCT_CATEGORY_CREATE,
            PermissionConstants.PRODUCT_CATEGORY_UPDATE,
            PermissionConstants.PRODUCT_CATEGORY_DELETE,
            PermissionConstants.PRODUCT_SPEC_READ,
            PermissionConstants.PRODUCT_SPEC_CREATE,
            PermissionConstants.PRODUCT_SPEC_UPDATE,
            PermissionConstants.PRODUCT_SPEC_DELETE,
            PermissionConstants.COURIER_READ,
            PermissionConstants.COURIER_CREATE,
            PermissionConstants.COURIER_UPDATE,
            PermissionConstants.COURIER_DELETE,
            PermissionConstants.COURIER_PROFILE_READ,
            PermissionConstants.COURIER_PROFILE_CREATE,
            PermissionConstants.COURIER_PROFILE_UPDATE,
            PermissionConstants.COURIER_PROFILE_DELETE,
            PermissionConstants.ERP_READ,
            PermissionConstants.ERP_PRODUCT_IMPORT,
            PermissionConstants.ERP_ORDER_SYNC,
            PermissionConstants.ERP_ORDER_MOCK,
            PermissionConstants.REPAIR_READ,
            PermissionConstants.REPAIR_UPDATE,
            PermissionConstants.SELL_READ,
            PermissionConstants.SELL_UPDATE,
            PermissionConstants.SUPPORT_READ,
            PermissionConstants.SUPPORT_UPDATE,
            PermissionConstants.QUIQUP_READ,
            PermissionConstants.QUIQUP_ORDER_CREATE,
            PermissionConstants.QUIQUP_ORDER_UPDATE,
            PermissionConstants.QUIQUP_ORDER_CANCEL,
            PermissionConstants.QUIQUP_EVENT_DELETE,
            PermissionConstants.BANNER_READ,
            PermissionConstants.BANNER_CREATE,
            PermissionConstants.BANNER_UPDATE,
            PermissionConstants.BANNER_MODERATE,
            PermissionConstants.BANNER_DELETE,
            PermissionConstants.GAME_READ,
            PermissionConstants.GAME_CONFIG_UPDATE,
            PermissionConstants.GAME_QUIZ_CREATE,
            PermissionConstants.GAME_QUIZ_UPDATE,
            PermissionConstants.GAME_QUIZ_DELETE,
            PermissionConstants.GAME_REWARD_UPDATE,
            PermissionConstants.NEWSLETTER_ARTICLE_READ,
            PermissionConstants.NEWSLETTER_ARTICLE_CREATE,
            PermissionConstants.NEWSLETTER_ARTICLE_MODERATE,
            PermissionConstants.NEWSLETTER_SUBSCRIBER_READ,
            PermissionConstants.PROMO_READ,
            PermissionConstants.PROMO_CREATE,
            PermissionConstants.PROMO_UPDATE,
            PermissionConstants.PROMO_DELETE,
            PermissionConstants.PROMO_ISSUE,
            PermissionConstants.PROMO_CONFIG_UPDATE,
            PermissionConstants.STORY_READ,
            PermissionConstants.STORY_CREATE,
            PermissionConstants.STORY_UPDATE,
            PermissionConstants.STORY_MODERATE,
            PermissionConstants.STORY_DELETE,
            PermissionConstants.ORDER_READ,
            PermissionConstants.ORDER_STATUS_UPDATE,
            PermissionConstants.ORDER_TRACKING_UPDATE,
            PermissionConstants.ORDER_COURIER_ASSIGN,
            PermissionConstants.ORDER_PAYMENT_CONTACT,
            PermissionConstants.PAYMENT_REFUND,
            PermissionConstants.PAYOUT_READ,
            PermissionConstants.PAYOUT_MODERATE,
            PermissionConstants.PAYOUT_PAYOUT,
            PermissionConstants.PAYOUT_ACCOUNT_READ,
            PermissionConstants.REFUND_READ,
            PermissionConstants.REFUND_MODERATE,
            PermissionConstants.REFUND_UPDATE,
            PermissionConstants.REFUND_PAYOUT,
            PermissionConstants.REFUND_SETTING_READ,
            PermissionConstants.REFUND_SETTING_UPDATE,
            PermissionConstants.REVENUE_READ,
            PermissionConstants.REVENUE_EXPORT,
            PermissionConstants.REVENUE_EXPORT_READ,
            PermissionConstants.REVIEW_READ,
            PermissionConstants.REVIEW_MODERATE,
            PermissionConstants.REVIEW_DELETE,
            PermissionConstants.REVIEW_REPLY_CREATE,
            PermissionConstants.REVIEW_REPLY_UPDATE,
            PermissionConstants.REVIEW_REPLY_DELETE,
            PermissionConstants.QUESTION_READ,
            PermissionConstants.QUESTION_MODERATE,
            PermissionConstants.QUESTION_DELETE,
            PermissionConstants.QUESTION_ANSWER_CREATE,
            PermissionConstants.QUESTION_ANSWER_UPDATE,
            PermissionConstants.QUESTION_ANSWER_TOGGLE,
            PermissionConstants.QUESTION_ANSWER_DELETE,
            PermissionConstants.PERMISSION_READ,
            PermissionConstants.PERMISSION_CREATE,
            PermissionConstants.PERMISSION_UPDATE,
            PermissionConstants.PERMISSION_DELETE,
            PermissionConstants.ROLE_READ,
            PermissionConstants.ROLE_CREATE,
            PermissionConstants.ROLE_UPDATE,
            PermissionConstants.ROLE_DELETE,
            PermissionConstants.ROLE_PERMISSION_ASSIGN,
            PermissionConstants.USER_ROLE_ASSIGN,
            PermissionConstants.USER_PERMISSION_ASSIGN,
            PermissionConstants.COUNTRY_READ,
            PermissionConstants.COUNTRY_CREATE,
            PermissionConstants.COUNTRY_UPDATE,
            PermissionConstants.COUNTRY_DELETE,
            PermissionConstants.STORE_READ,
            PermissionConstants.STORE_UPDATE,
            PermissionConstants.STORE_CREATE,
            PermissionConstants.STORE_DELETE,
            PermissionConstants.STORE_LOCATION_READ,
            PermissionConstants.STORE_LOCATION_CREATE,
            PermissionConstants.STORE_LOCATION_UPDATE,
            PermissionConstants.STORE_LOCATION_DELETE,
            PermissionConstants.STORE_PRODUCT_READ,
            PermissionConstants.STORE_PRODUCT_ASSIGN,
            PermissionConstants.STORE_PRODUCT_UPDATE,
            PermissionConstants.STORE_PRODUCT_REMOVE,
            PermissionConstants.STORE_ADMIN_READ,
            PermissionConstants.STORE_ADMIN_ASSIGN,
            PermissionConstants.STORE_ADMIN_UPDATE,
            PermissionConstants.STORE_ADMIN_REMOVE,
            PermissionConstants.SUPPLIER_APPLICATION_READ,
            PermissionConstants.SUPPLIER_APPLICATION_REVIEW,
            PermissionConstants.SUPPLIER_PRODUCT_APPROVE,
            PermissionConstants.SUPPLIER_PRODUCT_CHANGE_READ,
            PermissionConstants.SUPPLIER_PRODUCT_CHANGE_MODERATE,
            PermissionConstants.SUPPLIER_PRODUCT_READ,
            PermissionConstants.SUPPLIER_PRODUCT_CREATE,
            PermissionConstants.SUPPLIER_PRODUCT_UPDATE,
            PermissionConstants.SUPPLIER_ANALYTICS_READ,
            PermissionConstants.SUPPLIER_STORE_READ,
            PermissionConstants.SUPPLIER_ACCOUNT_READ,
            PermissionConstants.SUPPLIER_ACCOUNT_UPDATE,
            PermissionConstants.SUPPLIER_ACCOUNT_DELETE,
            PermissionConstants.SUPPLIER_ORDER_READ,
            PermissionConstants.SUPPLIER_ORDER_UPDATE,
            PermissionConstants.SUPPLIER_REFUND_READ,
            PermissionConstants.SUPPLIER_PAYOUT_READ,
            PermissionConstants.SUPPLIER_PAYOUT_CREATE,
            PermissionConstants.SUPPLIER_PAYOUT_UPDATE,
            PermissionConstants.USER_READ,
            PermissionConstants.USER_BLOCK,
            PermissionConstants.USER_FAVORITE_READ,
            PermissionConstants.USER_PROMOTE,
            PermissionConstants.USER_MFA_RESET,
            PermissionConstants.USER_ADMIN_READ,
            PermissionConstants.USER_ADMIN_CREATE
    );

    /**
     * Full day-to-day operations. Deliberately excludes role and permission administration, admin
     * creation, MFA reset, ERP, Quiqup and revenue exports — those stay SUPERADMIN-only.
     */
    private static final List<String> ADMIN_PERMISSIONS = List.of(
            PermissionConstants.ANALYTICS_VISITOR_READ,
            PermissionConstants.ASSISTANT_CONVERSATION_READ,
            PermissionConstants.B2B_COUNTRY_READ,
            PermissionConstants.B2B_COUNTRY_UPDATE,
            PermissionConstants.B2B_COUNTRY_DELETE,
            PermissionConstants.B2B_CREDIT_READ,
            PermissionConstants.B2B_CREDIT_UPDATE,
            PermissionConstants.B2B_CREDIT_CONFIG_UPDATE,
            PermissionConstants.B2B_INQUIRY_READ,
            PermissionConstants.B2B_INQUIRY_UPDATE,
            PermissionConstants.B2B_MEMBERSHIP_READ,
            PermissionConstants.B2B_MEMBERSHIP_UPDATE,
            PermissionConstants.B2B_MEMBERSHIP_DELETE,
            PermissionConstants.B2B_MEMBERSHIP_APPLICATION_READ,
            PermissionConstants.B2B_MEMBERSHIP_APPLICATION_CREATE,
            PermissionConstants.B2B_MEMBERSHIP_APPLICATION_MODERATE,
            PermissionConstants.B2B_PRODUCT_REQUEST_READ,
            PermissionConstants.B2B_PRODUCT_REQUEST_UPDATE,
            PermissionConstants.B2B_PRODUCT_REQUEST_MODERATE,
            PermissionConstants.B2B_QUOTE_READ,
            PermissionConstants.B2B_QUOTE_MODERATE,
            PermissionConstants.B2B_QUOTE_PAYMENT_VERIFY,
            PermissionConstants.B2B_WALLET_READ,
            PermissionConstants.B2B_WALLET_CREDIT,
            PermissionConstants.B2B_WALLET_ADJUST,
            PermissionConstants.REPAIR_READ,
            PermissionConstants.REPAIR_UPDATE,
            PermissionConstants.SELL_READ,
            PermissionConstants.SELL_UPDATE,
            PermissionConstants.SUPPORT_READ,
            PermissionConstants.SUPPORT_UPDATE,
            PermissionConstants.BANNER_READ,
            PermissionConstants.BANNER_CREATE,
            PermissionConstants.BANNER_UPDATE,
            PermissionConstants.BANNER_MODERATE,
            PermissionConstants.BANNER_DELETE,
            PermissionConstants.COUNTRY_READ,
            PermissionConstants.COUNTRY_CREATE,
            PermissionConstants.COUNTRY_UPDATE,
            PermissionConstants.COUNTRY_DELETE,
            PermissionConstants.COURIER_READ,
            PermissionConstants.COURIER_CREATE,
            PermissionConstants.COURIER_UPDATE,
            PermissionConstants.COURIER_DELETE,
            PermissionConstants.COURIER_PROFILE_READ,
            PermissionConstants.COURIER_PROFILE_CREATE,
            PermissionConstants.COURIER_PROFILE_UPDATE,
            PermissionConstants.COURIER_PROFILE_DELETE,
            PermissionConstants.GAME_READ,
            PermissionConstants.GAME_CONFIG_UPDATE,
            PermissionConstants.GAME_QUIZ_CREATE,
            PermissionConstants.GAME_QUIZ_UPDATE,
            PermissionConstants.GAME_QUIZ_DELETE,
            PermissionConstants.GAME_REWARD_UPDATE,
            PermissionConstants.NEWSLETTER_ARTICLE_READ,
            PermissionConstants.NEWSLETTER_ARTICLE_CREATE,
            PermissionConstants.NEWSLETTER_ARTICLE_MODERATE,
            PermissionConstants.NEWSLETTER_SUBSCRIBER_READ,
            PermissionConstants.ORDER_READ,
            PermissionConstants.ORDER_STATUS_UPDATE,
            PermissionConstants.ORDER_TRACKING_UPDATE,
            PermissionConstants.ORDER_COURIER_ASSIGN,
            PermissionConstants.ORDER_PAYMENT_CONTACT,
            PermissionConstants.PAYMENT_REFUND,
            PermissionConstants.PAYOUT_READ,
            PermissionConstants.PAYOUT_MODERATE,
            PermissionConstants.PAYOUT_PAYOUT,
            PermissionConstants.PAYOUT_ACCOUNT_READ,
            PermissionConstants.PRODUCT_READ,
            PermissionConstants.PRODUCT_CREATE,
            PermissionConstants.PRODUCT_UPDATE,
            PermissionConstants.PRODUCT_MODERATE,
            PermissionConstants.PRODUCT_DELETE,
            PermissionConstants.PRODUCT_REINDEX,
            PermissionConstants.PRODUCT_BRAND_CREATE,
            PermissionConstants.PRODUCT_BRAND_DELETE,
            PermissionConstants.PRODUCT_CATEGORY_CREATE,
            PermissionConstants.PRODUCT_CATEGORY_UPDATE,
            PermissionConstants.PRODUCT_CATEGORY_DELETE,
            PermissionConstants.PRODUCT_SPEC_READ,
            PermissionConstants.PRODUCT_SPEC_CREATE,
            PermissionConstants.PRODUCT_SPEC_UPDATE,
            PermissionConstants.PRODUCT_SPEC_DELETE,
            PermissionConstants.PROMO_READ,
            PermissionConstants.PROMO_CREATE,
            PermissionConstants.PROMO_UPDATE,
            PermissionConstants.PROMO_DELETE,
            PermissionConstants.PROMO_ISSUE,
            PermissionConstants.PROMO_CONFIG_UPDATE,
            PermissionConstants.QUESTION_READ,
            PermissionConstants.QUESTION_MODERATE,
            PermissionConstants.QUESTION_DELETE,
            PermissionConstants.QUESTION_ANSWER_CREATE,
            PermissionConstants.QUESTION_ANSWER_UPDATE,
            PermissionConstants.QUESTION_ANSWER_TOGGLE,
            PermissionConstants.QUESTION_ANSWER_DELETE,
            PermissionConstants.REFUND_READ,
            PermissionConstants.REFUND_MODERATE,
            PermissionConstants.REFUND_UPDATE,
            PermissionConstants.REFUND_PAYOUT,
            PermissionConstants.REFUND_SETTING_READ,
            PermissionConstants.REFUND_SETTING_UPDATE,
            PermissionConstants.REVENUE_READ,
            PermissionConstants.REVIEW_READ,
            PermissionConstants.REVIEW_MODERATE,
            PermissionConstants.REVIEW_DELETE,
            PermissionConstants.REVIEW_REPLY_CREATE,
            PermissionConstants.REVIEW_REPLY_UPDATE,
            PermissionConstants.REVIEW_REPLY_DELETE,
            PermissionConstants.STORY_READ,
            PermissionConstants.STORY_CREATE,
            PermissionConstants.STORY_UPDATE,
            PermissionConstants.STORY_MODERATE,
            PermissionConstants.STORY_DELETE,
            PermissionConstants.STORE_READ,
            PermissionConstants.STORE_CREATE,
            PermissionConstants.STORE_UPDATE,
            PermissionConstants.STORE_DELETE,
            PermissionConstants.STORE_LOCATION_READ,
            PermissionConstants.STORE_LOCATION_CREATE,
            PermissionConstants.STORE_LOCATION_UPDATE,
            PermissionConstants.STORE_LOCATION_DELETE,
            PermissionConstants.STORE_PRODUCT_READ,
            PermissionConstants.STORE_PRODUCT_ASSIGN,
            PermissionConstants.STORE_PRODUCT_UPDATE,
            PermissionConstants.STORE_PRODUCT_REMOVE,
            PermissionConstants.STORE_ADMIN_READ,
            PermissionConstants.STORE_ADMIN_ASSIGN,
            PermissionConstants.STORE_ADMIN_UPDATE,
            PermissionConstants.STORE_ADMIN_REMOVE,
            PermissionConstants.SUPPLIER_APPLICATION_READ,
            PermissionConstants.SUPPLIER_APPLICATION_REVIEW,
            PermissionConstants.SUPPLIER_PRODUCT_APPROVE,
            PermissionConstants.SUPPLIER_PRODUCT_CHANGE_READ,
            PermissionConstants.SUPPLIER_PRODUCT_CHANGE_MODERATE,
            PermissionConstants.USER_READ,
            PermissionConstants.USER_BLOCK,
            PermissionConstants.USER_FAVORITE_READ
    );

    /** Moderates reviews and product questions, and can look up customers to answer them. */
    private static final List<String> CUSTOMER_SUPPORT_PERMISSIONS = List.of(
            PermissionConstants.REVIEW_READ,
            PermissionConstants.REVIEW_MODERATE,
            PermissionConstants.REVIEW_DELETE,
            PermissionConstants.REVIEW_REPLY_CREATE,
            PermissionConstants.REVIEW_REPLY_UPDATE,
            PermissionConstants.REVIEW_REPLY_DELETE,
            PermissionConstants.QUESTION_READ,
            PermissionConstants.QUESTION_MODERATE,
            PermissionConstants.QUESTION_DELETE,
            PermissionConstants.QUESTION_ANSWER_CREATE,
            PermissionConstants.QUESTION_ANSWER_UPDATE,
            PermissionConstants.QUESTION_ANSWER_TOGGLE,
            PermissionConstants.QUESTION_ANSWER_DELETE,
            PermissionConstants.ORDER_READ,
            PermissionConstants.ORDER_PAYMENT_CONTACT,
            PermissionConstants.REFUND_READ,
            PermissionConstants.USER_READ,
            PermissionConstants.B2B_INQUIRY_READ,
            PermissionConstants.SUPPORT_READ,
            PermissionConstants.SUPPORT_UPDATE
    );

    /** Runs the courier fleet and the deliveries assigned to it. */
    private static final List<String> COURIER_ADMIN_PERMISSIONS = List.of(
            PermissionConstants.COURIER_READ,
            PermissionConstants.COURIER_CREATE,
            PermissionConstants.COURIER_UPDATE,
            PermissionConstants.COURIER_PROFILE_READ,
            PermissionConstants.COURIER_PROFILE_CREATE,
            PermissionConstants.COURIER_PROFILE_UPDATE,
            PermissionConstants.ORDER_READ,
            PermissionConstants.ORDER_COURIER_ASSIGN,
            PermissionConstants.ORDER_TRACKING_UPDATE
    );

    /**
     * Manages a store: its products, locations and staff. Creating and deleting stores stays with
     * ADMIN.
     */
    private static final List<String> STORE_ADMIN_PERMISSIONS = List.of(
            PermissionConstants.STORE_READ,
            PermissionConstants.STORE_UPDATE,
            PermissionConstants.STORE_PRODUCT_READ,
            PermissionConstants.STORE_PRODUCT_ASSIGN,
            PermissionConstants.STORE_PRODUCT_UPDATE,
            PermissionConstants.STORE_PRODUCT_REMOVE,
            PermissionConstants.STORE_ADMIN_READ,
            PermissionConstants.STORE_LOCATION_READ,
            PermissionConstants.STORE_LOCATION_CREATE,
            PermissionConstants.STORE_LOCATION_UPDATE,
            PermissionConstants.STORE_LOCATION_DELETE,
            PermissionConstants.ORDER_READ,
            PermissionConstants.ORDER_COURIER_ASSIGN,
            PermissionConstants.REFUND_UPDATE,
            PermissionConstants.COURIER_PROFILE_READ
    );

    /** Promotions and merchandising: promo codes, banners, stories, games and newsletters. */
    private static final List<String> MARKETING_PERMISSIONS = List.of(
            // Campaign work is judged on traffic, so marketing gets the visitor counters.
            PermissionConstants.ANALYTICS_VISITOR_READ,
            PermissionConstants.PROMO_READ,
            PermissionConstants.PROMO_CREATE,
            PermissionConstants.PROMO_UPDATE,
            PermissionConstants.PROMO_DELETE,
            PermissionConstants.PROMO_ISSUE,
            PermissionConstants.BANNER_READ,
            PermissionConstants.BANNER_CREATE,
            PermissionConstants.BANNER_UPDATE,
            PermissionConstants.BANNER_MODERATE,
            PermissionConstants.BANNER_DELETE,
            PermissionConstants.STORY_READ,
            PermissionConstants.STORY_CREATE,
            PermissionConstants.STORY_UPDATE,
            PermissionConstants.STORY_MODERATE,
            PermissionConstants.STORY_DELETE,
            PermissionConstants.GAME_READ,
            PermissionConstants.GAME_CONFIG_UPDATE,
            PermissionConstants.GAME_QUIZ_CREATE,
            PermissionConstants.GAME_QUIZ_UPDATE,
            PermissionConstants.GAME_QUIZ_DELETE,
            PermissionConstants.NEWSLETTER_ARTICLE_READ,
            PermissionConstants.NEWSLETTER_ARTICLE_CREATE,
            PermissionConstants.NEWSLETTER_ARTICLE_MODERATE,
            PermissionConstants.NEWSLETTER_SUBSCRIBER_READ,
            PermissionConstants.PRODUCT_READ,
            PermissionConstants.COUNTRY_READ
    );

    /** Prices B2B quote requests and works the product-sourcing queue. */
    private static final List<String> PROCUREMENT_PERMISSIONS = List.of(
            PermissionConstants.B2B_QUOTE_READ,
            PermissionConstants.B2B_QUOTE_MODERATE,
            PermissionConstants.B2B_QUOTE_PAYMENT_VERIFY,
            PermissionConstants.B2B_PRODUCT_REQUEST_READ,
            PermissionConstants.B2B_PRODUCT_REQUEST_UPDATE,
            PermissionConstants.B2B_PRODUCT_REQUEST_MODERATE,
            PermissionConstants.B2B_INQUIRY_READ,
            PermissionConstants.B2B_MEMBERSHIP_READ,
            // Customer trade-in (sell) requests are priced by procurement, not by the repair desk.
            PermissionConstants.SELL_READ,
            PermissionConstants.SELL_UPDATE,
            PermissionConstants.PRODUCT_READ
    );

    /** Repair desk: handles customer device-repair requests end to end, and nothing else. */
    private static final List<String> REPAIR_PERMISSIONS = List.of(
            PermissionConstants.REPAIR_READ,
            PermissionConstants.REPAIR_UPDATE
    );

    /** Third-party supplier: manages its own products and views its own analytics. */
    private static final List<String> SUPPLIER_PERMISSIONS = List.of(
            PermissionConstants.SUPPLIER_PRODUCT_CREATE,
            PermissionConstants.SUPPLIER_PRODUCT_READ,
            PermissionConstants.SUPPLIER_PRODUCT_UPDATE,
            PermissionConstants.SUPPLIER_ANALYTICS_READ,
            PermissionConstants.SUPPLIER_STORE_READ
    );

    // ── Role definitions ─────────────────────────────────────────────────────

    private record RoleDefinition(String name, String description, List<String> permissions) {}

    private static final List<RoleDefinition> ROLE_DEFINITIONS = List.of(
            new RoleDefinition(
                    "ADMIN",
                    "Full operational access to the dashboard, short of platform administration",
                    ADMIN_PERMISSIONS),
            new RoleDefinition(
                    "CUSTOMER_SUPPORT",
                    "Moderates customer reviews and product questions",
                    CUSTOMER_SUPPORT_PERMISSIONS),
            new RoleDefinition(
                    "COURIER_ADMIN",
                    "Manages courier accounts and delivery operations",
                    COURIER_ADMIN_PERMISSIONS),
            new RoleDefinition(
                    "STORE_ADMIN",
                    "Manages product assignments and inventory for their store",
                    STORE_ADMIN_PERMISSIONS),
            new RoleDefinition(
                    "MARKETING",
                    "Manages marketing: promo codes, newsletters, banners, stories and games",
                    MARKETING_PERMISSIONS),
            new RoleDefinition(
                    "SUPPLIER",
                    "Third-party supplier: manage own products and view own analytics",
                    SUPPLIER_PERMISSIONS),
            new RoleDefinition(
                    "PROCUREMENT",
                    "Prices B2B quote requests (RFQ) and handles product-sourcing requests",
                    PROCUREMENT_PERMISSIONS),
            new RoleDefinition(
                    "REPAIR",
                    "Manages customer device-repair requests from the repair dashboard",
                    REPAIR_PERMISSIONS),
            new RoleDefinition(
                    "SUPERADMIN",
                    "Full access to all modules and operations",
                    ALL_PERMISSIONS)
    );

    // ── Repositories ─────────────────────────────────────────────────────────

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;

    public RoleDataInitializer(RoleRepository roleRepository,
                               PermissionRepository permissionRepository,
                               RolePermissionRepository rolePermissionRepository,
                               UserRepository userRepository,
                               UserRoleRepository userRoleRepository) {
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
        this.rolePermissionRepository = rolePermissionRepository;
        this.userRepository = userRepository;
        this.userRoleRepository = userRoleRepository;
    }

    // ── Startup logic ─────────────────────────────────────────────────────────

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        // Ensure every permission code exists in the permissions table
        Map<String, Permission> permByCode = ensurePermissions();

        boolean adminRoleIsNew = false;
        for (RoleDefinition def : ROLE_DEFINITIONS) {
            boolean existed = roleRepository.findByName(def.name()).isPresent();
            Role role = ensureRole(def.name(), def.description());

            // Grant the starter permission set only when the role is first created. Re-applying it on
            // every boot would resurrect permissions a superadmin had deliberately revoked in the RBAC
            // console — from their side, the change would simply undo itself after the next deploy.
            if (!existed) {
                grantPermissions(role, def.permissions(), permByCode);
                if (ADMIN.equals(def.name())) {
                    adminRoleIsNew = true;
                }
            }
        }

        // SUPERADMIN is the exception: it is the recovery path for every other permission mistake, so
        // it stays synced to the full set and is locked against editing in the API.
        roleRepository.findByName(SUPERADMIN)
                .ifPresent(superadmin -> grantPermissions(superadmin, ALL_PERMISSIONS, permByCode));

        if (adminRoleIsNew) {
            backfillAdminRole();
        }
    }

    /**
     * Gives every pre-existing admin the new ADMIN role, once.
     *
     * <p>Before permission codes covered the admin endpoints, {@code user_type = ADMIN} alone granted the
     * whole dashboard. Turning on {@code rbac.strict-permissions} would therefore strip access from every
     * admin who happens to hold no role, or only a narrow one. Seeding the equivalent role preserves what
     * they can do today and leaves the superadmin free to swap it for something narrower afterwards.
     *
     * <p>Runs only on the boot that first creates the ADMIN role. Repeating it would re-grant the role to
     * admins a superadmin had deliberately narrowed — the same self-undoing behaviour that made the
     * permission seeding wrong.
     */
    private void backfillAdminRole() {
        Role adminRole = roleRepository.findByName(ADMIN).orElse(null);
        if (adminRole == null) return;

        // Supplier approval also creates user_type=ADMIN accounts (AdminSupplierService) whose access is
        // meant to stop at the supplier portal. Handing them the operational ADMIN role would silently
        // promote every supplier to a full dashboard admin.
        Set<UUID> suppliers = new HashSet<>(userRoleRepository.findUserIdsByRoleName(SUPPLIER));

        int granted = 0;
        int page = 0;
        Page<Users> batch;
        do {
            batch = userRepository.findByUserType(Users.UserType.ADMIN, PageRequest.of(page, 200));
            for (Users user : batch.getContent()) {
                UUID userId = user.getId();
                if (userId == null || suppliers.contains(userId)) continue;
                if (userRoleRepository.existsByIdUserIdAndIdRoleId(userId, adminRole.getId())) continue;

                UserRole ur = new UserRole();
                ur.setId(new UserRoleId(userId, adminRole.getId()));
                ur.setUser(user);
                ur.setRole(adminRole);
                userRoleRepository.save(ur);
                granted++;
            }
            page++;
        } while (batch.hasNext());

        log.info("RBAC backfill: granted the ADMIN role to {} existing admin account(s); {} supplier-owned "
                + "admin account(s) deliberately skipped.", granted, suppliers.size());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Map<String, Permission> ensurePermissions() {
        return ALL_PERMISSIONS.stream()
                .map(code -> permissionRepository.findByCode(code).orElseGet(() -> {
                    Permission p = new Permission();
                    p.setCode(code);
                    p.setDescription(humanise(code));
                    return permissionRepository.save(p);
                }))
                .collect(Collectors.toMap(Permission::getCode, Function.identity()));
    }

    private Role ensureRole(String name, String description) {
        return roleRepository.findByName(name).orElseGet(() -> {
            Role r = new Role();
            r.setName(name);
            r.setDescription(description);
            r.setIsSystem(true);
            return roleRepository.save(r);
        });
    }

    /** Adds any missing grants. Never revokes — existing grants are the superadmin's to manage. */
    private void grantPermissions(Role role, List<String> codes, Map<String, Permission> permByCode) {
        for (String code : codes) {
            Permission permission = permByCode.get(code);
            if (permission == null) continue;
            if (!rolePermissionRepository.existsByIdRoleIdAndIdPermissionId(role.getId(), permission.getId())) {
                RolePermission rp = new RolePermission();
                rp.setId(new RolePermissionId(role.getId(), permission.getId()));
                rp.setRole(role);
                rp.setPermission(permission);
                rolePermissionRepository.save(rp);
            }
        }
    }

    /** Converts "courier:reply:create" → "Courier reply create" */
    private String humanise(String code) {
        return Arrays.stream(code.split(":"))
                .reduce((a, b) -> a + " " + b)
                .map(s -> Character.toUpperCase(s.charAt(0)) + s.substring(1))
                .orElse(code);
    }
}
