package com.khoi.ewallet.controller;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/test")
@Profile({"local", "test"})
public class TestDbController {

    private final JdbcTemplate jdbcTemplate;

    public TestDbController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/ping")
    public Map<String, Object> ping() {
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Backend is running");
        response.put("status", "OK");
        return response;
    }

    @GetMapping("/db")
    public Map<String, Object> testDatabaseConnection() {
        Integer userCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users",
                Integer.class
        );

        Integer walletCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM wallets",
                Integer.class
        );

        Integer transactionCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM transactions",
                Integer.class
        );

        Map<String, Object> response = new HashMap<>();
        response.put("message", "Database connection successfully");
        response.put("users", userCount);
        response.put("wallets", walletCount);
        response.put("transactions", transactionCount);

        return response;
    }

    @GetMapping("/users")
    public List<Map<String, Object>> getUsersWithWallets() {
        String sql = """
                SELECT
                    u.id,
                    u.phone,
                    u.role,
                    u.status,
                    up.full_name,
                    w.id AS wallet_id,
                    w.balance
                FROM users u
                JOIN user_profiles up ON u.id = up.user_id
                JOIN wallets w ON up.user_id = w.user_id
                ORDER BY u.id
                """;

        return jdbcTemplate.queryForList(sql);
    }

    @GetMapping("/transactions")
    public List<Map<String, Object>> getTransactions() {
        String sql = """
                SELECT
                    t.id,
                    t.transaction_code,
                    t.type,
                    sender_user.phone AS sender_phone,
                    receiver_user.phone AS receiver_phone,
                    s.name AS service_name,
                    t.amount,
                    t.balance_before,
                    t.balance_after,
                    t.status,
                    t.description,
                    t.created_at
                FROM transactions t
                LEFT JOIN wallets sender_wallet
                    ON t.sender_wallet_id = sender_wallet.id
                LEFT JOIN users sender_user
                    ON sender_wallet.user_id = sender_user.id
                LEFT JOIN wallets receiver_wallet
                    ON t.receiver_wallet_id = receiver_wallet.id
                LEFT JOIN users receiver_user
                    ON receiver_wallet.user_id = receiver_user.id
                LEFT JOIN services s
                    ON t.service_id = s.id
                ORDER BY t.created_at DESC
                LIMIT 10
                """;

        return jdbcTemplate.queryForList(sql);
    }

    @GetMapping("/transactions/{phone}")
    public List<Map<String, Object>> getTransactionsByPhone(@PathVariable String phone) {
        String sql = """
                SELECT
                    t.id,
                    t.transaction_code,
                    t.type,
                    sender_user.phone AS sender_phone,
                    receiver_user.phone AS receiver_phone,
                    s.name AS service_name,
                    t.amount,
                    t.balance_before,
                    t.balance_after,
                    t.status,
                    t.description,
                    t.created_at
                FROM transactions t
                LEFT JOIN wallets sender_wallet
                    ON t.sender_wallet_id = sender_wallet.id
                LEFT JOIN users sender_user
                    ON sender_wallet.user_id = sender_user.id
                LEFT JOIN wallets receiver_wallet
                    ON t.receiver_wallet_id = receiver_wallet.id
                LEFT JOIN users receiver_user
                    ON receiver_wallet.user_id = receiver_user.id
                LEFT JOIN services s
                    ON t.service_id = s.id
                WHERE sender_user.phone = ?
                   OR receiver_user.phone = ?
                ORDER BY t.created_at DESC
                """;

        return jdbcTemplate.queryForList(sql, phone, phone);
    }
}
