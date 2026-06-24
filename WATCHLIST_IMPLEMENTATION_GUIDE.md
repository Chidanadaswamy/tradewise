# Watchlist Feature - Complete Analysis & Implementation Guide

## 🎯 Overview
Your TradeWise application has a fully functional watchlist feature that allows authenticated users to save stocks they want to monitor. Here's a comprehensive breakdown of how it works and how to use it.

---

## 📊 Database Schema

### Watchlist Table
```sql
CREATE TABLE watchlist (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT REFERENCES users(id) ON DELETE CASCADE,
    ticker VARCHAR(10) REFERENCES stocks(ticker),
    added_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(user_id, ticker)  -- Prevents duplicates per user
);
```

**Key Points:**
- ✅ **User-specific**: Each watchlist entry is tied to a specific user via `user_id`
- ✅ **Unique constraint**: A user cannot add the same stock twice
- ✅ **Auto timestamp**: `added_at` is automatically set when a stock is added
- ✅ **Referenced data**: Ticker must exist in the `stocks` table

---

## 🏗️ Architecture Stack

### 1. **Database Layer** (PostgreSQL)
- **Table**: `watchlist`
- **Relationships**: 
  - `user_id` → Foreign key to `users` table
  - `ticker` → Foreign key to `stocks` table

### 2. **Model Layer** (Java Entity)
**File**: `src/main/java/com/seedling/platform/model/Watchlist.java`

```java
@Entity
@Table(name = "watchlist", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"user_id", "ticker"})
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
        addedAt = LocalDateTime.now();  // Auto-set on creation
    }
}
```

**Properties:**
- `id`: Primary key (auto-generated)
- `user`: Reference to the User entity
- `ticker`: Stock symbol (e.g., "AAPL", "MSFT")
- `addedAt`: Timestamp when added (auto-generated)

### 3. **Repository Layer** (Data Access)
**File**: `src/main/java/com/seedling/platform/repository/WatchlistRepository.java`

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

**Methods:**
- `findByUserOrderByAddedAtDesc()` - Get all watchlist items (newest first)
- `findByUserAndTicker()` - Find a specific watchlist entry
- `deleteByUserAndTicker()` - Remove a stock from watchlist
- `countByUser()` - Count total watchlist items
- `existsByUserAndTicker()` - Check if stock is already in watchlist

### 4. **Service Layer** (Business Logic)
**File**: `src/main/java/com/seedling/platform/service/WatchlistService.java`

#### Key Methods:

**1. Add to Watchlist**
```java
@Transactional
public Watchlist addToWatchlist(Long userId, String ticker) {
    // 1. Validate user exists
    User user = userRepository.findById(userId)
        .orElseThrow(() -> new IllegalArgumentException("User not found"));

    // 2. Validate stock exists
    stockRepository.findById(ticker.toUpperCase())
        .orElseThrow(() -> new IllegalArgumentException("Stock not found: " + ticker));

    String normalizedTicker = ticker.toUpperCase();

    // 3. Check for duplicates (prevent adding same stock twice)
    if (watchlistRepository.existsByUserAndTicker(user, normalizedTicker)) {
        throw new IllegalStateException("Stock already in watchlist");
    }

    // 4. Create and save watchlist entry
    Watchlist entry = new Watchlist(user, normalizedTicker);
    return watchlistRepository.save(entry);
}
```

**2. Remove from Watchlist**
```java
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
```

**3. Get Watchlist with Enriched Data**
```java
public List<WatchlistItemDTO> getWatchlist(Long userId) {
    User user = userRepository.findById(userId)
        .orElseThrow(() -> new IllegalArgumentException("User not found"));

    List<Watchlist> entries = watchlistRepository.findByUserOrderByAddedAtDesc(user);
    List<WatchlistItemDTO> items = new ArrayList<>();

    for (Watchlist entry : entries) {
        Optional<Stock> stockOpt = stockRepository.findById(entry.getTicker());
        if (stockOpt.isPresent()) {
            Stock stock = stockOpt.get();
            
            // Calculate daily price change percentage
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
                changePercent,
                entry.getAddedAt()
            ));
        }
    }

    return items;
}
```

**WatchlistItemDTO** (Return object):
```java
public static class WatchlistItemDTO {
    public String ticker;           // Stock symbol
    public String stockName;        // Company name
    public BigDecimal currentPrice; // Live price
    public double dailyChangePercent; // Change from last price
    public LocalDateTime addedAt;   // When added to watchlist
}
```

**4. Check if Stock in Watchlist**
```java
public boolean isInWatchlist(Long userId, String ticker) {
    User user = userRepository.findById(userId)
        .orElseThrow(() -> new IllegalArgumentException("User not found"));
    return watchlistRepository.existsByUserAndTicker(user, ticker.toUpperCase());
}
```

### 5. **Controller Layer** (REST API)
**File**: `src/main/java/com/seedling/platform/controller/WatchlistController.java`

---

## 🔌 REST API Endpoints

All endpoints require user authentication (via session).

### 1️⃣ Add Stock to Watchlist
**Endpoint:** `POST /api/watchlist/add`

**Request Body:**
```json
{
    "ticker": "AAPL"
}
```

**Success Response (200):**
```json
{
    "message": "Added to watchlist",
    "ticker": "AAPL"
}
```

**Error Responses:**
- `401 Unauthorized` - Not authenticated
- `400 Bad Request` - Missing ticker or stock doesn't exist
- `409 Conflict` - Stock already in watchlist

**Example cURL:**
```bash
curl -X POST http://localhost:8080/api/watchlist/add \
  -H "Content-Type: application/json" \
  -d '{"ticker": "AAPL"}'
```

---

### 2️⃣ Remove Stock from Watchlist
**Endpoint:** `DELETE /api/watchlist/remove/{ticker}`

**Success Response (200):**
```json
{
    "message": "Removed from watchlist",
    "ticker": "AAPL"
}
```

**Error Responses:**
- `401 Unauthorized` - Not authenticated
- `400 Bad Request` - Stock not in watchlist

**Example cURL:**
```bash
curl -X DELETE http://localhost:8080/api/watchlist/remove/AAPL
```

---

### 3️⃣ Get All Watchlist Items
**Endpoint:** `GET /api/watchlist`

**Success Response (200):**
```json
[
    {
        "ticker": "AAPL",
        "stockName": "Apple Inc.",
        "currentPrice": 195.50,
        "dailyChangePercent": 2.45,
        "addedAt": "2026-06-18T10:30:15"
    },
    {
        "ticker": "MSFT",
        "stockName": "Microsoft Corporation",
        "currentPrice": 420.75,
        "dailyChangePercent": -1.20,
        "addedAt": "2026-06-17T14:22:45"
    }
]
```

**Error Responses:**
- `401 Unauthorized` - Not authenticated
- `400 Bad Request` - User not found

---

### 4️⃣ Get Watchlist Count
**Endpoint:** `GET /api/watchlist/count`

**Success Response (200):**
```json
{
    "count": 5
}
```

---

### 5️⃣ Check if Stock in Watchlist
**Endpoint:** `GET /api/watchlist/check/{ticker}`

**Success Response (200):**
```json
{
    "ticker": "AAPL",
    "inWatchlist": true
}
```

---

## 🎨 Frontend Implementation

### JavaScript Functions (app.js)

Currently, your frontend **doesn't have UI for adding stocks to watchlist**, but the backend is fully functional. Here's what needs to be added:

#### 1. Add Function to Script
```javascript
async function addToWatchlist(ticker) {
    try {
        const res = await fetch('/api/watchlist/add', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ ticker })
        });

        if (res.ok) {
            showToast('success', 'Added to Watchlist', `${ticker} added successfully`);
            // Optional: Refresh watchlist display
            fetchWatchlist();
        } else {
            const err = await res.text();
            showAlert('watchlistAlert', err || 'Failed to add to watchlist');
        }
    } catch (e) {
        showAlert('watchlistAlert', 'Server error: ' + e.message);
    }
}
```

#### 2. Remove Function
```javascript
async function removeFromWatchlist(ticker) {
    try {
        const res = await fetch(`/api/watchlist/remove/${ticker}`, {
            method: 'DELETE'
        });

        if (res.ok) {
            showToast('success', 'Removed', `${ticker} removed from watchlist`);
            fetchWatchlist();
        } else {
            const err = await res.text();
            showAlert('watchlistAlert', err);
        }
    } catch (e) {
        showAlert('watchlistAlert', 'Server error: ' + e.message);
    }
}
```

#### 3. Fetch Watchlist
```javascript
async function fetchWatchlist() {
    try {
        const res = await fetch('/api/watchlist');
        if (res.ok) {
            const items = await res.json();
            renderWatchlist(items);
        }
    } catch (e) {
        console.error('Fetch watchlist error:', e);
    }
}
```

#### 4. Check if in Watchlist (before rendering button)
```javascript
async function isInWatchlist(ticker) {
    try {
        const res = await fetch(`/api/watchlist/check/${ticker}`);
        if (res.ok) {
            const data = await res.json();
            return data.inWatchlist;
        }
    } catch (e) {
        console.error('Check watchlist error:', e);
    }
    return false;
}
```

---

## 🔄 Flow Diagram

```
┌─────────────────────────────────────────────┐
│          User Authenticated                  │
│       (Session contains userId)              │
└────────────────────┬────────────────────────┘
                     │
                     ▼
         ┌────────────────────────┐
         │  User is in Trade View  │
         │  Sees stock: AAPL       │
         └────────────┬───────────┘
                      │
            ┌─────────┴──────────┐
            ▼                    ▼
    ┌──────────────┐    ┌──────────────────┐
    │ Add to Watch │    │ Remove from      │
    │ POST /add    │    │ Watch DELETE /rm │
    └──────┬───────┘    └────────┬─────────┘
           │                     │
    ┌──────▼─────────────────────▼─────┐
    │ WatchlistController              │
    │ - Validate userId from session   │
    │ - Validate ticker (required)     │
    │ - Pass to service                │
    └──────┬──────────────────────────┘
           │
    ┌──────▼──────────────────────┐
    │ WatchlistService            │
    │ - Fetch User from DB        │
    │ - Validate Stock exists     │
    │ - Check for duplicates      │
    │ - Save/Delete entry         │
    └──────┬──────────────────────┘
           │
    ┌──────▼──────────────────────┐
    │ WatchlistRepository          │
    │ (JPA Spring Data)            │
    └──────┬──────────────────────┘
           │
    ┌──────▼──────────────────────┐
    │ PostgreSQL Database          │
    │ Update watchlist table       │
    └──────────────────────────────┘
```

---

## 💻 Step-by-Step: How to Use the Feature

### Step 1: User Authentication
- User logs in via `/api/auth/login`
- Session is created with `userId`

### Step 2: View Stocks
- User navigates to Trade view
- Stocks list is displayed from `/api/market/stocks`

### Step 3: Add to Watchlist (Backend-Ready)
- Call: `POST /api/watchlist/add` with `{"ticker": "AAPL"}`
- Backend validates:
  - ✅ User exists
  - ✅ Stock exists
  - ✅ Stock not already in watchlist
- Database stores entry with timestamp

### Step 4: View Watchlist
- Call: `GET /api/watchlist`
- Returns list with live stock data:
  - Current price
  - Daily change %
  - When added

### Step 5: Remove from Watchlist
- Call: `DELETE /api/watchlist/remove/AAPL`
- Entry deleted from database

---

## 🛡️ Error Handling

### Validation Rules

| Scenario | Error | HTTP Status |
|----------|-------|------------|
| Not authenticated | "Not authenticated" | 401 |
| Missing ticker | "Stock ticker is required" | 400 |
| Stock doesn't exist | "Stock not found: XXX" | 400 |
| Already in watchlist | "Stock already in watchlist" | 409 |
| User not found | "User not found" | 400 |
| Stock not in watchlist (on delete) | "Stock not in watchlist" | 400 |

---

## 📝 Key Implementation Details

### 1. **Unique Constraint**
```sql
UNIQUE(user_id, ticker)
```
- Prevents duplicate entries for the same user
- Enforced at database level (strongest guarantee)
- Also checked in service layer before insert

### 2. **Session Management**
```java
private Long getUserIdFromSession(HttpSession session) {
    return (Long) session.getAttribute("userId");
}
```
- User ID retrieved from HTTP session
- Required for all watchlist operations
- Returns null if not authenticated (checked before proceeding)

### 3. **Case Normalization**
```java
String normalizedTicker = ticker.toUpperCase();
```
- All tickers stored and searched in UPPERCASE
- Ensures consistency (AAPL, aapl, Aapl all stored as AAPL)

### 4. **Data Enrichment**
When retrieving watchlist, the service:
- Fetches live stock data from `stocks` table
- Calculates daily change percentage
- Returns enriched DTO with all relevant info

### 5. **Transactional Safety**
```java
@Transactional
public Watchlist addToWatchlist(Long userId, String ticker) { ... }
```
- Ensures ACID properties
- Automatic rollback on error

---

## 🚀 Next Steps to Complete Frontend

To fully enable watchlist functionality in your UI, you need to:

### 1. **Add Watchlist Button to Stock Rows**
In trade view, add a button to each stock row:
```html
<button id="watchlistBtn-${stock.ticker}" 
        onclick="addToWatchlist('${stock.ticker}')"
        class="btn btn-icon">
    <i class="bi bi-bookmark-plus"></i>
</button>
```

### 2. **Dynamic Button State**
Check if stock is in watchlist and update button visually

### 3. **Add Watchlist View**
Create a new view/tab to display all watchlist items with:
- Stock name, ticker, price
- Daily change %
- Option to remove
- Option to quickly buy/sell

### 4. **Update loadAllData() function**
Add watchlist fetch to refresh cycle

---

## 📊 Database Query Examples

### View all watchlist entries for a user
```sql
SELECT w.*, s.name, s.current_price, s.last_price
FROM watchlist w
JOIN stocks s ON w.ticker = s.ticker
WHERE w.user_id = 1
ORDER BY w.added_at DESC;
```

### Count watchlist items per user
```sql
SELECT user_id, COUNT(*) as watchlist_count
FROM watchlist
GROUP BY user_id;
```

### Find most popular stocks in watchlists
```sql
SELECT ticker, COUNT(*) as count
FROM watchlist
GROUP BY ticker
ORDER BY count DESC
LIMIT 10;
```

---

## ✅ Summary

| Component | Status | Details |
|-----------|--------|---------|
| **Database** | ✅ Ready | PostgreSQL table with constraints |
| **Model** | ✅ Ready | Watchlist.java entity |
| **Repository** | ✅ Ready | All CRUD methods |
| **Service** | ✅ Ready | Business logic + validation |
| **Controller** | ✅ Ready | 5 REST endpoints |
| **Frontend** | 🔄 Partial | API functions needed + UI |

Your backend is **100% complete and production-ready**. The frontend just needs button wiring!


