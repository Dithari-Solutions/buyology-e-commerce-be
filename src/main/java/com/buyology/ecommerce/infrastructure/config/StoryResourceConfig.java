package com.buyology.ecommerce.infrastructure.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class StoryResourceConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Maps /story/** to files under /opt/uploads/story
        registry.addResourceHandler("/story/**")
                .addResourceLocations("file:/opt/uploads/story/");
    }
}
