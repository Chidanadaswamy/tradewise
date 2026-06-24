package com.seedling.platform.dto;

import java.math.BigDecimal;

public class FinnhubQuoteResponse {

    private BigDecimal c;
    private BigDecimal d;
    private BigDecimal dp;

    public BigDecimal getC() {
        return c;
    }

    public void setC(BigDecimal c) {
        this.c = c;
    }

    public BigDecimal getD() {
        return d;
    }

    public void setD(BigDecimal d) {
        this.d = d;
    }

    public BigDecimal getDp() {
        return dp;
    }

    public void setDp(BigDecimal dp) {
        this.dp = dp;
    }
}