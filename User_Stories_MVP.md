# TradeWise MVP User Stories & Prioritization

This document details the user stories for the Minimum Viable Product (MVP) of **TradeWise**, a stock market learning and simulation platform designed for beginners. 

As a Senior Product Manager and Startup Founder, these stories are framed to deliver maximum educational value, engage users through simulation, and reduce the onboarding friction that beginners typically face.

---

## Priority Framework: MoSCoW Method
To ensure a successful launch, features are prioritized into three categories:
*   **Must Have (Core MVP)**: Absolute essentials required to build, test, and release the core value proposition (learn & simulate).
*   **Should Have (High Priority)**: Important features that significantly enhance usability, education, and engagement but can be deferred if launch timelines are threatened.
*   **Nice to Have (Backlog / Post-MVP)**: Delightful features that add gamification or advanced analytics but are not critical for the initial launch.

---

## 1. Registration and Login

### Must Have
*   **User Registration**
    *   *Story*: **As a** new user,  
        **I want to** create an account using my name, email, and a secure password,  
        **So that** I can save my simulation portfolio and track my learning progress.
*   **User Login**
    *   *Story*: **As a** returning user,  
        **I want to** log in securely with my email and password,  
        **So that** I can access my saved virtual portfolio and resume my lessons.

### Should Have
*   **Social Authentication**
    *   *Story*: **As a** mobile-first user,  
        **I want to** sign up or log in using Google or Apple authentication,  
        **So that** I can access the application instantly without creating another password.

---

## 2. Dashboard

### Must Have
*   **Portfolio & Cash Overview**
    *   *Story*: **As a** beginner trader,  
        **I want to** see a clear visual summary of my total portfolio value, virtual cash balance, and overall return percentage on the main dashboard,  
        **So that** I can gauge my trading performance at a single glance.
*   **Intuitive Navigation Hub**
    *   *Story*: **As a** first-time user,  
        **I want a** clean dashboard layout with direct navigation to the simulator, search, and learning modules,  
        **So that** I do not feel overwhelmed by complex menu items.

### Should Have
*   **Market Snapshot (Gainers / Losers)**
    *   *Story*: **As a** learner seeking inspiration,  
        **I want to** view a quick widget showing the top gainers, losers, and active stocks of the day,  
        **So that** I can find immediate ideas for what stocks to research and trade.

---

## 3. Stock Search

### Must Have
*   **Universal Search**
    *   *Story*: **As a** user looking for a specific company,  
        **I want to** search for stocks using either ticker symbols (e.g., "AAPL") or company names (e.g., "Apple"),  
        **So that** I can easily find stocks even if I don't know their stock exchange symbols.

### Should Have
*   **Search Auto-suggestions**
    *   *Story*: **As a** novice investor,  
        **I want to** see real-time suggestions and matching logos as I type in the search bar,  
        **So that** I can quickly locate the correct asset and avoid spelling errors.

### Nice to Have
*   **Category & Sector Filters**
    *   *Story*: **As a** thematic investor,  
        **I want to** filter stocks by categories (e.g., Tech, Healthcare, Green Energy),  
        **So that** I can discover and learn about companies operating within industries I care about.

---

## 4. Stock Details

### Must Have
*   **Price Chart & Real-Time Price**
    *   *Story*: **As a** visual learner,  
        **I want to** view a clean, simple line chart showing the stock's performance over various timeframes (1D, 1W, 1M, 1Y) alongside its current market price,  
        **So that** I can understand the stock's historical price movement.
*   **Simplified Fundamental Data**
    *   *Story*: **As a** student of finance,  
        **I want to** view core metrics (P/E ratio, Market Cap, 52-Week High/Low) with interactive explanations of what they mean,  
        **So that** I can learn how to evaluate a company's financial health.

### Should Have
*   **Candlestick Chart Toggle**
    *   *Story*: **As an** aspiring technical analyst,  
        **I want to** toggle the stock view from a line chart to a candlestick chart,  
        **So that** I can practice reading market sentiment and price action indicators.

---

## 5. Buy Stock (Virtual Money)

### Must Have
*   **Market Buy Order**
    *   *Story*: **As a** simulation trader,  
        **I want to** buy a specific number of shares of a stock at the current market price using my virtual cash,  
        **So that** I can immediately add the asset to my portfolio.
*   **Insufficent Funds Check**
    *   *Story*: **As a** user,  
        **I want** the system to alert me and block the transaction if I try to purchase shares exceeding my virtual cash balance,  
        **So that** I understand the real-world consequence of cash limits.

### Should Have
*   **Limit Buy Order**
    *   *Story*: **As a** strategic trader,  
        **I want to** set a target price for buying a stock (Limit Order) that only executes when the stock drops to that price,  
        **So that** I can learn how professional investors automate their entries.

---

## 6. Sell Stock

### Must Have
*   **Market Sell Order**
    *   *Story*: **As a** simulation trader,  
        **I want to** sell a specific quantity of shares that I currently hold at the market price,  
        **So that** I can lock in my profits or prevent further losses.
*   **Holdings Validation**
    *   *Story*: **As a** user,  
        **I want** the interface to limit my sale quantity to the amount of shares I actually own,  
        **So that** I do not accidentally trigger complex short-selling positions that are too advanced for my level.

### Should Have
*   **Limit Sell Order**
    *   *Story*: **As a** risk-averse trader,  
        **I want to** place a limit order to sell my stock automatically if it hits a specific target price,  
        **So that** I can learn how to lock in gains without watching the screen constantly.

---

## 7. Portfolio Tracking

### Must Have
*   **Active Holdings List**
    *   *Story*: **As an** investor,  
        **I want to** view a detailed table of all my open positions, showing average purchase price, current price, quantity, and total unrealized Profit/Loss (P&L),  
        **So that** I know exactly which investments are performing well and which are losing money.
*   **Historical Portfolio Performance Chart**
    *   *Story*: **As a** user,  
        **I want to** see an equity curve showing how my total portfolio value has changed over time,  
        **So that** I can track my overall progress as an investor.

### Should Have
*   **Asset Allocation & Diversification Chart**
    *   *Story*: **As a** learner,  
        **I want to** see a visual breakdown (like a pie chart) of my holdings by stock and sector,  
        **So that** I can understand my level of diversification and risk concentration.

---

## 8. Transaction History

### Must Have
*   **Chronological Trade Ledger**
    *   *Story*: **As an** analytical trader,  
        **I want to** access a history of all my buy and sell activities, showing trade date, transaction type, share price, quantity, and total cost,  
        **So that** I can audit my trading actions and review my decisions.

### Should Have
*   **History Filters**
    *   *Story*: **As a** user with many trades,  
        **I want to** filter my transaction history by stock ticker, date range, or transaction type (Buy/Sell),  
        **So that** I can quickly locate specific historical trades.

---

## 9. Learning Module

### Must Have
*   **Bite-Sized Concepts & Quizzes**
    *   *Story*: **As a** complete beginner,  
        **I want to** read short, jargon-free modules on investing basics and take a quick quiz at the end of each,  
        **So that** I can build foundational knowledge without getting overwhelmed by reading long textbooks.
*   **Learning Progress Tracker**
    *   *Story*: **As a** student,  
        **I want to** see visual indicators of which learning paths and modules I have completed,  
        **So that** I feel motivated and know where to pick up next.

### Should Have
*   **Actionable Learning Bridge**
    *   *Story*: **As a** practical learner,  
        **I want to** see an option to search or simulate-buy a related stock right after completing a theoretical module (e.g., reading about dividends leads to a list of dividend-paying stocks),  
        **So that** I can immediately put theory into practice.

---

## 10. User Profile

### Must Have
*   **Account Details & Simulation Reset**
    *   *Story*: **As a** learning user,  
        **I want to** view/update my basic account information and have the option to reset my simulation portfolio back to the starting cash (e.g., $100,000 virtual USD),  
        **So that** I can start fresh once I have learned from my early mistakes.

### Should Have
*   **Milestone Achievements**
    *   *Story*: **As a** gamified user,  
        **I want to** earn badges or achievements for completed tasks (e.g., "First Trade Executed," "Perfect Quiz Score," "10% Portfolio Gain"),  
        **So that** my learning journey feels rewarding and engaging.

### Nice to Have
*   **Avatar Customization**
    *   *Story*: **As a** community member,  
        **I want to** customize my profile avatar and username,  
        **So that** my profile page feels personalized.
