package com.platform.inventory.entity;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "products")
public class Product {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private int stock;

    protected Product() {}

    public Product(UUID id, String name, int stock) {
        this.id = id;
        this.name = name;
        this.stock = stock;
    }

    public boolean reserveStock(int quantity) {
        if (stock >= quantity) {
            stock -= quantity;
            return true;
        }
        return false;
    }

    public void releaseStock(int quantity) {
        stock += quantity;
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public int getStock() { return stock; }
}
