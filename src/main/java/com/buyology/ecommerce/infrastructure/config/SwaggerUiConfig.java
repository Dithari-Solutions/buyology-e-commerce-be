package com.buyology.ecommerce.infrastructure.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.Contact;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
    info = @Info(
        title = "Buyology E-Commerce APIs",
        version = "1.0",
        description = "This APIs handle all backend logic and services of the e-commerce applicaition for Buyology. \n Developed and maintained by Firdovsi Rzaev (Dithari Solutions).",
        contact = @Contact(
            name = "Firdovsi Rzaev",
            email = "firdovsirz@gmail.com"
        )
    )
)
public class SwaggerUiConfig {
}