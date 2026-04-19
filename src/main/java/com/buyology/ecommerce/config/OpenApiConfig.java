package com.buyology.ecommerce.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI buyologyEcommerceOpenAPI() {
        final String bearerSchemeName = "bearerAuth";

        return new OpenAPI()
                .info(new Info()
                        .title("Buyology E-Commerce API")
                        .version("1.0.0")
                        .description("""
                                REST API for the Buyology e-commerce platform.

                                **Authentication**: Most write endpoints require a Bearer JWT token obtained
                                via `POST /api/auth/signin`. Public read endpoints (products, categories, brands)
                                do not require authentication.

                                **Multi-language**: Pass `lang=EN`, `lang=AZ`, or `lang=AR` on all product/category endpoints.

                                **Country scoping**: Pass `countryCode` (ISO 3166-1 alpha-3, e.g. `UAE`, `AZE`) to scope
                                pricing to a specific country's stores.
                                """)
                        .contact(new Contact()
                                .name("Buyology Team")
                                .email("firdovsirz@gmail.com")))
                .servers(List.of(
                        new Server().url("http://localhost:8080").description("Local development"),
                        new Server().url("https://api.buyology.com").description("Production")))
                .addSecurityItem(new SecurityRequirement().addList(bearerSchemeName))
                .components(new Components()
                        .addSecuritySchemes(bearerSchemeName, new SecurityScheme()
                                .name(bearerSchemeName)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("JWT access token — obtain from POST /api/auth/signin")));
    }
}
