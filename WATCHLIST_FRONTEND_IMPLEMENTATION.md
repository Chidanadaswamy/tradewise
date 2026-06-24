# Watchlist Feature - Frontend UI Implementation

## Ready-to-Use HTML & CSS Components

---

## 1️⃣ Add Watchlist Button to Each Stock Row

### Current Code (in `renderStocksList()`)
```javascript
container.innerHTML = stocks.map(stock => {
    const diff       = stock.currentPrice - stock.lastPrice;
    const pct        = (diff / stock.lastPrice) * 100;
    const isSelected = stock.ticker === selectedTicker;

    return `
    <div class="stock-row ${isSelected ? 'selected' : ''}" onclick="selectStock('${stock.ticker}')">
        <div class="stock-row-left">
            <div class="stock-ticker-badge">${stock.ticker}</div>
            <div class="stock-name">${stock.name}</div>
        </div>
        <div class="stock-row-right">
            <div class="stock-price ticker-price-${stock.ticker}">$${stock.currentPrice.toFixed(2)}</div>
            <div class="stock-change ${pct >= 0 ? 'pos' : 'neg'}">${pct >= 0 ? '+' : ''}${pct.toFixed(2)}%</div>
        </div>
    </div>`;
}).join('');
```

### Enhanced Code WITH Watchlist Button
```javascript
async function renderStocksList() {
    const container = document.getElementById('stocksListContainer');

    if (!stocks.length) {
        container.innerHTML = `<div class="empty-state"><i class="bi bi-wifi-off"></i><p>Could not load market data.</p></div>`;
        return;
    }

    // Build HTML with watchlist info
    let html = '';
    for (const stock of stocks) {
        const diff       = stock.currentPrice - stock.lastPrice;
        const pct        = (diff / stock.lastPrice) * 100;
        const isSelected = stock.ticker === selectedTicker;
        
        // Check if in watchlist (without awaiting in loop - will update on refresh)
        const inWatchlist = lastKnownWatchlist?.some(w => w.ticker === stock.ticker) ?? false;

        html += `
        <div class="stock-row ${isSelected ? 'selected' : ''}" onclick="selectStock('${stock.ticker}')">
            <div class="stock-row-left">
                <div class="stock-ticker-badge">${stock.ticker}</div>
                <div class="stock-name">${stock.name}</div>
            </div>
            <div class="stock-row-middle">
                <div class="stock-price ticker-price-${stock.ticker}">$${stock.currentPrice.toFixed(2)}</div>
                <div class="stock-change ${pct >= 0 ? 'pos' : 'neg'}">${pct >= 0 ? '+' : ''}${pct.toFixed(2)}%</div>
            </div>
            <div class="stock-row-right">
                <button class="btn-watchlist ${inWatchlist ? 'active' : ''}"
                        id="wl-btn-${stock.ticker}"
                        onclick="event.stopPropagation(); toggleWatchlist('${stock.ticker}')"
                        title="${inWatchlist ? 'Remove from watchlist' : 'Add to watchlist'}">
                    <i class="bi ${inWatchlist ? 'bi-bookmark-fill' : 'bi-bookmark'}"></i>
                </button>
            </div>
        </div>`;
    }

    container.innerHTML = html;
}

// Track watchlist globally for quick reference
let lastKnownWatchlist = [];

// Add this to your loadAllData() function
async function loadAllData() {
    await Promise.all([
        fetchStocks(),
        fetchPortfolio(),
        fetchJournalAnalysis(),
        fetchWatchlist()  // NEW
    ]);
    updateHeaderSummary();
}

// Download watchlist data
async function fetchWatchlist() {
    try {
        const res = await fetch('/api/watchlist');
        if (res.ok) {
            lastKnownWatchlist = await res.json();
            renderWatchlistDisplay(); // If you have a watchlist view
        }
    } catch (e) {
        console.error('Error fetching watchlist:', e);
    }
}
```

### CSS for Watchlist Button
```css
/* Add to your styles.css */

.btn-watchlist {
    background: none;
    border: none;
    padding: 0.5rem;
    cursor: pointer;
    color: var(--text-secondary);
    font-size: 1.2rem;
    transition: all 0.2s ease;
    display: flex;
    align-items: center;
    justify-content: center;
    border-radius: 4px;
}

.btn-watchlist:hover {
    background: rgba(255, 255, 255, 0.05);
    color: var(--blue);
}

.btn-watchlist.active {
    color: var(--blue);
    background: rgba(59, 130, 246, 0.1);
}

.btn-watchlist i {
    font-size: 1.3rem;
}
```

---

## 2️⃣ JavaScript Functions to Add

### Add these to `app.js`

```javascript
// ── REST: Watchlist ────────────────────────────────────────────────

/**
 * Toggle watchlist status for a stock
 */
async function toggleWatchlist(ticker) {
    try {
        // Optimistically update UI first
        const btn = document.getElementById(`wl-btn-${ticker}`);
        const wasActive = btn?.classList.contains('active');
        
        if (wasActive) {
            // Remove from watchlist
            const res = await fetch(`/api/watchlist/remove/${ticker}`, {
                method: 'DELETE'
            });
            
            if (res.ok) {
                showToast('success', 'Removed', `${ticker} removed from watchlist`);
                // Update UI
                btn?.classList.remove('active');
                btn?.innerHTML = '<i class="bi bi-bookmark"></i>';
                btn?.title = 'Add to watchlist';
                lastKnownWatchlist = lastKnownWatchlist.filter(w => w.ticker !== ticker);
            } else {
                const err = await res.text();
                showAlert('orderFormAlert', err || 'Failed to remove from watchlist');
                // Revert optimistic update
                btn?.classList.add('active');
                btn?.innerHTML = '<i class="bi bi-bookmark-fill"></i>';
            }
        } else {
            // Add to watchlist
            const res = await fetch('/api/watchlist/add', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ ticker })
            });
            
            if (res.ok) {
                showToast('success', 'Added', `${ticker} added to watchlist`);
                // Update UI
                btn?.classList.add('active');
                btn?.innerHTML = '<i class="bi bi-bookmark-fill"></i>';
                btn?.title = 'Remove from watchlist';
                // Fetch latest watchlist data
                await fetchWatchlist();
            } else {
                const err = await res.text();
                showAlert('orderFormAlert', err || 'Failed to add to watchlist');
                // Revert optimistic update
                btn?.classList.remove('active');
                btn?.innerHTML = '<i class="bi bi-bookmark"></i>';
            }
        }
    } catch (e) {
        console.error('Watchlist toggle error:', e);
        showAlert('orderFormAlert', 'Connection error. Please try again.');
    }
}

/**
 * Fetch current user's watchlist
 */
async function fetchWatchlist() {
    try {
        const res = await fetch('/api/watchlist');
        if (res.ok) {
            lastKnownWatchlist = await res.json();
            renderWatchlistDisplay();
            updateWatchlistCount();
        }
    } catch (e) {
        console.error('Error fetching watchlist:', e);
    }
}

/**
 * Update watchlist count display
 */
function updateWatchlistCount() {
    const countEl = document.getElementById('watchlistCount');
    if (countEl) {
        countEl.textContent = lastKnownWatchlist.length;
    }
}

/**
 * Check if a stock is in watchlist
 */
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

/**
 * Render watchlist view
 */
function renderWatchlistDisplay() {
    const container = document.getElementById('watchlistContainer');
    
    if (!container) return; // Element doesn't exist yet
    
    if (lastKnownWatchlist.length === 0) {
        container.innerHTML = `
            <div class="empty-state">
                <i class="bi bi-bookmark"></i>
                <p>Your watchlist is empty</p>
                <p style="color: var(--text-muted); font-size: 0.9rem;">
                    Browse stocks in the Trade tab and bookmark your favorites
                </p>
                <button class="btn btn-primary btn-sm" onclick="switchView('trade')">
                    <i class="bi bi-graph-up-arrow"></i> Browse Stocks
                </button>
            </div>`;
        return;
    }
    
    container.innerHTML = lastKnownWatchlist.map(item => {
        const changeClass = item.dailyChangePercent >= 0 ? 'positive' : 'negative';
        const changeIcon = item.dailyChangePercent >= 0 ? '📈' : '📉';
        const addedDate = new Date(item.addedAt).toLocaleDateString();
        
        return `
        <div class="watchlist-card">
            <div class="watchlist-header">
                <div class="watchlist-ticker">${item.ticker}</div>
                <button class="btn btn-icon btn-remove" 
                        onclick="removeFromWatchlist('${item.ticker}')"
                        title="Remove from watchlist">
                    <i class="bi bi-x-lg"></i>
                </button>
            </div>
            
            <div class="watchlist-company">
                ${item.stockName}
            </div>
            
            <div class="watchlist-body">
                <div class="watchlist-price">
                    <div class="label">Current Price</div>
                    <div class="price">$${item.currentPrice.toFixed(2)}</div>
                </div>
                
                <div class="watchlist-change ${changeClass}">
                    <div class="label">Daily Change</div>
                    <div class="change">
                        <span class="icon">${changeIcon}</span>
                        <span>${item.dailyChangePercent >= 0 ? '+' : ''}${item.dailyChangePercent.toFixed(2)}%</span>
                    </div>
                </div>
                
                <div class="watchlist-added">
                    <div class="label">Added</div>
                    <div class="date">${addedDate}</div>
                </div>
            </div>
            
            <div class="watchlist-actions">
                <button class="btn btn-secondary btn-sm" 
                        onclick="switchView('trade'); selectStock('${item.ticker}')">
                    <i class="bi bi-graph-up"></i> Trade
                </button>
            </div>
        </div>`;
    }).join('');
}

/**
 * Remove specific stock from watchlist
 */
async function removeFromWatchlist(ticker) {
    if (!confirm(`Remove ${ticker} from your watchlist?`)) return;
    
    try {
        const res = await fetch(`/api/watchlist/remove/${ticker}`, {
            method: 'DELETE'
        });
        
        if (res.ok) {
            showToast('success', 'Removed', `${ticker} removed from watchlist`);
            lastKnownWatchlist = lastKnownWatchlist.filter(w => w.ticker !== ticker);
            renderWatchlistDisplay();
            updateWatchlistCount();
            
            // Update stock row button if visible
            const btn = document.getElementById(`wl-btn-${ticker}`);
            if (btn) {
                btn.classList.remove('active');
                btn.innerHTML = '<i class="bi bi-bookmark"></i>';
                btn.title = 'Add to watchlist';
            }
        } else {
            const err = await res.text();
            showAlert('watchlistAlert', err || 'Failed to remove from watchlist');
        }
    } catch (e) {
        console.error('Error removing from watchlist:', e);
        showAlert('watchlistAlert', 'Connection error. Please try again.');
    }
}
```

---

## 3️⃣ Add Watchlist Tab/View to HTML

### Add this to your `index.html` navigation

```html
<!-- In the main menu/navigation section -->
<div class="menu-items">
    <button id="menu-dashboard" class="menu-item active" onclick="switchView('dashboard')">
        <i class="bi bi-speedometer2"></i>
        <span>Dashboard</span>
    </button>
    <button id="menu-trade" class="menu-item" onclick="switchView('trade')">
        <i class="bi bi-graph-up-arrow"></i>
        <span>Trade</span>
    </button>
    <!-- NEW WATCHLIST MENU -->
    <button id="menu-watchlist" class="menu-item" onclick="switchView('watchlist')">
        <i class="bi bi-bookmark"></i>
        <span>Watchlist</span>
        <span id="watchlistCount" class="badge" style="display: none;"></span>
    </button>
    <button id="menu-journal" class="menu-item" onclick="switchView('journal')">
        <i class="bi bi-journal"></i>
        <span>Journal</span>
    </button>
</div>
```

### Add this view container to HTML

```html
<!-- NEW WATCHLIST VIEW -->
<div id="view-watchlist" class="view d-none" style="padding: 1.5rem;">
    <div id="watchlistContainer" class="watchlist-grid">
        <!-- Will be populated by renderWatchlistDisplay() -->
    </div>
</div>
```

### Update switchView() function

```javascript
function switchView(view) {
    activeView = view;

    // Hide all views
    ['dashboard', 'trade', 'watchlist', 'journal'].forEach(v => {  // ADD 'watchlist'
        document.getElementById(`view-${v}`)?.classList.add('d-none');
        document.getElementById(`menu-${v}`)?.classList.remove('active');
    });

    // Show target view
    document.getElementById(`view-${view}`)?.classList.remove('d-none');
    document.getElementById(`menu-${view}`)?.classList.add('active');

    // Update header title
    const titles = {
        dashboard: 'Dashboard',
        trade: 'Trade Stocks',
        watchlist: 'My Watchlist',  // NEW
        journal: 'Behavioral Journal'
    };
    
    const subtitles = {
        dashboard: `Hello, <span id="currentUserDisplay" style="color:var(--blue);font-weight:600;">${currentUser?.username || 'Investor'}</span> — your journey continues`,
        trade: 'Browse stocks and place orders',
        watchlist: 'Monitor stocks you are interested in',  // NEW
        journal: 'Review your trading behaviour and improve'
    };

    document.getElementById('viewTitle').textContent = titles[view];
    document.getElementById('viewSubtitle').innerHTML = subtitles[view];

    if (view === 'trade') {
        selectStock(selectedTicker);
    } else if (view === 'watchlist') {
        renderWatchlistDisplay();
    }

    loadAllData();
}
```

---

## 4️⃣ CSS Styles for Watchlist

### Add this to `styles.css`

```css
/* ── Watchlist Components ──────────────────────────────────── */

.watchlist-grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
    gap: 1.5rem;
    padding: 0.5rem;
}

.watchlist-card {
    background: linear-gradient(135deg, rgba(255,255,255,0.03) 0%, rgba(255,255,255,0.01) 100%);
    border: 1px solid rgba(255,255,255,0.08);
    border-radius: 12px;
    padding: 1.25rem;
    transition: all 0.3s ease;
    display: flex;
    flex-direction: column;
    gap: 1rem;
}

.watchlist-card:hover {
    border-color: rgba(59, 130, 246, 0.3);
    background: linear-gradient(135deg, rgba(59, 130, 246, 0.05) 0%, rgba(59, 130, 246, 0.02) 100%);
    transform: translateY(-4px);
    box-shadow: 0 12px 24px rgba(0, 0, 0, 0.15);
}

.watchlist-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
}

.watchlist-ticker {
    font-size: 1.3rem;
    font-weight: 700;
    color: var(--blue);
    letter-spacing: 0.5px;
}

.btn-remove {
    background: none;
    border: none;
    color: var(--text-muted);
    cursor: pointer;
    padding: 0.25rem;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 1rem;
    transition: all 0.2s;
}

.btn-remove:hover {
    color: var(--red);
    background: rgba(255, 77, 109, 0.1);
    border-radius: 4px;
}

.watchlist-company {
    font-size: 0.9rem;
    color: var(--text-secondary);
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
}

.watchlist-body {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 1rem;
    padding: 1rem 0;
    border-top: 1px solid rgba(255,255,255,0.05);
    border-bottom: 1px solid rgba(255,255,255,0.05);
}

.watchlist-body > div {
    display: flex;
    flex-direction: column;
    gap: 0.3rem;
}

.watchlist-body .label {
    font-size: 0.75rem;
    color: var(--text-muted);
    text-transform: uppercase;
    letter-spacing: 0.5px;
    font-weight: 600;
}

.watchlist-price .price {
    font-size: 1.5rem;
    font-weight: 700;
    color: var(--text-primary);
}

.watchlist-change {
    padding: 0.5rem;
    border-radius: 6px;
    background: rgba(255,255,255,0.02);
}

.watchlist-change.positive {
    background: rgba(0, 212, 170, 0.1);
}

.watchlist-change.positive .change {
    color: var(--green);
}

.watchlist-change.negative {
    background: rgba(255, 77, 109, 0.1);
}

.watchlist-change.negative .change {
    color: var(--red);
}

.watchlist-change .change {
    font-size: 1.1rem;
    font-weight: 600;
    display: flex;
    align-items: center;
    gap: 0.5rem;
}

.watchlist-change .icon {
    font-size: 1.2rem;
}

.watchlist-added .date {
    font-size: 0.9rem;
    color: var(--text-secondary);
}

.watchlist-actions {
    display: flex;
    gap: 0.5rem;
    margin-top: auto;
}

.watchlist-actions .btn {
    flex: 1;
}

/* Responsive */
@media (max-width: 768px) {
    .watchlist-grid {
        grid-template-columns: 1fr;
    }
    
    .watchlist-body {
        grid-template-columns: 1fr;
    }
}

/* Updated stock row to accommodate watchlist button */
.stock-row {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 1rem;
    border: 1px solid rgba(255,255,255,0.05);
    border-radius: 8px;
    cursor: pointer;
    transition: all 0.2s;
    margin-bottom: 0.5rem;
}

.stock-row:hover {
    background: rgba(255,255,255,0.02);
    border-color: rgba(59, 130, 246, 0.2);
}

.stock-row-left {
    flex: 1;
    display: flex;
    align-items: center;
    gap: 1rem;
}

.stock-row-middle {
    display: flex;
    align-items: center;
    gap: 1rem;
    margin: 0 1rem;
}

.stock-row-right {
    display: flex;
    align-items: center;
    justify-content: flex-end;
}
```

---

## 5️⃣ Update `loadAllData()` Function

```javascript
// ── Data Orchestration ────────────────────────────────────────
async function loadAllData() {
    await Promise.all([
        fetchStocks(),
        fetchPortfolio(),
        fetchJournalAnalysis(),
        fetchWatchlist()  // ADD THIS
    ]);
    updateHeaderSummary();
}

// ── Update Polling ────────────────────────────────────────
async function pollMarketAndPortfolio() {
    // Snapshot old prices for flash animation
    const oldPrices = {};
    stocks.forEach(s => (oldPrices[s.ticker] = s.currentPrice));

    await Promise.all([
        fetchStocks(),
        fetchPortfolio(),
        fetchJournalAnalysis(),
        fetchWatchlist()  // ADD THIS
    ]);
    updateHeaderSummary();

    // ... rest of polling code ...
}
```

---

## 6️⃣ Integration Checklist

- [ ] Add watchlist functions to `app.js`
- [ ] Add watchlist button to stock rows
- [ ] Add CSS for watchlist components
- [ ] Add watchlist view to HTML
- [ ] Update `switchView()` function
- [ ] Update `pollMarketAndPortfolio()` to include `fetchWatchlist()`
- [ ] Test: Add stock to watchlist
- [ ] Test: View watchlist
- [ ] Test: Remove from watchlist
- [ ] Test: Button state changes
- [ ] Test: Real-time price updates

---

## 7️⃣ Testing the Integration

### Manual Testing Steps

1. **Login to the application**
   ```
   - Go to http://localhost:8080
   - Username: testuser
   - Password: password123
   ```

2. **Add to Watchlist**
   ```
   - Navigate to Trade tab
   - Click bookmark icon on any stock
   - Should see success toast
   ```

3. **View Watchlist**
   ```
   - Click Watchlist tab
   - Should see card for added stock
   - Should show live price and daily %
   ```

4. **Remove from Watchlist**
   ```
   - On watchlist card, click X button
   - Or in Trade view, click bookmark again
   - Stock should disappear
   ```

5. **Persistence Check**
   ```
   - Add stock to watchlist
   - Refresh page (Ctrl+R)
   - Stock should still be there
   - Logout and login again
   - Watchlist should persist
   ```

---

## 🎯 Summary

You now have:

1. ✅ Complete backend implementation (already done)
2. ✅ JavaScript functions to call API
3. ✅ HTML structure for watchlist view
4. ✅ CSS styling for components
5. ✅ Integration points in your app
6. ✅ Testing checklist

**Next Step:** Copy the JavaScript functions and CSS to your files and test!


