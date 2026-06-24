package com.seedling.platform.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Response DTO for stock candlestick chart data
 *
 * Used by frontend to render professional OHLCV charts
 * Supports both simple (close prices only) and advanced (full OHLC) formats
 *
 * Fields:
 * - prices: Close prices for simple chart rendering (backward compatible)
 * - timestamps: Unix epoch seconds for each candle
 * - ohlc: List of candles with Open, High, Low, Close, Volume (advanced)
 * - volumes: Volume data for volume bars (professional charts)
 * - status: "ok", "no_data", or "error"
 * - lastUpdated: Unix seconds when data was last fetched from Finnhub
 * - session: Market session type (REGULAR, PRE_MARKET, POST_MARKET, DATABASE_FALLBACK)
 */
public class StockCandleResponse {
    private List<BigDecimal> prices;              // Close prices (backward compatible)
    private List<Long> timestamps;                // Epoch seconds
    private String status;                        // "ok", "no_data", "error"
    private List<Map<String, Object>> ohlc;       // OHLCV candle data
    private List<Long> volumes;                   // Volume data
    private Long lastUpdated;                     // Unix seconds
    private String session;                       // Market session type

    public List<BigDecimal> getPrices() {
        return prices;
    }

    public void setPrices(List<BigDecimal> prices) {
        this.prices = prices;
    }

    public List<Long> getTimestamps() {
        return timestamps;
    }

    public void setTimestamps(List<Long> timestamps) {
        this.timestamps = timestamps;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public List<Map<String, Object>> getOhlc() {
        return ohlc;
    }

    public void setOhlc(List<Map<String, Object>> ohlc) {
        this.ohlc = ohlc;
    }

    public List<Long> getVolumes() {
        return volumes;
    }

    public void setVolumes(List<Long> volumes) {
        this.volumes = volumes;
    }

    public Long getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(Long lastUpdated) {
        this.lastUpdated = lastUpdated;
    }

    public String getSession() {
        return session;
    }

    public void setSession(String session) {
        this.session = session;
    }
}

