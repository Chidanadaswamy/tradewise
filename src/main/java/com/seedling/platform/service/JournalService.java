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
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class JournalService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PositionRepository positionRepository;

    @Autowired
    private TradeRepository tradeRepository;

    @Autowired
    private StockRepository stockRepository;

    public static class CoachingInsight {
        private String ruleName;       // MISSING_STOP_LOSS, OVERTRADING, DISPOSITION_EFFECT, CONCENTRATION_RISK
        private String severity;       // WARNING, DANGER, INFO
        private String title;
        private String description;
        private String recommendation;

        public CoachingInsight(String ruleName, String severity, String title, String description, String recommendation) {
            this.ruleName = ruleName;
            this.severity = severity;
            this.title = title;
            this.description = description;
            this.recommendation = recommendation;
        }

        // Getters
        public String getRuleName() { return ruleName; }
        public String getSeverity() { return severity; }
        public String getTitle() { return title; }
        public String getDescription() { return description; }
        public String getRecommendation() { return recommendation; }
    }

    public static class JournalSummary {
        private String score; // A, B, C, D
        private List<CoachingInsight> insights;
        private int disciplineScore; // 0 - 100

        public JournalSummary(String score, List<CoachingInsight> insights, int disciplineScore) {
            this.score = score;
            this.insights = insights;
            this.disciplineScore = disciplineScore;
        }

        // Getters
        public String getScore() { return score; }
        public List<CoachingInsight> getInsights() { return insights; }
        public int getDisciplineScore() { return disciplineScore; }
    }

    public JournalSummary analyzePortfolio(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        List<Position> positions = positionRepository.findByUser(user);
        List<Trade> allTrades = tradeRepository.findByUserOrderByTimestampDesc(user);
        List<CoachingInsight> insights = new ArrayList<>();

        // Calculate Total Portfolio Value (Cash + Equity)
        BigDecimal cash = user.getBalance();
        BigDecimal totalEquity = BigDecimal.ZERO;

        for (Position position : positions) {
            Optional<Stock> stockOpt = stockRepository.findById(position.getTicker());
            if (stockOpt.isPresent()) {
                BigDecimal currentPrice = stockOpt.get().getCurrentPrice();
                BigDecimal positionValue = position.getQuantity().multiply(currentPrice);
                totalEquity = totalEquity.add(positionValue);
            }
        }
        BigDecimal portfolioValue = cash.add(totalEquity);

        // RULE 1: Missing Stop-Loss on active holdings
        for (Position position : positions) {
            if (position.getStopLossPrice() == null || position.getStopLossPrice().compareTo(BigDecimal.ZERO) <= 0) {
                insights.add(new CoachingInsight(
                        "MISSING_STOP_LOSS",
                        "WARNING",
                        "Missing Safety Net (" + position.getTicker() + ")",
                        "You are holding " + position.getQuantity() + " shares of " + position.getTicker() + " without a stop-loss order.",
                        "Set a stop-loss price at 5-10% below your average purchase price ($" + position.getAverageBuyPrice() + ") to protect your capital from market drawdowns."
                ));
            }
        }

        // RULE 2: Overtrading (Count trades in last 24 hours)
        LocalDateTime twentyFourHoursAgo = LocalDateTime.now().minusDays(1);
        List<Trade> recentTrades = tradeRepository.findByUserAndTimestampAfter(user, twentyFourHoursAgo);
        if (recentTrades.size() > 5) {
            insights.add(new CoachingInsight(
                    "OVERTRADING",
                    "DANGER",
                    "High Trade Frequency Detected",
                    "You have executed " + recentTrades.size() + " trades in the last 24 hours. Overtrading increases transaction fee drag and often indicates emotional trading.",
                    "Slow down. Try to limit yourself to a maximum of 3 trades per day. Focus on research and patient entries rather than reacting to short-term market noise."
            ));
        }

        // RULE 3: Disposition Effect (Holding Losers Too Long & Selling Winners Early)
        boolean holdsSignificantLoser = false;
        String loserTicker = "";
        BigDecimal loserLossPct = BigDecimal.ZERO;

        for (Position position : positions) {
            Optional<Stock> stockOpt = stockRepository.findById(position.getTicker());
            if (stockOpt.isPresent()) {
                BigDecimal currentPrice = stockOpt.get().getCurrentPrice();
                BigDecimal averagePrice = position.getAverageBuyPrice();
                
                if (averagePrice.compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal change = currentPrice.subtract(averagePrice);
                    BigDecimal pctChange = change.divide(averagePrice, 4, RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(100));

                    // Check if down > 15%
                    if (pctChange.compareTo(BigDecimal.valueOf(-15)) <= 0) {
                        holdsSignificantLoser = true;
                        loserTicker = position.getTicker();
                        loserLossPct = pctChange.abs().setScale(2, RoundingMode.HALF_UP);
                    }
                }
            }
        }

        // Check if they hold a loser and sold a stock recently (in last 48 hours)
        boolean hasRecentSell = false;
        for (Trade trade : allTrades) {
            if (trade.getTradeType().equalsIgnoreCase("SELL") && 
                trade.getTimestamp().isAfter(LocalDateTime.now().minusDays(2))) {
                hasRecentSell = true;
                break;
            }
        }

        if (holdsSignificantLoser) {
            String desc = "You are currently holding " + loserTicker + " which is down " + loserLossPct + "%. ";
            if (hasRecentSell) {
                desc += "You also recently closed out positions for small gains. This pattern is known as the 'Disposition Effect': selling winners too early while holding onto losers hoping they break even.";
            } else {
                desc += "Holding onto declining stocks without an exit strategy often leads to deeper portfolio damage as emotional attachment replaces analytical judgment.";
            }

            insights.add(new CoachingInsight(
                    "DISPOSITION_EFFECT",
                    "DANGER",
                    "Holding Declining Positions Too Long",
                    desc,
                    "Re-evaluate your thesis for " + loserTicker + ". If the fundamentals have changed, cut your loss now. Don't fall into the trap of holding a stock just to 'get back to even'."
            ));
        }

        // RULE 4: Concentration Risk (Position Sizing > 25% of total portfolio value)
        if (portfolioValue.compareTo(BigDecimal.ZERO) > 0) {
            for (Position position : positions) {
                Optional<Stock> stockOpt = stockRepository.findById(position.getTicker());
                if (stockOpt.isPresent()) {
                    BigDecimal currentPrice = stockOpt.get().getCurrentPrice();
                    BigDecimal positionValue = position.getQuantity().multiply(currentPrice);
                    
                    BigDecimal allocationRatio = positionValue.divide(portfolioValue, 4, RoundingMode.HALF_UP);
                    BigDecimal allocationPct = allocationRatio.multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP);

                    if (allocationRatio.compareTo(BigDecimal.valueOf(0.25)) > 0) {
                        insights.add(new CoachingInsight(
                                "CONCENTRATION_RISK",
                                "WARNING",
                                "High Portfolio Concentration (" + position.getTicker() + ")",
                                "Position in " + position.getTicker() + " constitutes " + allocationPct + "% of your entire portfolio (Limit: 25%). High concentration makes your net worth highly sensitive to a single stock's movements.",
                                "Trim your position in " + position.getTicker() + " and reallocate the cash, or buy other curated stocks to diversify your portfolio risk."
                        ));
                    }
                }
            }
        }

        // Compute Quantitative Discipline Index (0 - 100)
        int disciplineScore = 100;

        // Deduction 1: Missing Stop-Loss. Deduct 10 points per position without SL, max 30 points.
        long positionsWithoutStopLoss = positions.stream()
                .filter(p -> p.getStopLossPrice() == null || p.getStopLossPrice().compareTo(BigDecimal.ZERO) <= 0)
                .count();
        int stopLossDeduction = (int) Math.min(positionsWithoutStopLoss * 10, 30);
        disciplineScore -= stopLossDeduction;

        // Deduction 2: Overtrading. Deduct 15 points if trade frequency is high.
        if (recentTrades.size() > 5) {
            disciplineScore -= 15;
        }

        // Deduction 3: Disposition Effect. Deduct 15 points if holding a significant loser.
        if (holdsSignificantLoser) {
            disciplineScore -= 15;
        }

        // Deduction 4: Concentration Risk. Deduct 10 points per concentrated position, max 20 points.
        long concentratedPositionsCount = 0;
        if (portfolioValue.compareTo(BigDecimal.ZERO) > 0) {
            for (Position position : positions) {
                Optional<Stock> stockOpt = stockRepository.findById(position.getTicker());
                if (stockOpt.isPresent()) {
                    BigDecimal currentPrice = stockOpt.get().getCurrentPrice();
                    BigDecimal positionValue = position.getQuantity().multiply(currentPrice);
                    BigDecimal allocationRatio = positionValue.divide(portfolioValue, 4, RoundingMode.HALF_UP);
                    if (allocationRatio.compareTo(BigDecimal.valueOf(0.25)) > 0) {
                        concentratedPositionsCount++;
                    }
                }
            }
        }
        int concentrationDeduction = (int) Math.min(concentratedPositionsCount * 10, 20);
        disciplineScore -= concentrationDeduction;

        // Ensure score stays between 0 and 100
        disciplineScore = Math.max(0, Math.min(100, disciplineScore));

        // Map Discipline Score directly to Letter Grade (A, B, C, D)
        String score;
        if (disciplineScore >= 90) {
            score = "A";
        } else if (disciplineScore >= 75) {
            score = "B";
        } else if (disciplineScore >= 60) {
            score = "C";
        } else {
            score = "D";
        }

        return new JournalSummary(score, insights, disciplineScore);
    }
}
