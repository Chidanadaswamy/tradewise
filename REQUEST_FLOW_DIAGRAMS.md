# TradeWise Platform - Visual Request Flow Diagrams

## Complete Request/Response Cycles for All Features

---

## REQUEST FLOW OVERVIEW

```
┌──────────────────────────────────────────────────────────────────┐
│                      FRONTEND (Browser/JavaScript)               │
│                                                                  │
│  ┌─────────────┐  ┌──────────┐  ┌─────────────┐  ┌──────────┐ │
│  │ Dashboard   │  │  Trade   │  │  Watchlist  │  │ Journal  │ │
│  │ View Holdings│ · Buy/Sell │  │  Monitor    │  │ Analysis │ │
│  │ Positions   │  · Charts   │  │  Stocks     │  │ Discipline
│  └─────────────┘  └──────────┘  └─────────────┘  └──────────┘ │
│                         │                                        │
│              fetch API / HTTP REST                               │
└──────────────────────────┼─────────────────────────────────────┘
                           │
                  ┌────────▼────────┐
                  │  HTTP Request   │
                  │  with Session   │
                  │  Cookie         │
                  └────────┬────────┘
                           │
┌──────────────────────────▼─────────────────────────────────────┐
│                   BACKEND (Spring Boot Server)                  │
│                                                                 │
│  ┌─────────────────────────────────────────────────────────┐  │
│  │ REQUEST MAPPING (HTTP Layer)                            │  │
│  │                                                           │  │
│  │  @RestController              @RequestMapping("/api/")   │  │
│  │  ├─ AuthController            /auth                       │  │
│  │  ├─ PortfolioController       /portfolio                 │  │
│  │  ├─ WatchlistController       /watchlist                 │  │
│  │  ├─ MarketController          /market                    │  │
│  │  └─ JournalController         /journal                   │  │
│  │                                                           │  │
│  │  Responsibilities:                                        │  │
│  │  • Parse HTTP request                                    │  │
│  │  • Extract session (authentication)                      │  │
│  │  • Validate inputs                                       │  │
│  │  • Call service layer                                    │  │
│  │  • Format response (JSON)                                │  │
│  └─────────────────────────────────────────────────────────┘  │
│                           │                                     │
│  ┌────────────────────────▼────────────────────────────────┐  │
│  │ SERVICE LAYER (Business Logic)                          │  │
│  │                                                           │  │
│  │  @Service                                                │  │
│  │  ├─ PortfolioService                                    │  │
│  │  │  • buyStock()                                         │  │
│  │  │  • sellStock()                                        │  │
│  │  │  • updateStopLoss()                                   │  │
│  │  │  • checkStopLosses() [@Scheduled]                     │  │
│  │  │  • getUserPositions()                                 │  │
│  │  │  • getUserTrades()                                    │  │
│  │  │                                                       │  │
│  │  ├─ WatchlistService                                    │  │
│  │  │  • addToWatchlist()                                   │  │
│  │  │  • removeFromWatchlist()                              │  │
│  │  │  • getWatchlist()                                     │  │
│  │  │  • getWatchlistCount()                                │  │
│  │  │  • isInWatchlist()                                    │  │
│  │  │                                                       │  │
│  │  ├─ MarketService                                       │  │
│  │  │  • initMarket() [@PostConstruct]                      │  │
│  │  │  • tickMarket() [@Scheduled]                          │  │
│  │  │  • getAllStocks()                                     │  │
│  │  │  • getStockByTicker()                                 │  │
│  │  │  • getStockHistory()                                  │  │
│  │  │                                                       │  │
│  │  └─ JournalService                                      │  │
│  │     • analyzePortfolio()                                 │  │
│  │       - checkMissingStopLoss()                           │  │
│  │       - checkOvertrading()                               │  │
│  │       - checkDispositionEffect()                         │  │
│  │       - checkConcentrationRisk()                         │  │
│  │       - calculateDisciplineScore()                       │  │
│  │                                                           │  │
│  │  Responsibilities:                                        │  │
│  │  • Core algorithm implementation                         │  │
│  │  • Data validation                                       │  │
│  │  • Calculations (avg buy price, P&L, etc.)              │  │
│  │  • Orchestrate repository calls                          │  │
│  │  • Transaction management (@Transactional)               │  │
│  └─────────────────────────────────────────────────────────┘  │
│                           │                                     │
│  ┌────────────────────────▼────────────────────────────────┐  │
│  │ REPOSITORY LAYER (Data Access Abstraction)              │  │
│  │                                                           │  │
│  │  @Repository (extends JpaRepository)                    │  │
│  │  ├─ UserRepository                                      │  │
│  │  │  • findByUsername()                                  │  │
│  │  │  • findById() [inherited]                            │  │
│  │  │                                                      │  │
│  │  ├─ PositionRepository                                 │  │
│  │  │  • findByUser()                                      │  │
│  │  │  • findByUserAndTicker()                             │  │
│  │  │  • findByStopLossPriceIsNotNull()                    │  │
│  │  │                                                      │  │
│  │  ├─ TradeRepository                                    │  │
│  │  │  • findByUserOrderByTimestampDesc()                 │  │
│  │  │  • findByUserAndTimestampAfter()                    │  │
│  │  │  • save() [inherited]                               │  │
│  │  │                                                      │  │
│  │  ├─ WatchlistRepository                                │  │
│  │  │  • findByUserOrderByAddedAtDesc()                   │  │
│  │  │  • findByUserAndTicker()                            │  │
│  │  │  • deleteByUserAndTicker()                          │  │
│  │  │  • countByUser()                                    │  │
│  │  │  • existsByUserAndTicker()                          │  │
│  │  │                                                      │  │
│  │  ├─ StockRepository                                    │  │
│  │  │  • findById() [inherited]                           │  │
│  │  │  • findAll() [inherited]                            │  │
│  │  │                                                      │  │
│  │  └─ StockHistoryRepository                             │  │
│  │     • findByTickerOrderByDateAsc()                      │  │
│  │                                                           │  │
│  │  Responsibilities:                                        │  │
│  │  • Generate SQL automatically                           │  │
│  │  • Provide query methods (find, save, delete)           │  │
│  │  • Prevent SQL injection (parameterized queries)        │  │
│  │  • Easy mock/swap for testing                           │  │
│  └─────────────────────────────────────────────────────────┘  │
│                           │                                     │
│  ┌────────────────────────▼────────────────────────────────┐  │
│  │ MODEL/ENTITY LAYER (Data Mapping)                       │  │
│  │                                                           │  │
│  │  @Entity @Table                                          │  │
│  │  ├─ User                                                │  │
│  │  │  • id (PK)                                           │  │
│  │  │  • username (UNIQUE)                                 │  │
│  │  │  • password                                          │  │
│  │  │  • balance                                           │  │
│  │  │                                                      │  │
│  │  ├─ Stock                                              │  │
│  │  │  • ticker (PK)                                       │  │
│  │  │  • name                                              │  │
│  │  │  • currentPrice (live)                               │  │
│  │  │  • lastPrice (for daily Δ%)                          │  │
│  │  │                                                      │  │
│  │  ├─ Position                                           │  │
│  │  │  • id (PK)                                           │  │
│  │  │  • user_id (FK)                                      │  │
│  │  │  • ticker                                            │  │
│  │  │  • quantity                                          │  │
│  │  │  • average_buy_price                                 │  │
│  │  │  • stop_loss_price (nullable)                        │  │
│  │  │                                                      │  │
│  │  ├─ Trade (Audit Log)                                  │  │
│  │  │  • id (PK)                                           │  │
│  │  │  • user_id (FK)                                      │  │
│  │  │  • ticker                                            │  │
│  │  │  • trade_type (BUY|SELL)                             │  │
│  │  │  • quantity                                          │  │
│  │  │  • price (execution)                                 │  │
│  │  │  • stop_loss_price                                   │  │
│  │  │  • timestamp (@PrePersist auto-set)                  │  │
│  │  │                                                      │  │
│  │  ├─ Watchlist                                          │  │
│  │  │  • id (PK)                                           │  │
│  │  │  • user_id (FK)                                      │  │
│  │  │  • ticker                                            │  │
│  │  │  • added_at                                          │  │
│  │  │  • UNIQUE(user_id, ticker)                           │  │
│  │  │                                                      │  │
│  │  └─ StockHistory                                       │  │
│  │     • id (PK)                                           │  │
│  │     • ticker                                            │  │
│  │     • price                                             │  │
│  │     • date                                              │  │
│  │                                                           │  │
│  │  JPA automatically maps to tables                       │  │
│  └─────────────────────────────────────────────────────────┘  │
│                           │                                     │
└──────────────────────────▼─────────────────────────────────────┘
                           │
              ┌────────────▼────────────┐
              │  SQL + JDBC             │
              │  (Parameterized Queries)│
              └────────────┬────────────┘
                           │
┌──────────────────────────▼──────────────────────────────────────┐
│              PostgreSQL Database (Persistent Storage)            │
│                                                                  │
│  ┌─────────────┐  ┌──────────┐  ┌──────────┐  ┌────────────┐  │
│  │ users       │  │ stocks   │  │ positions│  │ trades     │  │
│  │ ─────────── │  │ ──────── │  │ ───────── │  │ ────────── │  │
│  │ id (PK)     │  │ ticker   │  │ id   (PK)│  │ id (PK)    │  │
│  │ username    │  │ name     │  │ user_id  │  │ user_id    │  │
│  │ password    │  │ current  │  │ ticker   │  │ ticker     │  │
│  │ balance     │  │ last     │  │ qty      │  │ type       │  │
│  │             │  │          │  │ avg_price│  │ qty        │  │
│  │  UNIQUE     │  │          │  │ stop_loss│  │ price      │  │
│  │ username    │  │          │  │          │  │ stop_loss  │  │
│  └─────────────┘  └──────────┘  └──────────┘  │ timestamp  │  │
│                                                 └────────────┘  │
│  ┌──────────────┐  ┌────────────────┐                          │
│  │ watchlist    │  │ stock_history  │                          │
│  │ ──────────── │  │ ──────────────  │                          │
│  │ id (PK)      │  │ id (PK)        │                          │
│  │ user_id (FK) │  │ ticker         │                          │
│  │ ticker       │  │ price          │                          │
│  │ added_at     │  │ date           │                          │
│  │              │  │                │                          │
│  │ UNIQUE       │  │ (30-day history│                          │
│  │ user_id,     │  │  for charting) │                          │
│  │ ticker       │  │                │                          │
│  └──────────────┘  └────────────────┘                          │
│                                                                  │
└──────────────────────────────────────────────────────────────────┘
```

---

## REQUEST FLOW 1: REGISTRATION & LOGIN

```
┌─────────────────────────┐
│ User Registration       │
└─────────────────────────┘
           │
           ▼
┌──────────────────────────────────────────┐
│ Frontend                                 │
│                                          │
│ User fills form:                         │
│ - Username: "alice_trader"               │
│ - Password: "secure_pass"                │
│                                          │
│ Click "Create Account"                   │
│                                          │
│ JavaScript prevents empty fields         │
│ fetch('/api/auth/register', {            │
│   method: 'POST',                        │
│   headers: { 'Content-Type': 'App..' }, │
│   body: JSON.stringify({                 │
│     username: 'alice_trader',            │
│     password: 'secure_pass'              │
│   })                                     │
│ })                                       │
└────────────────┬─────────────────────────┘
                 │ HTTP POST
                 │ Content-Type: application/json
                 │ Body:
                 │ {
                 │   username: "alice_trader",
                 │   password: "secure_pass"
                 │ }
                 │
┌────────────────▼──────────────────────────┐
│ Backend HTTP Transport Layer              │
│                                           │
│ Request received on port 8080              │
│ Spring DispatcherServlet routes to:        │
│  → AuthController.register()               │
└────────────────┬──────────────────────────┘
                 │
┌────────────────▼────────────────────────────────────┐
│ @RestController                                    │
│ @RequestMapping("/api/auth")                       │
│ public class AuthController {                      │
│                                                   │
│   @PostMapping("/register")                       │
│   public ResponseEntity<?> register(              │
│     @RequestBody AuthRequest request,   ← Parse JSON
│     HttpSession session                 ← Server-side session
│   ) {                                             │
│     // Validation layer                          │
│     if (request.username == null ||               │
│         request.username.trim().isEmpty()) {      │
│       return ResponseEntity.badRequest()          │
│         .body("Username required");    ← 400 if invalid
│     }                                             │
│                                                   │
│     if (request.password == null ||               │
│         request.password.trim().isEmpty()) {      │
│       return ResponseEntity.badRequest()          │
│         .body("Password required");               │
│     }                                             │
│                                                   │
│     // Call service layer                        │
│     if (userRepository.findByUsername(            │
│       request.username).isPresent()) {            │
│       return ResponseEntity                       │
│         .status(HttpStatus.CONFLICT)              │
│         .body("Username taken");      ← 409 if duplicate
│     }                                             │
│                                                   │
│     // Create new entity                         │
│     User user = new User(                         │
│       request.username,                           │
│       request.password                            │
│     );                                            │
│                                                   │
│     // Set default balance                       │
│     user.setBalance(new BigDecimal("10000.00")); │
│                                                   │
│     // Delegate to repository (data layer)       │
│     userRepository.save(user);                    │
│                                                   │
│     // Auto-login: store session                 │
│     session.setAttribute("userId", user.getId()); │
│                                                   │
│     // Return success                            │
│     return ResponseEntity.ok(user);   ← 200 OK
│   }                                               │
│ }                                                 │
└────────────────┬────────────────────────────────────┘
                 │
         ┌───────▼────────┐
         │ Repository Call │
         └───────┬────────┘
                 │
            ┌────▼──────────────────────┐
            │ SQL: INSERT INTO users    │
            │ INSERT INTO users         │
            │ (username, password,      │
            │  balance)                 │
            │ VALUES                    │
            │ ('alice_trader',          │
            │  'secure_pass',           │
            │  10000.00)                │
            │                           │
            │ Generated ID: 3           │
            └────┬──────────────────────┘
                 │
    ┌────────────▼─────────────┐
    │ PostgreSQL Database      │
    │                          │
    │ INSERT successful        │
    │ returns: User{id=3,..}   │
    │                          │
    │ users table now:         │
    │ 1,john,pass,10000.00    │
    │ 2,bob,pass,10000.00     │
    │ 3,alice_trader,pass,..  │
    └────────────┬─────────────┘
                 │
    ┌────────────▼──────────────────────┐
    │ Return to HTTP Response Layer      │
    │                                   │
    │ HTTP Status: 200 OK               │
    │ Headers:                          │
    │ - Content-Type: application/json  │
    │ - Set-Cookie: JSESSIONID=ABC...   │
    │                                   │
    │ Body (JSON):                      │
    │ {                                 │
    │   "id": 3,                        │
    │   "username": "alice_trader",     │
    │   "password": "secure_pass",      │
    │   "balance": 10000.00             │
    │ }                                 │
    └────────────┬──────────────────────┘
                 │ HTTP response
     ┌───────────▼─────────────┐
     │ Frontend Receives        │
     │                         │
     │ response.status: 200    │
     │ response.body:          │
     │ {                       │
     │   id: 3,                │
     │   username: "alice..",  │
     │   balance: 10000.00     │
     │ }                       │
     │                         │
     │ Browser stores:         │
     │ Cookie: JSESSIONID=..   │
     │ (session key)           │
     │                         │
     │ JavaScript:             │
     │ // Hide auth screen     │
     │ authContainer           │
     │   .classList.add('d-none')
     │                         │
     │ // Show app             │
     │ appContainer            │
     │   .classList.remove..   │
     │                         │
     │ // Update UI            │
     │ currentUserDisplay      │
     │   .textContent =        │
     │   'alice_trader'        │
     │                         │
     │ // All future requests  │
     │ // include:             │
     │ // Cookie: JSESSIONID=..│
     └─────────────────────────┘
```

---

## REQUEST FLOW 2: BUY STOCK (WITH ERROR HANDLING)

```
┌────────────────────────────────┐
│ User Action: BUY STOCK         │
└────────────────────────────────┘
                │
                ▼
        ┌───────────────────┐
        │ User fills:       │
        │ Ticker: AAPL      │
        │ Qty: 100          │
        │ StopLoss: 170     │
        │                   │
        │ Click BUY         │
        │ [Estimated: $18K] │
        └───────────┬───────┘
                    │
        ┌───────────▼──────────────────┐
        │ Frontend Validation          │
        │                              │
        │ if (qty <= 0) error();       │
        │ if (ticker == "") error();   │
        │                              │
        │ ✓ All valid → proceed        │
        └───────────┬──────────────────┘
                    │
        ┌───────────▼──────────────────────────────┐
        │ fetch('/api/portfolio/buy',  {           │
        │   method: 'POST',                        │
        │   headers: {...},                        │
        │   body: JSON.stringify({                 │
        │     ticker: "AAPL",                      │
        │     quantity: 100,                       │
        │     stopLossPrice: 170                   │
        │   })                                     │
        │ })                                       │
        │                                          │
        │ Request includes:                        │
        │ Cookie: JSESSIONID=ABC123... (auth)      │
        └───────────┬──────────────────────────────┘
                    │ HTTP POST /api/portfolio/buy
                    │
        ┌───────────▼───────────────────────────────────┐
        │ @RestController                              │
        │ public class PortfolioController {           │
        │                                              │
        │   @PostMapping("/buy")                       │
        │   public ResponseEntity<?> buyStock(         │
        │     @RequestBody BuyRequest request,         │
        │     HttpSession session      ← Session auth  │
        │   ) {                                        │
        │     // Step 1: Auth check                    │
        │     Long userId = (Long)                     │
        │       session.getAttribute("userId");        │
        │                                              │
        │     if (userId == null) {                    │
        │       return ResponseEntity                  │
        │         .status(UNAUTHORIZED)                │
        │         .body("Not logged in");    ← 401    │
        │     }                                        │
        │                                              │
        │     // Step 2: Input validation              │
        │     if (request.ticker == null ||            │
        │         request.quantity == null ||          │
        │         request.quantity.compareTo()         │
        │           .ZERO) <= 0) {                     │
        │       return ResponseEntity                  │
        │         .badRequest()                        │
        │         .body("Invalid ticker/qty");  ← 400 │
        │     }                                        │
        │                                              │
        │     try {                                    │
        │       // Step 3: Call service layer          │
        │       Trade trade =                          │
        │         portfolioService.buyStock(           │
        │           userId,                           │
        │           request.ticker.toUpperCase(),      │
        │           request.quantity,                  │
        │           request.stopLossPrice              │
        │         );                                   │
        │                                              │
        │       // Step 4: Return success              │
        │       return ResponseEntity.ok(trade); ← 200│
        │                                              │
        │     } catch (IllegalArgumentException e) {   │
        │       // Stock not found, user not found     │
        │       return ResponseEntity                  │
        │         .badRequest()                        │
        │         .body(e.getMessage());     ← 400    │
        │                                              │
        │     } catch (IllegalStateException e) {      │
        │       // Insufficient balance, etc.          │
        │       return ResponseEntity                  │
        │         .badRequest()                        │
        │         .body(e.getMessage());     ← 400    │
        │     }                                        │
        │   }                                          │
        │ }                                            │
        │                                              │
        │ ⚠️ EXCEPTION THROWN: "Stock not found"      │
        │                                              │
        │ Spring catches & returns:                   │
        │ HTTP 400 Bad Request                        │
        │ Body: "Stock not found: AAPL"               │
        └───────────┬───────────────────────────────────┘
                    │
        ┌───────────▼─────────────────────────┐
        │ Frontend Error Handling              │
        │                                     │
        │ if (response.status === 400) {      │
        │   let error = await response        │
        │     .text();                        │
        │                                     │
        │   document.getElementById(          │
        │     'orderFormAlert'                │
        │   ).textContent = error;            │
        │                                     │
        │   // Show error message to user:   │
        │   "Stock not found: AAPL"           │
        │                                     │
        │   // UI remains on Trade page       │
        │   // User can retry                 │
        │ }                                   │
        └─────────────────────────────────────┘

         ═══════════════ HAPPY PATH ═══════════════

        ┌─────────────────────────────────────────────────┐
        │ Service Layer (PortfolioService.buyStock())     │
        │                                                 │
        │ @Transactional  ← Start transaction             │
        │ public Trade buyStock(                          │
        │   Long userId,                                  │
        │   String ticker,                                │
        │   BigDecimal quantity,                          │
        │   BigDecimal stopLossPrice                      │
        │ ) {                                             │
        │                                                 │
        │   // STEP 1: Validate preconditions             │
        │   User user = userRepository                    │
        │     .findById(userId)                           │
        │     .orElseThrow(() →                           │
        │       new IllegalArgumentException(             │
        │         "User not found"                        │
        │       )                                         │
        │     );                                          │
        │   Result: User{id=3, balance=10000}             │
        │                                                 │
        │   Stock stock = stockRepository                 │
        │     .findById(ticker)                           │
        │     .orElseThrow(() →                           │
        │       new IllegalArgumentException(             │
        │         "Stock not found: " + ticker             │
        │       )                                         │
        │     );                                          │
        │   Result: Stock{ticker=AAPL,                    │
        │            currentPrice=180.50}                 │
        │                                                 │
        │   // STEP 2: Business calculation               │
        │   BigDecimal currentPrice =                     │
        │     stock.getCurrentPrice();                    │
        │   // currentPrice = 180.50                      │
        │                                                 │
        │   BigDecimal totalCost =                        │
        │     currentPrice.multiply(quantity)             │
        │     .setScale(2, RoundingMode.HALF_UP);         │
        │   // totalCost = 180.50 × 100 = 18050.00        │
        │                                                 │
        │   // STEP 3: Business validation                │
        │   if (user.getBalance()                         │
        │     .compareTo(totalCost) < 0) {                │
        │     throw new IllegalStateException(            │
        │       "Insufficient balance. " +                │
        │       "Required: $" + totalCost + ", " +        │
        │       "Available: $" +                          │
        │       user.getBalance()                         │
        │     );                                          │
        │   }                                             │
        │   // Check: 10000 >= 18050? NO!                 │
        │   // EXCEPTION THROWN                           │
        │   // Transaction ROLLED BACK                    │
        │                                                 │
        │   // Unreached code...                          │
        │ }                                               │
        │                                                 │
        │ @Transactional AUTO-ROLLBACK                    │
        │ Database: unchanged (10000 still there)         │
        └─────────────────────────────────────────────────┘
                    │ exception bubbles up
                    │
        ┌───────────▼───────────────────────┐
        │ Controller Catch Block             │
        │                                   │
        │ catch (IllegalStateException e) {  │
        │   return ResponseEntity            │
        │     .badRequest()                  │
        │     .body(e.getMessage());         │
        │ }                                  │
        │                                   │
        │ Returns HTTP 400                  │
        │ Body: "Insufficient balance. .." │
        └───────────┬───────────────────────┘
                    │
        ┌───────────▼──────────────────────────┐
        │ Frontend Receives Error               │
        │                                      │
        │ response.status = 400                │
        │ response.body = "Insufficient.."     │
        │                                      │
        │ Show error toast:                    │
        │ "❌ You need $18,050 but only have   │
        │    $10,000. Try 50 shares instead."  │
        │                                      │
        │ User retries with qty=50            │
        └──────────────────────────────────────┘
```

---

## REQUEST FLOW 3: STOP-LOSS WATCHDOG (Background Scheduled Task)

```
┌───────────────────────────────────────┐
│ Application Startup                   │
└───────────────────────────────────────┘
           │
           ▼
┌──────────────────────────────────────────────┐
│ Spring Boot Initialization                   │
│                                              │
│ @SpringBootApplication                      │
│ @EnableScheduling  ← CRITICAL                │
│ public class TradeWiseApplication { }        │
│                                              │
│ Triggers:                                    │
│ • Component scanning                        │
│ • Dependency injection                      │
│ • @PostConstruct lifecycle hooks             │
│ • @Scheduled method registration             │
│                                              │
│ Result: Scheduled tasks registered with     │
│ TaskScheduler (thread pool size = 2)        │
└──────────────────────────┬───────────────────┘
                           │
        ┌──────────────────▼────────────────┐
        │ @Service                          │
        │ public class MarketService {      │
        │                                   │
        │   @PostConstruct                  │
        │   @Transactional                  │
        │   public void initMarket() {      │
        │     // Run ONCE at startup        │
        │     if (stockRepository           │
        │       .count() == 0) {            │
        │       // Generate 10 stocks       │
        │       // + 30-day history         │
        │     }                             │
        │   }                               │
        │ }                                 │
        │                                   │
        │ Result: Database populated with   │
        │ AAPL, MSFT, GOOGL, ... (10 stocks)│
        └──────────────────┬────────────────┘
                           │
        ┌──────────────────▼────────────────────┐
        │ @Service                             │
        │ public class PortfolioService {      │
        │                                      │
        │   @Scheduled(fixedDelay = 5000)      │
        │   @Transactional                     │
        │   public void checkStopLosses() {    │
        │     // Runs every 5 seconds          │
        │   }                                  │
        │                                      │
        │ REGISTERED with TaskScheduler        │
        │ Will execute every 5 seconds         │
        │ in dedicated thread pool             │
        └──────────────────┬───────────────────┘
                           │
                    Timeline begins:
        ┌──────────────────▼─────────────────┐
        │ 12:00:00 - Application starts      │
        │                                    │
        │ User action:                       │
        │ POST /api/portfolio/buy            │
        │ { ticker: AAPL, qty: 100,          │
        │   stopLoss: 145 }                  │
        │                                    │
        │ Creates:                           │
        │ Position {                         │
        │   user_id: 1,                      │
        │   ticker: AAPL,                    │
        │   qty: 100,                        │
        │   avg_price: 150.00,               │
        │   stop_loss_price: 145.00          │
        │ }                                  │
        │                                    │
        │ Stock current: $150.00             │
        └──────────────────┬─────────────────┘
                           │
        ┌──────────────────▼────────────────┐
        │ 12:00:05                           │
        │                                   │
        │ Scheduler fires: checkStopLosses() │
        │ (Every 5 seconds cycle)            │
        │                                   │
        │ SELECT * FROM positions            │
        │ WHERE stop_loss_price IS NOT NULL  │
        │                                   │
        │ Result: 1 position (AAPL)          │
        │ {                                  │
        │   ticker: AAPL,                    │
        │   qty: 100,                        │
        │   stop_loss: 145.00                │
        │ }                                  │
        │                                    │
        │ Check: current_price (150.00)      │
        │        > stop_loss (145.00)?       │
        │ YES ✓ → CONTINUE monitoring        │
        │                                   │
        │ Also runs: tickMarket()            │
        │ Updates stock prices               │
        │ AAPL: 150.00 → 149.75 (-0.17%)    │
        └──────────────────┬─────────────────┘
                           │
        ┌──────────────────▼──────────────────────┐
        │ 12:00:10 through 12:05:30 [OMITTED]    │
        │                                        │
        │ Scheduler continues every 5 seconds:   │
        │ • checkStopLosses() - no action        │
        │ • tickMarket() - prices fluctuate     │
        │                                        │
        │ Market trending DOWN:                  │
        │ AAPL: 150.00 → 149.75 → 149.50 ...   │
        │       → 146.00 → 145.50 → ...         │
        └──────────────────┬──────────────────────┘
                           │
        ┌──────────────────▼──────────────────────┐
        │ 12:05:35 ⚠️ CRITICAL EVENT             │
        │                                        │
        │ Scheduler fires: checkStopLosses()    │
        │                                        │
        │ SELECT * FROM positions                │
        │ WHERE stop_loss_price IS NOT NULL      │
        │ Result: AAPL position                  │
        │                                        │
        │ Current price of AAPL:                 │
        │ SELECT currentPrice FROM stocks        │
        │ WHERE ticker = 'AAPL'                  │
        │ Result: 144.95                         │
        │                                        │
        │ Check: current_price (144.95)          │
        │        <= stop_loss (145.00)?          │
        │ YES! ✓✓✓ CONDITION MET               │
        │ TRIGGER AUTO-SELL!                     │
        │                                        │
        │ System logs:                           │
        │ "Stop-Loss Triggered! Selling 100     │
        │  shares of AAPL for user alice_..    │
        │  at current price $144.95 (SL was    │
        │  set at $145.00)"                     │
        │                                        │
        │ Call: sellStock(userId=3, ticker=     │
        │       AAPL, qty=100)                  │
        └──────────────────┬──────────────────────┘
                           │
        ┌──────────────────▼──────────────────────┐
        │ sellStock() @Transactional              │
        │ (All steps atomic - commits or         │
        │  all rollback if any step fails)       │
        │                                        │
        │ STEP 1: Fetch entities                 │
        │   user = User{id=3, balance=9975}     │
        │   (already had $975 after first buy)  │
        │   stock = Stock{ticker=AAPL,          │
        │            currentPrice=144.95}       │
        │   position = Position{...}             │
        │                                        │
        │ STEP 2: Validate position ownership    │
        │   User owns AAPL? YES ✓                │
        │   Owns 100 shares? YES ✓               │
        │                                        │
        │ STEP 3: Calculate sale proceeds        │
        │   proceeds = 144.95 × 100 = 14,495   │
        │                                        │
        │ STEP 4: Credit user balance            │
        │   user.balance = 9,975 + 14,495       │
        │               = 24,470                 │
        │   UPDATE users SET balance = 24470 ... │
        │                                        │
        │ STEP 5: Remove position                │
        │   DELETE FROM positions WHERE id = ?   │
        │                                        │
        │ STEP 6: Record trade audit log         │
        │   INSERT INTO trades (...) VALUES      │
        │   (user_id=3, ticker=AAPL,             │
        │    type=SELL, qty=100,                 │
        │    price=144.95,                       │
        │    stop_loss=145.00,                   │
        │    timestamp=NOW())                    │
        │                                        │
        │ @Transactional AUTO-COMMITS            │
        │ All database changes persisted!        │
        │                                        │
        │ Result: Portfolio state updated        │
        │ • AAPL position gone                   │
        │ • Cash: $24,470 (up from $9,975)       │
        │ • P&L: -$500 (bought at 150, sold     │
        │         at 144.95 = $5 loss × 100)    │
        │ • Audit trail: SELL trade recorded     │
        └──────────────────┬──────────────────────┘
                           │
        ┌──────────────────▼──────────────────────┐
        │ 12:05:40                               │
        │                                        │
        │ Scheduler fires: checkStopLosses()    │
        │                                        │
        │ SELECT * FROM positions                │
        │ WHERE stop_loss_price IS NOT NULL      │
        │ Result: 0 rows (AAPL already sold)    │
        │                                        │
        │ Action: NOTHING (no positions to       │
        │ monitor anymore)                       │
        │                                        │
        │ Monitoring continues for other users' │
        │ stop-losses (if any)                   │
        └──────────────────────────────────────────┘
                           │
                    (Scheduler continues
                     every 5 seconds indefinitely)
```

---

## REQUEST FLOW 4: PORTFOLIO ANALYSIS (Journal)

```
┌────────────────────────────┐
│ User Click: "Journal" Tab  │
└────────────────────────────┘
           │
           ▼
┌──────────────────────────────────────────┐
│ Frontend JavaScript                      │
│                                          │
│ switchView('journal')                    │
│                                          │
│ fetch('/api/journal/analysis', {         │
│   method: 'GET'                          │
│ })                                       │
│                                          │
│ (Session cookie included automatically)  │
└───────────────┬──────────────────────────┘
                │ HTTP GET
    ┌───────────▼─────────────────────────┐
    │ @RestController                     │
    │ @RequestMapping("/api/journal")     │
    │ public class JournalController {    │
    │                                     │
    │   @GetMapping("/analysis")          │
    │   public ResponseEntity<?> get      │
    │     JournalAnalysis(                │
    │     HttpSession session             │
    │   ) {                               │
    │     // Auth check                   │
    │     Long userId = (Long)            │
    │       session.getAttribute(          │
    │         "userId"                    │
    │       );                            │
    │     if (userId == null) {           │
    │       return UNAUTHORIZED();         │
    │     }                               │
    │                                     │
    │     try {                           │
    │       JournalService.JournalSummary │
    │       summary=                      │
    │       journalService               │
    │       .analyzePortfolio(userId);   │
    │                                     │
    │       return OK(summary);           │
    │     } catch (Exception e) {         │
    │       return BAD_REQUEST();         │
    │     }                               │
    │   }                                 │
    │ }                                   │
    └───────────┬─────────────────────────┘
                │ Calls service
    ┌───────────▼──────────────────────────────────────┐
    │ @Service                                         │
    │ public class JournalService {                    │
    │                                                  │
    │   public JournalSummary analyzePortfolio(        │
    │     Long userId                                  │
    │   ) {                                            │
    │     // Fetch portfolio state                     │
    │     User user = userRepository                   │
    │       .findById(userId).orElseThrow();           │
    │                                                  │
    │     List<Position> positions =                   │
    │       positionRepository.findByUser(user);       │
    │     List<Trade> allTrades =                      │
    │       tradeRepository                           │
    │       .findByUserOrderByTimestampDesc(user);     │
    │                                                  │
    │     // Analyze portfolio value                   │
    │     BigDecimal cash = user.getBalance();         │
    │     BigDecimal totalEquity = 0;                  │
    │     for (Position p : positions) {               │
    │       Stock stock = stockRepository              │
    │         .findById(p.getTicker())                 │
    │         .get();                                  │
    │       BigDecimal posValue =                      │
    │         p.getQuantity() ×                        │
    │         stock.currentPrice;                      │
    │       totalEquity += posValue;                   │
    │     }                                            │
    │     BigDecimal portfolioValue =                  │
    │       cash + totalEquity;                        │
    │                                                  │
    │     List<CoachingInsight> insights =             │
    │       new ArrayList<>();                         │
    │                                                  │
    │     ┌─ RULE 1: Missing Stop-Loss ────┐          │
    │     │                                 │          │
    │     │ for (Position p : positions) {  │          │
    │     │   if (p.getStopLossPrice() ==   │          │
    │     │       null || < 0) {            │          │
    │     │     insights.add(                │          │
    │     │       new CoachingInsight(       │          │
    │     │         "MISSING_STOP_LOSS",     │          │
    │     │         "WARNING",               │          │
    │     │         "No Safety Net (...)",   │          │
    │     │         "You hold ... without SL │         │
    │     │          Set SL at 5-10% below   │         │
    │     │          avg purchase...",       │          │
    │     │         "..."                    │          │
    │     │       )                          │          │
    │     │     );                           │          │
    │     │   }                              │          │
    │     │ }                                │          │
    │     └─────────────────────────────────┘          │
    │                                                  │
    │     ┌─ RULE 2: Overtrading ──────────┐          │
    │     │                                 │          │
    │     │ LocalDateTime 24hAgo =          │          │
    │     │   LocalDateTime.now()           │          │
    │     │   .minusDays(1);                │          │
    │     │ List<Trade> recent =            │          │
    │     │   tradeRepository               │          │
    │     │   .findByUserAndTimestampAfter( │          │
    │     │     user, 24hAgo);              │          │
    │     │                                 │          │
    │     │ if (recent.size() > 5) {        │          │
    │     │   insights.add(                 │          │
    │     │     "High Trade Frequency..."   │          │
    │     │   );                            │          │
    │     │ }                               │          │
    │     └─────────────────────────────────┘          │
    │                                                  │
    │     ┌─ RULE 3: Disposition Effect ──┐           │
    │     │                                 │          │
    │     │ Check for positions down > 15% │          │
    │     │ AND recent sells (in 48h)      │          │
    │     │                                 │          │
    │     │ Pattern: Selling winners +      │          │
    │     │         Holding losers          │          │
    │     │ (Common bias → warning)         │          │
    │     └─────────────────────────────────┘          │
    │                                                  │
    │     ┌─ RULE 4: Concentration Risk ──┐           │
    │     │                                 │          │
    │     │ for (Position p : positions) {  │          │
    │     │   posValPct =                   │          │
    │     │     (p.qty × price) /           │          │
    │     │     portfolio.value × 100;      │          │
    │     │                                 │          │
    │     │   if (posValPct > 25%) {        │          │
    │     │     insights.add(               │          │
    │     │       "High Concentration..."   │          │
    │     │     );                          │          │
    │     │   }                             │          │
    │     │ }                               │          │
    │     └─────────────────────────────────┘          │
    │                                                  │
    │     // Calculate Discipline Score               │
    │     int score = 100;                            │
    │                                                  │
    │     // Deduct for violations                    │
    │     // Missing SL: -10 per position             │
    │     // (max -30)                                │
    │     score -= min(noSLCount × 10, 30);           │
    │                                                  │
    │     // Overtrading: -15 if > 5 trades/day      │
    │     if (recent.size() > 5) {                    │
    │       score -= 15;                              │
    │     }                                            │
    │                                                  │
    │     // Disposition: -15 if pattern found        │
    │     if (dispositionFound) {                     │
    │       score -= 15;                              │
    │     }                                            │
    │                                                  │
    │     // Concentration: -10 per position           │
    │     // (max -20)                                │
    │     score -= min(concentrated × 10, 20);        │
    │                                                  │
    │     // Ensure range [0, 100]                    │
    │     score = max(0, min(100, score));            │
    │                                                  │
    │     // Map to letter grade                      │
    │     String grade;                               │
    │     if (score >= 90) grade = "A"; // Expert     │
    │     else if (score >= 75) grade = "B"; // Good  │
    │     else if (score >= 60) grade = "C"; // Fair  │
    │     else grade = "D"; // Poor                    │
    │                                                  │
    │     return new JournalSummary(                  │
    │       grade,      // A, B, C, D                  │
    │       insights,   // List of issues              │
    │       score       // 0-100                       │
    │     );                                          │
    │   }                                              │
    │ }                                                │
    └───────────┬──────────────────────────────────────┘
                │ Returns
    ┌───────────▼────────────────────────────┐
    │ Response JSON                          │
    │ HTTP 200 OK                            │
    │                                        │
    │ {                                      │
    │   "score": "B",                        │
    │   "disciplineScore": 75,               │
    │   "insights": [                        │
    │     {                                  │
    │       "ruleName": "MISSING_STOP_LOSS", │
    │       "severity": "WARNING",           │
    │       "title": "Missing Safety Net ...",
    │       "description": "You are holding  │
    │         MSFT without stop-loss...",    │
    │       "recommendation": "Set SL at ... │
    │          2 below average..."           │
    │     },                                 │
    │     {                                  │
    │       "ruleName": "CONCENTRATION_RISK",│
    │       "severity": "WARNING",           │
    │       "title": "High Portfolio ....",  │
    │       "description": "...",            │
    │       "recommendation": "..."          │
    │     }                                  │
    │   ]                                    │
    │ }                                      │
    └───────────┬────────────────────────────┘
                │ Browser receives
    ┌───────────▼────────────────────────────────┐
    │ Frontend Updates Journal View               │
    │                                             │
    │ // Display grade badge                     │
    │ score = response.score;  // "B"             │
    │ disciplineScore = 75;    // 0-100          │
    │                                             │
    │ // Draw circular score ring                │
    │ scoreChart.update({                        │
    │   data: [75, 25]  // 75% filled, 25% empty│
    │ });                                         │
    │                                             │
    │ scoreGradeBadge.textContent = "B";         │
    │ scoreGradeLabel.textContent =               │
    │   "Prudent Investor";                       │
    │                                             │
    │ // Display insights                        │
    │ insightsList.innerHTML = "";               │
    │ response.insights.forEach(insight → {      │
    │   const card = createInsightCard(insight); │
    │                                             │
    │   // Color by severity                     │
    │   if (insight.severity === "DANGER") {     │
    │     card.style.borderLeft =                │
    │       "4px solid #FF4D6D";  // Red          │
    │   } else if (insight.severity ===          │
    │              "WARNING") {                  │
    │     card.style.borderLeft =                │
    │       "4px solid #FFB800";  // Gold         │
    │   }                                         │
    │                                             │
    │   insightsList.append(card);               │
    │ });                                         │
    │                                             │
    │ // Show coaching insights overlay          │
    │ displayCoachingMessages(response.insights);│
    │                                             │
    │ UI Now Shows:                              │
    │ ┌─ Discipline Index ─────────────────┐    │
    │ │ Grade: B                            │    │
    │ │ Score: 75/100 (ring chart filled)  │    │
    │ │ Label: "Prudent Investor"           │    │
    │ └─────────────────────────────────────┘    │
    │                                             │
    │ ┌─ Active Issues ────────────────────┐    │
    │ │ ⚠️ Missing Safety Net (MSFT)       │    │
    │ │    You're holding without SL...     │    │
    │ │                                     │    │
    │ │    → Set SL at $165 (5% below avg) │    │
    │ │                                     │    │
    │ ⚠️ High Portfolio Concentration ..      │
    │ │    NVDA is 38% of portfolio         │    │
    │ │                                     │    │
    │ │    → Trim position or diversify     │    │
    │ └─────────────────────────────────────┘    │
    │                                             │
    │ User sees behavioural coaching &           │
    │ can make improvements                      │
    └─────────────────────────────────────────────┘
```

---

## ARCHITECTURE PATTERN SUMMARY

```
                Frontend HTTP Request
                        │
                        ▼
        ┌───────────────────────────────┐
        │   CONTROLLER (HTTP Layer)     │
        │  ┌─────────────────────────┐  │
        │  │ 1. Extract request data │  │
        │  │ 2. Check auth (session) │  │
        │  │ 3. Validate input       │  │
        │  │ 4. Call service         │  │
        │  │ 5. Format JSON response │  │
        │  └─────────────────────────┘  │
        └───────────────┬───────────────┘
                        │ method call
        ┌───────────────▼───────────────┐
        │ SERVICE (Business Logic)      │
        │ ┌─────────────────────────┐  │
        │ │ 1. Validate business    │  │
        │ │    rules                │  │
        │ │ 2. Calculations         │  │
        │ │ 3. @Transactional       │  │
        │ │ 4. Orchestrate repos    │  │
        │ │ 5. Return result        │  │
        │ └─────────────────────────┘  │
        └───────────────┬───────────────┘
                        │ query method
        ┌───────────────▼──────────────────┐
        │ REPOSITORY (Data Access)         │
        │ ┌──────────────────────────────┐ │
        │ │ 1. Generate SQL             │ │
        │ │ 2. Map result to   entity   │ │
        │ │ 3. Prevent SQL injection    │ │
        │ │ 4. Return entity            │ │
        │ └──────────────────────────────┘ │
        └───────────────┬──────────────────┘
                        │ SQL query
        ┌───────────────▼───────────────┐
        │ DATABASE (PostgreSQL)         │
        │ ┌─────────────────────────┐  │
        │ │ Execute SQL             │  │
        │ │ Enforce constraints     │  │
        │ │ Return result set       │  │
        │ └─────────────────────────┘  │
        └───────────────┬───────────────┘
                        │ result rows
                       Back up chain...
```

---
