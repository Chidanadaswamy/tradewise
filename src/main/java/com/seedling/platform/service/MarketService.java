package com.seedling.platform.service;

import com.seedling.platform.model.Stock;
import com.seedling.platform.model.StockHistory;
import com.seedling.platform.repository.StockHistoryRepository;
import com.seedling.platform.repository.StockRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.seedling.platform.dto.FinnhubQuoteResponse;
import com.seedling.platform.dto.FinnhubCandleResponse;
import com.seedling.platform.dto.StockCandleResponse;
import com.seedling.platform.dto.MarketStatusResponse;
import com.seedling.platform.dto.StockSearchDto;
import com.seedling.platform.dto.StockSearchDto;
import com.seedling.platform.dto.FinnhubSearchResponse;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.DayOfWeek;
import java.util.*;

/**
 * Market Service - Core business logic for stock market operations
 *
 * Responsibilities:
 * - Initialize and manage stock master data
 * - Fetch and update current stock prices from Finnhub
 * - Aggregate candlestick data for chart visualization
 * - Determine market open/closed status
 * - Implement graceful fallback to database when API fails
 *
 * Caching Strategy:
 * - All external API calls are cached via @Cacheable annotations
 * - TTLs configured in CacheConfig.java per endpoint
 * - Graceful degradation: Falls back to database on API failures
 * - Market status cached to avoid repeated calculations
 */
@Service
public class MarketService {

    private static final Logger logger = LoggerFactory.getLogger(MarketService.class);

    @Autowired
    private StockRepository stockRepository;

    @Autowired
    private StockHistoryRepository stockHistoryRepository;

    @Autowired
    private FinnhubService finnhubService;

    @Autowired
    private ChartDataService chartDataService;

    @Autowired
    private RateLimitService rateLimitService;

    private final Random random = new Random();

    // Map of curated stocks with their initial prices
    private static final Map<String, StockInit> CURATED_STOCKS = new LinkedHashMap<>();

    static {
        CURATED_STOCKS.put("AAPL", new StockInit("Apple Inc.", 175.00));
        CURATED_STOCKS.put("MSFT", new StockInit("Microsoft Corp.", 400.00));
        CURATED_STOCKS.put("GOOGL", new StockInit("Alphabet Inc.", 150.00));
        CURATED_STOCKS.put("AMZN", new StockInit("Amazon.com Inc.", 180.00));
        CURATED_STOCKS.put("TSLA", new StockInit("Tesla Inc.", 170.00));
        CURATED_STOCKS.put("NFLX", new StockInit("Netflix Inc.", 600.00));
        CURATED_STOCKS.put("NVDA", new StockInit("NVIDIA Corp.", 850.00));
        CURATED_STOCKS.put("JNJ", new StockInit("Johnson & Johnson", 160.00));
        CURATED_STOCKS.put("KO", new StockInit("Coca-Cola Co.", 60.00));
        CURATED_STOCKS.put("DIS", new StockInit("Walt Disney Co.", 110.00));
    }

    private static class StockInit {
        String name;
        double price;
        StockInit(String name, double price) {
            this.name = name;
            this.price = price;
        }
    }

    @PostConstruct
    @Transactional
    public void initMarket() {
        if (stockRepository.count() == 0) {
            logger.info("Initializing market with {} stocks", CURATED_STOCKS.size());
            for (Map.Entry<String, StockInit> entry : CURATED_STOCKS.entrySet()) {
                String ticker = entry.getKey();
                StockInit init = entry.getValue();
                BigDecimal basePrice = BigDecimal.valueOf(init.price).setScale(2, RoundingMode.HALF_UP);

                // Create stock
                Stock stock = new Stock(ticker, init.name, basePrice, basePrice);
                stockRepository.save(stock);

                // Generate 365 days of mock history backwards from yesterday
                LocalDate today = LocalDate.now();
                BigDecimal histPrice = basePrice;
                List<StockHistory> historyList = new ArrayList<>();

                for (int i = 365; i >= 1; i--) {
                    LocalDate date = today.minusDays(i);
                    // Daily random walk: +/- 1.5%
                    double changePct = (random.nextDouble() * 3.0 - 1.5) / 100.0;
                    BigDecimal factor = BigDecimal.valueOf(1.0 + changePct);
                    histPrice = histPrice.multiply(factor).setScale(2, RoundingMode.HALF_UP);

                    historyList.add(new StockHistory(ticker, histPrice, date));
                }
                stockHistoryRepository.saveAll(historyList);
                logger.debug("Initialized {} with {} historical records", ticker, historyList.size());
            }
            logger.info("Market initialization complete");
        }
    }

    /** ...existing code... **/

    /**
     * Get candlestick data for chart visualization
     * Implements intelligent caching and graceful fallback
     *
     * @param ticker Stock symbol (e.g., "AAPL")
     * @param timeframe Chart timeframe (1D, 1W, 1M, 3M, 6M, 1Y, 3Y, 5Y)
     * @return StockCandleResponse with OHLCV data
     */
    public StockCandleResponse getStockCandles(String ticker, String timeframe) {
        logger.debug("Fetching candles for {} [{}]", ticker, timeframe);

        try {
            // Validate timeframe
            String normalizedTimeframe = normalizeTimeframe(timeframe);
            if (normalizedTimeframe == null) {
                logger.warn("Invalid timeframe: {}", timeframe);
                return createErrorResponse("Invalid timeframe");
            }

            // Calculate resolution and time range based on timeframe
            ChartParams params = getChartParams(normalizedTimeframe);

            // Check rate limit before calling API
            boolean rateLimitAllowed = rateLimitService.isAllowed("candle");

            FinnhubCandleResponse finnhubResponse = null;
            if (rateLimitAllowed) {
                logger.debug("Rate limit OK, fetching from Finnhub");
                finnhubResponse = finnhubService.getCandles(
                        ticker,
                        params.resolution,
                        params.from,
                        params.to
                );
            } else {
                logger.info("Rate limited - will use cached data for {} [{}]", ticker, normalizedTimeframe);
            }

            // If we got data from Finnhub, process and return it
            if (finnhubResponse != null && "ok".equalsIgnoreCase(finnhubResponse.getS())) {
                StockCandleResponse response = chartDataService.processFinnhubResponse(ticker, finnhubResponse);
                logger.debug("Successfully processed Finnhub data for {} [{}]", ticker, normalizedTimeframe);
                return response;
            }

            // Fallback to database history
            logger.info("Finnhub unavailable, falling back to database history for {} [{}]", ticker, normalizedTimeframe);
            List<StockHistory> history = getStockHistory(ticker);
            return chartDataService.createResponseFromHistory(ticker, history, params.sliceSize);

        } catch (Exception e) {
            logger.error("Error fetching candles for {} [{}]: {}", ticker, timeframe, e.getMessage(), e);
            return createErrorResponse("Error fetching chart data: " + e.getMessage());
        }
    }

    /**
     * Simulates live market movements every minute.
     * Updates prices from Finnhub API with error handling and rate limit awareness.
     */
    @Scheduled(fixedRate = 60000)
    @Transactional
    public void tickMarket() {
        logger.debug("Ticking market prices");

        List<Stock> stocks = stockRepository.findAll();

        for (Stock stock : stocks) {
            try {
                // Check rate limit
                if (!rateLimitService.isAllowed("quote")) {
                    logger.debug("Rate limited, skipping quote refresh for {}", stock.getTicker());
                    continue;
                }

                FinnhubQuoteResponse quote = finnhubService.getQuote(stock.getTicker());

                if (quote != null && quote.getC() != null) {
                    logger.debug("{} = {}", stock.getTicker(), quote.getC());

                    stock.setLastPrice(stock.getCurrentPrice());
                    stock.setCurrentPrice(quote.getC());
                    stock.setPriceChange(quote.getD());
                    stock.setChangePercent(quote.getDp());

                    stockRepository.save(stock);
                } else {
                    logger.warn("No quote data received for {}", stock.getTicker());
                }

            } catch (Exception e) {
                logger.warn("Failed to update {} : {}", stock.getTicker(), e.getMessage());
            }
        }
    }

    /**
     * Manually refresh stock price from Finnhub API
     * @param ticker Stock symbol
     * @return Updated stock entity
     */
    @Transactional
    public Stock refreshStockPrice(String ticker) {
        logger.debug("Manual refresh for {}", ticker);

        Stock stock = stockRepository
                .findById(ticker)
                .orElseThrow(() -> new RuntimeException("Stock not found: " + ticker));

        FinnhubQuoteResponse quote = finnhubService.getQuote(ticker);

        if (quote != null && quote.getC() != null) {
            stock.setLastPrice(stock.getCurrentPrice());
            stock.setCurrentPrice(quote.getC());
            stock.setPriceChange(quote.getD());
            stock.setChangePercent(quote.getDp());
        } else {
            logger.warn("No quote data for manual refresh: {}", ticker);
        }

        return stockRepository.save(stock);
    }

    /**
     * Get all stocks
     */
    @Cacheable(value = "stockListCache", key = "'all'")
    public List<Stock> getAllStocks() {
        return stockRepository.findAll();
    }

    /**
     * Get single stock by ticker
     */
    public Optional<Stock> getStockByTicker(String ticker) {
        return stockRepository.findById(ticker);
    }

    /**
     * Get historical prices from database
     */
    public List<StockHistory> getStockHistory(String ticker) {
        return stockHistoryRepository.findByTickerOrderByDateAsc(ticker);
    }

    public List<StockSearchDto> searchStocks(String query) {

        if(query == null || query.isBlank()){
            return Collections.emptyList();
        }

        return finnhubService.searchStocks(
                query.trim()
        );
    }

    /**
     * Get market status (open/closed/pre-market/post-market)
     */
    @Cacheable(value = "marketStatusCache", key = "'status'")
    public MarketStatusResponse getMarketStatus() {
        ZonedDateTime etTime = ZonedDateTime.now(ZoneId.of("America/New_York"));
        DayOfWeek day = etTime.getDayOfWeek();
        LocalTime time = etTime.toLocalTime();

        boolean isWeekend = day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY;

        LocalTime marketOpen = LocalTime.of(9, 30);
        LocalTime marketClose = LocalTime.of(16, 0);

        if (isWeekend) {
            return new MarketStatusResponse(false, "CLOSED", "Market Closed (Weekend)");
        } else if (time.isBefore(marketOpen)) {
            return new MarketStatusResponse(false, "PRE_MARKET", "Pre-Market");
        } else if (time.isAfter(marketClose)) {
            return new MarketStatusResponse(false, "POST_MARKET", "Post-Market");
        } else {
            return new MarketStatusResponse(true, "REGULAR", "Market Open");
        }
    }

    // ──────────────────── Helper Methods ────────────────────────

    /**
     * Normalize and validate timeframe input
     */
    private String normalizeTimeframe(String timeframe) {
        if (timeframe == null) return "1D";
        String normalized = timeframe.toUpperCase();
        switch (normalized) {
            case "1D":
            case "1W":
            case "1M":
            case "3M":
            case "6M":
            case "1Y":
            case "3Y":
            case "5Y":
                return normalized;
            default:
                return null;
        }
    }

    /**
     * Get chart parameters (resolution, time range, slice size) for timeframe
     */
    private ChartParams getChartParams(String timeframe) {
        long now = System.currentTimeMillis() / 1000L;
        ChartParams params = new ChartParams();
        params.to = now;

        switch (timeframe) {
            case "1D":
                params.from = now - (24 * 3600);
                params.resolution = "5";    // 5-minute candles
                params.sliceSize = 288;     // 24 hours * 60 min / 5 min
                break;
            case "1W":
                params.from = now - (7 * 24 * 3600);
                params.resolution = "30";   // 30-minute candles
                params.sliceSize = 336;     // 7 days * 24 hours * 60 min / 30 min (during open hours)
                break;
            case "1M":
                params.from = now - (30L * 24 * 3600);
                params.resolution = "D";    // Daily candles
                params.sliceSize = 30;
                break;
            case "3M":
                params.from = now - (90L * 24 * 3600);
                params.resolution = "D";
                params.sliceSize = 90;
                break;
            case "6M":
                params.from = now - (180L * 24 * 3600);
                params.resolution = "D";
                params.sliceSize = 180;
                break;
            case "1Y":
                params.from = now - (365L * 24 * 3600);
                params.resolution = "D";
                params.sliceSize = 365;
                break;
            case "3Y":
                params.from = now - (3L * 365 * 24 * 3600);
                params.resolution = "W";    // Weekly candles
                params.sliceSize = 156;     // ~3 years * 52 weeks/year
                break;
            case "5Y":
                params.from = now - (5L * 365 * 24 * 3600);
                params.resolution = "M";    // Monthly candles
                params.sliceSize = 60;      // ~5 years * 12 months/year
                break;
            default:
                params.from = now - (24 * 3600);
                params.resolution = "5";
                params.sliceSize = 288;
        }

        return params;
    }

    /**
     * Create error response
     */
    private StockCandleResponse createErrorResponse(String message) {
        StockCandleResponse response = new StockCandleResponse();
        response.setStatus("error");
        logger.warn("Error response: {}", message);
        return response;
    }

    /**
     * Helper class for chart parameters
     */
    private static class ChartParams {
        long from;
        long to;
        String resolution;
        int sliceSize;
    }
}

