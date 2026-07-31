package com.khoi.ewallet.controller;

import com.khoi.ewallet.auth.AccountTokenService;
import com.khoi.ewallet.auth.EmailService;
import com.khoi.ewallet.security.JwtService;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Locale;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Pattern PHONE_PATTERN = Pattern.compile("^0[0-9]{9}$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final BigDecimal INITIAL_BALANCE = new BigDecimal("10.00");

    private final JdbcTemplate jdbcTemplate;
    private final JwtService jwtService;
    private final AccountTokenService accountTokenService;
    private final EmailService emailService;
    private final String frontendBaseUrl;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public AuthController(JdbcTemplate jdbcTemplate, JwtService jwtService,
            AccountTokenService accountTokenService, EmailService emailService,
            @Value("${frontend.base-url:http://localhost:5173}") String frontendBaseUrl) {
        this.jdbcTemplate = jdbcTemplate;
        this.jwtService = jwtService;
        this.accountTokenService = accountTokenService;
        this.emailService = emailService;
        this.frontendBaseUrl = frontendBaseUrl.replaceAll("/+$", "");
    }

    @PostMapping("/register")
    @Transactional
    public ResponseEntity<Map<String, Object>> register(@RequestBody RegisterRequest request) {
        String phone = request.phone() == null ? "" : request.phone().trim();
        String email = normalizeEmail(request.email());
        String password = request.password() == null ? "" : request.password();
        String fullName = request.fullName() == null ? "" : request.fullName().trim();

        String validationError = validateRegisterRequest(phone, email, password, fullName);
        if (validationError != null) {
            return error(validationError, HttpStatus.BAD_REQUEST);
        }

        Integer existingUsers = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE phone = ?",
                Integer.class,
                phone
        );

        if (existingUsers != null && existingUsers > 0) {
            return error("Phone already exists", HttpStatus.CONFLICT);
        }

        Integer existingEmails = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE LOWER(email) = ?", Integer.class, email);
        if (existingEmails != null && existingEmails > 0) {
            return error("Email already exists", HttpStatus.CONFLICT);
        }

        try {
            String passwordHash = passwordEncoder.encode(password);
            KeyHolder userKeyHolder = new GeneratedKeyHolder();
            jdbcTemplate.update(connection -> {
                PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO users (phone, email, email_verified, password, role, status) VALUES (?, ?, FALSE, ?, 'user', 'active')",
                        Statement.RETURN_GENERATED_KEYS
                );
                statement.setString(1, phone);
                statement.setString(2, email);
                statement.setString(3, passwordHash);
                return statement;
            }, userKeyHolder);

            int userId = Objects.requireNonNull(userKeyHolder.getKey()).intValue();

            jdbcTemplate.update(
                    "INSERT INTO user_profiles (user_id, full_name) VALUES (?, ?)",
                    userId,
                    fullName
            );

            String verificationToken = accountTokenService.issue(userId, AccountTokenService.EMAIL_VERIFICATION);
            emailService.sendVerificationEmail(email, frontendBaseUrl + "/verify-email?token=" + verificationToken);

            jdbcTemplate.update(
                    "INSERT INTO wallets (user_id, balance) VALUES (?, ?)",
                    userId,
                    INITIAL_BALANCE
            );

            Map<String, Object> user = findUserByPhone(phone);
            Map<String, Object> wallet = findWalletByUserId(userId);

            Map<String, Object> response = new HashMap<>();
            response.put("message", "Registration successful. Please verify your email.");
            addToken(response, userId);
            response.put("user", user);
            response.put("wallet", wallet);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (DuplicateKeyException exception) {
            return error("Phone or email already exists", HttpStatus.CONFLICT);
        }
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody LoginRequest request) {
        String phone = request.phone() == null ? "" : request.phone().trim();
        String password = request.password() == null ? "" : request.password();

        if (!PHONE_PATTERN.matcher(phone).matches()) {
            return error("Phone must match ^0[0-9]{9}$", HttpStatus.BAD_REQUEST);
        }

        if (password.isBlank()) {
            return error("Password is required", HttpStatus.BAD_REQUEST);
        }

        List<Map<String, Object>> users = jdbcTemplate.queryForList(
                """
                SELECT
                    u.id,
                    u.phone,
                    u.email,
                    u.email_verified,
                    u.password,
                    u.role,
                    u.status,
                    COALESCE(up.full_name, ap.full_name) AS full_name,
                    ap.position
                FROM users u
                LEFT JOIN user_profiles up ON u.id = up.user_id
                LEFT JOIN admin_profiles ap ON u.id = ap.user_id
                WHERE u.phone = ?
                LIMIT 1
                """,
                phone
        );

        if (users.isEmpty()) {
            return error("Invalid phone or password", HttpStatus.UNAUTHORIZED);
        }

        Map<String, Object> userRow = users.get(0);
        String storedHash = String.valueOf(userRow.get("password"));
        if (!passwordEncoder.matches(password, storedHash)) {
            return error("Invalid phone or password", HttpStatus.UNAUTHORIZED);
        }

        if ("blocked".equalsIgnoreCase(String.valueOf(userRow.get("status")))) {
            return error("User is blocked", HttpStatus.FORBIDDEN);
        }

        int userId = ((Number) userRow.get("id")).intValue();
        Map<String, Object> user = publicUser(userRow);
        Map<String, Object> wallet = findWalletByUserId(userId);

        Map<String, Object> response = new HashMap<>();
        response.put("message", "Login successfully");
        addToken(response, userId);
        response.put("user", user);
        response.put("wallet", wallet);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/verify-email")
    @Transactional
    public ResponseEntity<Map<String, Object>> verifyEmail(@RequestBody TokenRequest request) {
        AccountTokenService.TokenRecord token = accountTokenService.findUsable(
                request == null ? null : request.token(), AccountTokenService.EMAIL_VERIFICATION);
        if (token == null) return error("Verification token is invalid, expired, or already used", HttpStatus.BAD_REQUEST);
        if (!accountTokenService.markUsed(token)) return error("Verification token is invalid, expired, or already used", HttpStatus.BAD_REQUEST);
        jdbcTemplate.update("UPDATE users SET email_verified = TRUE WHERE id = ? AND role = 'user'", token.userId());
        accountTokenService.invalidateUnused(token.userId(), AccountTokenService.EMAIL_VERIFICATION);
        return success("Email verified successfully");
    }

    @PostMapping("/resend-verification")
    @Transactional
    public ResponseEntity<Map<String, Object>> resendVerification(@RequestBody EmailRequest request) {
        String email = normalizeEmail(request == null ? null : request.email());
        if (EMAIL_PATTERN.matcher(email).matches()) {
            List<Map<String, Object>> users = jdbcTemplate.queryForList(
                    "SELECT id, email_verified FROM users WHERE LOWER(email) = ? AND role = 'user' AND status = 'active' LIMIT 1", email);
            if (!users.isEmpty() && !isTruthy(users.get(0).get("email_verified"))) {
                int userId = ((Number) users.get(0).get("id")).intValue();
                String token = accountTokenService.issue(userId, AccountTokenService.EMAIL_VERIFICATION);
                emailService.sendVerificationEmail(email, frontendBaseUrl + "/verify-email?token=" + token);
            }
        }
        return success("If the account is eligible, a verification link has been generated.");
    }

    @PostMapping("/forgot-password")
    @Transactional
    public ResponseEntity<Map<String, Object>> forgotPassword(@RequestBody EmailRequest request) {
        String email = normalizeEmail(request == null ? null : request.email());
        if (EMAIL_PATTERN.matcher(email).matches()) {
            List<Map<String, Object>> users = jdbcTemplate.queryForList(
                    "SELECT id FROM users WHERE LOWER(email) = ? AND role = 'user' AND status = 'active' LIMIT 1", email);
            if (!users.isEmpty()) {
                int userId = ((Number) users.get(0).get("id")).intValue();
                String token = accountTokenService.issue(userId, AccountTokenService.PASSWORD_RESET);
                emailService.sendPasswordResetEmail(email, frontendBaseUrl + "/reset-password?token=" + token);
            }
        }
        return success("If an eligible account exists, a password-reset link has been generated.");
    }

    @PostMapping("/reset-password")
    @Transactional
    public ResponseEntity<Map<String, Object>> resetPassword(@RequestBody ResetPasswordRequest request) {
        if (request == null || request.password() == null || request.password().length() < 6) {
            return error("Password must be at least 6 characters", HttpStatus.BAD_REQUEST);
        }
        if (!request.password().equals(request.passwordConfirmation())) {
            return error("Password confirmation does not match", HttpStatus.BAD_REQUEST);
        }
        AccountTokenService.TokenRecord token = accountTokenService.findUsable(request.token(), AccountTokenService.PASSWORD_RESET);
        if (token == null) return error("Password-reset token is invalid, expired, or already used", HttpStatus.BAD_REQUEST);
        if (!accountTokenService.markUsed(token)) return error("Password-reset token is invalid, expired, or already used", HttpStatus.BAD_REQUEST);
        jdbcTemplate.update("UPDATE users SET password = ? WHERE id = ? AND role = 'user'", passwordEncoder.encode(request.password()), token.userId());
        accountTokenService.invalidateUnused(token.userId(), AccountTokenService.PASSWORD_RESET);
        return success("Password reset successfully. Please log in with your new password.");
    }

    private String validateRegisterRequest(String phone, String email, String password, String fullName) {
        if (!PHONE_PATTERN.matcher(phone).matches()) {
            return "Phone must match ^0[0-9]{9}$";
        }

        if (!EMAIL_PATTERN.matcher(email).matches() || email.length() > 254) {
            return "A valid email is required";
        }

        if (password.length() < 6) {
            return "Password must be at least 6 characters";
        }

        if (fullName.isBlank()) {
            return "Full name is required";
        }

        return null;
    }

    private Map<String, Object> findUserByPhone(String phone) {
        Map<String, Object> userRow = jdbcTemplate.queryForMap(
                """
                SELECT
                    u.id,
                    u.phone,
                    u.email,
                    u.email_verified,
                    u.role,
                    u.status,
                    up.full_name
                FROM users u
                LEFT JOIN user_profiles up ON u.id = up.user_id
                WHERE u.phone = ?
                """,
                phone
        );

        return publicUser(userRow);
    }

    private Map<String, Object> publicUser(Map<String, Object> userRow) {
        Map<String, Object> user = new HashMap<>();
        user.put("id", userRow.get("id"));
        user.put("phone", userRow.get("phone"));
        user.put("email", userRow.get("email"));
        user.put("emailVerified", "admin".equalsIgnoreCase(String.valueOf(userRow.get("role"))) || isTruthy(userRow.get("email_verified")));
        user.put("role", userRow.get("role"));
        user.put("status", userRow.get("status"));
        user.put("fullName", userRow.get("full_name"));
        if ("admin".equalsIgnoreCase(String.valueOf(userRow.get("role")))) {
            user.put("position", userRow.get("position"));
        }
        return user;
    }

    private Map<String, Object> findWalletByUserId(int userId) {
        List<Map<String, Object>> wallets = jdbcTemplate.queryForList(
                "SELECT id, user_id, balance FROM wallets WHERE user_id = ? LIMIT 1",
                userId
        );

        if (wallets.isEmpty()) {
            return null;
        }

        Map<String, Object> walletRow = wallets.get(0);
        Map<String, Object> wallet = new HashMap<>();
        wallet.put("id", walletRow.get("id"));
        wallet.put("userId", walletRow.get("user_id"));
        wallet.put("balance", walletRow.get("balance"));
        return wallet;
    }

    private void addToken(Map<String, Object> response, int userId) {
        String token = jwtService.generateAccessToken(userId);
        response.put("token", token);
        response.put("accessToken", token);
        response.put("tokenType", "Bearer");
        response.put("expiresIn", jwtService.getExpirationSeconds());
    }

    private ResponseEntity<Map<String, Object>> error(String message, HttpStatus status) {
        Map<String, Object> response = new HashMap<>();
        response.put("message", message);
        return ResponseEntity.status(status).body(response);
    }

    private ResponseEntity<Map<String, Object>> success(String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("message", message);
        return ResponseEntity.ok(response);
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private boolean isTruthy(Object value) {
        return Boolean.TRUE.equals(value) || (value instanceof Number number && number.intValue() != 0);
    }

    public record RegisterRequest(String phone, String email, String password, String fullName) {
    }

    public record LoginRequest(String phone, String password) {
    }

    public record TokenRequest(String token) {}
    public record EmailRequest(String email) {}
    public record ResetPasswordRequest(String token, String password, String passwordConfirmation) {}
}
