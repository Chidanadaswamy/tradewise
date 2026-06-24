# TradeWise Platform - Interview Cheat Sheet
## Quick Reference for Key Concepts

---

## 1. PROJECT SNAPSHOT

**What:** Paper trading simulator teaching behavioral discipline  
**Why:** Learn trading psychology, not just mechanics  
**Tech:** Spring Boot 3.2.5, Java 21, PostgreSQL, REST API  
**Scale:** MVP (100K users target, single server)  
**Key Feature:** Discipline Index (0-100 behavioral scoring)

---

## 2. CORE BUSINESS LOGIC

### Trading Flow
```
User has: $10,000 (starting balance)
│
├─ BUY 100 AAPL @ $180.50
│  Cost: $18,050.00 → BLOCKED (insufficient funds)
│  User tries: 50 shares
│  Cost: $9,025.00 → OK
│  new balance: $975.00
│  Position: {AAPL, qty=50, avg_price=180.50, stop_loss=170}
│
├─ BUY 50 more AAPL @ $155
│  Cost: $7,750.00 → OK
│  new balance: -$6,775.00 → BLOCKED (insufficient)
│
├─ SELL 30 AAPL @ $182
│  Proceeds: $5,460.00
│  new balance: $6,460.00
│  Position: {AAPL, qty=20, avg_price=?, stop_loss=170}
│
└─ STOP-LOSS TRIGGER (price hits $170)
   Auto-SELL 20 AAPL @ $170
   Proceeds: $3,400.00
   P&L: Bought @ 180.50, sold @ 170 = -$210 loss
```

### Average Buy Price Formula (Critical)
```
Scenario: Already own 100 AAPL @ $150/share
          Now buy 50 more @ $160/share

Step 1: Calculate total invested
  old: 100 × 150 = $15,000
  new: 50 × 160 = $8,000
  total: $23,000

Step 2: Calculate new quantity
  100 + 50 = 150 shares

Step 3: Calculate weighted average
  $23,000 / 150 = $153.33 per share

Result: Average buy price updated to $153.33
        (used for cost-basis, P&L calculations)

Code:
  BigDecimal totalSpent = oldQty.multiply(oldAvg)
    .add(quantity.multiply(currentPrice));
  BigDecimal newAvgPrice = totalSpent.divide(newQty, 2, 
    RoundingMode.HALF_UP);
```

---

## 3. DATABASE SCHEMA AT A GLANCE

```
USERS
├─ id (PK, auto-increment)
├─ username (UNIQUE)
├─ password (plain text MVP)
└─ balance (BigDecimal precision=15, scale=2)
   └─ Default: $10,000.00

STOCKS  
├─ ticker (PK: AAPL, MSFT, etc.)
├─ name
├─ currentPrice (updated every 5 seconds)
└─ lastPrice (for daily % change)

POSITIONS
├─ id (PK, user-driven)
├─ user_id (FK) → users
├─ ticker
├─ quantity (current holdings)
├─ average_buy_price (cost basis)
├─ stop_loss_price (nullable)
└─ UNIQUE(user_id, ticker) ← ONE position per stock per user

TRADES (Immutable audit log)
├─ id (PK)
├─ user_id (FK) → users
├─ ticker
├─ trade_type (BUY | SELL)
├─ quantity
├─ price (execution price)
├─ stop_loss_price (what was set)
└─ timestamp (@PrePersist auto-set)

WATCHLIST (Starred stocks)
├─ id (PK)
├─ user_id (FK) → users
├─ ticker
├─ added_at
└─ UNIQUE(user_id, ticker) ← Can't star same stock twice

STOCK_HISTORY (Time-series for charting)
├─ id (PK)
├─ ticker
├─ price (daily snapshot)
└─ date
```

---

## 4. LAYER RESPONSIBILITIES

| Layer | What | How | Why |
|-------|------|-----|-----|
| **Controller** | HTTP ↔ Business | @RestController, @PostMapping, HttpSession | Separate HTTP concerns from business logic |
| **Service** | Business Logic | @Service, @Transactional, calculations | Testable, reusable, encapsulated |
| **Repository** | Data Access | JpaRepository, Spring Data JPA | Auto-generate SQL, prevent SQL injection |
| **Entity** | Data Models | @Entity, @Column, constraints | Type-safe, schema validation |
| **Database** | Persistence | PostgreSQL tables | ACID guarantees, UNIQUE/FK constraints |

---

## 5. SPRING ANNOTATIONS EXPLAINED

```
@SpringBootApplication
  └─ Combines @Configuration + @ComponentScan + @EnableAutoConfiguration

@EnableScheduling
  └─ Activates @Scheduled tasks in background

@RestController
  └─ = @Controller + @ResponseBody (returns JSON, not HTML)

@RequestMapping("/api/portfolio")
  └─ Base URL path for all methods in class

@PostMapping("/buy")
  └─ = @RequestMapping(method=POST) + auto JSON parsing

@RequestBody
  └─ Deserialize JSON request → Java object

@PathVariable
  └─ Extract from URL: /api/stocks/{ticker}

@Service
  └─ Spring bean for business logic (singleton, injectable)

@Transactional
  └─ Atomic: all succeed or all rollback

@Repository
  └─ = @Component for data layer (exception translation)

@Entity
  └─ JPA: map Java class to database table

@Id
  └─ Primary key

@GeneratedValue(strategy=GenerationType.IDENTITY)
  └─ Auto-increment ID (database-native)

@Column(unique=true, nullable=false)
  └─ Database constraints

@ManyToOne
  └─ Relationship: many trades can belong to one user

@PrePersist
  └─ Hook: before INSERT, auto-set timestamp

@Scheduled(fixedDelay=5000)
  └─ Run method every 5 seconds in background

@Lock(LockModeType.PESSIMISTIC_WRITE)
  └─ Prevent concurrent updates (row-level lock)

@Autowired
  └─ Dependency injection (Spring provides instance)
```

---

## 6. KEY DESIGN PATTERNS

### Pattern 1: Layered Architecture
```
Request → Controller → Service → Repository → Database
Response ← (formatted) ← (business logic) ← (SQL)
```

### Pattern 2: Repository Pattern (Data Access Abstraction)
```
// Spring generates SQL automatically
interface UserRepository extends JpaRepository<User, Long> {
  Optional<User> findByUsername(String username);
  // Spring generates: SELECT * FROM users WHERE username = ?
}
```

### Pattern 3: Transactional Atomicity
```
@Transactional
void buyStock(...) {
  // Begin transaction
  deductBalance();
  createPosition();
  recordTrade();
  // Commit OR Rollback (all-or-nothing)
}
```

### Pattern 4: DTO (Data Transfer Object)
```
// Service returns enriched DTO, not raw entity
public class WatchlistItemDTO {
  String ticker;
  String stockName;
  BigDecimal currentPrice;
  double dailyChangePercent;  // Calculated here
}
// Decouples API contract from database schema
```

### Pattern 5: Scheduled Tasks (Background Processing)
```
@Scheduled(fixedDelay = 5000)  // Every 5 seconds
void checkStopLosses() {
  // Runs in background thread
  // Doesn't block user requests
}
```

---

## 7. INTERVIEW QUESTIONS & ANSWERS

### Q: "Explain transaction handling"
**A:** All buy/sell operations use `@Transactional`. If any step fails (DB connection lost, insufficient shares, etc.), the entire transaction ROLLBACKS. Example: if we deduct balance but crash before creating position, money refunded automatically. ACID guarantee.

### Q: "How do you prevent double-booking (race conditions)?"
**A:** Row-level locking + transaction isolation. When User A reads position at qty=100, User B also reads 100. Both calculate new qty and try to update. Database locks the row, so User B waits for A to commit first, then B re-reads (qty=150) and calculates correctly.

### Q: "Why use BigDecimal instead of double?"
**A:** Floating-point precision loss. `double 10000 - 9999.99 = 0.010000000002` (wrong). `BigDecimal` maintains exact cents. Critical for financial systems.

### Q: "How do you stop-loss detection without polling?"
**A:** `@Scheduled` runs every 5 seconds checking all positions with stop_loss_price IS NOT NULL. If price ≤ stop_loss, auto-exec sellStock(). All transactional, atomic, auditable.

### Q: "What would break if you deleted the Trade table?"
**A:** Ledger history gone. Users can't see transaction history. Journal analysis relies on trade history to detect overtrading. But core trading still works (positions would survive).

### Q: "How would you scale this to 1M users?"
**A:** 
1. **DB:** Read replicas (market data), primary for writes, batch trades
2. **API:** Load balancer + multiple servers behind Redis session store
3. **Scheduled tasks:** Move to message queue (RabbitMQ) with multiple workers
4. **Cache:** Redis for stock list (10 tickers = 10 queries → 1)
5. **Prices:** WebSocket broadcast instead of client polling

### Q: "Why no JWT, just sessions?"
**A:** MVP simplicity. Sessions are stateful (revocation instant), JWT is stateless (more scalable but simpler for 1 server).

---

## 8. KEY METRICS & THRESHOLDS

```
Discipline Scoring Rules:
├─ Missing Stop-Loss: -10 per position (max -30)
│  └─ If 3+ positions without SL → warning
├─ Overtrading: -15 if > 5 trades in 24 hours
│  └─ Indicates emotional trading
├─ Disposition Effect: -15 if holding > 15% loss + recent sells
│  └─ Pattern: selling winners, holding losers
└─ Concentration Risk: -10 per position > 25% portfolio
   └─ Limit: 25% max per stock (diversification rule)

Grade Mapping:
├─ Score ≥ 90 →  A (Compounding Expert)
├─ Score ≥ 75 →  B (Prudent Investor)
├─ Score ≥ 60 →  C (Emotional Trader)
└─ Score < 60 →  D (Gambler Territory)

Market Simulation:
├─ Init: Random walk ±1.5% daily for 30-day history
├─ Tick: ±0.15% every 5 seconds (0.5% daily equivalent)
└─ Floor: $1.00 minimum (stocks don't go to zero)

Monitoring:
├─ Stop-Loss Check: Every 5 seconds
├─ Price Update: Every 5 seconds
├─ Session Timeout: 30 minutes (default HttpSession)
└─ Database Pool: 10 connections (default HikariCP)
```

---

## 9. CRITICAL FORMULAS

### 1. Average Buy Price (Cost Basis)
```
newAvgPrice = (oldQty × oldAvg + newQty × newPrice) / (oldQty + newQty)
Example: (100×150 + 50×160) / 150 = $153.33
```

### 2. P&L Calculation
```
P&L = (Current Market Price - Average Buy Price) × Quantity
Example: ($175 - $153.33) × 100 = $2,166.70 gain
```

### 3. Portfolio Composition
```
Portfolio Value = Cash + Equity Value
Equity Value = Σ(Position Quantity × Current Stock Price)
Example: $1,000 cash + (100 × $175) = $18,500 total
```

### 4. Position Allocation %
```
Allocation % = (Position Value / Portfolio Value) × 100
Example: ($17,500 / $18,500) × 100 = 94.6% in AAPL (too concentrated!)
```

### 5. Daily Change %
```
Daily Change % = ((Current - Last) / Last) × 100
Example: (($175 - $174) / $174) × 100 = 0.57% up
```

---

## 10. COMMON PITFALLS & SOLUTIONS

| Pitfall | Problem | Solution |
|---------|---------|----------|
| No @Transactional | Partial updates on crash | Add @Transactional to service methods |
| Using double | Cents lost | Use BigDecimal(precision=15, scale=2) |
| No stop-loss | Portfolio bleeds | Implemented @Scheduled watchdog |
| Overtrading | Emotional decisions | Discipline scoring detects & coaches |
| No unique constraint | Duplicate watchlist entries | Added UNIQUE(user_id, ticker) |
| Plain text passwords | Security risk | Switch to BCrypt (10-min refactor) |
| No error handling | Crashes | Added try/catch in controllers |
| Hardcoded strings | "BYU" typo accepted | Use Enums (TradeType.BUY, .SELL) |
| Polling all positions | N+1 query problem | Use findByStopLossPriceIsNotNull() |
| Session in memory | Doesn't scale | Switch to Redis for multiple servers |

---

## 11. FILES & THEIR PURPOSE

| File | Purpose | Key Classes |
|------|---------|-------------|
| **AuthController.java** | User onboarding | register(), login(), logout(), status() |
| **PortfolioController.java** | Trading operations | buyStock(), sellStock(), updateStopLoss() |
| **WatchlistController.java** | Favorite stocks | addToWatchlist(), removeFromWatchlist(), getWatchlist() |
| **MarketController.java** | Public price data | getAllStocks(), getStockDetails(), getHistory() |
| **JournalController.java** | Behavioral analysis | getJournalAnalysis() |
| **PortfolioService.java** | Trading logic | Core buy/sell, @Scheduled monitoring |
| **WatchlistService.java** | Watchlist mgmt | Add/remove/list with enriched DTOs |
| **MarketService.java** | Price simulation | initMarket(), @Scheduled tickMarket() |
| **JournalService.java** | Discipline scoring | analyzePortfolio(), 4 behavioral rules |
| **User.java** | Entity | id, username, password, balance |
| **Position.java** | Entity | Holdings record | user, ticker, qty, avg_price, stop_loss |
| **Trade.java** | Entity | Immutable log | user, ticker, type, qty, price, timestamp |
| **Stock.java** | Entity | Market data | ticker, currentPrice, lastPrice |
| **Watchlist.java** | Entity | Starred stocks | user, ticker, added_at |
| **StockHistory.java** | Entity | Price history | ticker, price, date |

---

## 12. CRITICAL SQL OPERATIONS

```java
// Most important queries (Spring generates these)

// 1. Find user by ID (authentication)
userRepository.findById(userId)

// 2. Find user's positions (portfolio view)
positionRepository.findByUser(user)

// 3. Find specific position (buy/sell validation)
positionRepository.findByUserAndTicker(user, ticker)

// 4. Find all positions with stop-loss (scheduler)
positionRepository.findByStopLossPriceIsNotNull()

// 5. Find user's trades (ledger, overtrading check)
tradeRepository.findByUserOrderByTimestampDesc(user)

// 6. Find recent trades (overtrading detection)
tradeRepository.findByUserAndTimestampAfter(user, 24hAgo)

// 7. Find watchlist items (UI list)
watchlistRepository.findByUserOrderByAddedAtDesc(user)

// 8. Check duplicate watchlist entry
watchlistRepository.existsByUserAndTicker(user, ticker)

// 9. Get all stocks (market view)
stockRepository.findAll()

// 10. Get stock history (charting)
stockHistoryRepository.findByTickerOrderByDateAsc(ticker)
```

---

## 13. TESTING STRATEGY

```
Unit Tests (80%)
├─ Test service logic in isolation
├─ Mock repositories
├─ Fast (< 1ms per test)
└─ Example: testAverageBuyPriceRecalculation()

Integration Tests (15%)
├─ Test full transaction (service → repo → DB)
├─ Real database (H2 in-memory)
├─ Medium (10-100ms per test)
└─ Example: testBuyStockEnd2End()

E2E Tests (5%)
├─ Test full user flow (UI → API → DB)
├─ Real browser
├─ Slow (1-10s per test)
└─ Example: testBuyStockFromTradingView()

Key Testing Principles:
├─ Test behavior, not implementation
├─ Use ArgumentCaptor to verify side-effects
├─ Mock external dependencies
└─ Assert on outcomes, not method calls
```

---

## 14. DEPLOYMENT CHECKLIST

- [ ] Remove `@EnableScheduling` if running multiple instances (prevent duplicate task runs)
- [ ] Configure Redis for session store (vs. local memory)
- [ ] Migrate passwords to BCrypt
- [ ] Add Spring Security (@EnableWebSecurity)
- [ ] Enable HTTPS (SSL/TLS)
- [ ] Set up logging (SLF4J + LogBack)
- [ ] Add rate limiting (@RateLimiter)
- [ ] Configure database connection pooling (HikariCP)
- [ ] Add API documentation (Swagger/OpenAPI)
- [ ] Set up monitoring (Prometheus + Grafana)
- [ ] Implement graceful shutdown
- [ ] Add database backup strategy

---

## 15. INTERVIEW PREPARATION FINAL NOTES

**Topics to Master:**
1. ✅ Layered architecture (why separate concerns)
2. ✅ Transactional boundaries (atomicity)
3. ✅ Repository pattern (data access abstraction)
4. ✅ BigDecimal (financial precision)
5. ✅ Concurrent modifications (race conditions)
6. ✅ Scheduled tasks (background processing)
7. ✅ Session management (HTTP state)
8. ✅ Request flow (end-to-end)
9. ✅ Scaling strategies (1M users)
10. ✅ Testing pyramid

**Questions You Should Ask**
- "At what point does this architecture break?" (Answer: 1M users)
- "What would you do differently in production?" (Answer: Redis, JWT, rate limiting, etc.)
- "How do you handle X scenario?" (Be ready for edge cases)

**Red Flags to Avoid**
- "I don't know how @Transactional works"
- "We just save directly in the controller"
- "We use double for money"
- "We don't have tests"
- "We don't handle concurrent access"

**Green Flags You Should Show**
- Understanding of design patterns (Layered, Repository, DTO)
- Knowledge of transaction management (ACID)
- Awareness of scalability limits
- Experience with Spring frameworks
- Testing mindset (unit + integration)
- Ability to explain trade-offs (MVP vs. production)

---

**Created:** 2026-06-18  
**Version:** 1.0 - Complete Analysis  
**Difficulty Level:** Intermediate to Advanced  
**Study Time:** 4-6 hours for mastery


