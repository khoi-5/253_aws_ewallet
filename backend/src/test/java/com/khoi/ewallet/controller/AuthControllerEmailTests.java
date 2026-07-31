package com.khoi.ewallet.controller;

import com.khoi.ewallet.auth.AccountTokenService;
import com.khoi.ewallet.auth.EmailService;
import com.khoi.ewallet.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.support.KeyHolder;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import org.mockito.ArgumentCaptor;

class AuthControllerEmailTests {
    JdbcTemplate jdbc;
    AccountTokenService tokens;
    EmailService email;
    AuthController controller;

    @BeforeEach void setUp() {
        jdbc = mock(JdbcTemplate.class);
        tokens = mock(AccountTokenService.class);
        email = mock(EmailService.class);
        controller = new AuthController(jdbc, mock(JwtService.class), tokens, email, "http://localhost:5173");
    }

    @Test void registrationRequiresEmail() {
        ResponseEntity<Map<String, Object>> response = controller.register(
                new AuthController.RegisterRequest("0912345678", null, "password", "Test User"));
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("A valid email is required", response.getBody().get("message"));
        verifyNoInteractions(tokens, email);
    }

    @Test void duplicateEmailIsRejectedCaseInsensitively() {
        when(jdbc.queryForObject(contains("phone"), eq(Integer.class), any())).thenReturn(0);
        when(jdbc.queryForObject(contains("LOWER(email)"), eq(Integer.class), eq("user@example.com"))).thenReturn(1);
        ResponseEntity<Map<String, Object>> response = controller.register(
                new AuthController.RegisterRequest("0912345678", " User@Example.COM ", "password", "Test User"));
        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("Email already exists", response.getBody().get("message"));
    }

    @Test void newAccountIsUnverifiedAndReceivesVerificationLink() {
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any())).thenReturn(0);
        when(jdbc.update(any(PreparedStatementCreator.class), any(KeyHolder.class))).thenAnswer(invocation -> {
            KeyHolder holder = invocation.getArgument(1);
            holder.getKeyList().add(Map.of("GENERATED_KEY", 7));
            return 1;
        });
        when(jdbc.queryForMap(contains("WHERE u.phone"), any(Object[].class))).thenReturn(Map.of(
                "id", 7, "phone", "0912345678", "email", "user@example.com", "email_verified", false,
                "role", "user", "status", "active", "full_name", "Test User"));
        when(jdbc.queryForList(contains("FROM wallets"), any(Object[].class))).thenReturn(List.of(Map.of(
                "id", 11, "user_id", 7, "balance", 10)));
        when(tokens.issue(7, AccountTokenService.EMAIL_VERIFICATION)).thenReturn("verification-token");

        ResponseEntity<Map<String, Object>> response = controller.register(
                new AuthController.RegisterRequest("0912345678", " USER@EXAMPLE.COM ", "password", "Test User"));

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        @SuppressWarnings("unchecked") Map<String, Object> user = (Map<String, Object>) response.getBody().get("user");
        assertEquals(false, user.get("emailVerified"));
        assertEquals("user@example.com", user.get("email"));
        verify(email).sendVerificationEmail(eq("user@example.com"), contains("verification-token"));
    }

    @Test void unverifiedUserCanLoginAndVerificationStateIsReturned() {
        when(jdbc.queryForList(contains("u.password"), any(Object[].class))).thenReturn(List.of(Map.of(
                "id", 7, "phone", "0912345678", "email", "user@example.com", "email_verified", false,
                "password", new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode("password"),
                "role", "user", "status", "active", "full_name", "Test User")));
        when(jdbc.queryForList(contains("FROM wallets"), any(Object[].class))).thenReturn(List.of());
        ResponseEntity<Map<String, Object>> response = controller.login(new AuthController.LoginRequest("0912345678", "password"));
        assertEquals(HttpStatus.OK, response.getStatusCode());
        @SuppressWarnings("unchecked") Map<String, Object> user = (Map<String, Object>) response.getBody().get("user");
        assertEquals(false, user.get("emailVerified"));
    }

    @Test void validVerificationTokenVerifiesAccount() {
        AccountTokenService.TokenRecord record = new AccountTokenService.TokenRecord(3, 7);
        when(tokens.findUsable("valid-token", AccountTokenService.EMAIL_VERIFICATION)).thenReturn(record);
        when(tokens.markUsed(record)).thenReturn(true);
        ResponseEntity<Map<String, Object>> response = controller.verifyEmail(new AuthController.TokenRequest("valid-token"));
        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(jdbc).update(contains("email_verified = TRUE"), eq(7));
        verify(tokens).invalidateUnused(7, AccountTokenService.EMAIL_VERIFICATION);
    }

    @Test void expiredVerificationTokenIsRejected() {
        assertInvalidVerification("expired-token");
    }

    @Test void usedVerificationTokenIsRejected() {
        assertInvalidVerification("used-token");
    }

    @Test void resendIssuesReplacementVerificationToken() {
        when(jdbc.queryForList(contains("LOWER(email)"), any(Object[].class)))
                .thenReturn(List.of(Map.of("id", 7, "email_verified", false)));
        when(tokens.issue(7, AccountTokenService.EMAIL_VERIFICATION)).thenReturn("replacement");
        ResponseEntity<Map<String, Object>> response = controller.resendVerification(new AuthController.EmailRequest(" USER@EXAMPLE.COM "));
        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(tokens).issue(7, AccountTokenService.EMAIL_VERIFICATION);
        verify(email).sendVerificationEmail(eq("user@example.com"), contains("replacement"));
    }

    @Test void forgotPasswordNeverRevealsWhetherEmailExists() {
        when(jdbc.queryForList(contains("LOWER(email)"), any(Object[].class))).thenReturn(List.of());
        String missing = String.valueOf(controller.forgotPassword(new AuthController.EmailRequest("missing@example.com")).getBody().get("message"));
        when(jdbc.queryForList(contains("LOWER(email)"), any(Object[].class))).thenReturn(List.of(Map.of("id", 7)));
        when(tokens.issue(7, AccountTokenService.PASSWORD_RESET)).thenReturn("reset-token");
        String existing = String.valueOf(controller.forgotPassword(new AuthController.EmailRequest("user@example.com")).getBody().get("message"));
        assertEquals(missing, existing);
    }

    @Test void validPasswordResetHashesPasswordAndConsumesToken() {
        AccountTokenService.TokenRecord record = new AccountTokenService.TokenRecord(9, 7);
        when(tokens.findUsable("reset-token", AccountTokenService.PASSWORD_RESET)).thenReturn(record);
        when(tokens.markUsed(record)).thenReturn(true);
        ResponseEntity<Map<String, Object>> response = controller.resetPassword(
                new AuthController.ResetPasswordRequest("reset-token", "new-password", "new-password"));
        assertEquals(HttpStatus.OK, response.getStatusCode());
        ArgumentCaptor<Object> passwordHash = ArgumentCaptor.forClass(Object.class);
        verify(jdbc).update(contains("SET password"), passwordHash.capture(), eq(7));
        org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder encoder =
                new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder();
        assertTrue(encoder.matches("new-password", String.valueOf(passwordHash.getValue())));
        assertFalse(encoder.matches("old-password", String.valueOf(passwordHash.getValue())));
        verify(tokens).invalidateUnused(7, AccountTokenService.PASSWORD_RESET);
    }

    @Test void expiredAndUsedResetTokensAreRejected() {
        assertEquals(HttpStatus.BAD_REQUEST, controller.resetPassword(
                new AuthController.ResetPasswordRequest("expired", "new-password", "new-password")).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, controller.resetPassword(
                new AuthController.ResetPasswordRequest("used", "new-password", "new-password")).getStatusCode());
    }

    private void assertInvalidVerification(String token) {
        ResponseEntity<Map<String, Object>> response = controller.verifyEmail(new AuthController.TokenRequest(token));
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        verify(jdbc, never()).update(contains("email_verified"), any(Object[].class));
    }
}
