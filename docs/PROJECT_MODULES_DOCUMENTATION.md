# E-Commerce Backend Project Modules and Responsibilities

## Overview

This document provides a comprehensive overview of all modules in the Buyology E-Commerce Backend project, detailing what each module does, the jobs it handles, and key components involved.

**Project Framework**: Spring Boot 4.0.2 (Java 17)  
**Build Tool**: Maven  
**Architecture**: Multi-module Spring Boot application with layered architecture (Controllers → Services → Repositories → Entities)

---

## Core Modules

### 1. AUTH Module
**Package**: `com.buyology.ecommerce.auth`

**Jobs and Responsibilities**:
- User authentication and authorization management
- Support for email/password and OAuth2 (Google) login
- JWT token generation, validation, and management
- Email OTP verification for user signup
- Password reset and forgot password workflows
- Refresh token management using HttpOnly cookies
- Multi-provider authentication support

**Key Classes**:
- `AuthService`: Orchestrates authentication flows (signup, signin, OTP verification, password reset)
- `TokenService`: Handles JWT token creation and validation
- `GoogleOAuthService`: Manages OAuth2 integration with Google
- `AuthCredentials` (domain): Stores user authentication credentials with provider information
- `EmailOtp` (domain): Manages OTP storage and expiry tracking

---

### 2. PRODUCT Module
**Package**: `com.buyology.ecommerce.product`

**Jobs and Responsibilities**:
- Complete product catalog management with multi-language support (Azerbaijani, English, Arabic)
- Hierarchical product category management (root and subcategories)
- Product variant management (color, size, etc.) with individual pricing
- Product specifications (global and product-specific)
- Brand management and association
- Product media management (images and videos)
- Product accessories management
- Quick delivery configuration for products

**Key Classes**:
- `ProductService`: Core product CRUD operations, variant management, advanced filtering
- `ProductCategoryService`: Manages category hierarchy with translation support
- `BrandService`: Handles brand operations
- `GlobalSpecService`: Manages global specification options
- `Product` (domain): Main product entity with category, brand, SKU, and availability status
- `ProductVariant` (domain): Product variants with pricing and SKU
- `ProductSpecification` (domain): Product-specific specifications
- `ProductTranslation` (domain): Multi-language product data storage

---

### 3. STORE Module
**Package**: `com.buyology.ecommerce.store`

**Jobs and Responsibilities**:
- Physical store and location management
- Store information and multi-language translations
- Store operating hours configuration
- Geographic store locations with coordinates
- Country management for store locations
- Store administrator assignment
- Store-product relationship management

**Key Classes**:
- `StoreService`: Store CRUD operations and management
- `StoreLocationService`: Location-based store operations
- `StoreAdminService`: Admin assignment to stores
- `StoreOperatingHoursService`: Business hours management
- `CountryService`: Country data operations
- `Store` (domain): Main store entity
- `StoreLocation` (domain): Geographic locations and coordinates
- `StoreProduct` (domain): Product-store associations

---

### 4. CART Module
**Package**: `com.buyology.ecommerce.cart`

**Jobs and Responsibilities**:
- Shopping cart management for authenticated users
- Add/remove items from cart functionality
- Cart item specification selection (variants, specs)
- Persistent cart storage per user
- Price calculations for cart items

**Key Classes**:
- `CartService`: Cart operations (retrieve, add item, remove, update quantity)
- `Cart` (domain): Active cart per user
- `CartItem` (domain): Individual cart items with quantity
- `CartItemSpecSelection` (domain): User's selected specifications for cart items

---

### 5. PAYMENT Module
**Package**: `com.buyology.ecommerce.payment`

**Jobs and Responsibilities**:
- Payment processing integration with Paymob provider
- Multiple payment method configurations
- Payment transaction tracking and order management
- Refund processing and management
- Webhook event handling for payment status updates
- Payment provider order coordination

**Key Classes**:
- `PaymentService`: Payment initiation, refunds, webhook processing
- `PaymobClient`: HTTP client for Paymob API integration
- `PaymentProvider` (domain): Payment gateway configuration
- `PaymentMethodConfig` (domain): Payment method types (card, wallet, etc.)
- `PaymentTransaction` (domain): Transaction records
- `PaymentProviderOrder` (domain): Provider-side order tracking
- `PaymentRefund` (domain): Refund tracking and status

---

### 6. REVIEW Module
**Package**: `com.buyology.ecommerce.review`

**Jobs and Responsibilities**:
- Product reviews and ratings management
- Review moderation (approval/rejection workflow)
- Review media attachments (images/videos)
- Product questions and answers functionality
- Review voting system (helpful/unhelpful)
- Content filtering for inappropriate text
- Review analytics and statistics

**Key Classes**:
- `ReviewService`: Review CRUD and moderation
- `ReviewAdminService`: Admin review management
- `QuestionService`: Product Q&A operations
- `QuestionAdminService`: Question management
- `ReviewContentFilter`: Content moderation
- `ProductReview` (domain): Review entity with rating and content
- `ProductReviewStats` (domain): Aggregated review metrics
- `ProductQuestion` (domain): Q&A entries
- `ProductReviewMedia` (domain): Review attachments

---

### 7. USER Module
**Package**: `com.buyology.ecommerce.user`

**Jobs and Responsibilities**:
- User profile management (name, phone, avatar)
- User address management (delivery/billing addresses)
- Address validation using Google Maps Geocoding API
- SMS OTP sending via Twilio integration
- User account status tracking

**Key Classes**:
- `UserProfileService`: Profile CRUD and avatar uploads
- `UserAddressService`: Address management and validation
- `GeocodingService`: Google Maps integration
- `SmsService`: Twilio SMS OTP delivery
- `Users` (domain): Core user entity (CUSTOMER/ADMIN roles)
- `UserProfiles` (domain): Extended profile data
- `UserAddress` (domain): Address storage with coordinates

---

### 8. FAVORITE Module
**Package**: `com.buyology.ecommerce.favorite`

**Jobs and Responsibilities**:
- Wishlist/favorites functionality
- Add/remove products from user favorites
- Retrieve user's favorite products list
- Admin analytics for favorites

**Key Classes**:
- `FavoriteService`: Favorite operations
- `Favorite` (domain): User-product favorite relationship

---

### 9. STORY Module
**Package**: `com.buyology.ecommerce.story`

**Jobs and Responsibilities**:
- Story/media content management (promotional content)
- Multi-language story translations
- Story media files (thumbnails and multiple media items)
- Story status management (DRAFT, PUBLISHED, ARCHIVED)

**Key Classes**:
- `StoryService`: Story creation, publication, and management
- `Story` (domain): Story entity with metadata
- `StoryTranslation` (domain): Multi-language story content
- `StoryMedia` (domain): Attached media files

---

### 10. ROLE & PERMISSION Module
**Package**: `com.buyology.ecommerce.role`

**Jobs and Responsibilities**:
- Role-Based Access Control (RBAC) implementation
- Permission definition and assignment
- Role-permission relationship management
- User-role assignment functionality
- Direct user-permission assignments

**Key Classes**:
- `RoleService`: Role management operations
- `PermissionService`: Permission management
- `UserRoleService`: User role assignments
- `UserPermissionService`: Direct permission assignments
- `Role` (domain): Role entity
- `Permission` (domain): Permission entity
- `RolePermission` (domain): Role-permission mappings
- `UserPermission` (domain): User-permission mappings

---

### 11. ADMIN Module
**Package**: `com.buyology.ecommerce.admin`

**Jobs and Responsibilities**:
- Admin user management and oversight
- User activity monitoring (cart, favorites, profiles)
- Admin-specific operations and analytics

**Key Classes**:
- `AdminUserService`: Admin user operations, analytics

---

### 12. COURIER Module
**Package**: `com.buyology.ecommerce.courier`

**Jobs and Responsibilities**:
- Integration with external courier service
- JWT token provider for courier service authentication
- Multipart request forwarding to courier service
- Exception handling for courier operations

**Key Classes**:
- `CourierServiceClient`: HTTP client for courier service
- `CourierServiceTokenProvider`: JWT token generation
- `KeycloakTokenProvider`: Keycloak OAuth integration
- `CourierServiceException`: Custom exception handling

---

### 13. NOTIFICATION Module
**Package**: `com.buyology.ecommerce.notification`

**Status**: Module exists but no implementations yet

---

### 14. RENTAL Module
**Package**: `com.buyology.ecommerce.rental`

**Status**: Module exists but no implementations yet

---

### 15. COMMON Module
**Package**: `com.buyology.ecommerce.common`

**Jobs and Responsibilities**:
- Centralized utilities and constants
- Email service integration (Twilio SendGrid)
- Global exception handling
- API response wrappers
- Security utilities
- Enum definitions and constants

**Key Components**:
- `EmailService`: SendGrid email sending with OTP templates
- `ApiResponse`: Generic API response wrapper
- Exception classes and global handlers
- Constants and enums (Language, SpecUnit, etc.)
- Utility classes (EmailValidation, PasswordUtils, SlugUtils)

---

### 16. INFRASTRUCTURE Module
**Package**: `com.buyology.ecommerce.infrastructure`

**Jobs and Responsibilities**:
- Spring Security configuration and setup
- JWT authentication filter implementation
- API rate limiting filter
- External API client configurations
- Swagger/OpenAPI documentation setup
- Property management for external services

**Key Components**:
- `SecurityConfig`: Spring Security configuration, filter chains
- `JwtAuthenticationFilter`: JWT validation and authentication
- `RateLimitingFilter`: API rate limiting
- `TwilioSendGridProperties`: SendGrid configuration
- `OtpProperties`: OTP configuration (expiry, attempts)
- `RestTemplateConfig`: HTTP client configuration
- `ObjectMapperConfig`: JSON serialization setup
- `StoryResourceConfig`: Static file serving
- `SwaggerUiConfig`: OpenAPI documentation

---

### 17. SERVICE Module
**Package**: `com.buyology.ecommerce.service`

**Status**: Currently empty, reserved for future shared services

---

## Architecture Patterns

### Layered Architecture
- **Controllers**: REST API endpoints
- **Services**: Business logic layer
- **Repositories**: Data access layer (Spring Data JPA)
- **Entities**: Domain models

### Key Design Patterns
- **DTO Pattern**: Data Transfer Objects for API contracts
- **Repository Pattern**: Data access abstraction
- **Service Layer Pattern**: Business logic encapsulation
- **Domain-Driven Design**: Entities represent business concepts

---

## Key Features

### Multi-Tenancy & Localization
- Multi-language support (AZ, EN, AR)
- Translation entities for products, stores, stories
- Store-specific product assignments

### Security Features
- JWT-based authentication
- OAuth2 integration (Google)
- Role-Based Access Control (RBAC)
- HttpOnly refresh token cookies
- API rate limiting

### External Integrations
- **Paymob**: Payment processing
- **Twilio**: SMS OTP delivery
- **SendGrid**: Email services
- **Google Maps**: Address geocoding
- **Courier Service**: Shipping (custom/Keycloak auth)

---

## Database Relationships

- **Users** → AuthCredentials (multiple auth methods)
- **Users** → UserProfiles, UserAddresses
- **Products** → ProductCategory, Brand, Variants, Specifications
- **Cart** → CartItems → CartItemSpecSelections
- **Store** → StoreProducts, StoreLocations, OperatingHours
- **ProductReview** → ReviewMedia, ReviewReplies, ReviewVotes
- **Favorite** → Product associations

---

## Technologies & Dependencies

- **Spring Boot 4.0.2**: Web, Data JPA, Security, Mail
- **Spring Security**: OAuth2 Client support
- **Spring Data JPA**: Hibernate ORM
- **JWT**: Token management
- **RestTemplate**: HTTP client operations
- **Twilio/SendGrid/Google Maps APIs**: External integrations
- **SpringDoc OpenAPI**: API documentation
- **File Upload Support**: Product images, user avatars, stories

---

*This documentation provides a complete overview of the Buyology E-Commerce Backend's modular architecture, responsibilities, and key components.*