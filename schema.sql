-- Seedling Platform Database Schema Reference (PostgreSQL)

-- Drop tables if they exist for clean recreate
DROP TABLE IF EXISTS positions CASCADE;
DROP TABLE IF EXISTS trades CASCADE;
DROP TABLE IF EXISTS stocks CASCADE;
DROP TABLE IF EXISTS users CASCADE;

-- 1. Users Table
CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(50) UNIQUE NOT NULL,
    password VARCHAR(255) NOT NULL,
    balance DECIMAL(15,2) DEFAULT 10000.00
);

-- 2. Stocks Table (Curated list of 10 stocks)
CREATE TABLE stocks (
    ticker VARCHAR(10) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    current_price DECIMAL(12,2) NOT NULL,
    last_price DECIMAL(12,2) NOT NULL
);

-- 3. Positions Table (Active holdings of users)
CREATE TABLE positions (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT REFERENCES users(id) ON DELETE CASCADE,
    ticker VARCHAR(10) REFERENCES stocks(ticker),
    quantity DECIMAL(12,4) NOT NULL,
    average_buy_price DECIMAL(12,2) NOT NULL,
    stop_loss_price DECIMAL(12,2)
);

-- 4. Trades Table (Transaction history ledger)
CREATE TABLE trades (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT REFERENCES users(id) ON DELETE CASCADE,
    ticker VARCHAR(10),
    trade_type VARCHAR(10) NOT NULL, -- 'BUY' or 'SELL' (including stop-loss executions)
    quantity DECIMAL(12,4) NOT NULL,
    price DECIMAL(12,2) NOT NULL,
    stop_loss_price DECIMAL(12,2),
    timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 5. Watchlist Table (User's saved/starred stocks)
DROP TABLE IF EXISTS watchlist CASCADE;
CREATE TABLE watchlist (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT REFERENCES users(id) ON DELETE CASCADE,
    ticker VARCHAR(10) REFERENCES stocks(ticker),
    added_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(user_id, ticker)
);
