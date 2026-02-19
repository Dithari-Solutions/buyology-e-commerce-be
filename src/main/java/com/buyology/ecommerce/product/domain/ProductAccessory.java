package com.buyology.ecommerce.product.domain;

import java.util.UUID;
import jakarta.persistence.*;

@Entity
@Table(name = "product_accessories", uniqueConstraints = {
        @UniqueConstraint(columnNames = { "product_id", "accessory_id" })
})
public class ProductAccessory {

    @Id
    @GeneratedValue
    private UUID id;

    // The main product
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    // The accessory product
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "accessory_id", nullable = false)
    private Product accessory;

    // Constructors
    public ProductAccessory() {
    }

    public ProductAccessory(Product product, Product accessory) {
        this.product = product;
        this.accessory = accessory;
    }

    // Getters & Setters
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Product getProduct() {
        return product;
    }

    public void setProduct(Product product) {
        this.product = product;
    }

    public Product getAccessory() {
        return accessory;
    }

    public void setAccessory(Product accessory) {
        this.accessory = accessory;
    }
}
