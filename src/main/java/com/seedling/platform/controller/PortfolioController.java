package com.seedling.platform.controller;

import com.seedling.platform.model.Position;
import com.seedling.platform.model.Trade;
import com.seedling.platform.service.PortfolioService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/portfolio")
public class PortfolioController {

    @Autowired
    private PortfolioService portfolioService;

    public static class BuyRequest {
        public String ticker;
        public BigDecimal quantity;
        public BigDecimal stopLossPrice;
    }

    public static class SellRequest {
        public String ticker;
        public BigDecimal quantity;
    }

    public static class StopLossRequest {
        public String ticker;
        public BigDecimal stopLossPrice;
    }

    private Long getUserIdFromSession(HttpSession session) {
        return (Long) session.getAttribute("userId");
    }

    @GetMapping("/positions")
    public ResponseEntity<?> getPositions(HttpSession session) {
        Long userId = getUserIdFromSession(session);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Not authenticated");
        }
        List<Position> positions = portfolioService.getUserPositions(userId);
        return ResponseEntity.ok(positions);
    }

    @GetMapping("/trades")
    public ResponseEntity<?> getTrades(HttpSession session) {
        Long userId = getUserIdFromSession(session);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Not authenticated");
        }
        List<Trade> trades = portfolioService.getUserTrades(userId);
        return ResponseEntity.ok(trades);
    }

    @PostMapping("/buy")
    public ResponseEntity<?> buyStock(@RequestBody BuyRequest request, HttpSession session) {
        Long userId = getUserIdFromSession(session);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Not authenticated");
        }

        if (request.ticker == null || request.quantity == null || request.quantity.compareTo(BigDecimal.ZERO) <= 0) {
            return ResponseEntity.badRequest().body("Invalid stock ticker or quantity");
        }

        try {
            Trade trade = portfolioService.buyStock(userId, request.ticker.toUpperCase(), request.quantity, request.stopLossPrice);
            return ResponseEntity.ok(trade);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/sell")
    public ResponseEntity<?> sellStock(@RequestBody SellRequest request, HttpSession session) {
        Long userId = getUserIdFromSession(session);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Not authenticated");
        }

        if (request.ticker == null || request.quantity == null || request.quantity.compareTo(BigDecimal.ZERO) <= 0) {
            return ResponseEntity.badRequest().body("Invalid stock ticker or quantity");
        }

        try {
            Trade trade = portfolioService.sellStock(userId, request.ticker.toUpperCase(), request.quantity);
            return ResponseEntity.ok(trade);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/stoploss")
    public ResponseEntity<?> updateStopLoss(@RequestBody StopLossRequest request, HttpSession session) {
        Long userId = getUserIdFromSession(session);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Not authenticated");
        }

        if (request.ticker == null) {
            return ResponseEntity.badRequest().body("Invalid stock ticker");
        }

        try {
            Position position = portfolioService.updateStopLoss(userId, request.ticker.toUpperCase(), request.stopLossPrice);
            return ResponseEntity.ok(position);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
