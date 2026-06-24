package com.seedling.platform.model;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "positions")
public class Position {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 10)
    private String ticker;

    @Column(nullable = false, precision = 12, scale = 4)
    private BigDecimal quantity;

    @Column(name = "average_buy_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal averageBuyPrice;

    @Column(name = "stop_loss_price", precision = 12, scale = 2)
    private BigDecimal stopLossPrice;

    // Constructors
    public Position() {}

    public Position(User user, String ticker, BigDecimal quantity, BigDecimal averageBuyPrice, BigDecimal stopLossPrice) {
        this.user = user;
        this.ticker = ticker;
        this.quantity = quantity;
        this.averageBuyPrice = averageBuyPrice;
        this.stopLossPrice = stopLossPrice;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public String getTicker() {
        return ticker;
    }

    public void setTicker(String ticker) {
        this.ticker = ticker;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getAverageBuyPrice() {
        return averageBuyPrice;
    }

    public void setAverageBuyPrice(BigDecimal averageBuyPrice) {
        this.averageBuyPrice = averageBuyPrice;
    }

    public BigDecimal getStopLossPrice() {
        return stopLossPrice;
    }

    public void setStopLossPrice(BigDecimal stopLossPrice) {
        this.stopLossPrice = stopLossPrice;
    }
}
