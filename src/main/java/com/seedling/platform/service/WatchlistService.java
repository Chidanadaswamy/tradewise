package com.seedling.platform.service;

import com.seedling.platform.model.Stock;
import com.seedling.platform.model.User;
import com.seedling.platform.model.Watchlist;
import com.seedling.platform.repository.StockRepository;
import com.seedling.platform.repository.UserRepository;
import com.seedling.platform.repository.WatchlistRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class WatchlistService {

    @Autowired
    private WatchlistRepository watchlistRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StockRepository stockRepository;

    /**
     * DTO for returning enriched watchlist items with live stock data.
     */
    public static class WatchlistItemDTO {
        public String ticker;
        public String stockName;
        public BigDecimal currentPrice;
        public double dailyChangePercent;
        public LocalDateTime addedAt;

        public WatchlistItemDTO(String ticker, String stockName, BigDecimal currentPrice,
                                double dailyChangePercent, LocalDateTime addedAt) {
            this.ticker = ticker;
            this.stockName = stockName;
            this.currentPrice = currentPrice;
            this.dailyChangePercent = dailyChangePercent;
            this.addedAt = addedAt;
        }
    }

    /**
     * Add a stock to the user's watchlist.
     */
    @Transactional
    public Watchlist addToWatchlist(Long userId, String ticker) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        // Validate stock exists
        stockRepository.findById(ticker.toUpperCase())
                .orElseThrow(() -> new IllegalArgumentException("Stock not found: " + ticker));

        String normalizedTicker = ticker.toUpperCase();

        // Check for duplicates
        if (watchlistRepository.existsByUserAndTicker(user, normalizedTicker)) {
            throw new IllegalStateException("Stock already in watchlist");
        }

        Watchlist entry = new Watchlist(user, normalizedTicker);
        return watchlistRepository.save(entry);
    }

    /**
     * Remove a stock from the user's watchlist.
     */
    @Transactional
    public void removeFromWatchlist(Long userId, String ticker) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        String normalizedTicker = ticker.toUpperCase();

        if (!watchlistRepository.existsByUserAndTicker(user, normalizedTicker)) {
            throw new IllegalArgumentException("Stock not in watchlist");
        }

        watchlistRepository.deleteByUserAndTicker(user, normalizedTicker);
    }

    /**
     * Get all watchlist items for a user, enriched with live stock data.
     */
    public List<WatchlistItemDTO> getWatchlist(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        List<Watchlist> entries = watchlistRepository.findByUserOrderByAddedAtDesc(user);
        List<WatchlistItemDTO> items = new ArrayList<>();

        for (Watchlist entry : entries) {
            Optional<Stock> stockOpt = stockRepository.findById(entry.getTicker());
            if (stockOpt.isPresent()) {
                Stock stock = stockOpt.get();
                double changePercent = stock.getChangePercent() != null ? stock.getChangePercent().doubleValue() : 0.0;
                items.add(new WatchlistItemDTO(
                        stock.getTicker(),
                        stock.getName(),
                        stock.getCurrentPrice(),
                        changePercent,
                        entry.getAddedAt()
                ));
            }
        }

        return items;
    }

    /**
     * Get the count of watchlist items for a user.
     */
    public long getWatchlistCount(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        return watchlistRepository.countByUser(user);
    }

    /**
     * Check if a specific stock is in the user's watchlist.
     */
    public boolean isInWatchlist(Long userId, String ticker) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        return watchlistRepository.existsByUserAndTicker(user, ticker.toUpperCase());
    }
}
