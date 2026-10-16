package com.buyology.ecommerce.common.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class GlobalExceptionHandlerStatusTest {
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new ProductErrorProbe())
            .setControllerAdvice(new GlobalExceptionHandler()).build();

    @RestController
    static class ProductErrorProbe {
        @GetMapping("/api/product/by-slug")
        void missingSlug() {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found for slug: missing-device");
        }
        @GetMapping("/api/product/unavailable")
        void upstreamFailure() {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Internal host detail");
        }
    }

    @Test
    void missingSlugRemainsNotFoundInTheApiEnvelope() throws Exception {
        mvc.perform(get("/api/product/by-slug").param("slug", "missing-device").accept("application/json"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.statusCode").value(404))
                .andExpect(jsonPath("$.message").value("Product not found for slug: missing-device"));
    }

    @Test
    void intentionalUpstreamFailureIsNotMisreportedAsMissingOrLeaked() throws Exception {
        mvc.perform(get("/api/product/unavailable").accept("application/json"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.statusCode").value(503))
                .andExpect(jsonPath("$.message").value("An unexpected error occurred"));
    }
}
