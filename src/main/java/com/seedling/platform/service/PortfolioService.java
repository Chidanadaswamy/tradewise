package com.seedling.platform.service;

import com.seedling.platform.model.Position;
import com.seedling.platform.model.Stock;
import com.seedling.platform.model.Trade;
import com.seedling.platform.model.User;
import com.seedling.platform.repository.PositionRepository;
import com.seedling.platform.repository.StockRepository;
import com.seedling.platform.repository.TradeRepository;
import com.seedling.platform.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;

@Service
public class PortfolioService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StockRepository stockRepository;

    @Autowired
    private PositionRepository positionRepository;

    @Autowired
    private TradeRepository tradeRepository;

    @Transactional
    public Trade buyStock(Long userId, String ticker, BigDecimal quantity, BigDecimal stopLossPrice) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        Stock stock = stockRepository.findById(ticker)
                .orElseThrow(() -> new IllegalArgumentException("Stock not found: " + ticker));

        BigDecimal currentPrice = stock.getCurrentPrice();
        BigDecimal totalCost = currentPrice.multiply(quantity).setScale(2, RoundingMode.HALF_UP);

        if (user.getBalance().compareTo(totalCost) < 0) {
            throw new IllegalStateException("Insufficient balance. Required: $" + totalCost + ", Available: $" + user.getBalance());
        }

        // Deduct balance
        user.setBalance(user.getBalance().subtract(totalCost));
        userRepository.save(user);

        // Update or create position
        Optional<Position> positionOpt = positionRepository.findByUserAndTicker(user, ticker);
        Position position;
        if (positionOpt.isPresent()) {
            position = positionOpt.get();
            BigDecimal oldQty = position.getQuantity();
            BigDecimal newQty = oldQty.add(quantity);
            
            // Recalculate average buy price: (oldQty * oldAvg + newQty * price) / (oldQty + newQty)
            BigDecimal totalSpent = oldQty.multiply(position.getAverageBuyPrice())
                    .add(quantity.multiply(currentPrice));
            BigDecimal newAvgPrice = totalSpent.divide(newQty, 2, RoundingMode.HALF_UP);
            
            position.setQuantity(newQty);
            position.setAverageBuyPrice(newAvgPrice);
            if (stopLossPrice != null) {
                position.setStopLossPrice(stopLossPrice);
            }
        } else {
            position = new Position(user, ticker, quantity, currentPrice, stopLossPrice);
        }
        positionRepository.save(position);

        // Record Trade log
        Trade trade = new Trade(user, ticker, "BUY", quantity, currentPrice, stopLossPrice);
        return tradeRepository.save(trade);
    }

    @Transactional
    public Trade sellStock(Long userId, String ticker, BigDecimal quantity) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        Stock stock = stockRepository.findById(ticker)
                .orElseThrow(() -> new IllegalArgumentException("Stock not found: " + ticker));

        Position position = positionRepository.findByUserAndTicker(user, ticker)
                .orElseThrow(() -> new IllegalStateException("You do not hold a position in " + ticker));

        if (position.getQuantity().compareTo(quantity) < 0) {
            throw new IllegalStateException("Insufficient shares. You own: " + position.getQuantity() + ", tried to sell: " + quantity);
        }

        BigDecimal currentPrice = stock.getCurrentPrice();
        BigDecimal saleProceeds = currentPrice.multiply(quantity).setScale(2, RoundingMode.HALF_UP);

        // Update balance
        user.setBalance(user.getBalance().add(saleProceeds));
        userRepository.save(user);

        // Update or delete position
        BigDecimal remainingQty = position.getQuantity().subtract(quantity);
        BigDecimal stopLoss = position.getStopLossPrice();
        
        if (remainingQty.compareTo(BigDecimal.ZERO) <= 0) {
            positionRepository.delete(position);
        } else {
            position.setQuantity(remainingQty);
            positionRepository.save(position);
        }

        // Record Trade log
        Trade trade = new Trade(user, ticker, "SELL", quantity, currentPrice, stopLoss);
        return tradeRepository.save(trade);
    }

    @Transactional
    public Position updateStopLoss(Long userId, String ticker, BigDecimal stopLossPrice) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        Position position = positionRepository.findByUserAndTicker(user, ticker)
                .orElseThrow(() -> new IllegalStateException("You do not hold a position in " + ticker));

        position.setStopLossPrice(stopLossPrice);
        return positionRepository.save(position);
    }

    public List<Position> getUserPositions(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        return positionRepository.findByUser(user);
    }

    public List<Trade> getUserTrades(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        return tradeRepository.findByUserOrderByTimestampDesc(user);
    }

    /**
     * Stop-Loss Watchdog Scheduler. Runs every 5 seconds.
     * Evaluates active stop-losses against current market prices and auto-executes sells.
     */
    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void checkStopLosses() {
        List<Position> positionsWithStopLoss = positionRepository.findByStopLossPriceIsNotNull();
        for (Position position : positionsWithStopLoss) {
            Optional<Stock> stockOpt = stockRepository.findById(position.getTicker());
            if (stockOpt.isPresent()) {
                Stock stock = stockOpt.get();
                BigDecimal currentPrice = stock.getCurrentPrice();
                BigDecimal stopLossPrice = position.getStopLossPrice();

                // If current price falls to or below the stop-loss price, trigger automatic sell
                if (currentPrice.compareTo(stopLossPrice) <= 0) {
                    try {
                        System.out.println("Stop-Loss Triggered! Selling " + position.getQuantity() + " shares of " 
                                + position.getTicker() + " for User " + position.getUser().getUsername() 
                                + " at current price $" + currentPrice + " (Stop-loss set at $" + stopLossPrice + ")");
                        
                        sellStock(position.getUser().getId(), position.getTicker(), position.getQuantity());
                    } catch (Exception e) {
                        System.err.println("Failed to execute stop-loss sell for position ID: " + position.getId() + ". Error: " + e.getMessage());
                    }
                }
            }
        }
    }
}
