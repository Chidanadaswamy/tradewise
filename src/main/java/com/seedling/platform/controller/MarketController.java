package com.seedling.platform.controller;

import com.seedling.platform.dto.StockCandleResponse;
import com.seedling.platform.model.Stock;
import com.seedling.platform.model.StockHistory;
import com.seedling.platform.service.MarketService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.seedling.platform.dto.StockSearchDto;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Market API Controller
 *
 * Endpoints for accessing stock data, prices, and candlestick charts
 * All responses include appropriate HTTP cache control headers
 */
@RestController
@RequestMapping("/api/market")
public class MarketController {

    private static final Logger logger = LoggerFactory.getLogger(MarketController.class);

    @Autowired
    private MarketService marketService;

    /**
     * Get all available stocks
     * Cached for 60 seconds on client side
     */
    @GetMapping("/stocks")
    public ResponseEntity<List<Stock>> getStocks() {
        logger.debug("Fetching all stocks");
        List<Stock> stocks = marketService.getAllStocks();
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(60, TimeUnit.SECONDS).cachePublic())
                .body(stocks);
    }

    /**
     * Get single stock details by ticker
     * Cached for 30 seconds on client side
     */
    @GetMapping("/stocks/{ticker}")
    public ResponseEntity<?> getStockDetails(@PathVariable String ticker) {
        logger.debug("Fetching details for {}", ticker);
        Optional<Stock> stockOpt = marketService.getStockByTicker(ticker.toUpperCase());
        if (stockOpt.isEmpty()) {
            logger.warn("Stock not found: {}", ticker);
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(30, TimeUnit.SECONDS).cachePublic())
                .body(stockOpt.get());
    }

    /**
     * Get historical stock prices from database
     * Cached for 5 minutes (historical data changes infrequently)
     */
    @GetMapping("/stocks/{ticker}/history")
    public ResponseEntity<List<StockHistory>> getStockHistory(@PathVariable String ticker) {
        logger.debug("Fetching history for {}", ticker);
        List<StockHistory> history = marketService.getStockHistory(ticker.toUpperCase());
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(5, TimeUnit.MINUTES).cachePublic())
                .body(history);
    }

    /**
     * Manually refresh stock price from Finnhub
     * No cache - always fetches fresh data
     */
    @PostMapping("/stocks/{ticker}/refresh")
    public ResponseEntity<?> refreshStockPrice(@PathVariable String ticker) {
        logger.debug("Manual refresh for {}", ticker);
        try {
            Stock refreshed = marketService.refreshStockPrice(ticker.toUpperCase());
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.noCache())
                    .body(refreshed);
        } catch (Exception e) {
            logger.error("Error refreshing stock price: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to refresh stock price: " + e.getMessage());
        }
    }

    /**
     * Get candlestick chart data for a stock
     *
     * Query Parameters:
     * - timeframe: 1D, 1W, 1M, 3M, 6M, 1Y, 3Y, 5Y (default: 1D)
     *
     * Cache durations per timeframe:
     * - 1D: 30 seconds
     * - 1W: 5 minutes
     * - 1M: 15 minutes
     * - 3M: 30 minutes
     * - 6M-5Y: 1 hour
     */
    @GetMapping("/stocks/{ticker}/candles")
    public ResponseEntity<?> getStockCandles(
            @PathVariable String ticker,
            @RequestParam(defaultValue = "1D") String timeframe) {

        logger.debug("Fetching candles for {} [{}]", ticker, timeframe);

        try {
            StockCandleResponse response = marketService.getStockCandles(ticker.toUpperCase(), timeframe);

            // Validate response
            if (response == null) {
                logger.warn("No response for candles: {} [{}]", ticker, timeframe);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body("No chart data available");
            }

            // Set appropriate cache control based on timeframe
            CacheControl cacheControl = getCacheControl(timeframe);

            return ResponseEntity.ok()
                    .cacheControl(cacheControl)
                    .body(response);

        } catch (Exception e) {
            logger.error("Error fetching candles for {} [{}]: {}", ticker, timeframe, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error fetching chart data: " + e.getMessage());
        }
    }

    /**
     * Get market status (open/closed/pre-market/post-market)
     * Cached for 30 seconds
     */
    @GetMapping("/status")
    public ResponseEntity<?> getMarketStatus() {
        logger.debug("Fetching market status");
        try {
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.maxAge(30, TimeUnit.SECONDS).cachePublic())
                    .body(marketService.getMarketStatus());
        } catch (Exception e) {
            logger.error("Error fetching market status: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error fetching market status");
        }

    }

    @GetMapping("/search")
    public ResponseEntity<List<StockSearchDto>> searchStocks(
            @RequestParam String q) {

        logger.debug(
                "Searching stocks: {}",
                q
        );

        return ResponseEntity.ok(
                marketService.searchStocks(q)
        );
    }


    // ───────────────────── Helper Methods ─────────────────────

    /**
     * Determine cache control duration based on timeframe
     */
    private CacheControl getCacheControl(String timeframe) {
        return switch (timeframe.toUpperCase()) {
            case "1D" -> CacheControl.maxAge(30, TimeUnit.SECONDS).cachePublic();
            case "1W" -> CacheControl.maxAge(5, TimeUnit.MINUTES).cachePublic();
            case "1M" -> CacheControl.maxAge(15, TimeUnit.MINUTES).cachePublic();
            case "3M" -> CacheControl.maxAge(30, TimeUnit.MINUTES).cachePublic();
            case "6M", "1Y", "3Y", "5Y" -> CacheControl.maxAge(1, TimeUnit.HOURS).cachePublic();
            default -> CacheControl.maxAge(30, TimeUnit.SECONDS).cachePublic();
        };
    }
}

