package com.buyology.ecommerce.infrastructure.config;

import com.buyology.ecommerce.order.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * One-time cleanup script to remove stale DB check constraints that Hibernate
 * refuses to update automatically.
 */
@Component
public class DatabaseConstraintCleanupInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DatabaseConstraintCleanupInitializer.class);

    private final OrderRepository orderRepository;

    public DatabaseConstraintCleanupInitializer(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Override
    public void run(String... args) {
        try {
            log.info("[DB-CLEANUP] Dropping stale delivery_method_check constraint...");
            orderRepository.dropDeliveryMethodCheckConstraint();
            log.info("[DB-CLEANUP] Constraint dropped successfully.");
        } catch (Exception e) {
            // Log as warning — it might fail if the table doesn't exist yet, which is fine
            log.warn("[DB-CLEANUP] Could not drop constraint (expected on first-time or H2 runs): {}", e.getMessage());
        }
    }
}
