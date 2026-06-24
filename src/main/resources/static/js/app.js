// ============================================================
//  TRADEWISE — Frontend Application Controller (Think Different. Trade Smarter.)
//  All original API logic preserved. New UI layer added.
// ============================================================

// ── State ────────────────────────────────────────────────────
let currentUser   = null;
let stocks        = [];
let positions     = [];
let trades        = [];
let selectedTicker = 'AAPL';
let activeView     = 'dashboard';
let chartInstance  = null;
let scoreRingChart = null;
let dashboardScoreChartInstance = null;
let pollInterval   = null;
let activeTimeframe = '1D';
let marketOpen      = false;
let currentPollRate = 5000;
let searchDebounceTimer;

// ── Boot ─────────────────────────────────────────────────────
document.addEventListener('DOMContentLoaded', () => {
    setupEventListeners();
    setupSearchShortcuts();
    checkAuthStatus();
});

// ── Event Wiring ─────────────────────────────────────────────
function setupEventListeners() {
    // Auth forms
    document.getElementById('loginForm').addEventListener('submit', handleLogin);
    document.getElementById('registerForm').addEventListener('submit', handleRegister);
    document.getElementById('logoutBtn').addEventListener('click', handleLogout);

    // Buy quantity → live cost estimate
    document.getElementById('buyQty').addEventListener('input', e => {
        const qty   = parseFloat(e.target.value) || 0;
        const stock = stocks.find(s => s.ticker === selectedTicker);
        document.getElementById('buyEstCost').textContent =
            stock ? `$${(stock.currentPrice * qty).toFixed(2)}` : '$0.00';
    });

    // Sell quantity → live proceeds estimate
    document.getElementById('sellQty').addEventListener('input', e => {
        const qty   = parseFloat(e.target.value) || 0;
        const stock = stocks.find(s => s.ticker === selectedTicker);
        document.getElementById('sellEstProceeds').textContent =
            stock ? `$${(stock.currentPrice * qty).toFixed(2)}` : '$0.00';
    });

    // Order forms
    document.getElementById('buyForm').addEventListener('submit', handleBuyOrder);
    document.getElementById('sellForm').addEventListener('submit', handleSellOrder);

    // Stop-loss modal form
    document.getElementById('stopLossForm').addEventListener('submit', handleStopLossUpdate);

    // Close modal on overlay click
    document.getElementById('stopLossModal').addEventListener('click', e => {
        if (e.target === e.currentTarget) closeStopLossModal();
    });
}

// ── Auth Tab Switch ───────────────────────────────────────────
function switchAuthTab(tab) {
    document.querySelectorAll('.auth-tab-btn').forEach(b => b.classList.remove('active'));
    document.querySelectorAll('.auth-pane').forEach(p => p.classList.remove('active'));
    document.getElementById(`${tab}-tab`).classList.add('active');
    document.getElementById(`${tab}-pane`).classList.add('active');
    hideAlert('authAlert');
}

// ── Order Tab Switch ──────────────────────────────────────────
function switchOrderTab(tab) {
    document.querySelectorAll('.order-tab-btn').forEach(b => b.classList.remove('active'));
    document.querySelectorAll('.order-pane').forEach(p => p.classList.remove('active'));
    document.getElementById(`${tab}-tab`).classList.add('active');
    document.getElementById(`${tab}-pane`).classList.add('active');
    hideAlert('orderFormAlert');
    hideAlert('orderFormSuccess');
}

// ── Stop-Loss Toggle ──────────────────────────────────────────
function toggleStopLoss(checkbox) {
    const container = document.getElementById('stopLossInputContainer');
    const input     = document.getElementById('buyStopLossPrice');
    if (checkbox.checked) {
        container.classList.remove('d-none');
        const stock = stocks.find(s => s.ticker === selectedTicker);
        if (stock) input.value = (stock.currentPrice * 0.90).toFixed(2);
    } else {
        container.classList.add('d-none');
        input.value = '';
    }
}

// ── Ledger Accordion ──────────────────────────────────────────
function toggleLedger() {
    const btn  = document.getElementById('ledgerToggle');
    const body = document.getElementById('ledgerAccordion');
    const isOpen = body.classList.toggle('open');
    btn.classList.toggle('open', isOpen);
}

// ── REST: Auth ────────────────────────────────────────────────
async function checkAuthStatus() {
    try {
        const res = await fetch('/api/auth/status');
        if (res.ok) {
            currentUser = await res.json();
            showApp();
        } else {
            showOnboarding();
        }
    } catch {
        showOnboarding();
    }
}

async function handleLogin(e) {
    e.preventDefault();
    const username = document.getElementById('loginUsername').value.trim();
    const password = document.getElementById('loginPassword').value;
    hideAlert('authAlert');
    setButtonLoading('loginBtn', true);

    try {
        const res = await fetch('/api/auth/login', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ username, password })
        });
        if (res.ok) {
            currentUser = await res.json();
            showApp();
        } else {
            const err = await res.text();
            showAlert('authAlert', err || 'Invalid username or password');
        }
    } catch {
        showAlert('authAlert', 'Unable to reach the server. Please try again.');
    } finally {
        setButtonLoading('loginBtn', false);
    }
}

async function handleRegister(e) {
    e.preventDefault();
    const username = document.getElementById('regUsername').value.trim();
    const password = document.getElementById('regPassword').value;
    hideAlert('authAlert');
    setButtonLoading('registerBtn', true);

    try {
        const res = await fetch('/api/auth/register', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ username, password })
        });
        if (res.ok) {
            currentUser = await res.json();
            showApp();
        } else {
            const err = await res.text();
            showAlert('authAlert', err || 'Username is already taken. Try another.');
        }
    } catch {
        showAlert('authAlert', 'Unable to reach the server. Please try again.');
    } finally {
        setButtonLoading('registerBtn', false);
    }
}

async function handleLogout() {
    await fetch('/api/auth/logout', { method: 'POST' });
    currentUser = null;
    clearInterval(pollInterval);
    showOnboarding();
}

// ── Screen Switcher ───────────────────────────────────────────
function showOnboarding() {
    clearInterval(pollInterval);
    document.getElementById('auth-container').classList.remove('d-none');
    document.getElementById('app-container').classList.add('d-none');
    document.getElementById('loginForm').reset();
    document.getElementById('registerForm').reset();
}

function showApp() {
    document.getElementById('auth-container').classList.add('d-none');
    document.getElementById('app-container').classList.remove('d-none');
    document.getElementById('currentUserDisplay').textContent = currentUser.username;

    switchView('dashboard');
    loadAllData();

    // Check market hours status on startup and launch dynamic polling
    checkMarketStatusAndAdjustPolling().then(() => {
        startPolling(marketOpen ? 5000 : 30000);
    });
}

function startPolling(rate) {
    if (pollInterval) clearInterval(pollInterval);
    currentPollRate = rate;
    pollInterval = setInterval(async () => {
        await pollMarketAndPortfolio();
        await checkMarketStatusAndAdjustPolling();
    }, rate);
}

async function checkMarketStatusAndAdjustPolling() {
    try {
        const res = await fetch('/api/market/status');
        if (res.ok) {
            const status = await res.json();
            marketOpen = status.open;
            
            const badge = document.getElementById('activeMarketStatus');
            if (badge) {
                badge.textContent = status.message;
                badge.className = `market-status-badge ${marketOpen ? 'open' : 'closed'}`;
            }

            // Dynamically slow down polling if market is closed to save resources
            const newRate = marketOpen ? 5000 : 30000;
            if (newRate !== currentPollRate) {
                startPolling(newRate);
            }
        }
    } catch (e) {
        console.error('Error checking market status:', e);
    }
}

function switchView(view) {
    activeView = view;

    // Hide all views
    ['dashboard', 'trade', 'watchlist', 'journal'].forEach(v => {
        const el = document.getElementById(`view-${v}`);
        if (el) el.classList.add('d-none');
        const menu = document.getElementById(`menu-${v}`);
        if (menu) menu.classList.remove('active');
    });

    // Show target view
    document.getElementById(`view-${view}`).classList.remove('d-none');
    document.getElementById(`menu-${view}`).classList.add('active');

    // Update header title
    const titles    = { dashboard: 'Dashboard', trade: 'Trade Stocks', watchlist: 'My Watchlist', journal: 'Behavioral Journal' };
    const subtitles = {
        dashboard: `Hello, <span id="currentUserDisplay" style="color:var(--blue);font-weight:600;">${currentUser?.username || 'Investor'}</span> — your journey continues`,
        trade: 'Browse stocks and place orders',
        watchlist: 'Monitor your favorite stocks',
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

// ── Data Orchestration ────────────────────────────────────────
let watchlist = [];
let journalSummary = null;
let watchlistCacheKey = '';
let positionsCacheKey = '';

async function loadAllData() {
    await Promise.all([fetchStocks(), fetchPortfolio(), fetchJournalAnalysis(), fetchWatchlist()]);
    renderAllViews();
}

function renderAllViews() {
    // 1. Update overall headers
    updateHeaderSummary();

    // 2. Render holdings cards (dashboard) - only if data structure changed
    const currentPositionsKey = positions.map(p => `${p.ticker}:${p.quantity}:${p.averageBuyPrice}:${p.stopLossPrice ?? ''}`).sort().join(',');
    if (currentPositionsKey !== positionsCacheKey) {
        positionsCacheKey = currentPositionsKey;
        renderHoldingsCards();
    }

    // 3. Render transaction ledger
    renderLedgerTable();

    // 4. Render watchlist components - only if data structure changed
    const currentWatchlistKey = watchlist.map(w => w.ticker).sort().join(',');
    if (currentWatchlistKey !== watchlistCacheKey) {
        watchlistCacheKey = currentWatchlistKey;
        renderDashboardWatchlist();
        updateWatchlistBadge();
        if (activeView === 'watchlist') {
            renderWatchlistDisplay();
        }
    } else {
        updateWatchlistBadge();
        if (activeView === 'watchlist' && !document.querySelector('.watchlist-grid')) {
            renderWatchlistDisplay();
        }
    }

    // 5. Active view specific rendering
    if (activeView === 'trade') {
        renderStocksList();
    }

    // 6. Coaching insights rendering
    if (journalSummary) {
        renderCoachingInsights(journalSummary);
    }
}

async function pollMarketAndPortfolio() {
    // Snapshot old prices for flash animation
    const oldPrices = {};
    stocks.forEach(s => (oldPrices[s.ticker] = s.currentPrice));

    await Promise.all([fetchStocks(), fetchPortfolio(), fetchJournalAnalysis(), fetchWatchlist()]);
    
    renderAllViews();

    // Flash price changes in live views and update DOM in-place
    stocks.forEach(stock => {
        const old = oldPrices[stock.ticker];
        const currentPrice = stock.currentPrice;
        const priceChange = stock.priceChange;
        const changePercent = stock.changePercent;

        // Update all unit price elements and flash them
        document.querySelectorAll(`.ticker-price-${stock.ticker}`).forEach(el => {
            el.textContent = `$${currentPrice.toFixed(2)}`;
            if (old && old !== currentPrice) {
                const cls = currentPrice > old ? 'price-up' : 'price-down';
                el.classList.add(cls);
                setTimeout(() => el.classList.remove(cls), 900);
            }
        });

        // Update all daily change percentage elements
        document.querySelectorAll(`.ticker-change-${stock.ticker}`).forEach(el => {
            const isPositive = changePercent >= 0;
            const sign = isPositive ? '+' : '';
            const isDashboardItem = el.classList.contains('dw-change');
            if (isDashboardItem) {
                const icon = isPositive ? '📈' : '📉';
                el.textContent = `${icon} ${sign}${changePercent.toFixed(2)}%`;
                el.className = `dw-change ${isPositive ? 'positive' : 'negative'} ticker-change-${stock.ticker}`;
            } else {
                const icon = isPositive ? '▲' : '▼';
                el.innerHTML = `<span class="wc-icon">${icon}</span>${sign}${changePercent.toFixed(2)}%`;
                
                const container = el.closest('.wc-change-section');
                if (container) {
                    container.className = `wc-change-section ${isPositive ? 'positive' : 'negative'} ticker-change-container-${stock.ticker}`;
                }
            }
        });

        // Update active holdings card details and flash them
        document.querySelectorAll(`.holding-value-${stock.ticker}`).forEach(el => {
            const qty = parseFloat(el.dataset.qty) || 0;
            const totalVal = qty * currentPrice;
            el.textContent = `$${totalVal.toFixed(2)}`;
            if (old && old !== currentPrice) {
                const cls = currentPrice > old ? 'price-up' : 'price-down';
                el.classList.add(cls);
                setTimeout(() => el.classList.remove(cls), 900);
            }
        });

        document.querySelectorAll(`.holding-return-${stock.ticker}`).forEach(el => {
            const avg = parseFloat(el.dataset.avg) || 0;
            const qty = parseFloat(el.dataset.qty) || 0;
            if (avg > 0) {
                const profitPct = ((currentPrice - avg) / avg) * 100;
                el.textContent = `${(currentPrice - avg) >= 0 ? '+' : ''}${profitPct.toFixed(2)}%`;
                el.className = `holding-return ${(currentPrice - avg) >= 0 ? 'positive' : 'negative'} holding-return-${stock.ticker}`;
            }
        });
    });

    // Refresh active stock price display in Trade view
    if (activeView === 'trade' && selectedTicker) {
        if (activeTimeframe === '1D') {
            await fetchAndDrawCandles(selectedTicker);
        } else {
            const stock = stocks.find(s => s.ticker === selectedTicker);
            if (stock && chartInstance) {
                document.getElementById('activeStockPrice').textContent = `$${stock.currentPrice.toFixed(2)}`;
                const prices = chartInstance.data.datasets[0].data;
                if (prices && prices.length > 0) {
                    const firstPrice = prices[0];
                    const lastPrice = stock.currentPrice;
                    const changeVal = lastPrice - firstPrice;
                    const changePct = (changeVal / firstPrice) * 100;
                    
                    const el = document.getElementById('activeStockChange');
                    el.textContent = `${changeVal >= 0 ? '+' : ''}${changeVal.toFixed(2)} (${changePct >= 0 ? '+' : ''}${changePct.toFixed(2)}%)`;
                    el.className = `stock-detail-change ${changePct >= 0 ? 'text-green' : 'text-red'}`;
                }
            }
        }
    }
}

// ── REST: Market Data ─────────────────────────────────────────
async function fetchStocks() {
    try {
        const res = await fetch('/api/market/stocks');
        if (res.ok) {
            stocks = await res.json();
        }
    } catch (e) {
        console.error('Error fetching stocks:', e);
    }
}

// ── REST: Portfolio ───────────────────────────────────────────
async function fetchPortfolio() {
    try {
        // Refresh wallet balance from session
        const statusRes = await fetch('/api/auth/status');
        if (statusRes.ok) currentUser = await statusRes.json();

        const posRes = await fetch('/api/portfolio/positions');
        if (posRes.ok) {
            positions = await posRes.json();
        }

        const tradesRes = await fetch('/api/portfolio/trades');
        if (tradesRes.ok) {
            trades = await tradesRes.json();
        }
    } catch (e) {
        console.error('Error fetching portfolio:', e);
    }
}

// ── REST: Journal Analysis ────────────────────────────────────
async function fetchJournalAnalysis() {
    try {
        const res = await fetch('/api/journal/analysis');
        if (res.ok) {
            journalSummary = await res.json();
        }
    } catch (e) {
        console.error('Error fetching journal:', e);
    }
}

// ── Header Summary Update ─────────────────────────────────────
function updateHeaderSummary() {
    let equity = 0;
    positions.forEach(pos => {
        const stock = stocks.find(s => s.ticker === pos.ticker);
        if (stock) equity += pos.quantity * stock.currentPrice;
    });

    const cash     = currentUser?.balance ?? 0;
    const netWorth = cash + equity;
    const profit   = netWorth - 10000;
    const profitPct = (profit / 10000) * 100;
    const fmt = n => n.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });

    // Top bar
    document.getElementById('portfolioValueHeader').textContent = `$${fmt(netWorth)}`;

    // Hero card
    document.getElementById('heroPortfolioValue').textContent = `$${fmt(netWorth)}`;
    const heroChange = document.getElementById('heroPLChange');
    heroChange.textContent = `${profit >= 0 ? '+' : ''}$${fmt(profit)} (${profitPct.toFixed(2)}%) all time`;
    heroChange.className   = `hero-change ${profit >= 0 ? 'positive' : 'negative'}`;

    // Stat pills
    document.getElementById('cashWalletDisplay').textContent    = `$${fmt(cash)}`;
    document.getElementById('holdingsValueDisplay').textContent = `$${fmt(equity)}`;

    const plEl = document.getElementById('totalPlDisplay');
    plEl.textContent = `${profit >= 0 ? '+' : ''}$${fmt(profit)}`;
    plEl.style.color = profit >= 0 ? 'var(--green)' : 'var(--red)';
}

// ── Render: Holdings Cards (Dashboard) ───────────────────────
function renderHoldingsCards() {
    const container = document.getElementById('holdingsContainer');

    if (positions.length === 0) {
        container.innerHTML = `
            <div class="empty-state">
                <i class="bi bi-wallet2" style="color:var(--text-muted);"></i>
                <p>No holdings yet. Go to <strong>Trade</strong> to buy your first stock.</p>
                <button class="btn btn-primary btn-sm" onclick="switchView('trade')">
                    <i class="bi bi-graph-up-arrow"></i> Browse Stocks
                </button>
            </div>`;
        return;
    }

    container.innerHTML = positions.map(pos => {
        const stock       = stocks.find(s => s.ticker === pos.ticker);
        const currentPrice = stock ? stock.currentPrice : pos.averageBuyPrice;
        const value        = pos.quantity * currentPrice;
        const profit       = (currentPrice - pos.averageBuyPrice) * pos.quantity;
        const profitPct    = ((currentPrice - pos.averageBuyPrice) / pos.averageBuyPrice) * 100;
        const hasSL        = !!pos.stopLossPrice;
        const initials     = pos.ticker.slice(0, 2);

        return `
        <div class="holding-card" onclick="switchView('trade'); selectStock('${pos.ticker}')">
            <div class="holding-card-left">
                <div class="holding-avatar">${initials}</div>
                <div>
                    <div class="holding-name">${pos.ticker}</div>
                    <div class="holding-qty">${pos.quantity} shares · avg $${pos.averageBuyPrice.toFixed(2)}</div>
                </div>
            </div>
            <div class="holding-card-right">
                <div class="holding-value holding-value-${pos.ticker}" data-qty="${pos.quantity}">$${value.toFixed(2)}</div>
                <div class="holding-return ${profit >= 0 ? 'positive' : 'negative'} holding-return-${pos.ticker}" data-avg="${pos.averageBuyPrice}" data-qty="${pos.quantity}">
                    ${profit >= 0 ? '+' : ''}${profitPct.toFixed(2)}%
                </div>
            </div>
            <div class="holding-sl-warning ${hasSL ? 'sl-set-bar' : ''}"></div>
        </div>`;
    }).join('');

    // Keep hidden table body in sync for any legacy references
    document.getElementById('positionsTableBody').innerHTML =
        positions.map(pos => `<tr data-ticker="${pos.ticker}"></tr>`).join('');
}

// ── Render: Stocks List (Trade View) ─────────────────────────
function renderStocksList() {
    const container = document.getElementById('stocksListContainer');

    if (!stocks.length) {
        container.innerHTML = `<div class="empty-state"><i class="bi bi-wifi-off"></i><p>Could not load market data.</p></div>`;
        return;
    }

    container.innerHTML = stocks.map(stock => {
        const diff = stock.priceChange;
        const pct  = stock.changePercent;
        const isSelected = stock.ticker === selectedTicker;
        const inWatchlist = watchlist.some(w => w.ticker === stock.ticker);

        return `
        <div class="stock-row ${isSelected ? 'selected' : ''}" onclick="selectStock('${stock.ticker}')">
            <div class="stock-row-left">
                <div class="stock-ticker-badge">${stock.ticker}</div>
                <div class="stock-name">${stock.name}</div>
            </div>
            <div class="stock-row-center">
                <div class="stock-price ticker-price-${stock.ticker}">$${stock.currentPrice.toFixed(2)}</div>
                <div class="stock-change ${pct >= 0 ? 'pos' : 'neg'}">${pct >= 0 ? '+' : ''}${pct.toFixed(2)}%</div>
            </div>
            <div class="stock-row-right">
                <button class="btn-watchlist ${inWatchlist ? 'active' : ''}" onclick="event.stopPropagation(); toggleWatchlist('${stock.ticker}');" title="${inWatchlist ? 'Remove from watchlist' : 'Add to watchlist'}">
                    <i class="bi ${inWatchlist ? 'bi-star-fill' : 'bi-star'}"></i>
                </button>
            </div>
        </div>`;
    }).join('');

    // Keep hidden legacy table body in sync
    document.getElementById('stocksTableBody').innerHTML =
        stocks.map(s => `<tr data-ticker="${s.ticker}"></tr>`).join('');
}

// ── Render: Coaching Insights ─────────────────────────────────
function renderCoachingInsights(summary) {
    // Score colour map
    const scoreColors = { A: 'var(--green)', B: 'var(--blue)', C: 'var(--gold)', D: 'var(--red)' };
    const scoreDescriptions = {
        A: { title: 'Compounding Expert',  desc: 'You are maintaining safety barriers, practicing proper sizing, and showing patience. Keep it up!' },
        B: { title: 'Prudent Investor',    desc: 'Good risk discipline. A few minor warnings, but overall you protect your downside well.' },
        C: { title: 'Emotional Trader',    desc: 'Multiple bad habits detected. Overtrading or lack of stops is impacting your compounding capacity.' },
        D: { title: 'Gambler Territory',   desc: 'Warning: High-volatility practices. You hold major losers or trade impulsively. Return to strategy.' }
    };

    const color = scoreColors[summary.score] || 'var(--blue)';
    const desc  = scoreDescriptions[summary.score];

    // Update hidden legacy badge (ID must stay for any external references)
    const legacyBadge = document.getElementById('dashboardScoreDisplay');
    if (legacyBadge) legacyBadge.textContent = summary.score;

    // Update Dashboard Discipline Index Card
    const dValEl = document.getElementById('dashboardScoreValue');
    const dGradeEl = document.getElementById('dashboardScoreGrade');
    if (dValEl) dValEl.textContent = `${summary.disciplineScore}%`;
    if (dGradeEl) {
        dGradeEl.textContent = summary.score;
        dGradeEl.style.color = color;
    }
    updateDashboardScoreRing(summary.disciplineScore, color);

    // Journal score ring
    updateScoreRing(summary.score, color);
    document.getElementById('journalScoreBadge').textContent = summary.score;
    document.getElementById('journalScoreBadge').style.color = color;
    document.getElementById('journalScoreTitle').textContent = desc.title;
    document.getElementById('journalScoreDesc').textContent  = desc.desc;

    // Alert counts
    const count = summary.insights?.length ?? 0;
    document.getElementById('activeAlertCount').textContent = `${count} active`;
    const journalCount = document.getElementById('journalAlertCount');
    if (journalCount) journalCount.textContent = `${count} issue${count !== 1 ? 's' : ''}`;

    const dashList    = document.getElementById('dashboardInsightsList');
    const journalList = document.getElementById('journalInsightsContainer');

    if (!count) {
        const emptyHtml = `
            <div class="empty-state" style="padding:1.5rem;">
                <i class="bi bi-patch-check" style="color:var(--green);"></i>
                <p>No behavioral mistakes detected. You're on track!</p>
            </div>`;
        dashList.innerHTML    = emptyHtml;
        journalList.innerHTML = emptyHtml;
        return;
    }

    const insightsHtml = summary.insights.map(ins => {
        const isWarning = ins.severity !== 'DANGER';
        return `
        <div class="insight-card ${isWarning ? 'warning' : 'danger'}">
            <div class="insight-icon-row">
                <div class="insight-icon">
                    <i class="bi ${isWarning ? 'bi-exclamation-triangle-fill' : 'bi-exclamation-octagon-fill'}"></i>
                </div>
                <div class="insight-title">${ins.title}</div>
            </div>
            <p class="insight-desc">${ins.description}</p>
            <div class="insight-rec">
                <i class="bi bi-lightbulb-fill"></i>
                <span>${ins.recommendation}</span>
            </div>
        </div>`;
    }).join('');

    dashList.innerHTML    = insightsHtml;
    journalList.innerHTML = insightsHtml;
}

// ── Render: Journal Score Ring ────────────────────────────────
function updateScoreRing(score, color) {
    const canvas = document.getElementById('journalScoreChart');
    if (!canvas) return;

    // Score → percentage fill
    const fillMap = { A: 95, B: 75, C: 50, D: 25 };
    const fill    = fillMap[score] ?? 80;

    if (scoreRingChart) scoreRingChart.destroy();

    scoreRingChart = new Chart(canvas.getContext('2d'), {
        type: 'doughnut',
        data: {
            datasets: [{
                data: [fill, 100 - fill],
                backgroundColor: [color, 'rgba(255,255,255,0.05)'],
                borderWidth: 0,
                borderRadius: 6,
                spacing: 2
            }]
        },
        options: {
            cutout: '75%',
            responsive: false,
            plugins: { legend: { display: false }, tooltip: { enabled: false } },
            animation: { animateRotate: true, duration: 900 }
        }
    });
}

// ── Render: Dashboard Score Ring ──────────────────────────────
function updateDashboardScoreRing(score, color) {
    const canvas = document.getElementById('dashboardScoreChart');
    if (!canvas) return;

    if (dashboardScoreChartInstance) dashboardScoreChartInstance.destroy();

    dashboardScoreChartInstance = new Chart(canvas.getContext('2d'), {
        type: 'doughnut',
        data: {
            datasets: [{
                data: [score, 100 - score],
                backgroundColor: ['#fff', 'rgba(255,255,255,0.15)'],
                borderWidth: 0,
                borderRadius: 4,
                spacing: 1
            }]
        },
        options: {
            cutout: '78%',
            responsive: false,
            plugins: { legend: { display: false }, tooltip: { enabled: false } },
            animation: { animateRotate: true, duration: 800 }
        }
    });
}

// ── Render: Ledger Table ──────────────────────────────────────
function renderLedgerTable() {
    const tbody = document.getElementById('ledgerTableBody');

    if (!trades.length) {
        tbody.innerHTML = `<tr><td colspan="7" style="text-align:center; color:var(--text-muted); padding:1.5rem;">No transactions yet.</td></tr>`;
        return;
    }

    tbody.innerHTML = trades.map(trade => {
        const date  = new Date(trade.timestamp).toLocaleString();
        const total = trade.price * trade.quantity;
        const slTxt = trade.stopLossPrice ? `$${trade.stopLossPrice.toFixed(2)}` : '—';
        const isBuy = trade.tradeType === 'BUY';

        return `
        <tr>
            <td style="color:var(--text-secondary); white-space:nowrap;">${date}</td>
            <td><span class="badge badge-sym">${trade.ticker}</span></td>
            <td><span class="badge ${isBuy ? 'badge-buy' : 'badge-sell'}">${trade.tradeType}</span></td>
            <td>${trade.quantity}</td>
            <td>$${trade.price.toFixed(2)}</td>
            <td style="font-weight:600;">$${total.toFixed(2)}</td>
            <td>${slTxt !== '—' ? `<span class="badge badge-warn">${slTxt}</span>` : `<span style="color:var(--text-muted);">—</span>`}</td>
        </tr>`;
    }).join('');
}

// ── Stock Selection + Chart ───────────────────────────────────
async function selectStock(ticker) {
     selectedTicker = ticker;
     renderStocksList();

     const stock = stocks.find(s => s.ticker === ticker);
     if (!stock) return;

     document.getElementById('activeStockTicker').textContent = stock.ticker;
     document.getElementById('activeStockName').textContent   = stock.name;
     document.getElementById('activeStockPrice').textContent  = `$${stock.currentPrice.toFixed(2)}`;

     const diff = stock.priceChange ?? 0;
     const pct  = stock.changePercent ?? 0;
     const changeEl = document.getElementById('activeStockChange');
     changeEl.textContent = `${diff >= 0 ? '+' : ''}${diff.toFixed(2)}(${pct.toFixed(2)}%)`;
     changeEl.className   = `stock-detail-change ${pct >= 0 ? 'text-green' : 'text-red'}`;

     // Reset order forms
     document.getElementById('buyQty').value             = '';
     document.getElementById('buyEstCost').textContent   = '$0.00';
     document.getElementById('sellQty').value            = '';
     document.getElementById('sellEstProceeds').textContent = '$0.00';
     document.getElementById('enableStopLoss').checked   = false;
     document.getElementById('stopLossInputContainer').classList.add('d-none');
     document.getElementById('buyStopLossPrice').value   = '';

     const pos = positions.find(p => p.ticker === ticker);
     document.getElementById('ownedSharesDisplay').textContent = pos ? pos.quantity : '0';

     hideAlert('orderFormAlert');
     hideAlert('orderFormSuccess');

     // Fetch candles & draw the interactive chart
     await fetchAndDrawCandles(ticker, false);

     // Start intelligent chart polling for this timeframe
     startChartPolling(ticker, activeTimeframe);
}

// ── Chart LocalStorage Cache Manager ──────────────────────
class ChartDataCache {
    constructor() {
        this.prefix = 'tradewise_chart_';
    }

    key(ticker, timeframe) {
        return `${this.prefix}${ticker}_${timeframe}`;
    }

    get(ticker, timeframe) {
        try {
            const cached = localStorage.getItem(this.key(ticker, timeframe));
            if (!cached) return null;
            const data = JSON.parse(cached);
            // Check if cache is still fresh
            const age = (Date.now() - data.timestamp) / 1000;
            const ttl = this.getTTL(timeframe);
            if (age > ttl) {
                this.clear(ticker, timeframe);
                return null;
            }
            return data.value;
        } catch (e) {
            console.warn('Cache read error:', e);
            return null;
        }
    }

    set(ticker, timeframe, data) {
        try {
            localStorage.setItem(this.key(ticker, timeframe), JSON.stringify({
                value: data,
                timestamp: Date.now()
            }));
        } catch (e) {
            console.warn('Cache write error:', e);
        }
    }

    clear(ticker, timeframe) {
        try {
            localStorage.removeItem(this.key(ticker, timeframe));
        } catch (e) {
            console.warn('Cache clear error:', e);
        }
    }

    getTTL(timeframe) {
        // Cache duration in seconds per timeframe
        // Backend TTL values specified in CacheConfig.java
        const ttls = {
            '1D': 30,      // 30 seconds
            '1W': 300,     // 5 minutes
            '1M': 900,     // 15 minutes
            '3M': 1800,    // 30 minutes
            '6M': 3600,    // 1 hour
            '1Y': 3600,    // 1 hour
            '3Y': 3600,    // 1 hour
            '5Y': 3600     // 1 hour
        };
        return ttls[timeframe] || 3600;
    }
}

const chartCache = new ChartDataCache();

// ── Chart Intelligent Polling Manager ──────────────────────
let chartPollIntervals = {};

function getChartPollingInterval(timeframe) {
    // Polling intervals aligned with backend cache TTLs
    // Ensures most requests hit the cache on backend
    const intervals = {
        '1D': 5000,     // 5 seconds (backend cache: 30sec)
        '1W': 30000,    // 30 seconds (backend cache: 5min)
        '1M': 60000,    // 60 seconds (backend cache: 15min)
        '3M': 60000,    // 60 seconds (backend cache: 30min)
        '6M': 120000,   // 2 minutes (backend cache: 1hr)
        '1Y': 120000,   // 2 minutes (backend cache: 1hr)
        '3Y': 120000,   // 2 minutes (backend cache: 1hr)
        '5Y': 120000    // 2 minutes (backend cache: 1hr)
    };
    return intervals[timeframe] || 60000;
}

function startChartPolling(ticker, timeframe) {
    // Stop previous polling for this ticker
    const pollKey = `${ticker}_${timeframe}`;
    if (chartPollIntervals[pollKey]) {
        clearInterval(chartPollIntervals[pollKey]);
    }

    // Start new polling at intelligent interval
    const interval = getChartPollingInterval(timeframe);
    chartPollIntervals[pollKey] = setInterval(() => {
        if (activeView === 'trade' && selectedTicker === ticker && activeTimeframe === timeframe) {
            fetchAndDrawCandles(ticker, true); // true = silent update
        }
    }, interval);
}

function stopChartPolling(ticker, timeframe) {
    const pollKey = `${ticker}_${timeframe}`;
    if (chartPollIntervals[pollKey]) {
        clearInterval(chartPollIntervals[pollKey]);
        delete chartPollIntervals[pollKey];
    }
}

// ── Chart Fetching & Timeframe Orchestration ─────────────────
async function changeChartTimeframe(tf) {
    activeTimeframe = tf;
    
    // Highlight the active button
    document.querySelectorAll('.timeframe-btn').forEach(btn => {
        btn.classList.toggle('active', btn.dataset.timeframe === tf);
    });
    
    if (selectedTicker) {
        // Stop polling for old timeframe
        stopChartPolling(selectedTicker, activeTimeframe);
        // Fetch and display new timeframe
        await fetchAndDrawCandles(selectedTicker, false);
        // Start polling for new timeframe
        startChartPolling(selectedTicker, tf);
    }
}

async function fetchAndDrawCandles(ticker, silent = false) {
    const loader = document.getElementById('chartLoader');
    if (!silent && loader) loader.classList.add('active');

    // Remove any previous error message on non-silent updates
    if (!silent) {
        const existingError = document.getElementById('chartErrorMsg');
        if (existingError) existingError.remove();
    }

    try {
        // Check LocalStorage cache first
        const cachedData = chartCache.get(ticker, activeTimeframe);
        if (cachedData && !silent) {
            console.debug(`Using cached chart data for ${ticker} [${activeTimeframe}]`);
            drawCandlesChart(cachedData, ticker);
            if (loader) loader.classList.remove('active');
            return;
        }

        // Fetch from server
        const res = await fetch(`/api/market/stocks/${ticker}/candles?timeframe=${activeTimeframe}`);
        if (res.ok) {
            const data = await res.json();

            // Cache the response
            chartCache.set(ticker, activeTimeframe, data);

            if (data.status === 'ok' && data.prices && data.prices.length > 0) {
                drawCandlesChart(data, ticker);
            } else if (!silent) {
                showChartError('No historical data available for this timeframe.');
            }
        } else if (!silent) {
            showChartError('Failed to load market candles.');
        }
    } catch (e) {
        console.error('Candles fetch error:', e);
        if (!silent) {
            showChartError('Unable to connect to market server.');
        }
    } finally {
        if (loader) loader.classList.remove('active');
    }
}


function showChartError(msg) {
    const container = document.querySelector('.chart-container-wrapper');
    if (!container) return;
    
    const existing = document.getElementById('chartErrorMsg');
    if (existing) existing.remove();
    
    const errorDiv = document.createElement('div');
    errorDiv.id = 'chartErrorMsg';
    errorDiv.className = 'chart-error-alert';
    errorDiv.innerHTML = `
        <i class="bi bi-exclamation-triangle-fill" style="font-size: 1.5rem; margin-bottom: 6px;"></i>
        <div style="font-size: 0.85rem; font-weight: 600;">${msg}</div>
        <button class="btn btn-ghost btn-sm" onclick="fetchAndDrawCandles(selectedTicker)" style="margin-top: 8px; padding: 4px 8px; font-size:0.7rem;">
            <i class="bi bi-arrow-clockwise"></i> Try Again
        </button>
    `;
    container.appendChild(errorDiv);
}

// ── Chart Drawing ─────────────────────────────────────────────
/**
 * Draw professional candlestick or line chart from OHLCV data
 *
 * Data structure:
 * - prices: Array of close prices (for simple line chart fallback)
 * - ohlc: Array of {o, h, l, c, v, t} candle objects (for professional rendering)
 * - volumes: Array of volume values
 * - session: Market session type (REGULAR, PRE_MARKET, POST_MARKET, DATABASE_FALLBACK)
 */
function drawCandlesChart(candleData, ticker) {
     const canvas = document.getElementById('stockHistoryChart');
     if (!canvas) return;

     const prices = candleData.prices;
     const timestamps = candleData.timestamps;
     const session = candleData.session || 'REGULAR';

     // Format labels based on activeTimeframe
     const labels = timestamps.map(t => {
         const date = new Date(t * 1000);
         if (activeTimeframe === '1D') {
             return date.toLocaleTimeString('en-US', { hour: '2-digit', minute: '2-digit' });
         } else if (activeTimeframe === '1W') {
             return date.toLocaleDateString('en-US', { weekday: 'short' }) + ' ' + date.toLocaleTimeString('en-US', { hour: '2-digit', minute: '2-digit' });
         } else if (activeTimeframe === '1M' || activeTimeframe === '3M' || activeTimeframe === '6M') {
             return date.toLocaleDateString('en-US', { month: 'short', day: 'numeric' });
         } else {
             return date.toLocaleDateString('en-US', { year: '2-digit', month: 'short', day: 'numeric' });
         }
     });

     if (chartInstance) chartInstance.destroy();

     const ctx = canvas.getContext('2d');
     const firstPrice = prices[0];
     const lastPrice = prices[prices.length - 1];
     const isUp = lastPrice >= firstPrice;
     const accentColor = isUp ? '#00D4AA' : '#FF4D6D';

     const gradient = ctx.createLinearGradient(0, 0, 0, 220);
     gradient.addColorStop(0, isUp ? 'rgba(0,212,170,0.25)' : 'rgba(255,77,109,0.22)');
     gradient.addColorStop(1, 'rgba(0,0,0,0)');

     // Build chart configuration - use OHLC data for enhanced tooltip info
     const chartConfig = {
         type: 'line',
         data: {
             labels,
             datasets: [{
                 label: 'Price ($)',
                 data: prices,
                 borderColor: accentColor,
                 borderWidth: 2.5,
                 pointRadius: 0,
                 pointHoverRadius: 6,
                 pointHoverBackgroundColor: accentColor,
                 fill: true,
                 backgroundColor: gradient,
                 tension: activeTimeframe === '1D' ? 0.2 : 0.15,
                 // Store OHLCV data for enhanced tooltip display
                 ohlcData: candleData.ohlc || null,
                 volumeData: candleData.volumes || null
             }]
         },
         options: {
             responsive: true,
             maintainAspectRatio: false,
             interaction: { intersect: false, mode: 'index' },
             plugins: {
                 legend: { display: false },
                 tooltip: {
                     backgroundColor: 'rgba(13,21,37,0.95)',
                     borderColor: 'rgba(255,255,255,0.1)',
                     borderWidth: 1,
                     titleColor: '#7B8DB5',
                     bodyColor: '#EFF3FF',
                     bodyFont: { family: 'Sora', weight: '700', size: 14 },
                     padding: 12,
                     displayColors: false,
                     callbacks: {
                         title: (context) => {
                             // Show timestamp in tooltip
                             if (!context || !context.length) return 'Price';
                             const dataIndex = context[0].dataIndex;
                             const timestamp = timestamps[dataIndex];
                             const date = new Date(timestamp * 1000);
                             return date.toLocaleString('en-US');
                         },
                         label: (context) => {
                             const dataIndex = context.dataIndex;
                             let label = ` Close: $${context.parsed.y.toFixed(2)}`;

                             // Show OHLC data if available
                             const ohlcData = context.dataset.ohlcData;
                             if (ohlcData && dataIndex < ohlcData.length) {
                                 const candle = ohlcData[dataIndex];
                                 if (candle.o) label = ` Open: $${candle.o.toFixed(2)}` + label;
                                 if (candle.h) label += `\n High: $${candle.h.toFixed(2)}`;
                                 if (candle.l) label += `\n Low: $${candle.l.toFixed(2)}`;
                                 if (candle.v) label += `\n Vol: ${(candle.v / 1000000).toFixed(2)}M`;
                             } else if (context.dataset.volumeData && dataIndex < context.dataset.volumeData.length) {
                                 const vol = context.dataset.volumeData[dataIndex];
                                 label += `\n Vol: ${(vol / 1000000).toFixed(2)}M`;
                             }

                             return label;
                         }
                     }
                 }
             },
             scales: {
                 x: {
                     grid: { color: 'rgba(255,255,255,0.02)' },
                     ticks: { color: '#4A5878', font: { size: 9, family: 'Inter' }, maxTicksLimit: 6 },
                     border: { display: false }
                 },
                 y: {
                     position: 'right',
                     grid: { color: 'rgba(255,255,255,0.03)' },
                     ticks: {
                         color: '#4A5878',
                         font: { size: 9, family: 'Inter' },
                         callback: v => `$${v.toFixed(2)}`
                     },
                     border: { display: false }
                 }
             }
         }
     };

     chartInstance = new Chart(ctx, chartConfig);

     // Show data source indicator for database fallback
     if (session === 'DATABASE_FALLBACK') {
         const badge = document.createElement('div');
         badge.style.cssText = 'position:absolute;top:8px;right:8px;font-size:0.7rem;color:#FFB800;background:rgba(255,184,0,0.1);padding:4px 8px;border-radius:4px;border:1px solid rgba(255,184,0,0.3);';
         badge.textContent = '📊 Using historical data';
         canvas.parentElement.style.position = 'relative';
         canvas.parentElement.appendChild(badge);
     }

     // Sync metrics inside stock detail card
     const stock = stocks.find(s => s.ticker === ticker);
     if (stock) {
         document.getElementById('activeStockPrice').textContent = `$${lastPrice.toFixed(2)}`;

         let changeVal, changePct;
         if (activeTimeframe === '1D') {
             changeVal = stock.priceChange ?? 0.0;
             changePct = stock.changePercent ?? 0.0;
         } else {
             changeVal = lastPrice - firstPrice;
             changePct = (changeVal / firstPrice) * 100;
         }

         const el = document.getElementById('activeStockChange');
         el.textContent = `${changeVal >= 0 ? '+' : ''}${changeVal.toFixed(2)} (${changePct >= 0 ? '+' : ''}${changePct.toFixed(2)}%)`;
         el.className = `stock-detail-change ${changePct >= 0 ? 'text-green' : 'text-red'}`;
     }
}

// ── Order Handlers ────────────────────────────────────────────
async function handleBuyOrder(e) {
    e.preventDefault();
    const quantity     = parseFloat(document.getElementById('buyQty').value);
    const hasStopLoss  = document.getElementById('enableStopLoss').checked;
    const stopLossPrice = hasStopLoss
        ? parseFloat(document.getElementById('buyStopLossPrice').value) || null
        : null;

    hideAlert('orderFormAlert');
    hideAlert('orderFormSuccess');

    try {
        const res = await fetch('/api/portfolio/buy', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ ticker: selectedTicker, quantity, stopLossPrice })
        });

        if (res.ok) {
            document.getElementById('buyForm').reset();
            document.getElementById('buyEstCost').textContent = '$0.00';
            document.getElementById('stopLossInputContainer').classList.add('d-none');

            showToast('success', 'Order Placed! 🎉', `Bought ${quantity} share${quantity > 1 ? 's' : ''} of ${selectedTicker}`);
            loadAllData();
        } else {
            const err = await res.text();
            showAlert('orderFormAlert', err || 'Transaction failed. Please check your balance.');
        }
    } catch {
        showAlert('orderFormAlert', 'Could not reach the server. Check your connection.');
    }
}

async function handleSellOrder(e) {
    e.preventDefault();
    const quantity = parseFloat(document.getElementById('sellQty').value);

    hideAlert('orderFormAlert');
    hideAlert('orderFormSuccess');

    try {
        const res = await fetch('/api/portfolio/sell', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ ticker: selectedTicker, quantity })
        });

        if (res.ok) {
            document.getElementById('sellForm').reset();
            document.getElementById('sellEstProceeds').textContent = '$0.00';

            showToast('success', 'Sold Successfully', `Sold ${quantity} share${quantity > 1 ? 's' : ''} of ${selectedTicker}`);
            loadAllData();
        } else {
            const err = await res.text();
            showAlert('orderFormAlert', err || 'Transaction failed. Do you own enough shares?');
        }
    } catch {
        showAlert('orderFormAlert', 'Could not reach the server. Check your connection.');
    }
}

// ── Stop-Loss Modal ───────────────────────────────────────────
function openStopLossModal(ticker, avgPrice, currentPrice, currentSl) {
    document.getElementById('slModalTicker').value         = ticker;
    document.getElementById('stopLossModalTitle').textContent = `Stop-Loss: ${ticker}`;
    document.getElementById('slModalAvgPrice').textContent  = `$${parseFloat(avgPrice).toFixed(2)}`;
    document.getElementById('slModalCurrentPrice').textContent = `$${parseFloat(currentPrice).toFixed(2)}`;
    document.getElementById('slModalInput').value           = currentSl ? parseFloat(currentSl).toFixed(2) : '';
    hideAlert('slModalAlert');
    document.getElementById('stopLossModal').classList.add('open');
}

function closeStopLossModal() {
    document.getElementById('stopLossModal').classList.remove('open');
}

async function handleStopLossUpdate(e) {
    e.preventDefault();
    const ticker       = document.getElementById('slModalTicker').value;
    const inputVal     = document.getElementById('slModalInput').value;
    const stopLossPrice = inputVal !== '' ? parseFloat(inputVal) : null;

    hideAlert('slModalAlert');

    try {
        const res = await fetch('/api/portfolio/stoploss', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ ticker, stopLossPrice })
        });

        if (res.ok) {
            closeStopLossModal();
            showToast('success', 'Stop-Loss Updated', stopLossPrice
                ? `${ticker} will auto-sell at $${stopLossPrice.toFixed(2)}`
                : `Stop-loss removed for ${ticker}`);
            loadAllData();
        } else {
            const err = await res.text();
            showAlert('slModalAlert', err || 'Failed to update stop-loss.');
        }
    } catch {
        showAlert('slModalAlert', 'Service connection failed.');
    }
}

// ── Toast Notification System ─────────────────────────────────
function showToast(type, title, message, duration = 4000) {
    const iconMap = { success: 'bi-check-circle-fill', error: 'bi-x-circle-fill', info: 'bi-info-circle-fill' };
    const container = document.getElementById('toast-container');

    const toast = document.createElement('div');
    toast.className = `toast ${type}`;
    toast.innerHTML = `
        <i class="bi ${iconMap[type] || iconMap.info} toast-icon"></i>
        <div class="toast-body">
            <div class="toast-title">${title}</div>
            <div class="toast-msg">${message}</div>
        </div>`;

    container.appendChild(toast);

    setTimeout(() => {
        toast.classList.add('removing');
        setTimeout(() => toast.remove(), 300);
    }, duration);
}

// ── Alert Helpers ─────────────────────────────────────────────
function showAlert(id, text) {
    const el = document.getElementById(id);
    if (!el) return;
    el.textContent = text;
    el.classList.remove('d-none');
}

function hideAlert(id) {
    const el = document.getElementById(id);
    if (!el) return;
    el.classList.add('d-none');
    el.textContent = '';
}

// Legacy showSuccess kept for any inline references
function showSuccess(id, text) {
    showToast('success', 'Success', text);
}

// ── Button Loading State ──────────────────────────────────────
function setButtonLoading(id, loading) {
    const btn = document.getElementById(id);
    if (!btn) return;
    btn.disabled = loading;
    if (loading) {
        btn.dataset.origText = btn.innerHTML;
        btn.innerHTML = '<i class="bi bi-hourglass-split"></i> Please wait…';
    } else {
        btn.innerHTML = btn.dataset.origText || btn.innerHTML;
    }
}

// ── REST: Watchlist ────────────────────────────────────────────

/**
 * Fetch user's watchlist
 */
async function fetchWatchlist() {
    try {
        const res = await fetch('/api/watchlist');
        if (res.ok) {
            watchlist = await res.json();
        }
    } catch (e) {
        console.error('Error fetching watchlist:', e);
    }
}

/**
 * Toggle watchlist status for a stock
 */
async function toggleWatchlist(ticker) {
    const inWatchlist = watchlist.some(w => w.ticker === ticker);

    try {
        if (inWatchlist) {
            // Remove from watchlist
            const res = await fetch(`/api/watchlist/remove/${ticker}`, {
                method: 'DELETE'
            });

            if (res.ok) {
                watchlist = watchlist.filter(w => w.ticker !== ticker);
                renderAllViews();
                showToast('success', 'Removed', `${ticker} removed from watchlist`);
            } else {
                const err = await res.text();
                showToast('error', 'Error', err || 'Failed to remove from watchlist');
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
                await fetchWatchlist();
                renderAllViews();
            } else {
                const err = await res.text();
                showToast('error', 'Error', err || 'Failed to add to watchlist');
            }
        }
    } catch (e) {
        console.error('Error toggling watchlist:', e);
        showToast('error', 'Error', 'Connection error. Please try again.');
    }
}

/**
 * Update watchlist badge count
 */
function updateWatchlistBadge() {
    const badge = document.getElementById('watchlistNavBadge');
    if (!badge) return;

    if (watchlist.length > 0) {
        badge.textContent = watchlist.length;
        badge.classList.remove('d-none');
    } else {
        badge.classList.add('d-none');
    }
}

/**
 * Render watchlist preview on dashboard
 */
function renderDashboardWatchlist() {
    const container = document.getElementById('dashboardWatchlistContainer');
    if (!container) return;

    if (watchlist.length === 0) {
        container.innerHTML = `
            <div class="empty-state" style="padding:1.25rem;">
                <i class="bi bi-star" style="color:var(--gold); font-size:1.8rem;"></i>
                <p>Star stocks you're interested in to track them here.</p>
                <button class="btn btn-ghost btn-sm" onclick="switchView('trade')">
                    <i class="bi bi-search"></i> Browse Stocks
                </button>
            </div>`;
        return;
    }

    container.innerHTML = watchlist.slice(0, 5).map(item => {
        const changeClass = item.dailyChangePercent >= 0 ? 'positive' : 'negative';
        const changeIcon = item.dailyChangePercent >= 0 ? '📈' : '📉';

        return `
        <div class="dashboard-watchlist-item" onclick="switchView('trade'); selectStock('${item.ticker}');" style="cursor:pointer;">
            <div class="dw-left">
                <div class="dw-ticker">${item.ticker}</div>
                <div class="dw-name">${item.stockName}</div>
            </div>
            <div class="dw-right">
                <div class="dw-price ticker-price-${item.ticker}">$${item.currentPrice.toFixed(2)}</div>
                <div class="dw-change ${changeClass} ticker-change-${item.ticker}">
                    ${changeIcon} ${item.dailyChangePercent >= 0 ? '+' : ''}${item.dailyChangePercent.toFixed(2)}%
                </div>
            </div>
        </div>`;
    }).join('');

    if (watchlist.length > 5) {
        container.innerHTML += `
        <div style="padding:10px; text-align:center; font-size:0.75rem; color:var(--text-muted); border-top:1px solid var(--border-soft); margin-top:0.5rem;">
            +${watchlist.length - 5} more stocks
        </div>`;
    }
}

/**
 * Render watchlist display
 */
function renderWatchlistDisplay() {
    const container = document.getElementById('watchlistViewContainer');
    if (!container) return;

    if (watchlist.length === 0) {
        container.innerHTML = `
            <div class="empty-state">
                <i class="bi bi-star" style="color:var(--gold); font-size:2rem;"></i>
                <p style="margin-top:0.5rem;">Your watchlist is empty</p>
                <p style="font-size:0.75rem; color:var(--text-muted); margin-top:0.3rem;">Add stocks from Trade to monitor them</p>
                <button class="btn btn-primary btn-sm" onclick="switchView('trade')" style="margin-top:0.75rem;">
                    <i class="bi bi-plus-circle"></i> Add Stocks
                </button>
            </div>`;
        return;
    }

    container.innerHTML = `
    <div class="watchlist-grid">
        ${watchlist.map(item => {
            const changeClass = item.dailyChangePercent >= 0 ? 'positive' : 'negative';
            const changeIcon = item.dailyChangePercent >= 0 ? '▲' : '▼';
            
            return `
            <div class="watchlist-card">
                <div class="wc-header">
                    <div class="wc-ticker">${item.ticker}</div>
                    <button class="btn-close-wc" onclick="event.stopPropagation(); toggleWatchlist('${item.ticker}')" title="Remove from watchlist">
                        <i class="bi bi-x"></i>
                    </button>
                </div>
                <div class="wc-name">${item.stockName}</div>
                <div class="wc-body">
                    <div class="wc-price-section">
                        <div class="wc-label">Current Price</div>
                        <div class="wc-price ticker-price-${item.ticker}">$${item.currentPrice.toFixed(2)}</div>
                    </div>
                    <div class="wc-change-section ${changeClass} ticker-change-container-${item.ticker}">
                        <div class="wc-label">Daily Change</div>
                        <div class="wc-change ticker-change-${item.ticker}">
                            <span class="wc-icon">${changeIcon}</span>
                            ${item.dailyChangePercent >= 0 ? '+' : ''}${item.dailyChangePercent.toFixed(2)}%
                        </div>
                    </div>
                </div>
                <div class="wc-footer">
                    <button class="btn btn-ghost btn-sm" onclick="switchView('trade'); selectStock('${item.ticker}');">
                        <i class="bi bi-graph-up"></i> Trade
                    </button>
                </div>
            </div>`;
        }).join('')}
    </div>`;
}

// ============================================================
// STOCK SEARCH
// ============================================================

function filterStocks(searchTerm) {

    searchTerm = searchTerm.toLowerCase().trim();

    const stockRows =
        document.querySelectorAll(".stock-row");

    let visibleCount = 0;

    stockRows.forEach(row => {

        const text =
            row.textContent.toLowerCase();

        const match =
            text.includes(searchTerm);

        row.style.display =
            match ? "flex" : "none";

        if(match) visibleCount++;
    });

    const clearBtn =
        document.getElementById(
            "stockSearchClear"
        );

    if(clearBtn){
        clearBtn.classList.toggle(
            "d-none",
            searchTerm.length === 0
        );
    }

    showSearchEmptyState(
        visibleCount === 0 &&
        searchTerm.length > 0
    );
}

function clearStockSearch(){

    const input =
        document.getElementById(
            "stockSearchInput"
        );

    input.value = "";

    filterStocks("");
}

function showSearchEmptyState(show){

    let state =
        document.getElementById(
            "searchEmptyState"
        );

    if(!state){

        state =
            document.createElement("div");

        state.id =
            "searchEmptyState";

        state.className =
            "empty-state";

        state.innerHTML = `
            <i class="bi bi-search"></i>
            <p>No stocks found.</p>
        `;

        document
            .getElementById(
                "stocksListContainer"
            )
            .appendChild(state);
    }

    state.style.display =
        show ? "flex" : "none";
}

function setupSearchShortcuts() {
    document.addEventListener("keydown", e => {
        if (
            e.key === "/" &&
            activeView === "trade"
        ) {
            e.preventDefault();

            document
                .getElementById("stockSearchInput")
                ?.focus();
        }
    });
    const searchInput =
        document.getElementById(
            "stockSearchInput"
        );

    if(searchInput){

        searchInput.addEventListener(
            "input",
            e => {

                clearTimeout(
                    searchDebounceTimer
                );

                searchDebounceTimer =
                    setTimeout(() => {

                        searchGlobalStocks(
                            e.target.value
                        );

                    },300);

            }
        );
    }
}

function quickSearch(symbol){

    document
        .getElementById("stockSearchInput")
        .value = symbol;

    filterStocks(symbol);
}
async function searchGlobalStocks(query){

    if(query.length < 2){
        clearSuggestions();
        return;
    }

    try{

        const response =
            await fetch(
                `/api/market/search?q=${encodeURIComponent(query)}`
            );

        const results =
            await response.json();

        renderSuggestions(results);

    }catch(error){

        console.error(error);
    }
}

function renderSuggestions(results){

    const container =
        document.getElementById("searchSuggestions");

    if(!container) return;

    container.innerHTML =
        results
            .slice(0,8)
            .map(stock => `
                <div
                    class="suggestion-item"
                    onclick="selectSearchStock('${stock.symbol}')">

                    <div class="suggestion-name">
                        ${stock.description}
                    </div>

                    <div class="suggestion-symbol">
                        ${stock.symbol}
                    </div>

                </div>
            `)
            .join('');
}

function selectSearchStock(symbol){

    document.getElementById(
        "stockSearchInput"
    ).value = symbol;

    clearSuggestions();

    selectStock(symbol);
}

function clearSuggestions(){

    const container =
        document.getElementById(
            "searchSuggestions"
        );

    if(container){
        container.innerHTML = "";
    }
}

