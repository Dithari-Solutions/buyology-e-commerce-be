package com.buyology.ecommerce.infrastructure.filter;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class CatalogueCacheConcurrencyTest {
    @Test void concurrentRequestsComputeOnePublicResponse() throws Exception {
        CatalogueCacheFilter filter = new CatalogueCacheFilter();
        AtomicInteger calls = new AtomicInteger();
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        FilterChain chain = (request, response) -> {
            calls.incrementAndGet(); entered.countDown();
            try { assertTrue(release.await(5, TimeUnit.SECONDS)); } catch (InterruptedException e) { throw new RuntimeException(e); }
            response.setContentType("application/json"); response.getWriter().write("{\"data\":[]}");
        };
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Callable<MockHttpServletResponse> load = () -> {
                var request = new MockHttpServletRequest("GET", "/api/product");
                request.setQueryString("lang=EN&countryCode=UAE");
                var response = new MockHttpServletResponse();
                filter.doFilter(request, response, chain); return response;
            };
            Future<MockHttpServletResponse> first = executor.submit(load);
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            Future<MockHttpServletResponse> second = executor.submit(load);
            release.countDown();
            assertEquals(first.get(5, TimeUnit.SECONDS).getContentAsString(), second.get(5, TimeUnit.SECONDS).getContentAsString());
            assertEquals(1, calls.get());
        } finally { executor.shutdownNow(); }
    }
}
