package com.seedling.platform.dto;

import java.math.BigDecimal;
import java.util.List;

public class FinnhubCandleResponse {
    private List<BigDecimal> c; // Close prices
    private List<BigDecimal> h; // High prices
    private List<BigDecimal> l; // Low prices
    private List<BigDecimal> o; // Open prices
    private String s;           // Status
    private List<Long> t;       // Timestamps
    private List<Long> v;       // Volume

    public List<BigDecimal> getC() {
        return c;
    }

    public void setC(List<BigDecimal> c) {
        this.c = c;
    }

    public List<BigDecimal> getH() {
        return h;
    }

    public void setH(List<BigDecimal> h) {
        this.h = h;
    }

    public List<BigDecimal> getL() {
        return l;
    }

    public void setL(List<BigDecimal> l) {
        this.l = l;
    }

    public List<BigDecimal> getO() {
        return o;
    }

    public void setO(List<BigDecimal> o) {
        this.o = o;
    }

    public String getS() {
        return s;
    }

    public void setS(String s) {
        this.s = s;
    }

    public List<Long> getT() {
        return t;
    }

    public void setT(List<Long> t) {
        this.t = t;
    }

    public List<Long> getV() {
        return v;
    }

    public void setV(List<Long> v) {
        this.v = v;
    }
}
