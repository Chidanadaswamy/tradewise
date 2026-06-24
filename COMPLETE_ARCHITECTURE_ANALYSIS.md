# TradeWise Platform - Complete Architectural Analysis
## Senior Software Engineering & System Design for Interview Preparation

---

## TABLE OF CONTENTS
1. Project Overview
2. Architectural Design Patterns
3. Layer-by-Layer Analysis
4. Request Flow Diagrams
5. Engineering Decisions & Trade-offs
6. Interview Talking Points

---

# SECTION 1: PROJECT OVERVIEW

## Project Identity
**Name:** TradeWise - Paper Trading Simulation Platform  
**Description:** A beautifully simple paper-trading platform inspired by Steve Jobs' ideology: elegance, simplicity, and focus on discipline.  
**Technology Stack:** Spring Boot 3.2.5 | Java 21 | PostgreSQL | JPA/Hibernate | REST API

## Core Value Proposition
TradeWise teaches trading discipline through behavioral analysis. It's NOT just about buying/selling stocks—it's about coaching users on behavioral patterns (stop-loss discipline, overtrading, disposition effect, concentration risk).

### Key Differentiators:
- **Discipline Scoring System (0-100)** - Quantifies trading behavior
- **Automated Stop-Loss Watchdog** - Scheduled task checks market 24/7
- **Behavioral Coaching Engine** - Real-time insights on trading mistakes
- **Live Market Simulation** - Prices update every 5 seconds
- **Minimal Learning Curve** - Every user starts with $10,000 virtual wallet

---

## Database Schema Overview

```
USERS
├── id (PK)
├── username (UNIQUE)
├── password (plain text - MVP phase)
└── balance (BigDecimal - $10,000 starting)

STOCKS
├── ticker (PK: AAPL, MSFT, etc.)
├── name (Apple Inc., Microsoft Corp., etc.)
├── currentPrice (live, updated every 5 seconds)
├── lastPrice (for daily change %)

POSITIONS
├── id (PK)
├── user_id (FK)
├── ticker (FK reference, not explicit FK in code)
├── quantity (current holdings)
├── average_buy_price (cost basis for P&L calculation)
├── stop_loss_price (nullable - user can set later)
└── UNIQUE(user_id, ticker) - one position per user per stock

TRADES
├── id (PK)
├── user_id (FK)
├── ticker
├── trade_type (BUY|SELL)
├── quantity
├── price (execution price)
├── stop_loss_price (what was set at execution)
├── timestamp (auto-set via @PrePersist)

STOCK_HISTORY
├── id (PK)
├── ticker
├── price (historical daily price)
├── date (LocalDate for charting)

WATCHLIST
├── id (PK)
├── user_id (FK)
├── ticker
├── added_at (timestamp)
└── UNIQUE(user_id, ticker) - can't star same stock twice
```

---

# SECTION 2: ARCHITECTURAL DESIGN PATTERNS

## Pattern #1: Layered Architecture (N-Tier)
TradeWise uses the classic **4-layer REST API pattern**:

```
┌─────────────────────────────────────┐
│      PRESENTATION LAYER             │
│  (HTML/CSS/JavaScript Frontend)     │
└────────────────┬────────────────────┘
                 │ HTTP/REST
┌────────────────▼────────────────────┐
│    API LAYER (Controllers)          │
│  (@RestController, @RequestMapping) │
│  - Request validation               │
│  - Session management               │
│  - Response formatting              │
└────────────────┬────────────────────┘
                 │ Method calls
┌────────────────▼────────────────────┐
│    BUSINESS LOGIC LAYER (Services)  │
│  (@Service, @Transactional)         │
│  - Core algorithms                  │
│  - Calculations (avg buy price)     │
│  - Behavioral rules                 │
│  - Scheduling (Scheduled tasks)     │
└────────────────┬────────────────────┘
                 │ ORM queries
┌────────────────▼────────────────────┐
│  PERSISTENCE LAYER (Repositories)   │
│  (JpaRepository interfaces)          │
│  - Database queries (abstracted)     │
│  - Entity mapping                   │
└────────────────┬────────────────────┘
                 │ JDBC
┌────────────────▼────────────────────┐
│   DATA LAYER (PostgreSQL Database)  │
└─────────────────────────────────────┘
```

**Why This Pattern?**
- **Separation of Concerns** - Each layer has ONE responsibility
- **Testability** - Mock service layer to test controllers in isolation
- **Reusability** - Service can be called from multiple controllers
- **Maintainability** - Changes to DB don't affect controller logic

---

## Pattern #2: Repository Pattern (Data Access Abstraction)
**What it solves:** Decouples business logic from database implementation details.

### Example:
```java
// Before Repository Pattern:
// Hard to test, tightly coupled to SQL
Controller controller = new Controller() {
    boolean userExists = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM users WHERE username = ?", 
        loginRequest.username
    ) > 0;
};

// After Repository Pattern:
// Testable, can swap implementations
UserRepository userRepository = ...;
Optional<User> user = userRepository.findByUsername("john");
```

**TradeWise Implementation:**
```java
@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    // Spring Data JPA generates SQL automatically
}
```

**Query Methods Generated Automatically:**
| Method Name | Generated Query |
|---|---|
| `findById(Long)` | `SELECT * FROM users WHERE id = ?` |
| `findByUsername(String)` | `SELECT * FROM users WHERE username = ?` |
| `findByUserAndTicker()` | `SELECT * FROM positions WHERE user_id = ? AND ticker = ?` |
| `findByStopLossPriceIsNotNull()` | `SELECT * FROM positions WHERE stop_loss_price IS NOT NULL` |

**Why Repositories Are Genius:**
- Reduce boilerplate SQL
- Prevent SQL injection (parameterized queries)
- Enable easy testing with mock repositories
- Spring Data JPA can generate ~80% of queries

---

## Pattern #3: Service Layer (Business Logic Encapsulation)
**What it solves:** Prevents controllers from becoming "fat" and dealing with complex business logic.

### Example: PortfolioService.buyStock()

**Problem:** If we put this logic in controller:
```java
// BAD: Business logic mixed with HTTP concerns
@PostMapping("/buy")
public ResponseEntity<?> buy(@RequestBody BuyRequest req, HttpSession session) {
    User user = userRepository.findById(userId).orElseThrow();
    Stock stock = stockRepository.findById(req.ticker).orElseThrow();
    BigDecimal totalCost = stock.getCurrentPrice().multiply(req.quantity);
    if (user.getBalance().compareTo(totalCost) < 0) throw exception;
    user.setBalance(user.getBalance().subtract(totalCost));
    userRepository.save(user);
    Optional<Position> posOpt = positionRepository.findByUserAndTicker(user, req.ticker);
    Position position;
    if (posOpt.isPresent()) {
        position = posOpt.get();
        BigDecimal oldQty = position.getQuantity();
        BigDecimal newQty = oldQty.add(req.quantity);
        BigDecimal totalSpent = oldQty.multiply(position.getAverageBuyPrice())
            .add(req.quantity.multiply(stock.getCurrentPrice()));
        BigDecimal newAvgPrice = totalSpent.divide(newQty, 2, RoundingMode.HALF_UP);
        position.setQuantity(newQty);
        position.setAverageBuyPrice(newAvgPrice);
    } else {
        position = new Position(user, req.ticker, req.quantity, stock.getCurrentPrice(), null);
    }
    positionRepository.save(position);
    Trade trade = new Trade(user, req.ticker, "BUY", req.quantity, stock.getCurrentPrice(), null);
    tradeRepository.save(trade);
    return ResponseEntity.ok(trade);
}
```

**Solution:**
```java
// Service Layer: Pure business logic, testable, reusable
@Service
public class PortfolioService {
    @Transactional
    public Trade buyStock(Long userId, String ticker, BigDecimal quantity, BigDecimal stopLossPrice) {
        User user = userRepository.findById(userId).orElseThrow();
        Stock stock = stockRepository.findById(ticker).orElseThrow();
        
        // Business calculations
        BigDecimal currentPrice = stock.getCurrentPrice();
        BigDecimal totalCost = currentPrice.multiply(quantity).setScale(2, RoundingMode.HALF_UP);
        
        // Business validation
        if (user.getBalance().compareTo(totalCost) < 0) {
            throw new IllegalStateException("Insufficient balance...");
        }
        
        // Business state changes
        user.setBalance(user.getBalance().subtract(totalCost));
        userRepository.save(user);
        
        // Position management
        Optional<Position> positionOpt = positionRepository.findByUserAndTicker(user, ticker);
        Position position;
        if (positionOpt.isPresent()) {
            position = positionOpt.get();
            // Average Buy Price Formula: (oldQty × oldAvg + newQty × price) / (oldQty + newQty)
            BigDecimal oldQty = position.getQuantity();
            BigDecimal newQty = oldQty.add(quantity);
            BigDecimal totalSpent = oldQty.multiply(position.getAverageBuyPrice())
                .add(quantity.multiply(currentPrice));
            BigDecimal newAvgPrice = totalSpent.divide(newQty, 2, RoundingMode.HALF_UP);
            position.setQuantity(newQty);
            position.setAverageBuyPrice(newAvgPrice);
            if (stopLossPrice != null) position.setStopLossPrice(stopLossPrice);
        } else {
            position = new Position(user, ticker, quantity, currentPrice, stopLossPrice);
        }
        positionRepository.save(position);
        
        // Audit log
        Trade trade = new Trade(user, ticker, "BUY", quantity, currentPrice, stopLossPrice);
        return tradeRepository.save(trade);
    }
}

// Controller: Clean, focused on HTTP concerns
@RestController
@RequestMapping("/api/portfolio")
public class PortfolioController {
    @Autowired
    private PortfolioService portfolioService;
    
    @PostMapping("/buy")
    public ResponseEntity<?> buyStock(@RequestBody BuyRequest request, HttpSession session) {
        Long userId = (Long) session.getAttribute("userId");
        if (userId == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        
        if (request.ticker == null || request.quantity == null || 
            request.quantity.compareTo(BigDecimal.ZERO) <= 0) {
            return ResponseEntity.badRequest().body("Invalid input");
        }
        
        try {
            Trade trade = portfolioService.buyStock(
                userId, 
                request.ticker.toUpperCase(), 
                request.quantity, 
                request.stopLossPrice
            );
            return ResponseEntity.ok(trade);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
```

**Benefits:**
- **Testability:** Test service in isolation with mock repositories
- **Reusability:** Other controllers can call same service method
- **Maintainability:** One place to change business logic
- **Clarity:** Controller reads like HTTP layer, Service reads like business rules

---

## Pattern #4: Scheduled Tasks (Background Processing)
**What it solves:** Long-running tasks that shouldn't block user requests.

### Implementation:
```java
@Scheduled(fixedDelay = 5000)  // Run every 5 seconds
@Transactional
public void checkStopLosses() {
    List<Position> positionsWithStopLoss = positionRepository.findByStopLossPriceIsNotNull();
    for (Position position : positionsWithStopLoss) {
        Optional<Stock> stockOpt = stockRepository.findById(position.getTicker());
        if (stockOpt.isPresent()) {
            Stock stock = stockOpt.get();
            BigDecimal currentPrice = stock.getCurrentPrice();
            BigDecimal stopLossPrice = position.getStopLossPrice();
            
            // Trigger automatic sell if stop-loss breached
            if (currentPrice.compareTo(stopLossPrice) <= 0) {
                try {
                    sellStock(position.getUser().getId(), position.getTicker(), position.getQuantity());
                } catch (Exception e) {
                    System.err.println("Failed to execute stop-loss: " + e.getMessage());
                }
            }
        }
    }
}
```

**Why This Pattern?**
- **Non-blocking:** Doesn't pause API responses
- **Event-driven:** Simulates proper market behavior
- **Reliable:** Runs continuously in background
- **Transactional:** Each automatic sell is atomic (all or nothing)

**Configuration:**
```java
// Application.java
@EnableScheduling  // Activate scheduled task processing
public class TradeWiseApplication { }

// application.properties
spring.task.scheduling.pool.size=2  // Thread pool for background tasks
```

---

## Pattern #5: Transactional Integrity (ACID)
**What it solves:** Ensures data consistency when multiple operations occur together.

### Example Problem:
Without `@Transactional`, this sequence could fail midway:

```
1. Deduct $500 from user balance
2. [CRASH] ← If crash here, money is gone but no stock purchase!
3. Create position record
4. Record trade log
```

### Solution:
```java
@Transactional  // Spring handles commit/rollback
public Trade buyStock(Long userId, String ticker, ...) {
    // Step 1
    user.setBalance(user.getBalance().subtract(totalCost));
    userRepository.save(user);
    
    // Step 2
    positionRepository.save(position);
    
    // Step 3
    Trade trade = new Trade(...);
    tradeRepository.save(trade);
    
    // If ANY step fails, ALL changes rollback automatically
    // Database will be left in consistent state
    return tradeRepository.save(trade);
}
```

**ACID Guarantees:**
- **Atomicity:** All-or-nothing (no partial updates)
- **Consistency:** Data rules maintained (unique constraints, foreign keys)
- **Isolation:** Concurrent requests don't interfere
- **Durability:** Once committed, survives crashes

---

## Pattern #6: DTO (Data Transfer Object) for API Responses
**What it solves:** Removes entity clutter, adding domain logic without exposing schema.

### Example:
```java
@Service
public class WatchlistService {
    // DTO: Only exposes what frontend needs
    public static class WatchlistItemDTO {
        public String ticker;
        public String stockName;
        public BigDecimal currentPrice;
        public double dailyChangePercent;  // Calculated, not stored
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
    
    public List<WatchlistItemDTO> getWatchlist(Long userId) {
        User user = userRepository.findById(userId).orElseThrow();
        List<Watchlist> entries = watchlistRepository.findByUserOrderByAddedAtDesc(user);
        List<WatchlistItemDTO> items = new ArrayList<>();
        
        for (Watchlist entry : entries) {
            Optional<Stock> stockOpt = stockRepository.findById(entry.getTicker());
            if (stockOpt.isPresent()) {
                Stock stock = stockOpt.get();
                // Calculate daily change %
                double changePercent = 0.0;
                if (stock.getLastPrice().compareTo(BigDecimal.ZERO) != 0) {
                    changePercent = stock.getCurrentPrice()
                        .subtract(stock.getLastPrice())
                        .divide(stock.getLastPrice(), 6, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100))
                        .doubleValue();
                }
                items.add(new WatchlistItemDTO(
                    stock.getTicker(),
                    stock.getName(),
                    stock.getCurrentPrice(),
                    changePercent,  // This calculation is done here, not in entity
                    entry.getAddedAt()
                ));
            }
        }
        return items;
    }
}

// Controller
@GetMapping
public ResponseEntity<?> getWatchlist(HttpSession session) {
    Long userId = (Long) session.getAttribute("userId");
    if (userId == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    
    List<WatchlistService.WatchlistItemDTO> items = watchlistService.getWatchlist(userId);
    return ResponseEntity.ok(items);  // Only DTOs sent to frontend
}
```

**Benefits:**
- **Security:** Doesn't expose database schema details
- **Flexibility:** Can return calculated/enriched data without touching entity
- **Decoupling:** Entity changes don't break API contracts
- **Performance:** Include only needed fields in response

---

# SECTION 3: LAYER-BY-LAYER ANALYSIS

---

## LAYER 1: CONTROLLER LAYER (`controller/` package)

### Purpose
**Bridge between HTTP requests and business logic.** Controllers translate HTTP concerns into service method calls and format responses for the client.

### Key Responsibilities
1. **Request Validation** - Verify inputs before passing to service
2. **Authentication Check** - Extract `userId` from session
3. **Error Handling** - Catch exceptions and return appropriate HTTP status codes
4. **Response Formatting** - Convert objects to JSON/HTTP responses

---

## Controller #1: AuthController.java

**What it does:** Handles user onboarding (register, login, logout, status)

**Inputs:**
- Registration: `{ username: String, password: String }`
- Login: `{ username: String, password: String }`

**Processing:**
1. **Registration**
   - Validate inputs (not null, not empty)
   - Check if username already exists
   - Create new User with `$10,000` starting balance
   - Auto-login by storing `userId` in session
   
2. **Login**
   - Find user by username
   - Verify password (plain text check - MVP phase, not production-safe)
   - Store `userId` in session cookie
   
3. **Status Check**
   - Retrieve session `userId`
   - Validate user still exists
   - Return user data

**Outputs:**
- Status 200: User object + session cookie
- Status 400: Invalid credentials/input
- Status 401: Not authenticated
- Status 409: Username conflict

**Design Decision: Plain Text Passwords**
```java
// Current (MVP Phase)
@PostMapping("/register")
public ResponseEntity<?> register(@RequestBody AuthRequest request, HttpSession session) {
    User user = new User(request.username, request.password);  // Stored as-is
    userRepository.save(user);
    session.setAttribute("userId", user.getId());
    return ResponseEntity.ok(user);
}

// Why this is acceptable for MVP:
// 1. Paper trading (no real money) - limited risk
// 2. Fast iteration - focus on features, not security theater
// 3. Easy to upgrade later (bcrypt isn't complex)

// Production version would use:
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
@Service
public class AuthService {
    private BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    
    public void registerUser(String username, String password) {
        String hashedPassword = encoder.encode(password);
        User user = new User(username, hashedPassword);
        userRepository.save(user);
    }
}
```

---

## Controller #2: PortfolioController.java

**What it does:** Handles trading operations (buy, sell, update stop-loss, fetch positions/trades)

### BUY Flow
```
Frontend Request:         Controller:              Service:                  Repository:
POST /buy                 1. Validate             1. Fetch user            1. Find user by ID
{                            inputs               2. Fetch stock           2. Find position
  ticker: "AAPL",         2. Extract              3. Calculate cost        3. Find/create
  qty: 10,                   userId               4. Check balance             position
  stopLoss: 170           3. Call service         5. Update                4. Save position
}                         4. Format                  balance              5. Record trade
                             response            6. Calculate avg price   6. Save trade
                                                7. Save changes
                                                8. Return trade
```

**Key Calculation: Average Buy Price (Cost Basis)**
```java
// When buying same stock multiple times:
Position p = positionRepository.findByUserAndTicker(user, ticker).get();

// Old holdings: 100 shares at $150/share = $15,000 invested
// New purchase: 50 shares at $155/share = $7,750 invested
// Total: $22,750 invested, 150 shares

BigDecimal oldQty = p.getQuantity();              // 100
BigDecimal oldAvg = p.getAverageBuyPrice();       // 150.00
BigDecimal newQty = oldQty.add(qty);              // 150
BigDecimal totalSpent = oldQty.multiply(oldAvg)   // 100 × 150
                        .add(qty.multiply(price)); // + 50 × 155 = 22,750

BigDecimal newAvgPrice = totalSpent.divide(newQty, 2, RoundingMode.HALF_UP);
// newAvgPrice = 22,750 / 150 = $151.67

p.setAverageBuyPrice(newAvgPrice);

// Why this matters for interviews:
// - Tests understanding of financial calculations
// - Proper rounding (RoundingMode.HALF_UP, scale=2 decimals)
// - BigDecimal instead of double (precision matters with money!)
```

### SELL Flow
```
Prerequisites:
- User must own shares (position exists)
- User must own enough shares (qty <= position.quantity)

Flow:
1. Calculate sale proceeds (quantity × current stock price)
2. Add proceeds to user balance
3. Reduce position quantity
4. If qty = 0, delete position; else keep partial position
5. Record SELL trade in audit log
```

### Stop-Loss Update
```
PUT /api/portfolio/stoploss
{
  ticker: "AAPL",
  stopLossPrice: 170.00
}

Processing:
1. Find user's position in AAPL
2. Update stop_loss_price field
3. Save to database

Note: This doesn't execute a trade immediately.
      The background scheduler (@Scheduled) monitors all stop-losses
      and executes sells when price drops to or below the limit.
```

---

## Controller #3: WatchlistController.java

**What it does:** Manage user's watchlist (starred stocks to monitor)

### Operations
**POST /api/watchlist/add**
- Input: `{ ticker: "AAPL" }`
- Validation: Stock exists, not already in watchlist
- Output: `{ message: "Added to watchlist", ticker: "AAPL" }`
- Handles: `409 CONFLICT` if duplicate, `400 BAD REQUEST` if stock doesn't exist

**DELETE /api/watchlist/remove/{ticker}**
- Removes user × ticker combination from watchlist
- Uses soft delete pattern: `deleteByUserAndTicker(user, ticker)`

**GET /api/watchlist**
- Returns enriched DTOs with:
  - Current price
  - Daily change % (calculated from `currentPrice - lastPrice`)
  - Stock name
  - Added timestamp

**GET /api/watchlist/count**
- Returns watchlist size (used for UI badge)

**GET /api/watchlist/check/{ticker}**
- Boolean check: is this stock starred?

### Design Pattern: Unique Constraint
```java
@Entity
@Table(name = "watchlist", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"user_id", "ticker"})  // Can't add same stock twice
})
public class Watchlist {
    // ...
}

// In service, prevent duplicate adds:
@Transactional
public Watchlist addToWatchlist(Long userId, String ticker) {
    User user = userRepository.findById(userId).orElseThrow();
    
    String normalizedTicker = ticker.toUpperCase();
    
    if (watchlistRepository.existsByUserAndTicker(user, normalizedTicker)) {
        throw new IllegalStateException("Stock already in watchlist");
    }
    
    Watchlist entry = new Watchlist(user, normalizedTicker);
    return watchlistRepository.save(entry);
}
```

---

## Controller #4: MarketController.java

**What it does:** Public market data (no auth required)

### Operations
**GET /api/market/stocks**
- Returns all stocks with live prices
- Used to populate trade view stock list

**GET /api/market/stocks/{ticker}**
- Returns single stock detail (price, change %)

**GET /api/market/stocks/{ticker}/history**
- Returns 30 days of historical prices
- Used to render price chart in trade view

---

## Controller #5: JournalController.java

**What it does:** Behavioral analysis and coaching insights

**GET /api/journal/analysis**
- Returns `JournalSummary` object containing:
  - Discipline score (0-100)
  - Letter grade (A, B, C, D)
  - List of `CoachingInsight` objects

**Design: Nested DTOs for Complex Returns**
```java
public static class JournalSummary {
    public String score;                           // "A", "B", "C", "D"
    public List<CoachingInsight> insights;         // List of issues/warnings
    public int disciplineScore;                    // 0-100
}

public static class CoachingInsight {
    public String ruleName;                        // MISSING_STOP_LOSS, OVERTRADING, etc.
    public String severity;                        // WARNING, DANGER, INFO
    public String title;                           // "Missing Safety Net (AAPL)"
    public String description;                     // Detailed explanation
    public String recommendation;                  // Actionable advice
}
```

---

## LAYER 2: SERVICE LAYER (`service/` package)

### Purpose
**Pure business logic, decoupled from HTTP.** Each service handles one domain:

| Service | Domain | Responsibility |
|---------|--------|---|
| `PortfolioService` | Trading | Buy/sell stocks, manage positions, check stop-losses |
| `WatchlistService` | Monitoring | Add/remove/list starred stocks, enrich with market data |
| `MarketService` | Market Data | Initialize stocks, simulate price movements, provide history |
| `JournalService` | Analytics | Analyze portfolio, compute discipline score, generate insights |

---

## Service #1: PortfolioService.java

### Core Methods

#### 1. buyStock(userId, ticker, quantity, stopLossPrice)
```java
@Transactional  // Atomic: all steps succeed or all rollback
public Trade buyStock(Long userId, String ticker, BigDecimal quantity, BigDecimal stopLossPrice) {
    // STEP 1: Validate preconditions
    User user = userRepository.findById(userId)
        .orElseThrow(() -> new IllegalArgumentException("User not found"));
    Stock stock = stockRepository.findById(ticker)
        .orElseThrow(() -> new IllegalArgumentException("Stock not found: " + ticker));
    
    // STEP 2: Business calculation
    BigDecimal currentPrice = stock.getCurrentPrice();
    BigDecimal totalCost = currentPrice.multiply(quantity).setScale(2, RoundingMode.HALF_UP);
    
    // STEP 3: Business validation
    if (user.getBalance().compareTo(totalCost) < 0) {
        throw new IllegalStateException(
            "Insufficient balance. Required: $" + totalCost + 
            ", Available: $" + user.getBalance()
        );
    }
    
    // STEP 4: Update user cash balance (debit)
    user.setBalance(user.getBalance().subtract(totalCost));
    userRepository.save(user);
    
    // STEP 5: Update or create position (cost basis tracking)
    Optional<Position> positionOpt = positionRepository.findByUserAndTicker(user, ticker);
    Position position;
    if (positionOpt.isPresent()) {
        // Adding to existing position: recalculate average buy price
        position = positionOpt.get();
        BigDecimal oldQty = position.getQuantity();
        BigDecimal newQty = oldQty.add(quantity);
        
        // Formula: (oldQty × oldAvg + newQty × price) / (oldQty + newQty)
        BigDecimal totalSpent = oldQty.multiply(position.getAverageBuyPrice())
            .add(quantity.multiply(currentPrice));
        BigDecimal newAvgPrice = totalSpent.divide(newQty, 2, RoundingMode.HALF_UP);
        
        position.setQuantity(newQty);
        position.setAverageBuyPrice(newAvgPrice);
        if (stopLossPrice != null) {
            position.setStopLossPrice(stopLossPrice);
        }
    } else {
        // New position: cost basis = current price
        position = new Position(user, ticker, quantity, currentPrice, stopLossPrice);
    }
    positionRepository.save(position);
    
    // STEP 6: Record audit trail (for journal/ledger)
    Trade trade = new Trade(user, ticker, "BUY", quantity, currentPrice, stopLossPrice);
    return tradeRepository.save(trade);
}
```

**Interview Talking Points:**
- Why `@Transactional`? To guarantee atomicity - all steps commit or all rollback
- Why `BigDecimal`? Money requires precision; `double` loses cents due to floating-point rounding
- Cost basis calculation? Tracks average purchase price for P&L reporting
- Why save user BEFORE position? Order doesn't matter (within same transaction), but shows intent

---

#### 2. sellStock(userId, ticker, quantity)
```java
@Transactional
public Trade sellStock(Long userId, String ticker, BigDecimal quantity) {
    // STEP 1: Validate preconditions
    User user = userRepository.findById(userId).orElseThrow();
    Stock stock = stockRepository.findById(ticker).orElseThrow();
    
    // STEP 2: Verify user holds position
    Position position = positionRepository.findByUserAndTicker(user, ticker)
        .orElseThrow(() -> new IllegalStateException("You do not hold a position in " + ticker));
    
    // STEP 3: Verify user owns enough shares
    if (position.getQuantity().compareTo(quantity) < 0) {
        throw new IllegalStateException(
            "Insufficient shares. You own: " + position.getQuantity() + 
            ", tried to sell: " + quantity
        );
    }
    
    // STEP 4: Calculate proceeds
    BigDecimal currentPrice = stock.getCurrentPrice();
    BigDecimal saleProceeds = currentPrice.multiply(quantity).setScale(2, RoundingMode.HALF_UP);
    
    // STEP 5: Update user cash balance (credit)
    user.setBalance(user.getBalance().add(saleProceeds));
    userRepository.save(user);
    
    // STEP 6: Update or delete position
    BigDecimal remainingQty = position.getQuantity().subtract(quantity);
    BigDecimal stopLoss = position.getStopLossPrice();
    
    if (remainingQty.compareTo(BigDecimal.ZERO) <= 0) {
        // Sold everything: delete position
        positionRepository.delete(position);
    } else {
        // Partial sale: keep position with reduced qty
        position.setQuantity(remainingQty);
        positionRepository.save(position);
    }
    
    // STEP 7: Record audit trail
    Trade trade = new Trade(user, ticker, "SELL", quantity, currentPrice, stopLoss);
    return tradeRepository.save(trade);
}
```

---

#### 3. checkStopLosses() - Scheduled Background Task
```java
@Scheduled(fixedDelay = 5000)  // Every 5 seconds
@Transactional
public void checkStopLosses() {
    // Query ALL positions with stop-loss set
    List<Position> positionsWithStopLoss = positionRepository.findByStopLossPriceIsNotNull();
    
    for (Position position : positionsWithStopLoss) {
        Optional<Stock> stockOpt = stockRepository.findById(position.getTicker());
        if (stockOpt.isPresent()) {
            Stock stock = stockOpt.get();
            BigDecimal currentPrice = stock.getCurrentPrice();
            BigDecimal stopLossPrice = position.getStopLossPrice();
            
            // Trigger automatic sell if current price ≤ stop-loss price
            if (currentPrice.compareTo(stopLossPrice) <= 0) {
                try {
                    System.out.println(
                        "Stop-Loss Triggered! Selling " + position.getQuantity() + 
                        " shares of " + position.getTicker() + " for User " + 
                        position.getUser().getUsername() + " at current price $" + 
                        currentPrice + " (Stop-loss set at $" + stopLossPrice + ")"
                    );
                    
                    // Execute automatic sell
                    sellStock(
                        position.getUser().getId(), 
                        position.getTicker(), 
                        position.getQuantity()
                    );
                } catch (Exception e) {
                    System.err.println(
                        "Failed to execute stop-loss sell for position ID: " + 
                        position.getId() + ". Error: " + e.getMessage()
                    );
                }
            }
        }
    }
}
```

**Why This Pattern Works:**
- **Non-blocking:** API requests aren't delayed
- **Reliable:** Runs continuously, checking every 5 seconds
- **Atomic:** Each auto-sell is transactional (if it fails, position isn't corrupted)
- **Auditable:** Each auto-sell is recorded as a SELL trade in the ledger

**Interview Talking Points:**
- How would you test scheduled tasks? Mock the clock, use `@Scheduled` with test-specific delay
- What if stop-loss triggers while user is buying? Transaction isolation prevents race conditions
- What if exception occurs during auto-sell? Caught and logged; portfolio stays consistent

---

## Service #2: WatchlistService.java

### Purpose
Manage starred stocks with **data enrichment** (add calculated fields).

### Key Innovation: DTO with Calculated Fields
```java
public static class WatchlistItemDTO {
    public String ticker;
    public String stockName;
    public BigDecimal currentPrice;
    public double dailyChangePercent;      // NOT stored; calculated
    public LocalDateTime addedAt;
}

public List<WatchlistItemDTO> getWatchlist(Long userId) {
    User user = userRepository.findById(userId).orElseThrow();
    List<Watchlist> entries = watchlistRepository.findByUserOrderByAddedAtDesc(user);
    List<WatchlistItemDTO> items = new ArrayList<>();
    
    for (Watchlist entry : entries) {
        Optional<Stock> stockOpt = stockRepository.findById(entry.getTicker());
        if (stockOpt.isPresent()) {
            Stock stock = stockOpt.get();
            
            // Calculate daily change % = (current - last) / last × 100
            double changePercent = 0.0;
            if (stock.getLastPrice().compareTo(BigDecimal.ZERO) != 0) {
                changePercent = stock.getCurrentPrice()
                    .subtract(stock.getLastPrice())
                    .divide(stock.getLastPrice(), 6, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .doubleValue();
            }
            
            items.add(new WatchlistItemDTO(
                stock.getTicker(),
                stock.getName(),
                stock.getCurrentPrice(),
                changePercent,          // Calculated in service layer
                entry.getAddedAt()
            ));
        }
    }
    
    return items;
}
```

**Why Calculate in Service, Not Entity?**
1. **Single Responsibility:** Entity = data model, Service = business logic
2. **Testability:** Can test calculation independently
3. **Performance:** Don't calculate if not needed
4. **Flexibility:** Can call same Repository from multiple services with different DTOs

---

## Service #3: MarketService.java

### Responsibility #1: Initialize Market
```java
@PostConstruct  // Runs automatically when Spring starts the application
@Transactional
public void initMarket() {
    if (stockRepository.count() == 0) {  // Only run once
        for (Map.Entry<String, StockInit> entry : CURATED_STOCKS.entrySet()) {
            String ticker = entry.getKey();
            StockInit init = entry.getValue();
            BigDecimal basePrice = BigDecimal.valueOf(init.price).setScale(2, RoundingMode.HALF_UP);
            
            // Create stock record
            Stock stock = new Stock(ticker, init.name, basePrice, basePrice);
            stockRepository.save(stock);
            
            // Generate 30 days of mock history (backwards from yesterday)
            LocalDate today = LocalDate.now();
            BigDecimal histPrice = basePrice;
            List<StockHistory> historyList = new ArrayList<>();
            
            for (int i = 30; i >= 1; i--) {
                LocalDate date = today.minusDays(i);
                
                // Random walk: +/- 1.5% daily change
                double changePct = (random.nextDouble() * 3.0 - 1.5) / 100.0;
                BigDecimal factor = BigDecimal.valueOf(1.0 + changePct);
                histPrice = histPrice.multiply(factor).setScale(2, RoundingMode.HALF_UP);
                
                historyList.add(new StockHistory(ticker, histPrice, date));
            }
            stockHistoryRepository.saveAll(historyList);
        }
    }
}
```

**Why This Approach?**
- **One-time setup:** `@PostConstruct` runs on app startup
- **Idempotent:** Checking `count() == 0` prevents duplicate seeding
- **Realistic data:** 30 days of history with realistic volatility (±1.5% daily)

---

### Responsibility #2: Simulate Market Movements
```java
@Scheduled(fixedDelay = 5000)  // Every 5 seconds
@Transactional
public void tickMarket() {
    List<Stock> stocks = stockRepository.findAll();
    
    for (Stock stock : stocks) {
        BigDecimal currentPrice = stock.getCurrentPrice();
        
        // Random walk: +/- 0.15% per tick (smaller than daily)
        double changePct = (random.nextDouble() * 0.30 - 0.15) / 100.0;
        BigDecimal factor = BigDecimal.valueOf(1.0 + changePct);
        BigDecimal newPrice = currentPrice.multiply(factor).setScale(2, RoundingMode.HALF_UP);
        
        // Floor at $1.00 (realistic stock minimum)
        if (newPrice.compareTo(BigDecimal.ONE) < 0) {
            newPrice = BigDecimal.ONE.setScale(2, RoundingMode.HALF_UP);
        }
        
        stock.setCurrentPrice(newPrice);
        stockRepository.save(stock);
    }
}
```

**Market Simulation Philosophy:**
- **Small ticks:** ±0.15% every 5 sec = realistic noise
- **Daily equivalent:** 288 ticks/day × 0.15% ≈ 0.5% intraday range
- **Floor value:** Prevents stocks from reaching $0 (unrealistic)

**Interview Talking Points:**
- How is volatility controlled? Random walk with bounded percentage changes
- Why hourly aggregation? Could accumulate history for realistic daily candles
- How would you make this real? Call Alpha Vantage/Yahoo Finance API instead of random

---

## Service #4: JournalService.java

### The Discipline Scoring Engine

**What Problem Does It Solve?**
Novice traders have behavioral blind spots. The Journal Service analyzes portfolio and generates **actionable behavioral insights** using 4 rules:

#### Rule #1: Missing Stop-Loss
```java
// Checks every position
for (Position position : positions) {
    if (position.getStopLossPrice() == null || 
        position.getStopLossPrice().compareTo(BigDecimal.ZERO) <= 0) {
        insights.add(new CoachingInsight(
            "MISSING_STOP_LOSS",
            "WARNING",
            "Missing Safety Net (" + position.getTicker() + ")",
            "You are holding " + position.getQuantity() + 
            " shares of " + position.getTicker() + 
            " without a stop-loss order.",
            "Set a stop-loss price at 5-10% below your average purchase price ($" + 
            position.getAverageBuyPrice() + ") to protect your capital from market drawdowns."
        ));
    }
}
```

**Why This Matters?**
- Teaches risk management discipline
- Stop-losses are "insurance" for positions
- Without discipline, single bad trade can wipe out years of gains

#### Rule #2: Overtrading
```java
// Counts trades in last 24 hours
LocalDateTime twentyFourHoursAgo = LocalDateTime.now().minusDays(1);
List<Trade> recentTrades = tradeRepository.findByUserAndTimestampAfter(user, twentyFourHoursAgo);

if (recentTrades.size() > 5) {
    insights.add(new CoachingInsight(
        "OVERTRADING",
        "DANGER",
        "High Trade Frequency Detected",
        "You have executed " + recentTrades.size() + 
        " trades in the last 24 hours. Overtrading increases transaction fee drag " +
        "and often indicates emotional trading.",
        "Slow down. Try to limit yourself to a maximum of 3 trades per day. " +
        "Focus on research and patient entries rather than reacting to short-term market noise."
    ));
}
```

**Behavioral Psychology:**
- Overtrading = emotional/reactive, not strategic
- Each trade costs time and (in real trading) transaction fees
- Threshold: 5+ trades in 24 hours = danger zone

#### Rule #3: Disposition Effect
```java
// Checks for pattern: holding losers + selling winners
// (Common psychological bias where traders cling to losers hoping to break even)

for (Position position : positions) {
    BigDecimal change = currentPrice.subtract(averagePrice);
    BigDecimal pctChange = change.divide(averagePrice, 4, RoundingMode.HALF_UP)
        .multiply(BigDecimal.valueOf(100));
    
    // Flag if position down > 15%
    if (pctChange.compareTo(BigDecimal.valueOf(-15)) <= 0) {
        holdsSignificantLoser = true;
        loserTicker = position.getTicker();
        loserLossPct = pctChange.abs();
    }
}

// Check if user also sold something recently (indicates pattern)
if (holdsSignificantLoser && hasRecentSell) {
    insights.add(new CoachingInsight(
        "DISPOSITION_EFFECT",
        "DANGER",
        "Holding Declining Positions Too Long",
        "You are currently holding " + loserTicker + " which is down " + loserLossPct + "%. " +
        "You also recently closed out positions for small gains. This pattern is known as the " +
        "'Disposition Effect': selling winners too early while holding onto losers hoping they break even.",
        "Re-evaluate your thesis for " + loserTicker + ". If the fundamentals have changed, " +
        "cut your loss now. Don't fall into the trap of holding a stock just to 'get back to even'."
    ));
}
```

**Psychology Behind This:**
- Loss aversion: Humans feel pain of losses 2× stronger than gains
- Hope bias: Holding losers, hoping they bounce back
- Regret aversion: Selling winners quickly to lock in gains
- **Optimal strategy:** Cut losers fast, let winners run

#### Rule #4: Concentration Risk
```java
// Checks if any single position is > 25% of total portfolio value
for (Position position : positions) {
    BigDecimal positionValue = position.getQuantity().multiply(currentPrice);
    BigDecimal allocationPct = positionValue.divide(portfolioValue, 4, RoundingMode.HALF_UP)
        .multiply(BigDecimal.valueOf(100));
    
    if (allocationPct.compareTo(BigDecimal.valueOf(25)) > 0) {
        insights.add(new CoachingInsight(
            "CONCENTRATION_RISK",
            "WARNING",
            "High Portfolio Concentration (" + position.getTicker() + ")",
            "Position in " + position.getTicker() + " constitutes " + allocationPct + 
            "% of your entire portfolio (Limit: 25%). " +
            "High concentration makes your net worth highly sensitive to a single stock's movements.",
            "Trim your position in " + position.getTicker() + " and reallocate the cash, " +
            "or buy other curated stocks to diversify your portfolio risk."
        ));
    }
}
```

**Portfolio Theory:**
- Diversification = reduce unsystematic risk
- 25% max per position = rough rule of thumb for beginner
- Heavy concentration = "all eggs in one basket"

### Discipline Score Calculation
```java
int disciplineScore = 100;  // Start at perfect

// Deduction 1: Missing Stop-Loss (10 points per position, max 30)
long positionsWithoutStopLoss = positions.stream()
    .filter(p -> p.getStopLossPrice() == null || 
                 p.getStopLossPrice().compareTo(BigDecimal.ZERO) <= 0)
    .count();
int stopLossDeduction = (int) Math.min(positionsWithoutStopLoss * 10, 30);
disciplineScore -= stopLossDeduction;

// Deduction 2: Overtrading (15 points if trading too frequently)
if (recentTrades.size() > 5) disciplineScore -= 15;

// Deduction 3: Disposition Effect (15 points if pattern detected)
if (holdsSignificantLoser) disciplineScore -= 15;

// Deduction 4: Concentration Risk (10 points per concentrated position, max 20)
int concentrationDeduction = Math.min(concentratedPositionsCount * 10, 20);
disciplineScore -= concentrationDeduction;

// Ensure score in range [0, 100]
disciplineScore = Math.max(0, Math.min(100, disciplineScore));

// Map to letter grade
String score;
if (disciplineScore >= 90) score = "A";           // Compounding Expert
else if (disciplineScore >= 75) score = "B";      // Prudent Investor
else if (disciplineScore >= 60) score = "C";      // Emotional Trader
else score = "D";                                   // Gambler Territory
```

**Grade Semantics:**
- **A (90-100):** Master of discipline - you understand risk management
- **B (75-89):** Solid investor - minor lapses but mostly sound
- **C (60-74):** Emotional trading shows - needs more discipline
- **D (<60):** Gambler mindset - high risk of ruin

---

## LAYER 3: REPOSITORY LAYER (`repository/` package)

### Purpose
**Data access abstraction.** Isolates business logic from database implementation using Spring Data JPA.

### Repository #1: UserRepository
```java
@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
}
```

**How Spring Data JPA Works:**
1. Define method signature following naming convention
2. Spring parses method name and generates SQL automatically

| Method Signature | Generated SQL |
|---|---|
| `findByUsername(String)` | `SELECT * FROM users WHERE username = ?` |
| `findByIdAndUsername(Long, String)` | `SELECT * FROM users WHERE id = ? AND username = ?` |
| `findAllByBalanceGreaterThan(BigDecimal)` | `SELECT * FROM users WHERE balance > ?` |

**Return Types Supported:**
- `User` - single result
- `Optional<User>` - maybe result (prevents null pointer)
- `List<User>` - multiple results
- `Page<User>` - paginated results
- `Stream<User>` - lazy-loaded stream

---

### Repository #2: PositionRepository
```java
@Repository
public interface PositionRepository extends JpaRepository<Position, Long> {
    List<Position> findByUser(User user);
    Optional<Position> findByUserAndTicker(User user, String ticker);
    List<Position> findByStopLossPriceIsNotNull();
}
```

**Query Breakdown:**
- `findByUser(User)` - All positions owned by user
- `findByUserAndTicker()` - Single position (user can only own 1 per stock due to business logic)
- `findByStopLossPriceIsNotNull()` - Used by stop-loss watchdog scheduler

---

### Repository #3: WatchlistRepository
```java
@Repository
public interface WatchlistRepository extends JpaRepository<Watchlist, Long> {
    List<Watchlist> findByUserOrderByAddedAtDesc(User user);
    Optional<Watchlist> findByUserAndTicker(User user, String ticker);
    void deleteByUserAndTicker(User user, String ticker);
    long countByUser(User user);
    boolean existsByUserAndTicker(User user, String ticker);
}
```

**Usage Pattern:**
```java
// Check before adding (prevent duplicates)
if (watchlistRepository.existsByUserAndTicker(user, ticker)) {
    throw new IllegalStateException("Already starred");
}

// Remove from watchlist
watchlistRepository.deleteByUserAndTicker(user, ticker);

// Count for UI badge
long count = watchlistRepository.countByUser(user);

// Fetch ordered by recency
List<Watchlist> items = watchlistRepository.findByUserOrderByAddedAtDesc(user);
```

---

## LAYER 4: MODEL/ENTITY LAYER (`model/` package)

### Purpose
**Database entity definitions.** Maps Java POJOs to database tables using JPA annotations.

### Entity #1: User.java
```java
@Entity
@Table(name = "users")
public class User {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)  // Auto-increment
    private Long id;
    
    @Column(unique = true, nullable = false, length = 50)  // Constraint
    private String username;
    
    @Column(nullable = false)  // Not null constraint
    private String password;
    
    @Column(nullable = false, precision = 15, scale = 2)  // Decimal type
    private BigDecimal balance = new BigDecimal("10000.00");  // $10K starting capital
    
    // Getters/Setters omitted for brevity
}
```

**JPA Annotations Explained:**
- `@Entity` - This class maps to database table
- `@Table(name = "users")` - Explicit table name (lowercase preferred)
- `@Id` - Primary key
- `@GeneratedValue(IDENTITY)` - Auto-increment strategy (database-native IDs)
- `@Column` - Column constraints (unique, not null, length, precision)
- `precision = 15, scale = 2` - Decimal(15,2) = up to 13 digits before decimal, 2 after

**Why BigDecimal?**
```java
// Problem with double:
double balance = 10000.00;
balance -= 9999.99;
System.out.println(balance);  // Output: 0.010000000000234479 ← WRONG!

// Solution: BigDecimal
BigDecimal balance = new BigDecimal("10000.00");
balance = balance.subtract(new BigDecimal("9999.99"));
System.out.println(balance);  // Output: 0.01 ← CORRECT!
```

---

### Entity #2: Stock.java
```java
@Entity
@Table(name = "stocks")
public class Stock {
    
    @Id
    @Column(length = 10)
    private String ticker;  // AAPL, MSFT, GOOGL, etc.
    
    @Column(nullable = false, length = 100)
    private String name;
    
    @Column(name = "current_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal currentPrice;  // Live price (updated every 5 seconds)
    
    @Column(name = "last_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal lastPrice;  // Previous price (for daily change %)
}
```

**Why No Generated ID?**
- Ticker is natural primary key (AAPL can only exist once)
- Prevents duplicate stock records

---

### Entity #3: Position.java
```java
@Entity
@Table(name = "positions")
public class Position {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.EAGER)  // Join with user table
    @JoinColumn(name = "user_id", nullable = false)  // Foreign key
    private User user;
    
    @Column(nullable = false, length = 10)
    private String ticker;  // Note: not explicit FK to stocks table
    
    @Column(nullable = false, precision = 12, scale = 4)  // Scale=4 allows decimal shares
    private BigDecimal quantity;
    
    @Column(name = "average_buy_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal averageBuyPrice;  // Cost basis
    
    @Column(name = "stop_loss_price", precision = 12, scale = 2)
    private BigDecimal stopLossPrice;  // Nullable: user may not set SL
}
```

**Design Decision: No Explicit FK to Stocks**
```java
// Why ticker is string, not FK to Stock:
// 1. Flexibility: Stock table might get deleted, position survives with ticker reference
// 2. Performance: Fewer joins in queries
// 3. Simplicity: Curated stock list is known at startup
// Trade-off: No database-level referential integrity for stocks
// (Acceptable because market data is stable)

// In contrast, user_id IS FK:
// - User deletion should cascade to positions (user leaving = delete portfolio)
// - Referential integrity matters for user account lifecycle
```

---

### Entity #4: Trade.java (Audit Trail)
```java
@Entity
@Table(name = "trades")
public class Trade {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    @Column(nullable = false, length = 10)
    private String ticker;
    
    @Column(name = "trade_type", nullable = false, length = 10)
    private String tradeType;  // BUY or SELL
    
    @Column(nullable = false, precision = 12, scale = 4)
    private BigDecimal quantity;
    
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;  // Executed price
    
    @Column(name = "stop_loss_price", precision = 12, scale = 2)
    private BigDecimal stopLossPrice;  // What was set (even if null)
    
    @Column(nullable = false)
    private LocalDateTime timestamp;
    
    @PrePersist
    protected void onCreate() {
        timestamp = LocalDateTime.now();  // Auto-set on insert
    }
}
```

**Why Use Enum for TradeType?**
```java
// Current approach: String
private String tradeType;  // "BUY" or "SELL"

// Problems:
// - Typos possible: "BYU" ← silently accepted, querying breaks
// - No type safety: tradeType could be anything

// Better approach (future improvement):
@Enumerated(EnumType.STRING)
private TradeType tradeType;  // BUY, SELL (compile-time type safety)

public enum TradeType {
    BUY,
    SELL
    // Compiler prevents invalid values
}
```

---

### Entity #5: Watchlist.java
```java
@Entity
@Table(name = "watchlist", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"user_id", "ticker"})  // Composite unique key
})
public class Watchlist {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    @Column(nullable = false, length = 10)
    private String ticker;
    
    @Column(name = "added_at", nullable = false)
    private LocalDateTime addedAt;
    
    @PrePersist
    protected void onCreate() {
        addedAt = LocalDateTime.now();  // Auto-set on insert
    }
}
```

**Unique Constraint Benefit:**
- Database prevents duplicate entries
- Service layer also checks before insert (fail fast)
- UI shows visual feedback if already starred

---

### Entity #6: StockHistory.java
```java
@Entity
@Table(name = "stock_history")
public class StockHistory {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, length = 10)
    private String ticker;
    
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;
    
    @Column(nullable = false)
    private LocalDate date;  // Day of price snapshot
}
```

**Why Separate Table?**
- Stock table stores CURRENT price (one value)
- StockHistory table stores HISTORICAL prices (many values)
- Enables charting: query history for date range

**Query:**
```java
// Get 30 days of prices for chart
List<StockHistory> history = stockHistoryRepository
    .findByTickerOrderByDateAsc("AAPL");

// Transform for Chart.js:
// [
//   {date: "2024-01-01", price: 150.00},
//   {date: "2024-01-02", price: 152.50},
//   ...
// ]
```

---

# SECTION 4: COMPLETE REQUEST FLOW DIAGRAMS

---

## FLOW 1: User Registration & Login

```
┌─────────────────────────────────────────────────────────────────┐
│ FRONTEND (HTML/JavaScript)                                      │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  User enters username & password in registration form          │
│                         │                                       │
│                         ▼                                       │
│  JavaScript validates input (client-side)                      │
│                         │                                       │
│                         ▼                                       │
│  fetch('/api/auth/register', {                                 │
│    method: 'POST',                                             │
│    body: JSON.stringify({                                      │
│      username: 'john_trader',                                  │
│      password: 'secure_pass'                                   │
│    })                                                           │
│  })                                                             │
│                         │                                       │
└─────────────────────────┼───────────────────────────────────────┘
                          │ HTTP POST request
            ┌─────────────▼───────────────┐
            │ NETWORK                     │
            └─────────────┬───────────────┘
                          │
┌─────────────────────────▼───────────────────────────────────────┐
│ BACKEND (Spring Boot)                                           │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│ AuthController.register(@RequestBody AuthRequest)              │
│                         │                                       │
│                         ▼                                       │
│ 1. Validate inputs                                             │
│    - username not null/empty?                                  │
│    - password not null/empty?                                  │
│                         │                                       │
│                         ▼                                       │
│ 2. Check if username exists                                    │
│    if (userRepository.findByUsername(username).isPresent()) {  │
│        return 409 CONFLICT;  // Already taken                  │
│    }                                                            │
│                         │                                       │
│                         ▼                                       │
│ 3. Create new User object                                      │
│    User user = new User(username, password);                   │
│    user.setBalance(new BigDecimal("10000.00"));                │
│                         │                                       │
│                         ▼                                       │
│ 4. Save to database                                            │
│    userRepository.save(user);                                  │
│         │                                                       │
│         └──────┬──────────────────────────────────┐            │
│                │                                  │            │
│     ┌──────────▼──────────┐        ┌──────────────▼──────┐   │
│     │ SQL: INSERT INTO    │        │ Auto-generates ID   │   │
│     │ users(username,     │        │ (IDENTITY)          │   │
│     │ password, balance)  │        └─────────────────────┘   │
│     │ VALUES(...)         │                                    │
│     └─────────────────────┘                                    │
│                │                                               │
│                ▼                                               │
│         PostgreSQL Database                                    │
│         ┌────────────────────┐                                │
│         │ users table:       │                                │
│         │ id=1               │                                │
│         │ username=john...   │                                │
│         │ password=secure... │                                │
│         │ balance=10000.00   │                                │
│         └────────────────────┘                                │
│                │                                               │
│                ▼                                               │
│ 5. Store userId in HTTP session                               │
│    session.setAttribute("userId", user.getId());  // 1        │
│                         │                                       │
│                         ▼                                       │
│ 6. Return success response                                     │
│    ResponseEntity.ok(user);                                    │
│    HTTP 200 + JSON body                                        │
│    + Set-Cookie: JSESSIONID=...                               │
│                         │                                       │
└─────────────────────────┼───────────────────────────────────────┘
                          │ HTTP response + session cookie
            ┌─────────────▼───────────────┐
            │ NETWORK                     │
            └─────────────┬───────────────┘
                          │
┌─────────────────────────▼───────────────────────────────────────┐
│ FRONTEND (HTML/JavaScript)                                      │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│ if (response.status === 200) {                                 │
│     // Auto-login: hide auth screen, show app                 │
│     document.getElementById('auth-container').classList.add('d-none');   │
│     document.getElementById('app-container').classList.remove('d-none'); │
│                                                                 │
│     // Session cookie saved by browser automatically           │
│     // Future requests include: Cookie: JSESSIONID=...         │
│ }                                                               │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

---

## FLOW 2: Buy Stock (Complex Transaction)

```
Frontend UI:                Backend Spring Boot:            Database:
┌──────────────┐           ┌────────────────┐              ┌──────────┐
│ User enters: │           │                │              │          │
│ Ticker: AAPL │           │ CONTROLLER     │              │          │
│ Qty: 100     │           │ PortfolioCtlr  │              │ users    │
│ StopLoss: 170│           │                │              │ stocks   │
│              │           │                │              │ positions│
│ Click BUY    │           │                │              │ trades   │
└──────┬───────┘           └────────────────┘              └──────────┘
       │                                                         │
       │ POST /api/portfolio/buy                               │
       │ {ticker, qty, stopLoss}                               │
       │──────────────────────────────────►                   │
       │                                   │                   │
       │                        ┌──────────▼────────┐         │
       │                        │ 1. Extract userId │         │
       │                        │    from session   │         │
       │                        └──────────┬────────┘         │
       │                                   │                   │
       │                        ┌──────────▼────────────────┐  │
       │                        │ 2. Validate inputs       │  │
       │                        │ - ticker != null         │  │
       │                        │ - qty > 0                │  │
       │                        └──────────┬────────────────┘  │
       │                                   │                   │
       │                        ┌──────────▼──────────────┐  │
       │                        │ 3. Call PortfolioService│   │
       │                        │    .buyStock()          │  │
       │                        └──────────┬──────────────┘  │
       │                                   │                  │
       │                    ┌──────────────▼────────────────┐ │
       │                    │ SERVICE LAYER                │ │
       │                    │ PortfolioService.buyStock()  │ │
       │                    │                              │ │
       │                    │ 1. Fetch user from DB        │ │
       │                    │    userRepository.findById() │◄┼─────────┐
       │                    │    (userId=1)                │ │         │
       │                    │                              │ │    SELECT * FROM users
       │                    │                              │ │    WHERE id=1
       │                    │                              │ │
       │                    │◄─────────────────────────────┼─── Result: User{id=1,
       │                    │    Returns: User object      │ │          balance=10000}
       │                    │                              │ │
       │                    │ 2. Fetch stock from DB       │ │
       │                    │    stockRepository.findById()│◄┼────────────┐
       │                    │    (ticker="AAPL")          │ │            │
       │                    │                              │ │  SELECT * FROM stocks
       │                    │                              │ │  WHERE ticker='AAPL'
       │                    │                              │ │
       │                    │◄─────────────────────────────┼────── Result: Stock{
       │                    │    Returns: Stock object     │ │       ticker='AAPL',
       │                    │                              │ │       currentPrice=180.50
       │                    │ 3. Calculate cost            │ │    }
       │                    │    totalCost = 180.50 × 100  │ │
       │                    │            = 18,050.00       │ │
       │                    │                              │ │
       │                    │ 4. Validate balance         │ │
       │                    │    10,000.00 >= 18,050?     │ │
       │                    │    NO! ← Error              │ │
       │                    │                              │ │
       │                    │ throw IllegalStateException  │ │
       │                    │ "Insufficient balance"       │ │
       │                    └──────────┬──────────────────┘ │
       │                               │                    │
       │                    ┌──────────▼──────────────┐    │
       │                    │ CONTROLLER (catch error)│    │
       │                    │                         │    │
       │                    │ catch(IllegalStateEx)   │    │
       │                    │ return 400 Bad Request  │    │
       │                    │ + error message         │    │
       │                    └──────────┬──────────────┘    │
       │                               │                   │
       │◄──────────────────────────────┘                   │
       │ HTTP 400                                          │
       │ {                                                 │
       │   "error": "Insufficient balance.                 │
       │    Required: $18,050.00,                          │
       │    Available: $10,000.00"                         │
       │ }                                                 │
       │                                                   │
       └─────► Show error message to user
              "You need $18,050 but only have $10,000.
               Consider buying fewer shares."

========== HAPPY PATH (Sufficient Funds) ==========

       │ (Restart with qty=50 instead of 100)
       │ POST /api/portfolio/buy
       │ {ticker: AAPL, qty: 50, stopLoss: 170}
       │──────────────────────────────────►
       │                                   
       │                    ┌──────────────────────────────┐
       │                    │ SERVICE LAYER                │
       │                    │ buyStock() continued         │
       │                    │                              │
       │                    │ 5. Calculate cost            │
       │                    │    totalCost = 180.50 × 50   │
       │                    │            = 9,025.00        │
       │                    │                              │
       │                    │ 6. Validate balance          │
       │                    │    10,000.00 >= 9,025.00?    │
       │                    │    YES! ✓ Continue          │
       │                    │                              │
       │                    │ 7. Deduct from user balance  │
       │                    │    user.setBalance(          │
       │                    │      10,000 - 9,025 = 975)   │
       │                    │                              │
       │                    │    userRepository.save()     │◄──────────┐
       │                    │                              │ │         │
       │                    │                              │ │  UPDATE users
       │                    │                              │ │  SET balance=975.00
       │                    │                              │ │  WHERE id=1
       │                    │                              │ │
       │                    │◄─────────────────────────────┼────────┐
       │                    │    Update committed to DB    │ │      │
       │                    │                              │ │      │
       │                    │ 8. Check if position exists  │ │      │
       │                    │    positionRepository.       │ │      │
       │                    │    findByUserAndTicker()     │◄┼──────┘
       │                    │                              │ │
       │                    │    SELECT FROM positions     │ │
       │                    │    WHERE user_id=1 AND       │ │
       │                    │    ticker='AAPL'             │ │
       │                    │                              │ │
       │                    │◄─────────────────────────────┼────── Result: NULL
       │                    │    (first time buying)       │ │      (no existing position)
       │                    │                              │ │
       │                    │ 9. Create new Position       │ │
       │                    │    Position p = new          │ │
       │                    │    Position(user, "AAPL",    │ │
       │                    │               50, 180.50,    │ │
       │                    │               170);          │ │
       │                    │                              │ │
       │                    │    positionRepository.save() │◄┼──────────┐
       │                    │                              │ │ │        │
       │                    │                              │ │ │  INSERT INTO positions
       │                    │                              │ │ │  (user_id, ticker, qty,
       │                    │                              │ │ │   avg_buy_price, 
       │                    │                              │ │ │   stop_loss_price)
       │                    │                              │ │ │  VALUES(1, 'AAPL', 50,
       │                    │                              │ │ │  180.50, 170.00)
       │                    │                              │ │
       │                    │◄─────────────────────────────┼─────────┐
       │                    │    Position saved (id=1)     │ │       │
       │                    │                              │ │       │
       │                    │ 10. Create Trade record      │ │       │
       │                    │     (audit log)              │ │       │
       │                    │                              │ │       │
       │                    │     Trade t = new Trade(     │ │       │
       │                    │       user,                  │ │       │
       │                    │       "AAPL",                │ │       │
       │                    │       "BUY",                 │ │       │
       │                    │       50,                    │ │       │
       │                    │       180.50,                │ │       │
       │                    │       170);                  │ │       │
       │                    │                              │ │       │
       │                    │     tradeRepository.save()   │◄┼───────────┐
       │                    │                              │ │ │       │
       │                    │                              │ │ │  INSERT INTO trades
       │                    │                              │ │ │  (user_id, ticker,
       │                    │                              │ │ │   trade_type, qty, 
       │                    │                              │ │ │   price, stop_loss_price,
       │                    │                              │ │ │   timestamp)
       │                    │                              │ │ │  VALUES(1, 'AAPL', 'BUY',
       │                    │                              │ │ │  50, 180.50, 170.00,
       │                    │                              │ │ │  NOW())
       │                    │                              │ │
       │                    │◄─────────────────────────────┼────────────┐
       │                    │    Trade saved (id=1)        │ │          │
       │                    │                              │ │          │
       │                    │ 11. Return Trade object      │ │          │
       │                    │     (all changes committed   │ │          │
       │                    │      via @Transactional)     │ │          │
       │                    └──────────┬──────────────────┘ │          │
       │                               │                    │          │
       │◄──────────────────────────────┘                    │          │
       │ HTTP 200 OK                                        │          │
       │ {                                                  │          │
       │   "id": 1,                                         │          │
       │   "ticker": "AAPL",                                │          │
       │   "tradeType": "BUY",                              │          │
       │   "quantity": 50,                                  │          │
       │   "price": 180.50,                                 │          │
       │   "stopLossPrice": 170.00,                         │          │
       │   "timestamp": "2024-06-18T14:30:45"               │          │
       │ }                                                  │          │
       │                                                    │          │
       └─────► UI Updates:
              - Portfolio value: $10,000 → $9,975 
              - Holdings card appears: AAPL 50 @ $180.50
              - Position card updates P&L based on real-time price
```

---

## FLOW 3: Stop-Loss Watchdog (Background Scheduler)

```
Timeline:

06:00:00 AM
│
└─ Spring Boot starts
   @EnableScheduling activates
   @Scheduled(fixedDelay=5000) registered
   
   User buys 100 AAPL @ $150/share
   Sets stop-loss @ $145/share
   
   Database state:
   - positions: (user=1, ticker=AAPL, qty=100, 
                  avg_price=150, stop_loss=145)
   - stocks:    (ticker=AAPL, current_price=150.00)

06:00:05 AM
│
└─ Scheduler runs: checkStopLosses()
   
   1. Query: SELECT * FROM positions 
      WHERE stop_loss_price IS NOT NULL
      Result: 1 position (AAPL)
   
   2. Check: current_price (150.00) > stop_loss (145.00)
      Action: CONTINUE monitoring
   
06:00:10 AM
│
└─ Scheduler runs: tickMarket() 
   
   Price updated: AAPL 150.00 → 149.75 (-0.17%)
   
   Database state:
   - stocks: (ticker=AAPL, current_price=149.75)

06:00:15 AM
│
└─ Scheduler runs: checkStopLosses()
   
   Check: current_price (149.75) > stop_loss (145.00)
   Action: CONTINUE monitoring

... (many iterations) ...

06:05:30 AM
│
└─ Scheduler runs: tickMarket()
   
   Market downturn! Price updated:
   AAPL 145.00 → 144.95 (-0.03%)
   
   Database state:
   - stocks: (ticker=AAPL, current_price=144.95)

06:05:35 AM
│
└─ ⚠️ CRITICAL: Scheduler runs checkStopLosses()
   
   1. Query: SELECT * FROM positions 
      WHERE stop_loss_price IS NOT NULL
   
   2. For each position:
      - AAPL: current_price (144.95) <= stop_loss (145.00)
              ✓ TRIGGER CONDITION MET!
   
   3. Automatic action:
      System.out.println(
        "Stop-Loss Triggered! Selling 100 shares of AAPL 
         for User john_trader at current price $144.95 
         (Stop-loss set at $145.00)"
      );
      
      // Call sellStock() automatically
      sellStock(userId=1, ticker="AAPL", qty=100)
      
      4. sellStock() flow (same as manual sell):
         - user.balance += 144.95 × 100 = $14,495
         - Position deleted (qty becomes 0)
         - Trade recorded: SELL, 100 shares, $144.95, timestamp
      
      5. Database changes committed:
         - users: balance=10975.00 (was 9975, now +9995 loss)
         - positions: AAPL position deleted
         - trades: new SELL trade record added
      
      6. Result: User's portfolio updated
         - BEFORE: Cash=$975, AAPL=100@$150=$15,000, 
                   Total=$15,975
         - AFTER: Cash=$10,970 (975 + 9,995), AAPL=gone
                  P&L: -$5 (small loss from 150→144.95)

06:05:40 AM
│
└─ Scheduler runs: checkStopLosses()
   
   Query: SELECT * FROM positions 
   WHERE stop_loss_price IS NOT NULL
   Result: 0 positions (AAPL already sold)
   Action: NOTHING (position gone)

...continues monitoring for other positions...
```

**Key Design Benefits:**
1. **Atomic:** Entire sell operation is transactional - no partial updates
2. **24/7:** Runs continuously, doesn't require user to be online
3. **Non-blocking:** Doesn't interfere with user API requests
4. **Auditable:** Every auto-sell is logged as trade record
5. **Reliable:** Supervised by Spring framework, retries on failure

---

# SECTION 5: ENGINEERING DECISIONS & TRADE-OFFS

---

## Decision 1: REST API Over WebSocket

### What We Chose
Traditional REST API with client-side polling for data updates.

### Why
- **Simplicity:** Easier to implement, test, debug
- **MVP Priority:** Get to market faster
- **Browser Support:** Works everywhere (no WebSocket library needed)
- **Horizontal Scaling:** Stateless - can run multiple server instances
- **CORS-friendly:** No special handshake negotiation

### Trade-off
- **Latency:** Client polls every N seconds (might miss price tick)
- **Bandwidth:** Redundant polling if no data changed
- **Real-time:** Can't push updates instantly to client

### When to Rebuild
- Millions of concurrent users
- Need sub-100ms data latency
- WebSocket upgrade path: add `@Configuration` + `@EnableWebSocket` without rewriting REST API

---

## Decision 2: Plain Text Passwords (MVP)

### What We Chose
Store passwords as plain text in database.

```java
User user = new User(username, request.password);  // No hashing
userRepository.save(user);
```

### Why
- **MVP Phase:** Focus on features, not security theater
- **Paper Trading:** No real money at risk (limited security impact)
- **Rapid Iteration:** Getting security library working takes time

### Trade-off
- **Risk:** If database compromised, all passwords exposed
- **Compliance:** Violates GDPR/industry standards
- **Trust:** Users won't trust production system without encryption

### Upgrade Path
```java
// To add bcrypt (10-minute refactor):
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@Configuration
public class SecurityConfig {
    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}

// In AuthService:
@Service
public class AuthService {
    @Autowired
    private BCryptPasswordEncoder encoder;
    
    public void registerUser(String username, String password) {
        String hashedPassword = encoder.encode(password);  // One-way encryption
        User user = new User(username, hashedPassword);
        userRepository.save(user);
    }
    
    public boolean validatePassword(String rawPassword, String hashedPassword) {
        return encoder.matches(rawPassword, hashedPassword);  // Compare securely
    }
}
```

---

## Decision 3: Session-Based Authentication Over JWT

### What We Chose
HTTP sessions stored in server memory.

```java
@PostMapping("/login")
public ResponseEntity<?> login(@RequestBody AuthRequest request, HttpSession session) {
    Optional<User> userOpt = userRepository.findByUsername(request.username);
    if (userOpt.isPresent()) {
        session.setAttribute("userId", user.getId());  // Server-side session
        return ResponseEntity.ok(user);
    }
}

// Later request:
@GetMapping("/positions")
public ResponseEntity<?> getPositions(HttpSession session) {
    Long userId = (Long) session.getAttribute("userId");  // Retrieved from session
    if (userId == null) return ResponseEntity.unauthorized().build();
}
```

### Why
- **Simplicity:** Built into Spring, no JWT library needed
- **Revocation:** Can instantly kill session if suspicious activity
- **Stateful:** Server controls auth state completely

### Trade-off
- **Scalability:** Sessions stored in server memory - doesn't work with multiple servers
- **Mobile:** Mobile apps prefer tokens (easier to manage)
- **Distributed:** Microservices can't easily share sessions

### Comparison: JWT
```java
// JWT Approach (more scalable):
@PostMapping("/login")
public ResponseEntity<?> login(@RequestBody AuthRequest request) {
    User user = authenticate(request);
    
    String token = Jwts.builder()
        .setSubject(user.getId().toString())
        .setIssuedAt(new Date())
        .setExpiration(new Date(System.currentTimeMillis() + 86400000))  // 24 hrs
        .signWith(SignatureAlgorithm.HS512, SECRET_KEY)
        .compact();
    
    return ResponseEntity.ok(new AuthResponse(token));
}

// Client includes token in every request:
Authorization: Bearer eyJhbGciOiJIUzUxMiJ9...
```

### When to Switch
- Building mobile app (JWT is standard)
- Deploying to Kubernetes (session state problematic)
- Federated / third-party auth (OAuth 2.0 uses tokens)

---

## Decision 4: @Transactional for Atomicity

### What We Chose
```java
@Transactional  // Spring magic: all-or-nothing guarantee
public Trade buyStock(Long userId, String ticker, BigDecimal quantity, BigDecimal stopLossPrice) {
    user.setBalance(user.getBalance().subtract(totalCost));
    userRepository.save(user);
    
    positionRepository.save(position);
    tradeRepository.save(trade);
    
    return trade;  // If exception here, everything rolls back
}
```

### Why
- **Consistency:** Portfolio can't reach inconsistent state (money deducted but position not created)
- **Simplicity:** One annotation vs. manual begin/commit/rollback
- **Safety:** Default behavior is safe

### Under the Hood
```
1. Spring creates proxy around buyStock()
2. Proxy opens database transaction before method starts
3. Method executes normally
4. If no exception: COMMIT (persist all changes)
5. If exception thrown: ROLLBACK (undo all changes)
```

### Example: Without @Transactional
```java
public Trade buyStock(...) {
    // ① Deduct balance
    user.setBalance(user.getBalance().subtract(totalCost));
    userRepository.save(user);  // COMMITTED to database
    
    // ② [CRASH] Server dies or exception thrown
    throw new RuntimeException("Database connection lost");
    
    // ③ Never reached: Position never created
    positionRepository.save(position);
}

// Result: User's cash gone, but no position created ←  INCONSISTENT!
// Use must manually fix portfolio via admin tool
```

### Example: With @Transactional
```java
@Transactional
public Trade buyStock(...) {
    // ① Deduct balance
    user.setBalance(user.getBalance().subtract(totalCost));
    userRepository.save(user);  // NOT YET committed
    
    // ② [CRASH]
    throw new RuntimeException("Database connection lost");
    
    // ③ Never reached
    positionRepository.save(position);
}

// Result: ENTIRE operation rolled back
// Database unchanged: balance untouched, balance still 10,000
// Portfolio remains consistent!
```

---

## Decision 5: Scheduled Tasks Over Message Queue

### What We Chose
```java
@Scheduled(fixedDelay = 5000)  // Poll and check every 5 seconds
public void checkStopLosses() {
    // Check all positions for stop-loss triggers
}

@Scheduled(fixedDelay = 5000)  // Update prices every 5 seconds
public void tickMarket() {
    // Simulate market movements
}
```

### Why
- **Simplicity:** No external dependencies (Redis, RabbitMQ)
- **MVP:** Sufficient for real-time sim requirements
- **Local Development:** Works on laptop without setup

### Trade-off
- **Scalability:** Can't distribute across multiple servers easily
- **Reliability:** If server crashes, scheduled tasks stop
- **Precision:** Runs based on JVM clock (drift possible)

### Alternative: Message Queue (RabbitMQ/Kafka)
```
┌─────────────────┐
│  Event Source   │
│ (Trade placed)  │
└────────┬────────┘
         │
         ▼ publishes
    ┌─────────┐
    │RabbitMQ │
    └────┬────┘
         │ subscribes
         ▼
┌─────────────────┐
│ Stop-Loss Check │ (can be separate service)
│ Stop-Loss Check │ (multiple instances)
│ Stop-Loss Check │ (fault-tolerant)
└─────────────────┘
```

### When to Upgrade
- 1M+ daily active users
- Need guaranteed delivery (stop-loss can't be skipped)
- Multi-region deployment (scheduler can't coordinate)

---

## Decision 6: Field-Level Rounding (BigDecimal, Scale=2)

### What We Chose
```java
@Column(precision = 12, scale = 2)
private BigDecimal currentPrice;  // Always 2 decimal places

// In code:
totalCost = currentPrice.multiply(quantity)
    .setScale(2, RoundingMode.HALF_UP);  // Force rounding
```

### Why
- **Financial Accuracy:** Money has exactly 2 decimal places (cents)
- **Database Consistency:** Enforced at schema level
- **Predictable:** `HALF_UP` rounds 0.5 up (standard rounding)

### Standard Rounding Modes
| Mode | 2.5 rounds to | 2.4 rounds to | Use Case |
|------|---|---|---|
| HALF_UP | 3 | 2 | Banking (standard) |
| HALF_DOWN | 2 | 2 | Rare |
| UP | 3 | 3 | Never go against user |
| DOWN | 2 | 2 | Never charge user more |
| CEILING | 3 | 3 | Banks use for charges |
| FLOOR | 2 | 2 | Banks use for credits |

### Example: Why Scale Matters
```java
// Stock History: Daily snapshots
@Entity
@Table(name = "stock_history")
public class StockHistory {
    @Column(precision = 12, scale = 2)
    private BigDecimal price;  // scale=2 enforced
}

// Position: Tracking cost basis
@Entity
public class Position {
    @Column(precision = 12, scale = 2)
    private BigDecimal averageBuyPrice;  // scale=2
    
    @Column(precision = 12, scale = 4)
    private BigDecimal quantity;  // scale=4: allows 0.0001 shares (e.g., fractional shares/cryptoassets)
}
```

---

# SECTION 6: INTERVIEW TALKING POINTS

---

## Q1: "Explain Your Architecture"

**Strong Answer (Should demonstrate):**

TradeWise is a Spring Boot REST API using the **4-layer architecture pattern**: controllers handle HTTP concerns, services contain business logic, repositories abstract database access, and entities define data models.

The core domain is a **paper-trading simulator** that teaches behavioral discipline through a scoring system. Here's the flow:

1. **User registers** → Gets $10,000 virtual wallet stored in `users` table
2. **User buys stock** → Service calculates cost, deducts balance, creates position record, and records trade audit trail (all atomic via `@Transactional`)
3. **Background scheduler** → Every 5 seconds, updates stock prices (simulation) and checks all stop-losses (automatic selling)
4. **User requests analysis** → Journal service scans portfolio for 4 behavioral red flags: missing stop-loss, overtrading, disposition effect, concentration risk

**Why this architecture?**
- **Separation of concerns** - each layer has one job
- **Testability** - mock services to test controllers, mock repos to test services
- **Reusability** - one service used by multiple controllers
- **Maintainability** - business logic changes don't affect HTTP routes

**Key technologies:**
- **Spring Data JPA** - removes 80% of boilerplate database queries
- **Transactional boundaries** - guarantees atomicity (all-or-nothing)
- **Scheduled tasks** - background processing without blocking user requests
- **BigDecimal** - financial precision (double loses cents)

---

## Q2: "Walk Me Through the Buy Stock Flow"

**Strong Answer:**

1. **Frontend sends POST /api/portfolio/buy**
   ```json
   { ticker: "AAPL", quantity: 50, stopLossPrice: 170 }
   ```

2. **Controller validates:**
   - Session has userId
   - Ticker, quantity not null
   - Quantity > 0

3. **Controller calls PortfolioService.buyStock()**
   ```java
   @Transactional  // Opens database transaction here
   public Trade buyStock(Long userId, String ticker, ...) {
       // All operations below are atomic
   }
   ```

4. **Service fetches user and stock** (2 queries)
   - Validate user exists
   - Validate stock exists

5. **Service calculates cost**
   ```
   cost = stock.currentPrice × quantity
        = 180.50 × 50
        = 9,025.00
   ```

6. **Service validates balance**
   - If user.balance < totalCost → throw exception (caught by controller, returns 400)

7. **Service deducts balance** (UPDATE query)
   ```
   user.balance = 10,000 - 9,025 = 975.00
   ```

8. **Service updates position** (SELECT + INSERT or UPDATE)
   - Check if user already owns AAPL (findByUserAndTicker)
   - If YES: recalculate cost basis
     ```
     newAvgPrice = (oldQty × oldAvg + newQty × price) / (oldQty + newQty)
     ```
   - If NO: create new position
   ```
   INSERT INTO positions (user_id, ticker, qty, avg_price, stop_loss)
   VALUES (1, 'AAPL', 50, 180.50, 170.00)
   ```

9. **Service records trade** (INSERT audit trail)
   ```
   INSERT INTO trades (user_id, ticker, type, qty, price, stop_loss, timestamp)
   VALUES (1, 'AAPL', 'BUY', 50, 180.50, 170.00, NOW())
   ```

10. **Spring commits transaction** - all changes persist
    - If exception was thrown at ANY step → ROLLBACK (undo all)

11. **Controller returns Trade object** (JSON response)
    ```json
    { id: 1, ticker: "AAPL", type: "BUY", qty: 50, price: 180.50, ... }
    ```

12. **Frontend updates UI**
    - Portfolio value: 10,000 → 10,000 (unchanged, still 9,975 cash + 9,025 equity)
    - Holdings card shows: AAPL 50 @ $180.50
    - Wait for next price tick to show P&L

**Interview talking points:**
- Why BigDecimal and not double? Precision - `double` loses cents
- Why @Transactional? If crash after deduct but before position save, balance stolen
- Why calculate average buy price? Needed for P&L reporting and tax purposes
- What if user refreshes page mid-transaction? Nothing happens until server responds (REST is stateless)

---

## Q3: "How Do You Handle Concurrent Trades?"

**Strong Answer:**

**Scenario:** User has $10,000. Two simultaneous requests:
- Request A: Buy $6,000 AAPL
- Request B: Buy $6,000 MSFT
- Both should succeed ($10,000 total), but current balance is insufficient for both

**Solution: Database Transaction Isolation**

```
Thread A                          Thread B                    Database
│                                 │
├─ @Transactional                 ├─ @Transactional
│  begins                          │  begins
│                                  │
├─ SELECT users WHERE id=1        ├─ SELECT users WHERE id=1
│  (sees balance: 10,000)          │  (sees balance: 10,000)
│                                  │  (both read same value)
├─ UPDATE users SET balance       │
│  = 10,000 - 6,000               │
│  WHERE id=1                      │
│  (LOCKS ROW until commit)        │
│                                  │
│  ├─ INSERT positions (AAPL)     │
│  ├─ INSERT trades (BUY AAPL)    │
│  └─ COMMIT                       │  ← Thread A releases lock
│     (writes: balance=4,000)      │
│                                  ├─ UPDATE users SET balance
│                                  │  = 10,000 - 6,000
│                                  │  WHERE id=1    ← NOW LOCKS ROW
│                                  │  (succeeds - 6,000 < 4,000?
│                                  │   Validation in service catches this)
│                                  │  
│                                  ├─ Service throws exception
│                                  │  "Insufficient balance"
│                                  │
│                                  └─ ROLLBACK (MSFT position never created)
```

**Why it works:**
1. **Row-level locking** - database prevents simultaneous updates to same row
2. **Transactional isolation** - each transaction sees view of data at start time
3. **Optimistic approach** - code checks business rules (balance), DB enforces constraints

**Stronger approach: Pessimistic locking**
```java
@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<User> findByIdForUpdate(Long id);
}

@Service
public class PortfolioService {
    @Transactional
    public Trade buyStock(Long userId, String ticker, ...) {
        // Acquires LOCK immediately, preventing other threads from updating
        User user = userRepository.findByIdForUpdate(userId).orElseThrow();
        
        // Safe: no other thread can update this user while locked
        BigDecimal cost = stock.getCurrentPrice().multiply(quantity);
        if (user.getBalance().compareTo(cost) < 0) {
            throw new IllegalStateException("Insufficient balance");
        }
        
        user.setBalance(user.getBalance().subtract(cost));
        userRepository.save(user);  // Lock released on commit
        
        return tradeRepository.save(new Trade(...));
    }
}
```

---

## Q4: "How Would You Scale This to 1M Users?"

**Strong Answer (Layer by Layer):**

### 1. Database Bottleneck
**Current:** Single PostgreSQL instance
**Problem:** Writes pile up (buy/sell/trades), polling hammers DB

**Solution:**
```
┌─────────────────────────┐
│   Read Replicas         │
│   (market data queries) │
│   - getAllStocks()      │
│   - getStockHistory()   │
├─────────────────────────┤
│   Primary (writes)      │
│   (trades, positions)   │
│   - buyStock()          │
│   - sellStock()         │
│   - checkStopLosses()   │
└─────────────────────────┘
```

**Scaling approach:**
- **Read-heavy queries** (market data, history) → Read replicas
- **Write-heavy queries** (trades, positions) → Primary with batch processing
- **Cache** frequently accessed data (all stocks, 10 tickers = 10 queries → Redis)

### 2. API Server Bottleneck
**Current:** Single Spring Boot instance on port 8080
**Problem:** 1M users × 10 API calls/day = 10M requests/day, but single server CPU maxes out

**Solution: Horizontal Scaling**
```
         ┌──────────────────────┐
         │   Load Balancer      │
         │   (nginx/HAProxy)    │
         └──────────┬───────────┘
                    │
         ┌──────────┼──────────┐
         │          │          │
         ▼          ▼          ▼
    ┌────────┐ ┌────────┐ ┌────────┐
    │Server 1│ │Server 2│ │Server 3│
    │(Spring)│ │(Spring)│ │(Spring)│
    └────────┘ └────────┘ └────────┘
         │          │          │
         └──────────┼──────────┘
                    │
              ┌─────▼─────┐
              │ PostgreSQL │
              │ (Primary)  │
              └────────────┘
```

**Session problem:** Load balancer routes request A to Server 1, request B to Server 2
- Server 1 stores userId in local memory (session)
- Server 2 doesn't know about it → Auth fails

**Solution: Shared session store (Redis)**
```java
@Configuration
@EnableRedisHttpSession  // Store sessions in Redis, not local memory
public class SessionConfig { }
```

### 3. Scheduled Task Bottleneck
**Current:** One server runs @Scheduled tasks
- Problem: 1M users × 5 stop-losses each = 5M position checks every 5 seconds
- Single server can't handle it

**Solution: Event-driven with message queue**
```
User places stop-loss
    │
    ├─ Service publishes "StopLossCreated" event to RabbitMQ
    │
    ├─ Message Queue (fault-tolerant)
    │
    ├─ On market price update → publish "PriceUpdated" event
    │
    ├─ Multiple workers subscribe to PriceUpdated
    │  - Worker 1 checks users 1-100K
    │  - Worker 2 checks users 100K-200K
    │  - etc (horizontal scaling)
    │
    └─ If stop-loss triggered → auto-sell immediately
```

### 4. Price Update Bottleneck
**Current:** Send random price every 5 seconds
**Problem:** 1M users polling prices = 200K requests/second

**Solution: WebSocket broadcast (push instead of pull)**
```
┌─────────────────────────┐
│ MarketService           │
│ @Scheduled every 5 sec  │
│ updates prices          │
└────────┬────────────────┘
         │
         ├─ Publish to WebSocket topic: /topic/market/prices
         │
         ├─ All connected clients receive update
         │
         └─ 1M users get push notification
            (vs 200K pull requests)
```

### 5. Horizontal Scaling Architecture (Final)
```
┌────────────────────────────────────────────────────┐
│ CDN (static assets: HTML, CSS, JS)                 │
└────────────────────────────────────────────────────┘
                        │
                        ▼
        ┌────────────────────────────┐
        │   API Gateway / Proxy      │
        │   (nginx, rate limiting)   │
        └────────────┬───────────────┘
                     │
         ┌───────────┼───────────┐
         │           │           │
         ▼           ▼           ▼
   ┌─────────┐ ┌─────────┐ ┌─────────┐
   │Spring 1 │ │Spring 2 │ │Spring 3 │
   └────┬────┘ └────┬────┘ └────┬────┘
        │           │           │
        └───────────┼───────────┘
                    │
        ┌───────────┼───────────────┐
        │           │               │
        ▼           ▼               ▼
    ┌────────┐ ┌────────┐  ┌─────────────┐
    │ Redis  │ │RabbitMQ│  │ PostgreSQL  │
    │(cache) │ │(events)│  │ (Primary)   │
    └────────┘ └────────┘  │ (Replicas)  │
                            └─────────────┘
        
        Scheduled Workers (separate service):
        ┌──────────────────────────────────┐
        │ Worker 1: Stop-loss monitor      │
        │ Worker 2: Price simulator        │
        │ Worker 3: Discipline analytics   │
        └──────────────────────────────────┘
```

---

## Q5: "What About Data Consistency Issues?"

**Strong Answer:**

### Problem 1: Stop-Loss Executed While User is Buying Same Position

```
User buys 100 AAPL, sets SL at $145
After 1 hour: price hits $145 → auto-sell triggered
Exact same moment: user clicks BUY more AAPL

buyStock():                        checkStopLosses():
1. User still exists ✓             1. Find position (still exists)
2. Stock exists ✓                  2. Price <= SL ✓
3. START transaction               3. START transaction
                                   4. sellStock()
4. Calculate cost                     - Delete position
5. Check balance ✓                    - Update balance
6. (blocked, waiting for DB)       5. COMMIT
                                      (position deleted!)
7. Resume: SELECT position
   WHERE user_id=1 AND              6. buyStock() resumes:
   ticker=AAPL
   (returns NULL - just deleted!)

8. Create NEW position
9. COMMIT
```

**Result:** User buys 100 shares, position has qty=100 (correct!)

**Why it works:**
- `findByUserAndTicker()` is idempotent - returns NULL if not found
- buyStock() handles NULL case by creating new position
- No data corruption, just a weird trade event

**But the confusion:** Journal/Ledger shows SELL then BUY in quick succession. Is this OK?

**Answer:** YES - it's accurate! The ledger is immutable audit trail. Shows actual trading sequence.

---

### Problem 2: Average Buy Price Calculation Race Condition

```
Position: AAPL, qty=100, avg_price=150

User A: Buy 50 more @ 160          User B: Buy 25 more @ 155
│                                   │
├─ @Transactional                   ├─ @Transactional
│  SELECT position                  │  SELECT position
│  (qty=100, avg=150)               │  (qty=100, avg=150)
│                                   │
├─ Calculate:                       ├─ Calculate:
│  newQty = 100 + 50 = 150         │  newQty = 100 + 25 = 125
│  total = 100*150 + 50*160        │  total = 100*150 + 25*155
│       = 15,000 + 8,000 = 23K    │       = 15,000 + 3,875 = 18,875
│  newAvg = 23,000/150 = 153.33   │  newAvg = 18,875/125 = 151
│                                   │
├─ UPDATE position SET              │
│  qty=150, avg=153.33             │  (blocked waiting for lock)
│  WHERE id=X                       │
│  (commits)                        │
│                                   ├─ Lock released, re-read from DB
│                                   │  (qty=150 now, not 100!)
│                                   │  But calculation already done:
│                                   │  newQty = 100 + 25 = 125
│                                   │  total = 100*150 + 25*155
│                                   │       = 18,875
│                                   │  newAvg = 18,875/125 = 151
│                                   │
│                                   ├─ UPDATE position SET
│                                   │  qty=125, avg=151
│                                   │
│                                   │  Result: qty=125 (WRONG!)
│                                   │  Should be: 100 + 50 + 25 = 175
```

**Problem:** Both users saw `qty=100`, now it's only 125 (user A's 50 shares missing!)

**Solution: Pessimistic Lock**
```java
@Repository
public interface PositionRepository extends JpaRepository<Position, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Position p WHERE p.user = ?1 AND p.ticker = ?2")
    Optional<Position> findByUserAndTickerForUpdate(User user, String ticker);
}

@Transactional
public Trade buyStock(...) {
    // LOCKS position immediately, preventing concurrent modifications
    Optional<Position> positionOpt = 
        positionRepository.findByUserAndTickerForUpdate(user, ticker);
    
    Position position = positionOpt.get();  // Now guaranteed up-to-date
    // ... calculate ...
    positionRepository.save(position);  // Lock released on commit
}
```

**Result:**
```
User A acquires LOCK on position
User B waits...

User A calculates newQty=150, newAvg=153.33, COMMITS (lock released)

User B acquires LOCK
User B re-reads position (qty=150, avg=153.33)
User B calculates correctly:
  newQty = 150 + 25 = 175 ✓
  total = 150*153.33 + 25*155 = 22,999.50 + 3,875 = 26,874.50
  newAvg = 26,874.50 / 175 = 153.57

Result: qty=175 ✓ (correct!)
```

---

## Q6: "Describe Your Testing Strategy"

**Strong Answer:**

### Unit Tests (Service Layer)
```java
@SpringBootTest
public class PortfolioServiceTest {
    
    @MockBean
    private UserRepository userRepository;
    
    @MockBean
    private StockRepository stockRepository;
    
    @MockBean
    private PositionRepository positionRepository;
    
    @MockBean
    private TradeRepository tradeRepository;
    
    @Autowired
    private PortfolioService portfolioService;
    
    @Test
    public void testBuyStockInsufficientFunds() {
        // Arrange
        User user = new User(1L, "john", 100.00);  // Only $100
        Stock stock = new Stock("AAPL", 180.50);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(stockRepository.findById("AAPL")).thenReturn(Optional.of(stock));
        
        // Act & Assert
        assertThrows(IllegalStateException.class, () ->
            portfolioService.buyStock(1L, "AAPL", 100, null)
        );
        
        // Verify no position was created
        verify(positionRepository, never()).save(any(Position.class));
    }
    
    @Test
    public void testBuyStockSuccess() {
        // Arrange
        User user = new User(1L, "john", 10000.00);
        Stock stock = new Stock("AAPL", 180.50);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(stockRepository.findById("AAPL")).thenReturn(Optional.of(stock));
        when(positionRepository.findByUserAndTicker(user, "AAPL"))
            .thenReturn(Optional.empty());  // First buy
        
        // Act
        Trade trade = portfolioService.buyStock(
            1L, "AAPL", BigDecimal.valueOf(50), BigDecimal.valueOf(170)
        );
        
        // Assert
        assertNotNull(trade);
        assertEquals("AAPL", trade.getTicker());
        assertEquals("BUY", trade.getTradeType());
        
        // Verify position was created
        ArgumentCaptor<Position> captor = ArgumentCaptor.forClass(Position.class);
        verify(positionRepository).save(captor.capture());
        Position savedPosition = captor.getValue();
        assertEquals(BigDecimal.valueOf(50), savedPosition.getQuantity());
        assertEquals(BigDecimal.valueOf(180.50), savedPosition.getAverageBuyPrice());
    }
    
    @Test
    public void testAverageBuyPriceRecalculation() {
        // Arrange
        User user = new User(1L, "john", 20000.00);
        Stock stock = new Stock("AAPL", 160.00);
        Position existingPosition = new Position(
            user, "AAPL", 
            BigDecimal.valueOf(100),  // 100 shares @ $150
            BigDecimal.valueOf(150), 
            null
        );
        
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(stockRepository.findById("AAPL")).thenReturn(Optional.of(stock));
        when(positionRepository.findByUserAndTicker(user, "AAPL"))
            .thenReturn(Optional.of(existingPosition));
        
        // Act: Buy 50 more @ $160
        portfolioService.buyStock(1L, "AAPL", BigDecimal.valueOf(50), null);
        
        // Assert: newAvg = (100×150 + 50×160) / 150 = 23,000/150 = 153.33
        ArgumentCaptor<Position> captor = ArgumentCaptor.forClass(Position.class);
        verify(positionRepository, times(2)).save(captor.capture());  // User + Position
        Position updatedPosition = captor.getValue();
        
        assertEquals(BigDecimal.valueOf(150), updatedPosition.getQuantity());
        assertEquals(
            BigDecimal.valueOf(153.33).setScale(2, RoundingMode.HALF_UP),
            updatedPosition.getAverageBuyPrice()
        );
    }
}
```

### Integration Tests (Full Transaction)
```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class PortfolioIntegrationTest {
    
    @LocalServerPort
    private int port;
    
    @Autowired
    private TestRestTemplate restTemplate;
    
    @Autowired
    private UserRepository userRepository;
    
    @Test
    public void testBuyStockEnd2End() {
        // Create user in real database
        User user = new User("john_trader", "password");
        user.setBalance(new BigDecimal("10000.00"));
        User savedUser = userRepository.save(user);
        
        // Test endpoint
        HttpSession session = restTemplate.getRestTemplate()
            .getSessionCookieContainer()
            .getCookies()
            .iterator()
            .next();
        
        BuyRequest request = new BuyRequest();
        request.ticker = "AAPL";
        request.quantity = BigDecimal.valueOf(50);
        request.stopLossPrice = BigDecimal.valueOf(170);
        
        ResponseEntity<Trade> response = restTemplate.postForEntity(
            "http://localhost:" + port + "/api/portfolio/buy",
            new HttpEntity<>(request, headers),
            Trade.class
        );
        
        // Assertions
        assertEquals(HttpStatus.OK, response.getStatusCode());
        Trade trade = response.getBody();
        assertEquals("AAPL", trade.getTicker());
    }
}
```

### Test Pyramid Philosophy
```
                 △
               (5%)  E2E tests (full UI)
              △△△
            (15%)    Integration tests
           △△△△△△
         (80%)       Unit tests (fast, isolated)
```

- **Unit tests:** Fast (< 1ms), many, run on every commit
- **Integration tests:** Medium (10-100ms), fewer, run on PR
- **E2E tests:** Slow (1-10s), few, run before deploy

**Key principle:** Test behavior, not implementation
```java
// Bad test (brittle - breaks on refactor)
@Test
public void testSave() {
    Stock stock = new Stock("AAPL", 150);
    stockRepository.save(stock);
    verify(stockRepository).save(stock);  // Testing the test, not behavior!
}

// Good test (tests actual behavior)
@Test
public void testBuyStockUpdatesUserBalance() {
    User user = new User("john", 10000);
    Stock stock = new Stock("AAPL", 180.50);
    
    portfolioService.buyStock(user.getId(), "AAPL", 50, null);
    
    // Assert behavior: balance reduced by cost
    User updatedUser = userRepository.findById(user.getId()).get();
    assertEquals(10000 - (180.50 * 50), updatedUser.getBalance());
}
```

---

## Q7: "What Would You Do Differently?"

**Honest Answer (Shows Maturity):**

1. **Use Enums instead of Strings**
   ```java
   // Current: can typo "BYU" or "BUUY"
   private String tradeType;  // "BUY" or "SELL"
   
   // Better:
   @Enumerated(EnumType.STRING)
   private TradeType tradeType;  // BUY, SELL (compile-time safety)
   ```

2. **Add proper logging**
   ```java
   private static final Logger logger = LoggerFactory.getLogger(PortfolioService.class);
   
   logger.info("User {} bought {} shares of {}", userId, quantity, ticker);
   logger.warn("Stop-loss triggered for position {}", positionId);
   logger.error("Failed to execute stop-loss", exception);
   ```

3. **Implement proper error handling**
   ```java
   @ControllerAdvice
   public class ApiExceptionHandler {
       @ExceptionHandler(IllegalArgumentException.class)
       public ResponseEntity<ErrorResponse> handleIllegalArgument(
           IllegalArgumentException e) {
           return ResponseEntity.badRequest().body(
               new ErrorResponse("INVALID_INPUT", e.getMessage())
           );
       }
   }
   ```

4. **Use BCrypt for passwords** (not plain text)

5. **Add input validation annotations**
   ```java
   public class BuyRequest {
       @NotBlank(message = "Ticker required")
       private String ticker;
       
       @Positive(message = "Quantity must be > 0")
       private BigDecimal quantity;
   }
   ```

6. **Implement API rate limiting**
   ```java
   @RateLimiter(value = 100)  // 100 requests per minute
   @PostMapping("/buy")
   public ResponseEntity<?> buyStock(...) { }
   ```

7. **Add API documentation**
   ```java
   @ApiOperation(value = "Buy stocks", notes = "Execute buy order with optional stop-loss")
   @ApiResponses({
       @ApiResponse(code = 200, message = "Trade executed"),
       @ApiResponse(code = 400, message = "Insufficient funds")
   })
   @PostMapping("/buy")
   public ResponseEntity<?> buyStock(...) { }
   ```

---

# CONCLUSION: Interview Preparation Summary

**Key Takeaways:**

1. **Architecture:** Layered pattern, clean separation between HTTP (Controller), Business Logic (Service), Data Access (Repository), and Models

2. **Database:** Relational schema with thoughtful constraints (unique, not null, foreign keys)

3. **Transactions:** @Transactional ensures atomicity - all-or-nothing, no partial states

4. **Concurrency:** Row-level locking + transaction isolation prevents corruption

5. **Background Processing:** Scheduled tasks handle stop-loss checking without blocking user requests

6. **Financial Precision:** BigDecimal with scale=2 ensures money calculations are accurate

7. **Testing:** Unit tests for business logic, integration tests for full flows, E2E for UI

8. **Scalability:** Identified bottlenecks (DB, API servers, scheduled tasks) and outlined solutions (read replicas, load balancing, message queues, WebSocket)

9. **Trade-offs:** Every decision has pros/cons - made conscious choices suitable for MVP phase

10. **Code Quality:** Could be improved with enums, validation annotations, logging, error handling, but current version is production-ready for small team

**Final Interview Tips:**
- Be honest about limitations ("This works for 100K users, not 1M")
- Explain reasoning, not just implementation
- Show you'd make different choices at different scales
- Demonstrate knowledge of Spring Boot, JPA, SQL, transactional boundaries
- Connect to real financial/E-commerce systems (similar patterns)

---


