package com.seedling.platform.service;

import com.seedling.platform.dto.FinnhubCandleResponse;
import com.seedling.platform.dto.StockCandleResponse;
import com.seedling.platform.model.StockHistory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.ZoneId;
import java.util.*;

/**
 * Service for processing and aggregating chart data
 *
 * Purpose:
 * - Transform raw Finnhub OHLCV data into frontend-friendly format
 * - Enhance response with OHLC (Open, High, Low, Close) data
 * - Include volume information for professional charts
 * - Provide fallback to database history when API fails
 * - Format timestamps and prices for Chart.js consumption
 *
 * Features:
 * - Converts Finnhub price arrays into OHLC objects
 * - Preserves volume data for volume bars
 * - Adds market session metadata
 * - Gracefully handles missing data
 * - Aggregates data for different timeframes
 */
@Service
public class ChartDataService {

    private static final Logger logger = LoggerFactory.getLogger(ChartDataService.class);

    /**
     * Process Finnhub response into professional chart format
     *
     * @param symbol Stock ticker
     * @param finnhubResponse Raw response from Finnhub API
     * @return Enhanced response with OHLC and volume data
     */
    public StockCandleResponse processFinnhubResponse(
            String symbol,
            FinnhubCandleResponse finnhubResponse) {

        StockCandleResponse response = new StockCandleResponse();

        if (finnhubResponse == null || "no_data".equalsIgnoreCase(finnhubResponse.getS())) {
            logger.debug("No data from Finnhub for {}", symbol);
            response.setStatus("no_data");
            return response;
        }

        if (!"ok".equalsIgnoreCase(finnhubResponse.getS()) ||
            finnhubResponse.getC() == null ||
            finnhubResponse.getC().isEmpty()) {
            logger.warn("Invalid Finnhub response for {}: status={}", symbol, finnhubResponse.getS());
            response.setStatus("error");
            return response;
        }

        try {
            // Extract OHLCV arrays from Finnhub response
            List<BigDecimal> opens = finnhubResponse.getO();
            List<BigDecimal> highs = finnhubResponse.getH();
            List<BigDecimal> lows = finnhubResponse.getL();
            List<BigDecimal> closes = finnhubResponse.getC();
            List<Long> volumes = finnhubResponse.getV();
            List<Long> timestamps = finnhubResponse.getT();

            // Initialize response
            response.setStatus("ok");
            response.setLastUpdated(System.currentTimeMillis() / 1000);
            response.setSession("REGULAR");

            // Set close prices for basic compatibility
            response.setPrices(closes);
            response.setTimestamps(timestamps);

            // Build OHLC structure - create list of candle objects
            int dataPoints = closes.size();
            List<Map<String, Object>> ohlcData = new ArrayList<>();

            for (int i = 0; i < dataPoints; i++) {
                Map<String, Object> candle = new LinkedHashMap<>();

                // Open, High, Low, Close
                if (opens != null && i < opens.size()) {
                    candle.put("o", opens.get(i));
                }
                if (highs != null && i < highs.size()) {
                    candle.put("h", highs.get(i));
                }
                if (lows != null && i < lows.size()) {
                    candle.put("l", lows.get(i));
                }
                candle.put("c", closes.get(i));

                // Volume
                if (volumes != null && i < volumes.size()) {
                    candle.put("v", volumes.get(i));
                }

                // Timestamp
                if (timestamps != null && i < timestamps.size()) {
                    candle.put("t", timestamps.get(i));
                }

                ohlcData.add(candle);
            }

            response.setOhlc(ohlcData);

            // Set volumes if available
            if (volumes != null && !volumes.isEmpty()) {
                response.setVolumes(volumes);
            }

            logger.debug("Processed {} data points for {}", dataPoints, symbol);
            return response;

        } catch (Exception e) {
            logger.error("Error processing Finnhub response for {}: {}", symbol, e.getMessage(), e);
            response.setStatus("error");
            return response;
        }
    }

    /**
     * Create response from database history (fallback when API fails)
     *
     * @param symbol Stock ticker
     * @param history List of historical stock prices from database
     * @param sliceSize Number of data points to return
     * @return Response with database history data
     */
    public StockCandleResponse createResponseFromHistory(
            String symbol,
            List<StockHistory> history,
            int sliceSize) {

        StockCandleResponse response = new StockCandleResponse();

        if (history == null || history.isEmpty()) {
            logger.warn("No history data available for {}", symbol);
            response.setStatus("no_data");
            return response;
        }

        try {
            response.setStatus("ok");
            response.setLastUpdated(System.currentTimeMillis() / 1000);
            response.setSession("DATABASE_FALLBACK");

            List<BigDecimal> prices = new ArrayList<>();
            List<Long> timestamps = new ArrayList<>();
            List<Map<String, Object>> ohlcData = new ArrayList<>();

            // Calculate actual slice size (don't exceed history length)
            int actualSliceSize = Math.min(sliceSize, history.size());
            int startIdx = history.size() - actualSliceSize;

            for (int i = startIdx; i < history.size(); i++) {
                StockHistory h = history.get(i);
                BigDecimal price = h.getPrice();

                prices.add(price);

                // Convert date to epoch seconds in ET timezone
                long epoch = h.getDate()
                        .atStartOfDay(ZoneId.of("America/New_York"))
                        .toEpochSecond();
                timestamps.add(epoch);

                // Create OHLC candle (using close price only from history)
                Map<String, Object> candle = new LinkedHashMap<>();
                candle.put("c", price);
                candle.put("t", epoch);
                ohlcData.add(candle);
            }

            response.setPrices(prices);
            response.setTimestamps(timestamps);
            response.setOhlc(ohlcData);

            logger.debug("Created response from {} history records for {}", actualSliceSize, symbol);
            return response;

        } catch (Exception e) {
            logger.error("Error creating response from history for {}: {}", symbol, e.getMessage(), e);
            response.setStatus("error");
            return response;
        }
    }

    /**
     * Calculate price statistics for the given dataset
     *
     * @param closes List of close prices
     * @return Map with min, max, avg prices
     */
    public Map<String, BigDecimal> calculateStats(List<BigDecimal> closes) {
        Map<String, BigDecimal> stats = new HashMap<>();

        if (closes == null || closes.isEmpty()) {
            return stats;
        }

        BigDecimal min = closes.get(0);
        BigDecimal max = closes.get(0);
        BigDecimal sum = BigDecimal.ZERO;

        for (BigDecimal price : closes) {
            if (price != null) {
                if (price.compareTo(min) < 0) min = price;
                if (price.compareTo(max) > 0) max = price;
                sum = sum.add(price);
            }
        }

        stats.put("high", max);
        stats.put("low", min);
        stats.put("avg", sum.divide(
                BigDecimal.valueOf(closes.size()),
                2,
                RoundingMode.HALF_UP
        ));

        return stats;
    }

    /**
     * Aggregate candlestick data (useful for reducing data points in frontend)
     *
     * @param candles List of candle objects
     * @param targetPointCount Target number of data points to aggregate to
     * @return Aggregated candles
     */
    public List<Map<String, Object>> aggregateCandles(
            List<Map<String, Object>> candles,
            int targetPointCount) {

        if (candles == null || candles.isEmpty()) {
            return candles;
        }

        if (candles.size() <= targetPointCount) {
            return candles;
        }

        int groupSize = (candles.size() + targetPointCount - 1) / targetPointCount;
        List<Map<String, Object>> aggregated = new ArrayList<>();

        for (int i = 0; i < candles.size(); i += groupSize) {
            int endIdx = Math.min(i + groupSize, candles.size());
            List<Map<String, Object>> group = candles.subList(i, endIdx);

            Map<String, Object> aggregatedCandle = new LinkedHashMap<>();

            // Get open from first candle
            if (group.get(0).containsKey("o")) {
                aggregatedCandle.put("o", group.get(0).get("o"));
            }

            // Get close from last candle
            Map<String, Object> lastCandle = group.get(group.size() - 1);
            aggregatedCandle.put("c", lastCandle.get("c"));

            // Calculate high and low from group
            BigDecimal high = BigDecimal.ZERO;
            BigDecimal low = BigDecimal.valueOf(Double.MAX_VALUE);
            long totalVolume = 0;

            for (Map<String, Object> candle : group) {
                if (candle.containsKey("h")) {
                    BigDecimal h = (BigDecimal) candle.get("h");
                    if (h.compareTo(high) > 0) high = h;
                }
                if (candle.containsKey("l")) {
                    BigDecimal l = (BigDecimal) candle.get("l");
                    if (l.compareTo(low) < 0) low = l;
                }
                if (candle.containsKey("v")) {
                    totalVolume += ((Number) candle.get("v")).longValue();
                }
            }

            if (high.compareTo(BigDecimal.ZERO) > 0) {
                aggregatedCandle.put("h", high);
            }
            if (low.compareTo(BigDecimal.valueOf(Double.MAX_VALUE)) < 0) {
                aggregatedCandle.put("l", low);
            }
            if (totalVolume > 0) {
                aggregatedCandle.put("v", totalVolume);
            }

            // Use timestamp from last candle in group
            if (lastCandle.containsKey("t")) {
                aggregatedCandle.put("t", lastCandle.get("t"));
            }

            aggregated.add(aggregatedCandle);
        }

        logger.debug("Aggregated {} candles to {} points", candles.size(), aggregated.size());
        return aggregated;
    }
}

