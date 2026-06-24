# Watchlist Feature - Quick Reference & Code Examples

## 🎯 Quick Summary

Your application has a **complete, functional watchlist system**. Here's what each layer does:

```
User adds AAPL to watchlist
        ↓
Frontend: POST /api/watchlist/add {"ticker": "AAPL"}
        ↓
Controller: Validates user session, extracts userId
        ↓
Service: Validates User exists, Stock exists, No duplicates
        ↓
Repository: Executes SQL INSERT into watchlist table
        ↓
Database: Stores (user_id, ticker, added_at)
```

---

## 📋 How to Add Stocks to Watchlist - Complete Flow

### **From Frontend (JavaScript)**

#### Option 1: Simple Button Click
```javascript
// User clicks "Add to Watchlist" button
async function handleAddToWatchlist(ticker) {
    const response = await fetch('/api/watchlist/add', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ ticker: ticker })
    });
    
    if (response.ok) {
        const data = await response.json();
        console.log('Success:', data.message); // "Added to watchlist"
        console.log('Ticker:', data.ticker);   // "AAPL"
        showToast('success', 'Added!', `${ticker} added to watchlist`);
    } else {
        const error = await response.text();
        console.error('Error:', error);
        // Possible errors:
        // - "Not authenticated" (401)
        // - "Stock ticker is required" (400)
        // - "Stock not found: XXX" (400)
        // - "Stock already in watchlist" (409)
    }
}

// Usage: <button onclick="handleAddToWatchlist('AAPL')">Add</button>
```

#### Option 2: With Toggle (Add or Remove)
```javascript
async function toggleWatchlist(ticker) {
    try {
        // Check if already in watchlist
        const checkRes = await fetch(`/api/watchlist/check/${ticker}`);
        const checkData = await checkRes.json();
        
        if (checkData.inWatchlist) {
            // Remove it
            const removeRes = await fetch(`/api/watchlist/remove/${ticker}`, {
                method: 'DELETE'
            });
            if (removeRes.ok) {
                showToast('success', 'Removed', `${ticker} removed from watchlist`);
                updateWatchlistButton(ticker, false);
            }
        } else {
            // Add it
            const addRes = await fetch('/api/watchlist/add', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ ticker })
            });
            if (addRes.ok) {
                showToast('success', 'Added', `${ticker} added to watchlist`);
                updateWatchlistButton(ticker, true);
            }
        }
    } catch (error) {
        console.error('Error:', error);
    }
}

function updateWatchlistButton(ticker, isInWatchlist) {
    const btn = document.getElementById(`watchlist-btn-${ticker}`);
    if (isInWatchlist) {
        btn.classList.add('active');
        btn.innerHTML = '<i class="bi bi-bookmark-fill"></i>';
    } else {
        btn.classList.remove('active');
        btn.innerHTML = '<i class="bi bi-bookmark"></i>';
    }
}
```

---

### **From Backend (Java)**

#### Service Layer - Core Logic
```java
// src/main/java/com/seedling/platform/service/WatchlistService.java

@Transactional
public Watchlist addToWatchlist(Long userId, String ticker) {
    // Step 1: Get user from database
    User user = userRepository.findById(userId)
        .orElseThrow(() -> new IllegalArgumentException("User not found"));

    // Step 2: Validate stock exists
    stockRepository.findById(ticker.toUpperCase())
        .orElseThrow(() -> new IllegalArgumentException("Stock not found: " + ticker));

    // Step 3: Normalize ticker to uppercase
    String normalizedTicker = ticker.toUpperCase();

    // Step 4: Check if already in watchlist
    if (watchlistRepository.existsByUserAndTicker(user, normalizedTicker)) {
        throw new IllegalStateException("Stock already in watchlist");
    }

    // Step 5: Create watchlist entry
    Watchlist entry = new Watchlist(user, normalizedTicker);
    // addedAt is auto-set by @PrePersist annotation
    
    // Step 6: Save to database
    return watchlistRepository.save(entry);
    // Returns: Watchlist object with generated ID
}
```

#### Controller Layer - HTTP Endpoint
```java
// src/main/java/com/seedling/platform/controller/WatchlistController.java

@PostMapping("/add")
public ResponseEntity<?> addToWatchlist(
    @RequestBody WatchlistRequest request,
    HttpSession session
) {
    // Step 1: Get userId from session
    Long userId = (Long) session.getAttribute("userId");
    if (userId == null) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body("Not authenticated");
    }

    // Step 2: Validate request
    if (request.ticker == null || request.ticker.trim().isEmpty()) {
        return ResponseEntity.badRequest()
            .body("Stock ticker is required");
    }

    try {
        // Step 3: Call service
        watchlistService.addToWatchlist(userId, request.ticker.trim());
        
        // Step 4: Return success response
        return ResponseEntity.ok(Map.of(
            "message", "Added to watchlist",
            "ticker", request.ticker.trim().toUpperCase()
        ));
    } catch (IllegalStateException e) {
        // Stock already in watchlist
        return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
    } catch (IllegalArgumentException e) {
        // User not found, stock not found, etc.
        return ResponseEntity.badRequest().body(e.getMessage());
    }
}
```

---

## 🔍 How to Retrieve & Display Watchlist

### **Frontend: Fetch and Display**

```javascript
// Load watchlist data
async function loadWatchlist() {
    try {
        const response = await fetch('/api/watchlist');
        
        if (response.ok) {
            const watchlist = await response.json();
            // watchlist is an array of WatchlistItemDTO
            displayWatchlist(watchlist);
        }
    } catch (error) {
        console.error('Error loading watchlist:', error);
    }
}

// Display watchlist items
function displayWatchlist(items) {
    const container = document.getElementById('watchlistContainer');
    
    if (items.length === 0) {
        container.innerHTML = `
            <div class="empty-state">
                <i class="bi bi-bookmark"></i>
                <p>Your watchlist is empty. Add stocks to monitor them.</p>
            </div>
        `;
        return;
    }
    
    container.innerHTML = items.map(item => `
        <div class="watchlist-item">
            <div class="watchlist-left">
                <div class="ticker-badge">${item.ticker}</div>
                <div>
                    <div class="stock-name">${item.stockName}</div>
                    <div class="added-date">Added ${formatDate(item.addedAt)}</div>
                </div>
            </div>
            <div class="watchlist-right">
                <div class="current-price">$${item.currentPrice.toFixed(2)}</div>
                <div class="daily-change ${item.dailyChangePercent >= 0 ? 'positive' : 'negative'}">
                    ${item.dailyChangePercent >= 0 ? '+' : ''}${item.dailyChangePercent.toFixed(2)}%
                </div>
                <button onclick="removeFromWatchlist('${item.ticker}')" 
                        class="btn btn-icon btn-remove">
                    <i class="bi bi-x"></i>
                </button>
            </div>
        </div>
    `).join('');
}

function formatDate(dateString) {
    const date = new Date(dateString);
    const today = new Date();
    const yesterday = new Date(today);
    yesterday.setDate(yesterday.getDate() - 1);
    
    if (date.toDateString() === today.toDateString()) return 'today';
    if (date.toDateString() === yesterday.toDateString()) return 'yesterday';
    return date.toLocaleDateString();
}

// Remove from watchlist
async function removeFromWatchlist(ticker) {
    if (!confirm(`Remove ${ticker} from watchlist?`)) return;
    
    try {
        const response = await fetch(`/api/watchlist/remove/${ticker}`, {
            method: 'DELETE'
        });
        
        if (response.ok) {
            showToast('success', 'Removed', `${ticker} removed from watchlist`);
            loadWatchlist(); // Refresh display
        }
    } catch (error) {
        console.error('Error removing:', error);
    }
}
```

### **Backend: Data Returned**

```java
// Service returns list of enriched DTOs

// Each item contains:
{
    "ticker": "AAPL",
    "stockName": "Apple Inc.",
    "currentPrice": 195.50,
    "dailyChangePercent": 2.45,
    "addedAt": "2026-06-18T14:30:15"
}
```

---

## ✨ Real-World Usage Scenarios

### Scenario 1: User Adding Stock from Trade View
```
1. User viewing trade screen sees "TSLA"
2. Clicks "Add to Watchlist" button
3. Frontend: POST /api/watchlist/add {"ticker": "TSLA"}
4. Backend validates and saves
5. Button changes appearance (e.g., fills in bookmark icon)
6. Toast notification: "TSLA added to watchlist"
```

### Scenario 2: Checking Multiple Stocks
```
// Before rendering buttons, check
const inWatchlist = await isInWatchlist('AAPL');
const btnClass = inWatchlist ? 'bookmarked' : 'not-bookmarked';
```

### Scenario 3: Auto-refresh Watchlist
```javascript
// Add to your polling interval
async function pollMarketAndPortfolio() {
    await Promise.all([
        fetchStocks(),
        fetchPortfolio(),
        fetchJournalAnalysis(),
        loadWatchlist()  // Add this
    ]);
    updateHeaderSummary();
}
```

---

## 🔐 Security Considerations

### 1. **Session-Based Auth**
- User ID extracted from session object
- Session dies on logout
- No user can access another user's watchlist

### 2. **Unique Constraint**
- Database prevents duplicate (user, ticker) pairs
- Even if bypassed in code, database prevents insert

### 3. **Input Validation**
- Ticker must be non-empty string
- Stock must exist in stocks table
- User must exist in users table

### 4. **Transactional Integrity**
- All operations are `@Transactional`
- Automatic rollback on error
- No partial updates

---

## 📊 Performance Tips

### 1. **Caching Watchlist**
```javascript
let cachedWatchlist = null;

async function getWatchlist(useCache = true) {
    if (useCache && cachedWatchlist) {
        return cachedWatchlist;
    }
    const response = await fetch('/api/watchlist');
    cachedWatchlist = await response.json();
    return cachedWatchlist;
}

// Invalidate cache after add/remove
async function addToWatchlist(ticker) {
    // ... add code ...
    cachedWatchlist = null; // Clear cache
    loadWatchlist(); // Refresh
}
```

### 2. **Eager Loading in Service**
Already implemented!
```java
@ManyToOne(fetch = FetchType.EAGER)  // Loads User immediately
@JoinColumn(name = "user_id", nullable = false)
private User user;
```

### 3. **Ordered Queries**
```java
findByUserOrderByAddedAtDesc(user);  // Newest first
```

---

## 🐛 Common Issues & Solutions

### Issue 1: "Stock not found: ABC"
**Cause**: Stock doesn't exist in stocks table
**Solution**: Verify stock is initialized in database

### Issue 2: "Stock already in watchlist"
**Cause**: Trying to add duplicate
**Solution**: Check with `/api/watchlist/check/{ticker}` first

### Issue 3: "Not authenticated"
**Cause**: Session expired or not logged in
**Solution**: Redirect to login, check session

### Issue 4: Empty watchlist returned
**Possible causes**:
1. User just created account (watchlist empty)
2. All stocks were removed
3. Stocks were deleted from database

---

## 🎨 UI Components to Add

### 1. Watchlist Button (for each stock)
```html
<button id="watchlist-btn-AAPL" 
        onclick="toggleWatchlist('AAPL')"
        class="btn btn-icon"
        title="Add to watchlist">
    <i class="bi bi-bookmark"></i>
</button>
```

### 2. Watchlist Count Badge
```html
<span class="badge badge-primary" id="watchlistCount">0</span>
```

### 3. Watchlist View Tab
```html
<button onclick="switchView('watchlist')" id="menu-watchlist">
    <i class="bi bi-bookmark"></i> Watchlist
</button>
```

### 4. Quick Add Modal
```html
<div id="quickAddModal" class="modal">
    <div class="modal-content">
        <h3>Add to Watchlist</h3>
        <input type="text" id="quickAddTicker" placeholder="Enter ticker">
        <button onclick="quickAddWatchlist()">Add</button>
    </div>
</div>
```

---

## ✅ Validation Checklist

- [x] Database table exists with unique constraint
- [x] Model entity created
- [x] Repository with all methods ready
- [x] Service with business logic
- [x] Controller with 5 endpoints
- [x] Error handling
- [x] Session validation
- [x] Input validation
- [ ] Frontend buttons wired up
- [ ] Frontend display component
- [ ] Frontend toggle logic
- [ ] Watchlist in polling cycle

---

## 🚀 Implementation Order (for completing frontend)

1. **Add functions to app.js**
   - `addToWatchlist()`
   - `removeFromWatchlist()`
   - `toggleWatchlist()`
   - `loadWatchlist()`

2. **Add buttons to stock rows**
   - In `renderStocksList()` add watchlist button

3. **Add watchlist view**
   - New tab in main navigation
   - Display function for watchlist items

4. **Update polling**
   - Include watchlist in `loadAllData()`

5. **Test end-to-end**
   - Add stock
   - View watchlist
   - Remove stock

---

## 📞 API Quick Reference

| Action | Method | Endpoint | Body |
|--------|--------|----------|------|
| Add | POST | `/api/watchlist/add` | `{"ticker":"AAPL"}` |
| Remove | DELETE | `/api/watchlist/remove/{ticker}` | None |
| Get All | GET | `/api/watchlist` | None |
| Check | GET | `/api/watchlist/check/{ticker}` | None |
| Count | GET | `/api/watchlist/count` | None |

**Base URL:** `http://localhost:8080`

All requests include session cookie automatically.


