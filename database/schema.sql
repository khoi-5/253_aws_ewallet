CREATE DATABASE IF NOT EXISTS ewallet_db
CHARACTER SET utf8mb4
COLLATE utf8mb4_unicode_ci;

USE ewallet_db;

SET NAMES utf8mb4;
SET CHARACTER SET utf8mb4;

SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS transactions;
DROP TABLE IF EXISTS account_tokens;
DROP TABLE IF EXISTS services;
DROP TABLE IF EXISTS wallets;
DROP TABLE IF EXISTS admin_profiles;
DROP TABLE IF EXISTS user_profiles;
DROP TABLE IF EXISTS users;

SET FOREIGN_KEY_CHECKS = 1;

-- =====================================================
-- 1. USERS
-- Bảng tài khoản chung cho user và admin
-- =====================================================

CREATE TABLE users (
    id INT AUTO_INCREMENT PRIMARY KEY,

    phone VARCHAR(10) NOT NULL UNIQUE,
    email VARCHAR(254) NULL UNIQUE,
    email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    password VARCHAR(255) NOT NULL,

    role ENUM('user', 'admin') NOT NULL DEFAULT 'user',
    status ENUM('active', 'blocked') NOT NULL DEFAULT 'active',

    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT chk_users_phone_format
        CHECK (phone REGEXP '^0[0-9]{9}$'),
    CONSTRAINT chk_regular_user_email
        CHECK (role = 'admin' OR email IS NOT NULL)
);

CREATE TABLE account_tokens (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id INT NOT NULL,
    token_hash CHAR(64) NOT NULL UNIQUE,
    token_type ENUM('EMAIL_VERIFICATION', 'PASSWORD_RESET') NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    used_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_account_tokens_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- =====================================================
-- 2. USER PROFILES
-- Thông tin riêng của khách hàng dùng ví
-- =====================================================

CREATE TABLE user_profiles (
    user_id INT PRIMARY KEY,

    full_name VARCHAR(100) NOT NULL,
    date_of_birth DATE NULL,
    address VARCHAR(255) NULL,

    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT fk_user_profiles_user
        FOREIGN KEY (user_id)
        REFERENCES users(id)
        ON DELETE CASCADE
);

-- =====================================================
-- 3. ADMIN PROFILES
-- Thông tin riêng của admin
-- =====================================================

CREATE TABLE admin_profiles (
    user_id INT PRIMARY KEY,

    full_name VARCHAR(100) NOT NULL,
    position VARCHAR(100) NULL,

    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT fk_admin_profiles_user
        FOREIGN KEY (user_id)
        REFERENCES users(id)
        ON DELETE CASCADE
);

-- =====================================================
-- 4. WALLETS
-- Mỗi khách hàng có đúng 1 ví
-- Admin không có ví
-- =====================================================

CREATE TABLE wallets (
    id INT AUTO_INCREMENT PRIMARY KEY,

    user_id INT NOT NULL UNIQUE,
    balance DECIMAL(15,2) NOT NULL DEFAULT 10.00,

    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT fk_wallet_user_profile
        FOREIGN KEY (user_id)
        REFERENCES user_profiles(user_id)
        ON DELETE CASCADE,

    CONSTRAINT chk_wallet_balance
        CHECK (balance >= 0)
);

-- =====================================================
-- 5. SERVICES
-- Dịch vụ ảo để thanh toán
-- =====================================================

CREATE TABLE services (
    id INT AUTO_INCREMENT PRIMARY KEY,

    name VARCHAR(100) NOT NULL,
    price DECIMAL(15,2) NOT NULL,
    description VARCHAR(255) NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,

    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT chk_service_price
        CHECK (price > 0)
);

-- =====================================================
-- 6. TRANSACTIONS
-- Lịch sử giao dịch: deposit, transfer, payment
-- =====================================================

CREATE TABLE transactions (
    id INT AUTO_INCREMENT PRIMARY KEY,

    transaction_code VARCHAR(50) NOT NULL UNIQUE,

    sender_wallet_id INT NULL,
    receiver_wallet_id INT NULL,
    service_id INT NULL,

    amount DECIMAL(15,2) NOT NULL,

    balance_before DECIMAL(15,2) NULL,
    balance_after DECIMAL(15,2) NULL,

    type ENUM('deposit', 'transfer', 'payment') NOT NULL,
    status ENUM('success', 'failed') NOT NULL DEFAULT 'success',

    description VARCHAR(255) NULL,

    created_by INT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_transaction_sender_wallet
        FOREIGN KEY (sender_wallet_id)
        REFERENCES wallets(id)
        ON DELETE SET NULL,

    CONSTRAINT fk_transaction_receiver_wallet
        FOREIGN KEY (receiver_wallet_id)
        REFERENCES wallets(id)
        ON DELETE SET NULL,

    CONSTRAINT fk_transaction_service
        FOREIGN KEY (service_id)
        REFERENCES services(id)
        ON DELETE SET NULL,

    CONSTRAINT fk_transaction_created_by
        FOREIGN KEY (created_by)
        REFERENCES users(id)
        ON DELETE SET NULL,

    CONSTRAINT chk_transaction_amount
        CHECK (amount > 0)
);

-- =====================================================
-- 7. INDEX
-- =====================================================

CREATE INDEX idx_users_role
ON users(role);

CREATE INDEX idx_users_status
    ON users(status);

CREATE INDEX idx_account_tokens_user_type
    ON account_tokens(user_id, token_type, used_at);

CREATE INDEX idx_transactions_sender_wallet
ON transactions(sender_wallet_id);

CREATE INDEX idx_transactions_receiver_wallet
ON transactions(receiver_wallet_id);

CREATE INDEX idx_transactions_service
ON transactions(service_id);

CREATE INDEX idx_transactions_created_by
ON transactions(created_by);

CREATE INDEX idx_transactions_created_at
ON transactions(created_at);

CREATE INDEX idx_transactions_type
ON transactions(type);

-- =====================================================
-- 8. INITIAL DATA
-- =====================================================

-- Password mẫu:
-- user password demo: 123456
-- admin password demo: admin123
-- Passwords are stored as BCrypt hashes.

INSERT INTO users (id, phone, email, email_verified, password, role, status)
VALUES
(1, '0912345678', 'user1@example.local', TRUE, '$2y$10$R8ZcOJMic8eoIIN4.D2Vs.HgbcBpq04/CAZ85x02y.42Tv7qh25Lu', 'user', 'active'),
(2, '0987654321', 'user2@example.local', TRUE, '$2y$10$R8ZcOJMic8eoIIN4.D2Vs.HgbcBpq04/CAZ85x02y.42Tv7qh25Lu', 'user', 'active'),
(3, '0901234567', 'user3@example.local', TRUE, '$2y$10$R8ZcOJMic8eoIIN4.D2Vs.HgbcBpq04/CAZ85x02y.42Tv7qh25Lu', 'user', 'active'),
(4, '0933333333', 'user4@example.local', TRUE, '$2y$10$R8ZcOJMic8eoIIN4.D2Vs.HgbcBpq04/CAZ85x02y.42Tv7qh25Lu', 'user', 'active'),
(5, '0977777777', 'user5@example.local', TRUE, '$2y$10$R8ZcOJMic8eoIIN4.D2Vs.HgbcBpq04/CAZ85x02y.42Tv7qh25Lu', 'user', 'active'),
(6, '0900000000', NULL, TRUE, '$2y$10$X/CrAUyTu.5Sfu5VmtaM/u/jjx2T2Zy/vSNaNVBP0ERTHhX2Wh5dy', 'admin', 'active');

INSERT INTO user_profiles (user_id, full_name, date_of_birth, address)
VALUES
(1, 'Nguyen Van An', '2002-03-15', 'Quan 1, TP.HCM'),
(2, 'Tran Thi Binh', '2001-07-22', 'Quan 3, TP.HCM'),
(3, 'Le Minh Cuong', '2003-01-10', 'Quan 7, TP.HCM'),
(4, 'Pham Hoang Dung', '2002-11-05', 'Thu Duc, TP.HCM'),
(5, 'Vo Gia Han', '2001-09-18', 'Binh Thanh, TP.HCM');

INSERT INTO admin_profiles (user_id, full_name, position)
VALUES
(6, 'System Admin', 'Administrator');

-- Số dư cuối cùng đã được tính dựa trên các giao dịch mẫu bên dưới.
-- Mỗi user ban đầu xem như có 10 đồng khi tạo ví.

INSERT INTO wallets (id, user_id, balance)
VALUES
(1, 1, 105.00),
(2, 2, 55.00),
(3, 3, 105.00),
(4, 4, 100.00),
(5, 5, 40.00);

INSERT INTO services (id, name, price, description, is_active)
VALUES
(1, 'Mua thẻ điện thoại', 10.00, 'Thanh toán mô phỏng dịch vụ thẻ điện thoại', TRUE),
(2, 'Thanh toán tiền điện', 50.00, 'Thanh toán mô phỏng hóa đơn điện', TRUE),
(3, 'Thanh toán tiền nước', 30.00, 'Thanh toán mô phỏng hóa đơn nước', TRUE),
(4, 'Mua gói Internet', 100.00, 'Thanh toán mô phỏng gói Internet', TRUE),
(5, 'Nạp game', 20.00, 'Thanh toán mô phỏng dịch vụ nạp game', TRUE);

-- =====================================================
-- 9. OLD TRANSACTIONS
-- Mỗi khách hàng có 2 đến 3 giao dịch cũ
-- =====================================================

-- USER 1: Nguyen Van An
-- Ban đầu 10
-- Deposit +100 => 110
-- Transfer cho user 2 -15 => 95
-- Nhận từ user 5 +10 => 105

INSERT INTO transactions (
    transaction_code,
    sender_wallet_id,
    receiver_wallet_id,
    service_id,
    amount,
    balance_before,
    balance_after,
    type,
    status,
    description,
    created_by,
    created_at
)
VALUES
(
    'TXN202606010001',
    NULL,
    1,
    NULL,
    100.00,
    10.00,
    110.00,
    'deposit',
    'success',
    'Mock deposit to wallet',
    1,
    '2026-06-01 09:00:00'
),
(
    'TXN202606010002',
    1,
    2,
    NULL,
    15.00,
    110.00,
    95.00,
    'transfer',
    'success',
    'Transfer money from Nguyen Van An to Tran Thi Binh',
    1,
    '2026-06-01 10:30:00'
);

-- USER 2: Tran Thi Binh
-- Ban đầu 10
-- Nhận user 1 +15 => 25
-- Deposit +60 => 85
-- Payment tiền nước -30 => 55

INSERT INTO transactions (
    transaction_code,
    sender_wallet_id,
    receiver_wallet_id,
    service_id,
    amount,
    balance_before,
    balance_after,
    type,
    status,
    description,
    created_by,
    created_at
)
VALUES
(
    'TXN202606020001',
    NULL,
    2,
    NULL,
    60.00,
    25.00,
    85.00,
    'deposit',
    'success',
    'Mock deposit to wallet',
    2,
    '2026-06-02 08:45:00'
),
(
    'TXN202606020002',
    2,
    NULL,
    3,
    30.00,
    85.00,
    55.00,
    'payment',
    'success',
    'Payment for water bill',
    2,
    '2026-06-02 13:15:00'
);

-- USER 3: Le Minh Cuong
-- Ban đầu 10
-- Deposit +150 => 160
-- Payment tiền điện -50 => 110
-- Transfer cho user 4 -5 => 105

INSERT INTO transactions (
    transaction_code,
    sender_wallet_id,
    receiver_wallet_id,
    service_id,
    amount,
    balance_before,
    balance_after,
    type,
    status,
    description,
    created_by,
    created_at
)
VALUES
(
    'TXN202606030001',
    NULL,
    3,
    NULL,
    150.00,
    10.00,
    160.00,
    'deposit',
    'success',
    'Mock deposit to wallet',
    3,
    '2026-06-03 09:20:00'
),
(
    'TXN202606030002',
    3,
    NULL,
    2,
    50.00,
    160.00,
    110.00,
    'payment',
    'success',
    'Payment for electricity bill',
    3,
    '2026-06-03 11:00:00'
),
(
    'TXN202606030003',
    3,
    4,
    NULL,
    5.00,
    110.00,
    105.00,
    'transfer',
    'success',
    'Transfer money from Le Minh Cuong to Pham Hoang Dung',
    3,
    '2026-06-03 15:40:00'
);

-- USER 4: Pham Hoang Dung
-- Ban đầu 10
-- Nhận user 3 +5 => 15
-- Deposit +100 => 115
-- Payment thẻ điện thoại -10 => 105
-- Transfer cho user 5 -5 => 100

INSERT INTO transactions (
    transaction_code,
    sender_wallet_id,
    receiver_wallet_id,
    service_id,
    amount,
    balance_before,
    balance_after,
    type,
    status,
    description,
    created_by,
    created_at
)
VALUES
(
    'TXN202606040001',
    NULL,
    4,
    NULL,
    100.00,
    15.00,
    115.00,
    'deposit',
    'success',
    'Mock deposit to wallet',
    4,
    '2026-06-04 10:00:00'
),
(
    'TXN202606040002',
    4,
    NULL,
    1,
    10.00,
    115.00,
    105.00,
    'payment',
    'success',
    'Payment for phone card',
    4,
    '2026-06-04 12:10:00'
),
(
    'TXN202606040003',
    4,
    5,
    NULL,
    5.00,
    105.00,
    100.00,
    'transfer',
    'success',
    'Transfer money from Pham Hoang Dung to Vo Gia Han',
    4,
    '2026-06-04 16:25:00'
);

-- USER 5: Vo Gia Han
-- Ban đầu 10
-- Nhận user 4 +5 => 15
-- Deposit +50 => 65
-- Payment nạp game -20 => 45
-- Transfer cho user 1 -10 => 35
-- Deposit +5 => 40

INSERT INTO transactions (
    transaction_code,
    sender_wallet_id,
    receiver_wallet_id,
    service_id,
    amount,
    balance_before,
    balance_after,
    type,
    status,
    description,
    created_by,
    created_at
)
VALUES
(
    'TXN202606050001',
    NULL,
    5,
    NULL,
    50.00,
    15.00,
    65.00,
    'deposit',
    'success',
    'Mock deposit to wallet',
    5,
    '2026-06-05 08:30:00'
),
(
    'TXN202606050002',
    5,
    NULL,
    5,
    20.00,
    65.00,
    45.00,
    'payment',
    'success',
    'Payment for game top-up',
    5,
    '2026-06-05 11:50:00'
),
(
    'TXN202606050003',
    5,
    1,
    NULL,
    10.00,
    45.00,
    35.00,
    'transfer',
    'success',
    'Transfer money from Vo Gia Han to Nguyen Van An',
    5,
    '2026-06-05 17:00:00'
),
(
    'TXN202606050004',
    NULL,
    5,
    NULL,
    5.00,
    35.00,
    40.00,
    'deposit',
    'success',
    'Small mock deposit to wallet',
    5,
    '2026-06-05 18:30:00'
);
