package com.khoi.ewallet.auth;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AccountTokenServiceTests {
    @Test void issuingTokenInvalidatesPreviousUnusedTokenAndStoresOnlyHash() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        AccountTokenService service = new AccountTokenService(jdbc, 60, 30);
        String raw = service.issue(7, AccountTokenService.EMAIL_VERIFICATION);
        assertEquals(43, raw.length());
        verify(jdbc).update(contains("SET used_at"), eq(7), eq(AccountTokenService.EMAIL_VERIFICATION));
        verify(jdbc).update(contains("INSERT INTO account_tokens"), eq(7), argThat(hash ->
                hash instanceof String && ((String) hash).length() == 64 && !hash.equals(raw)),
                eq(AccountTokenService.EMAIL_VERIFICATION), any(LocalDateTime.class));
    }

    @Test void usableTokenMustBeUnexpiredAndUnused() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        AccountTokenService service = new AccountTokenService(jdbc, 60, 30);
        String token = "A".repeat(43);
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(Map.of(
                "id", 4, "user_id", 7, "expires_at", Timestamp.valueOf(LocalDateTime.now().plusMinutes(5)))));
        assertEquals(7, service.findUsable(token, AccountTokenService.PASSWORD_RESET).userId());
    }

    @Test void expiredTokenIsRejected() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        AccountTokenService service = new AccountTokenService(jdbc, 60, 30);
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(Map.of(
                "id", 4, "user_id", 7, "expires_at", Timestamp.valueOf(LocalDateTime.now().minusMinutes(1)))));
        assertNull(service.findUsable("A".repeat(43), AccountTokenService.PASSWORD_RESET));
    }

    @Test void malformedTokenIsRejectedWithoutDatabaseLookup() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        AccountTokenService service = new AccountTokenService(jdbc, 60, 30);
        assertNull(service.findUsable("not valid", AccountTokenService.EMAIL_VERIFICATION));
        verifyNoInteractions(jdbc);
    }
}
