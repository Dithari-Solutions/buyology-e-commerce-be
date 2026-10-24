package com.buyology.ecommerce.warranty.service;

import com.buyology.ecommerce.warranty.domain.WarrantyCard;
import com.buyology.ecommerce.warranty.dto.OrderItemWarranty;
import com.buyology.ecommerce.warranty.repository.WarrantyCardRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The cards on one order, for the customer's and the admin's order pages.
 *
 * <p>Kept apart from {@link WarrantyCardService} on purpose: OrderService needs only this, and
 * depending on the issuing service would pull ProductService into OrderService's graph.
 */
@Component
public class OrderWarrantyReader {

    private final WarrantyCardRepository cards;
    private final Clock clock;

    @Autowired
    public OrderWarrantyReader(WarrantyCardRepository cards) {
        this(cards, Clock.systemUTC());
    }

    OrderWarrantyReader(WarrantyCardRepository cards, Clock clock) {
        this.cards = cards;
        this.clock = clock;
    }

    /** Order item id → its cards in unit order. One query; empty when nothing was issued. */
    public Map<UUID, List<OrderItemWarranty>> forOrder(UUID orderId) {
        LocalDate today = LocalDate.now(clock.withZone(ZoneOffset.UTC));
        return cards.findByOrderIdOrderByUnitIndexAsc(orderId).stream()
                .collect(Collectors.groupingBy(WarrantyCard::getOrderItemId,
                        Collectors.mapping(c -> WarrantyCardView.forOrderItem(c, today), Collectors.toList())));
    }
}
