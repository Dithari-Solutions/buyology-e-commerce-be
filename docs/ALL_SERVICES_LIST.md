# E-Commerce Backend Services List

This document provides a comprehensive list of all services implemented across all modules in the Buyology E-Commerce Backend project.

---

## Services by Module

### AUTH Module (`com.buyology.ecommerce.auth`)
- **AuthService**: Orchestrates authentication flows (signup, signin, OTP verification, password reset)
- **TokenService**: Handles JWT token creation and validation
- **GoogleOAuthService**: Manages OAuth2 integration with Google

### PRODUCT Module (`com.buyology.ecommerce.product`)
- **ProductService**: Core product CRUD operations, variant management, advanced filtering
- **ProductCategoryService**: Manages category hierarchy with translation support
- **BrandService**: Handles brand operations
- **GlobalSpecService**: Manages global specification options

### STORE Module (`com.buyology.ecommerce.store`)
- **StoreService**: Store CRUD operations and management
- **StoreLocationService**: Location-based store operations
- **StoreAdminService**: Admin assignment to stores
- **StoreOperatingHoursService**: Business hours management
- **CountryService**: Country data operations

### CART Module (`com.buyology.ecommerce.cart`)
- **CartService**: Cart operations (retrieve, add item, remove, update quantity)

### PAYMENT Module (`com.buyology.ecommerce.payment`)
- **PaymentService**: Payment initiation, refunds, webhook processing
- **PaymobClient**: HTTP client for Paymob API integration

### REVIEW Module (`com.buyology.ecommerce.review`)
- **ReviewService**: Review CRUD and moderation
- **ReviewAdminService**: Admin review management
- **QuestionService**: Product Q&A operations
- **QuestionAdminService**: Question management
- **ReviewContentFilter**: Content moderation

### USER Module (`com.buyology.ecommerce.user`)
- **UserProfileService**: Profile CRUD and avatar uploads
- **UserAddressService**: Address management and validation
- **GeocodingService**: Google Maps integration
- **SmsService**: Twilio SMS OTP delivery

### FAVORITE Module (`com.buyology.ecommerce.favorite`)
- **FavoriteService**: Favorite operations

### STORY Module (`com.buyology.ecommerce.story`)
- **StoryService**: Story creation, publication, and management

### ROLE & PERMISSION Module (`com.buyology.ecommerce.role`)
- **RoleService**: Role management operations
- **PermissionService**: Permission management
- **UserRoleService**: User role assignments
- **UserPermissionService**: Direct permission assignments

### ADMIN Module (`com.buyology.ecommerce.admin`)
- **AdminUserService**: Admin user operations, analytics

### COURIER Module (`com.buyology.ecommerce.courier`)
- **CourierServiceClient**: HTTP client for courier service
- **CourierServiceTokenProvider**: JWT token generation
- **KeycloakTokenProvider**: Keycloak OAuth integration

### COMMON Module (`com.buyology.ecommerce.common`)
- **EmailService**: SendGrid email sending with OTP templates

### INFRASTRUCTURE Module (`com.buyology.ecommerce.infrastructure`)
- **SecurityConfig**: Spring Security configuration, filter chains
- **JwtAuthenticationFilter**: JWT validation and authentication
- **RateLimitingFilter**: API rate limiting
- **TwilioSendGridProperties**: SendGrid configuration
- **OtpProperties**: OTP configuration (expiry, attempts)
- **RestTemplateConfig**: HTTP client configuration
- **ObjectMapperConfig**: JSON serialization setup
- **StoryResourceConfig**: Static file serving
- **SwaggerUiConfig**: OpenAPI documentation

---

## Service Categories

### Business Logic Services
- AuthService, TokenService, GoogleOAuthService
- ProductService, ProductCategoryService, BrandService, GlobalSpecService
- StoreService, StoreLocationService, StoreAdminService, StoreOperatingHoursService, CountryService
- CartService
- PaymentService, PaymobClient
- ReviewService, ReviewAdminService, QuestionService, QuestionAdminService, ReviewContentFilter
- UserProfileService, UserAddressService, GeocodingService, SmsService
- FavoriteService
- StoryService
- RoleService, PermissionService, UserRoleService, UserPermissionService
- AdminUserService
- CourierServiceClient, CourierServiceTokenProvider, KeycloakTokenProvider
- EmailService

### Infrastructure Services
- SecurityConfig, JwtAuthenticationFilter, RateLimitingFilter
- TwilioSendGridProperties, OtpProperties
- RestTemplateConfig, ObjectMapperConfig
- StoryResourceConfig, SwaggerUiConfig

---

## Total Services Count
- **Business Logic Services**: 35
- **Infrastructure Services**: 9
- **Total Services**: 44

---

## Service Naming Convention
All services follow Spring Boot naming conventions:
- Service classes end with `Service` suffix
- Client classes end with `Client` suffix
- Provider classes end with `Provider` suffix
- Configuration classes use descriptive names
- Filter classes end with `Filter` suffix
- Properties classes end with `Properties` suffix

---

*This list provides a complete reference of all services in the Buyology E-Commerce Backend application.*