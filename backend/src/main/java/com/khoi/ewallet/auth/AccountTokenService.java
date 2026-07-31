package com.khoi.ewallet.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Service
public class AccountTokenService {
    public static final String EMAIL_VERIFICATION = "EMAIL_VERIFICATION";
    public static final String PASSWORD_RESET = "PASSWORD_RESET";
    private static final Pattern TOKEN_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{43}$");
    private final JdbcTemplate jdbcTemplate;
    private final SecureRandom secureRandom = new SecureRandom();
    private final long verificationMinutes;
    private final long resetMinutes;

    public AccountTokenService(JdbcTemplate jdbcTemplate,
            @Value("${account-token.verification-minutes:1440}") long verificationMinutes,
            @Value("${account-token.reset-minutes:30}") long resetMinutes) {
        this.jdbcTemplate = jdbcTemplate;
        this.verificationMinutes = verificationMinutes;
        this.resetMinutes = resetMinutes;
    }

    public String issue(int userId, String type) {
        jdbcTemplate.update("UPDATE account_tokens SET used_at = CURRENT_TIMESTAMP WHERE user_id = ? AND token_type = ? AND used_at IS NULL", userId, type);
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        long minutes = EMAIL_VERIFICATION.equals(type) ? verificationMinutes : resetMinutes;
        jdbcTemplate.update("INSERT INTO account_tokens (user_id, token_hash, token_type, expires_at) VALUES (?, ?, ?, ?)",
                userId, hash(rawToken), type, LocalDateTime.now().plusMinutes(minutes));
        return rawToken;
    }

    public TokenRecord findUsable(String rawToken, String type) {
        if (rawToken == null || !TOKEN_PATTERN.matcher(rawToken).matches()) return null;
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, user_id, expires_at, used_at FROM account_tokens WHERE token_hash = ? AND token_type = ? LIMIT 1",
                hash(rawToken), type);
        if (rows.isEmpty()) return null;
        Map<String, Object> row = rows.get(0);
        LocalDateTime expiresAt = ((java.sql.Timestamp) row.get("expires_at")).toLocalDateTime();
        if (row.get("used_at") != null || !expiresAt.isAfter(LocalDateTime.now())) return null;
        return new TokenRecord(((Number) row.get("id")).intValue(), ((Number) row.get("user_id")).intValue());
    }

    public boolean markUsed(TokenRecord token) {
        return jdbcTemplate.update("UPDATE account_tokens SET used_at = CURRENT_TIMESTAMP WHERE id = ? AND used_at IS NULL AND expires_at > CURRENT_TIMESTAMP", token.id()) == 1;
    }

    public void invalidateUnused(int userId, String type) {
        jdbcTemplate.update("UPDATE account_tokens SET used_at = CURRENT_TIMESTAMP WHERE user_id = ? AND token_type = ? AND used_at IS NULL", userId, type);
    }

    private String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public record TokenRecord(int id, int userId) {}
}
