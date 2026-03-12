package com.buyology.ecommerce.cart.domain;

import com.buyology.ecommerce.product.domain.ProductSpecOption;
import jakarta.persistence.*;
import java.util.UUID;

/**
 * Stores the spec options (e.g. RAM=16GB, Color=Silver) selected by the user
 * for a specific cart item. Supports DIY/configurable products where the customer
 * picks individual spec options before adding to cart.
 */
@Entity
@Table(name = "cart_item_spec_selections")
public class CartItemSpecSelection {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cart_item_id", nullable = false)
    private CartItem cartItem;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "spec_option_id", nullable = false)
    private ProductSpecOption specOption;

    public CartItemSpecSelection() {
    }

    public CartItemSpecSelection(CartItem cartItem, ProductSpecOption specOption) {
        this.cartItem = cartItem;
        this.specOption = specOption;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public CartItem getCartItem() { return cartItem; }
    public void setCartItem(CartItem cartItem) { this.cartItem = cartItem; }

    public ProductSpecOption getSpecOption() { return specOption; }
    public void setSpecOption(ProductSpecOption specOption) { this.specOption = specOption; }
}
