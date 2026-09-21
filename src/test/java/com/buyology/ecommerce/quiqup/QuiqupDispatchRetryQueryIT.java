package com.buyology.ecommerce.quiqup;

import com.buyology.ecommerce.order.domain.Order;
import com.buyology.ecommerce.order.domain.enums.DeliveryMethod;
import com.buyology.ecommerce.order.domain.enums.OrderStatus;
import com.buyology.ecommerce.order.repository.OrderRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pins the retry job's worklist against real Postgres.
 *
 * <p>The service decides when retries stop; this query is what makes the decision stick. An order
 * left in the list is sent again every five minutes from each replica, which is how a 422 Quiqup
 * would never accept reached them about 425 times in a day.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Testcontainers
class QuiqupDispatchRetryQueryIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private EntityManager em;

    private UUID order(Consumer<Order> customise) {
        Order o = new Order();
        o.setUserId(UUID.randomUUID());
        o.setAuthCredentialId(UUID.randomUUID());
        o.setCartId(UUID.randomUUID());
        o.setStatus(OrderStatus.PAID);
        o.setDeliveryMethod(DeliveryMethod.REGULAR);
        o.setSubtotal(new BigDecimal("105.50"));
        o.setTotalAmount(new BigDecimal("105.50"));
        o.setCurrency("AED");
        customise.accept(o);
        em.persist(o);
        em.flush();
        em.clear();
        return o.getId();
    }

    private List<UUID> worklist() {
        Instant now = Instant.now();
        return orderRepository.findUndispatchedQuiqupOrders(
                        now.plusSeconds(60), now.minusSeconds(3600), PageRequest.of(0, 25))
                .stream().map(Order::getId).toList();
    }

    @Test
    void anOrderWhoseRetriesStoppedIsNotSentAgain() {
        UUID failing = order(o -> o.setQuiqupDispatchAttempts(2));
        UUID stopped = order(o -> {
            o.setQuiqupDispatchAttempts(1);
            o.setQuiqupDispatchStoppedAt(Instant.now());
        });

        List<UUID> worklist = worklist();

        assertTrue(worklist.contains(failing), "still has attempts left; the retry job owns it");
        assertFalse(worklist.contains(stopped), "stopped means an admin decides, not the retry job");
    }

    @Test
    void anOrderStuckBeforeTheCounterExistedGetsItsRetry() {
        // Rows from before V56 have NULL in both columns. They must read as "never tried, not
        // stopped", or the order stuck on the 422 would never go out with the corrected payload.
        UUID legacy = order(o -> o.setQuiqupDispatchAttempts(null));

        assertTrue(worklist().contains(legacy));
    }

    @Test
    void theCountersSurviveARoundTrip() {
        Instant stoppedAt = Instant.parse("2026-09-21T08:00:00Z");
        UUID id = order(o -> {
            o.setQuiqupDispatchAttempts(5);
            o.setQuiqupDispatchStoppedAt(stoppedAt);
        });

        Order reloaded = orderRepository.findById(id).orElseThrow();
        assertEquals(5, reloaded.getQuiqupDispatchAttempts());
        assertEquals(stoppedAt, reloaded.getQuiqupDispatchStoppedAt());
    }
}
