package com.seedling.platform.controller;

import com.seedling.platform.service.WatchlistService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/watchlist")
public class WatchlistController {

    @Autowired
    private WatchlistService watchlistService;

    public static class WatchlistRequest {
        public String ticker;
    }

    private Long getUserIdFromSession(HttpSession session) {
        return (Long) session.getAttribute("userId");
    }

    /**
     * POST /api/watchlist/add — Add a stock to the user's watchlist.
     */
    @PostMapping("/add")
    public ResponseEntity<?> addToWatchlist(@RequestBody WatchlistRequest request, HttpSession session) {
        Long userId = getUserIdFromSession(session);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Not authenticated");
        }

        if (request.ticker == null || request.ticker.trim().isEmpty()) {
            return ResponseEntity.badRequest().body("Stock ticker is required");
        }

        try {
            watchlistService.addToWatchlist(userId, request.ticker.trim());
            return ResponseEntity.ok(Map.of(
                    "message", "Added to watchlist",
                    "ticker", request.ticker.trim().toUpperCase()
            ));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * DELETE /api/watchlist/remove/{ticker} — Remove a stock from the user's watchlist.
     */
    @DeleteMapping("/remove/{ticker}")
    public ResponseEntity<?> removeFromWatchlist(@PathVariable String ticker, HttpSession session) {
        Long userId = getUserIdFromSession(session);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Not authenticated");
        }

        try {
            watchlistService.removeFromWatchlist(userId, ticker);
            return ResponseEntity.ok(Map.of(
                    "message", "Removed from watchlist",
                    "ticker", ticker.toUpperCase()
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * GET /api/watchlist — Get all watchlist items with enriched stock data.
     */
    @GetMapping
    public ResponseEntity<?> getWatchlist(HttpSession session) {
        Long userId = getUserIdFromSession(session);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Not authenticated");
        }

        try {
            List<WatchlistService.WatchlistItemDTO> items = watchlistService.getWatchlist(userId);
            return ResponseEntity.ok(items);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * GET /api/watchlist/count — Get the number of watchlist items.
     */
    @GetMapping("/count")
    public ResponseEntity<?> getWatchlistCount(HttpSession session) {
        Long userId = getUserIdFromSession(session);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Not authenticated");
        }

        try {
            long count = watchlistService.getWatchlistCount(userId);
            return ResponseEntity.ok(Map.of("count", count));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * GET /api/watchlist/check/{ticker} — Check if a stock is in the user's watchlist.
     */
    @GetMapping("/check/{ticker}")
    public ResponseEntity<?> checkWatchlistStatus(@PathVariable String ticker, HttpSession session) {
        Long userId = getUserIdFromSession(session);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Not authenticated");
        }

        try {
            boolean inWatchlist = watchlistService.isInWatchlist(userId, ticker);
            return ResponseEntity.ok(Map.of(
                    "ticker", ticker.toUpperCase(),
                    "inWatchlist", inWatchlist
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
