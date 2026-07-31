package com.khoi.ewallet.controller;

import com.khoi.ewallet.security.AuthenticatedAccount;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class UserWalletRecipientPreviewTests {
    private static final String TOKEN = "Bearer test-token";

    @BeforeEach
    void authenticate() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        new AuthenticatedAccount(1, "user", "active"), null, List.of()
                )
        );
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void validActiveRecipientReturnsOnlyPhoneAndFullName() {
        ResponseEntity<Map<String, Object>> response = lookup(Map.of(
                "id", 2,
                "phone", "0945141298",
                "role", "user",
                "status", "active",
                "full_name", "Châu Trần Minh Khôi",
                "wallet_id", 22
        ), "0945141298");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(Map.of(
                "phone", "0945141298",
                "fullName", "Châu Trần Minh Khôi"
        ), response.getBody());
        for (String sensitive : List.of("id", "userId", "walletId", "balance", "email", "role")) {
            assertFalse(response.getBody().containsKey(sensitive));
        }
    }

    @Test
    void invalidPhoneFormatIsRejected() {
        ResponseEntity<Map<String, Object>> response = lookup(null, "123");
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("VALIDATION_ERROR", response.getBody().get("code"));
    }

    @Test
    void unknownPhoneUsesNotFoundResponse() {
        ResponseEntity<Map<String, Object>> response = lookup(null, "0945141298");
        assertUnavailable(response);
    }

    @Test
    void blockedRecipientIsUnavailable() {
        assertUnavailable(lookup(recipient(2, "user", "blocked", 22), "0945141298"));
    }

    @Test
    void adminRecipientIsUnavailable() {
        assertUnavailable(lookup(recipient(2, "admin", "active", 22), "0945141298"));
    }

    @Test
    void recipientWithoutWalletIsUnavailable() {
        assertUnavailable(lookup(recipient(2, "user", "active", null), "0945141298"));
    }

    @Test
    void selfTransferLookupIsRejected() {
        ResponseEntity<Map<String, Object>> response =
                lookup(recipient(1, "user", "active", 11), "0945141298");
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("SELF_TRANSFER", response.getBody().get("code"));
    }

    private ResponseEntity<Map<String, Object>> lookup(Map<String, Object> recipient, String phone) {
        return new UserWalletController(new RecipientJdbcTemplate(recipient)).getRecipient(TOKEN, phone);
    }

    private Map<String, Object> recipient(int id, String role, String status, Integer walletId) {
        java.util.HashMap<String, Object> row = new java.util.HashMap<>();
        row.put("id", id);
        row.put("phone", "0945141298");
        row.put("role", role);
        row.put("status", status);
        row.put("full_name", "Recipient Name");
        row.put("wallet_id", walletId);
        return row;
    }

    private void assertUnavailable(ResponseEntity<Map<String, Object>> response) {
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals("RECIPIENT_UNAVAILABLE", response.getBody().get("code"));
    }

    private static final class RecipientJdbcTemplate extends JdbcTemplate {
        private final Map<String, Object> recipient;

        private RecipientJdbcTemplate(Map<String, Object> recipient) {
            this.recipient = recipient;
        }

        @Override
        public List<Map<String, Object>> queryForList(String sql, Object... args) {
            if (sql.contains("SELECT id, role, status")) {
                return List.of(Map.of(
                        "id", 1,
                        "role", "user",
                        "status", "active",
                        "email_verified", true
                ));
            }
            if (sql.contains("LEFT JOIN user_profiles")) {
                return recipient == null ? List.of() : List.of(recipient);
            }
            throw new AssertionError("Unexpected query: " + sql);
        }
    }
}
