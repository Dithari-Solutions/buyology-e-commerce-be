package com.buyology.ecommerce.infrastructure.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;

@Configuration
public class StoryResourceConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/story/**")
                .addResourceLocations("file:/opt/uploads/story/");
        registry.addResourceHandler("/product/**")
                .addResourceLocations("file:/opt/uploads/product/");
        registry.addResourceHandler("/review/**")
                .addResourceLocations("file:/opt/uploads/review/");
    }
}
