package com.seedling.platform.model;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "stocks")
public class Stock {

    @Id
    @Column(length = 10)
    private String ticker;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "current_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal currentPrice;

    @Column(name = "last_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal lastPrice;

    @Column(name = "price_change")
    private BigDecimal priceChange;

    @Column(name = "change_percent")
    private BigDecimal changePercent;


    // Constructors
    public Stock() {}

    public Stock(String ticker, String name, BigDecimal currentPrice, BigDecimal lastPrice) {
        this.ticker = ticker;
        this.name = name;
        this.currentPrice = currentPrice;
        this.lastPrice = lastPrice;
    }

    // Getters and Setters
    public String getTicker() {
        return ticker;
    }

    public void setTicker(String ticker) {
        this.ticker = ticker;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public BigDecimal getCurrentPrice() {
        return currentPrice;
    }

    public void setCurrentPrice(BigDecimal currentPrice) {
        this.currentPrice = currentPrice;
    }

    public BigDecimal getLastPrice() {
        return lastPrice;
    }

    public void setLastPrice(BigDecimal lastPrice) {
        this.lastPrice = lastPrice;
    }
    public BigDecimal getPriceChange() {
        return priceChange;
    }

    public void setPriceChange(BigDecimal priceChange) {
        this.priceChange = priceChange;
    }

    public BigDecimal getChangePercent() {
        return changePercent;
    }

    public void setChangePercent(BigDecimal changePercent) {
        this.changePercent = changePercent;
    }
}
