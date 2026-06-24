package com.seedling.platform.service;

import com.seedling.platform.dto.FinnhubQuoteResponse;
import com.seedling.platform.dto.FinnhubCandleResponse;
import com.seedling.platform.dto.StockSearchDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import java.util.List;
import com.seedling.platform.dto.StockSearchDto;
import com.seedling.platform.dto.FinnhubSearchResponse;

import java.util.List;

/**
 * Service for interfacing with Finnhub API
 *
 * Key Features:
 * - Fetches real-time stock quotes (current price, daily change)
 * - Fetches candlestick OHLCV data for chart visualization
 * - Implements caching via Spring Cache + Caffeine (production-grade)
 * - Graceful error handling with null returns on API failures
 * - Logging for debugging API issues and rate limits
 *
 * API Rate Limiting:
 * - Finnhub Free Tier: 60 calls/minute
 * - Caching strategy ensures we hit API only when cache expires
 * - See CacheConfig.java for per-endpoint TTL configurations
 */
@Service
public class FinnhubService {

    private static final Logger logger = LoggerFactory.getLogger(FinnhubService.class);

    @Value("${finnhub.api.key}")
    private String apiKey;

    @Value("${finnhub.base.url}")
    private String baseUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * Fetch current stock quote from Finnhub API
     * <p>
     * Cached for 10 seconds to allow near real-time updates while respecting API limits
     * Returns null on API failure - caller should fallback to cached data or database
     *
     * @param symbol Stock ticker (e.g., "AAPL")
     * @return FinnhubQuoteResponse with price and daily change, or null on error
     */
    @Cacheable(value = "stockQuoteCache", key = "#symbol")
    public FinnhubQuoteResponse getQuote(String symbol) {
        try {
            String url = String.format(
                    "%s/quote?symbol=%s&token=%s",
                    baseUrl, symbol, apiKey
            );

            logger.debug("Fetching quote for {}: {}", symbol, url);
            FinnhubQuoteResponse response = restTemplate.getForObject(url, FinnhubQuoteResponse.class);
            logger.debug("Quote response for {}: {}", symbol, response);
            return response;

        } catch (RestClientException e) {
            logger.warn("Failed to fetch quote for {}: {}", symbol, e.getMessage());
            return null;
        } catch (Exception e) {
            logger.error("Unexpected error fetching quote for {}: {}", symbol, e.getMessage(), e);
            return null;
        }
    }

    /**
     * Fetch candlestick OHLCV data from Finnhub API
     * <p>
     * Cached with per-timeframe duration (30sec for 1D, 5min for 1W, etc.)
     * Returns null on API failure - caller should fallback to cached data or database
     *
     * @param symbol     Stock ticker (e.g., "AAPL")
     * @param resolution Candle interval: "1", "5", "15", "30", "60", "D", "W", "M"
     * @param from       Unix timestamp for start of range
     * @param to         Unix timestamp for end of range
     * @return FinnhubCandleResponse with OHLCV data, or null on error
     */
    @Cacheable(
            value = "stockChartCache",
            key = "#symbol + ':' + #resolution + ':' + #from + ':' + #to"
    )
    public FinnhubCandleResponse getCandles(String symbol, String resolution, long from, long to) {
        try {
            String url = String.format(
                    "%s/stock/candle?symbol=%s&resolution=%s&from=%d&to=%d&token=%s",
                    baseUrl, symbol, resolution, from, to, apiKey
            );

            logger.debug("Fetching candles for {} [{}]: {}", symbol, resolution, url);
            FinnhubCandleResponse response = restTemplate.getForObject(url, FinnhubCandleResponse.class);

            if (response != null && "ok".equalsIgnoreCase(response.getS())) {
                logger.debug("Candle response for {} [{}]: {} candles", symbol, resolution,
                        response.getC() != null ? response.getC().size() : 0);
            } else {
                logger.warn("No data received for {} [{}] in range [{}-{}]", symbol, resolution, from, to);
            }
            return response;

        } catch (RestClientException e) {
            logger.warn("Failed to fetch candles for {} [{}]: {}", symbol, resolution, e.getMessage());
            return null;
        } catch (Exception e) {
            logger.error("Unexpected error fetching candles for {} [{}]: {}", symbol, resolution,
                    e.getMessage(), e);
            return null;
        }
    }
    public List<StockSearchDto> searchStocks(
            String query) {

        try {

            String url = String.format(
                    "%s/search?q=%s&token=%s",
                    baseUrl,
                    query,
                    apiKey
            );

            FinnhubSearchResponse response =
                    restTemplate.getForObject(
                            url,
                            FinnhubSearchResponse.class
                    );

            if(response == null){
                return List.of();
            }

            return response.getResult();

        } catch (Exception e) {

            logger.error(
                    "Search error",
                    e
            );

            return List.of();
        }
    }

}