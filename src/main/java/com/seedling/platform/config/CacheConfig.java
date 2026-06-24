package com.seedling.platform.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Caffeine Cache Configuration for TradeWise
 *
 * Defines named caches with specific TTL (Time-To-Live) durations optimized for:
 * - Minimizing Finnhub API calls (free tier: 60 calls/minute)
 * - Reducing database queries
 * - Balancing near real-time data with resource efficiency
 *
 * Cache Strategy:
 * 1. Browser LocalStorage (Client-side) - Persistent across page reloads
 * 2. Spring Cache Caffeine (Server-side) - Fast in-memory storage
 * 3. Finnhub API (External) - Source of truth for market data
 *
 * When multiple layers exist, each layer is consulted before the next:
 * - Request hits LocalStorage first → If missing, hits Spring Cache → If missing, hits Finnhub API
 * - Both storage layers are updated on successful API response
 */
@Configuration
@EnableCaching
public class CacheConfig {

    /**
     * Configure Caffeine Cache Manager with multiple named caches
     * Each cache has optimized TTL based on data freshness requirements
     */
    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager(
                "stockQuoteCache",           // Current stock price (10 sec TTL)
                "stockChart1DCache",         // 1-day chart (30 sec TTL)
                "stockChart1WCache",         // 1-week chart (5 min TTL)
                "stockChart1MCache",         // 1-month chart (15 min TTL)
                "stockChart3MCache",         // 3-month chart (30 min TTL)
                "stockChartLongTermCache",   // 6M-5Y charts (1 hour TTL)
                "stockListCache",            // All stocks list (1 min TTL)
                "marketStatusCache",         // Market open/closed status (30 sec TTL)
                "stockChartCache"            // Generic chart cache for Finnhub responses
        );

        // Configure Caffeine builder for all caches
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(500)            // Default max entries across all caches
                .recordStats()               // Enable cache statistics for monitoring
                .expireAfterWrite(1, TimeUnit.HOURS) // Default TTL of 1 hour
        );

        return cacheManager;
    }

    /**
     * API Rate Limiting Configuration
     *
     * Finnhub Free Tier: 60 API calls / minute
     *
     * Estimated API Usage per User (during market open):
     * - Stock Quote: 10 calls/minute (1 call per 6 seconds for 10 stocks)
     * - Charts: 0-5 calls/minute (based on timeframe changes, cached heavily)
     * Total: ~15 calls/minute per active user
     *
     * With 4-5 concurrent active users: ~60-75 calls/minute
     * (Still within limits with caching protecting bursts)
     *
     * Graceful Degradation Strategy:
     * 1. If approaching rate limit → Serve cached data (even if stale by a few seconds)
     * 2. If rate limit exceeded → Return cached data with "cached_data" flag
     * 3. If no cached data → Return last known price from database
     *
     * This ensures users never see errors, just slightly older data
     *
     * Cache Durations by Endpoint:
     *
     * Quote Endpoint (Current Price):
     * - TTL: 10 seconds
     * - Polling: Client polls every 5 seconds
     * - Reasoning: Stock prices update frequently during market hours
     * - API load: 10 stocks * 1 user polling every 5 sec = 2 API calls/sec max
     * - Within limit: 120 calls/min << 3600 calls/min (60/sec available)
     *
     * 1D Chart (5-minute candles):
     * - TTL: 30 seconds
     * - Polling: Client polls every 5 seconds (first 5 requests hit cache)
     * - Resolution: 5-minute candles = ~78 data points per day
     * - Reasoning: Intraday updates need frequent refresh
     *
     * 1W Chart (30-minute candles):
     * - TTL: 5 minutes
     * - Polling: Client polls every 30 seconds
     * - Resolution: 30-minute candles = ~78 data points per week
     * - Reasoning: Weekly view less volatile than daily
     *
     * 1M Chart (Daily candles):
     * - TTL: 15 minutes
     * - Polling: Client polls every 60 seconds (first 1 request per min hits cache)
     * - Resolution: Daily candles = ~30 data points per month
     * - Reasoning: Monthly view changes slowly
     *
     * 3M Chart (Daily candles):
     * - TTL: 30 minutes
     * - Polling: Client polls every 60 seconds
     * - Reasoning: Quarterly view very stable
     *
     * 6M-5Y Charts (Weekly/Monthly candles):
     * - TTL: 1 hour
     * - Polling: Client polls every 2 minutes
     * - Resolution: Weekly or monthly = 26-60 data points
     * - Reasoning: Long-term trends don't change during single market session
     */
}



