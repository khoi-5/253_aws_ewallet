package com.khoi.ewallet.controller;

import com.khoi.ewallet.security.SecurityAccount;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/account")
public class AccountController {

    private final JdbcTemplate jdbcTemplate;

    public AccountController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> getCurrentAccount(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader
    ) {
        Integer accountId = extractUserId(authorizationHeader);
        if (accountId == null) {
            return error("UNAUTHORIZED", "Unauthorized", HttpStatus.UNAUTHORIZED);
        }

        List<Map<String, Object>> accounts = jdbcTemplate.queryForList(
                """
                SELECT
                    u.id,
                    u.phone,
                    u.email,
                    u.email_verified,
                    u.role,
                    u.status,
                    COALESCE(up.full_name, ap.full_name) AS full_name,
                    up.date_of_birth,
                    up.address,
                    ap.position,
                    w.id AS wallet_id,
                    w.balance
                FROM users u
                LEFT JOIN user_profiles up ON u.id = up.user_id
                LEFT JOIN admin_profiles ap ON u.id = ap.user_id
                LEFT JOIN wallets w ON u.id = w.user_id
                WHERE u.id = ?
                LIMIT 1
                """,
                accountId
        );

        if (accounts.isEmpty()) {
            return error("UNAUTHORIZED", "Unauthorized", HttpStatus.UNAUTHORIZED);
        }

        Map<String, Object> account = accounts.get(0);
        if ("blocked".equalsIgnoreCase(String.valueOf(account.get("status")))) {
            return error(
                    "ACCOUNT_BLOCKED",
                    "Your account has been blocked by an administrator.",
                    HttpStatus.FORBIDDEN
            );
        }

        if (!"active".equalsIgnoreCase(String.valueOf(account.get("status")))) {
            return error("UNAUTHORIZED", "Unauthorized", HttpStatus.UNAUTHORIZED);
        }

        return ResponseEntity.ok(buildAccount(account));
    }

    @PatchMapping("/me")
    @Transactional
    public ResponseEntity<Map<String, Object>> updateCurrentAccount(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @RequestBody ProfileUpdateRequest request
    ) {
        AccountAuthResult authResult = authenticate(authorizationHeader);
        if (authResult.errorResponse() != null) {
            return authResult.errorResponse();
        }

        Map<String, Object> account = authResult.account();
        int accountId = ((Number) account.get("id")).intValue();
        String role = String.valueOf(account.get("role"));

        String fullName = request == null || request.fullName() == null
                ? ""
                : request.fullName().trim();
        String fullNameError = validateFullName(fullName);
        if (fullNameError != null) {
            return error("VALIDATION_ERROR", fullNameError, HttpStatus.BAD_REQUEST);
        }

        if ("user".equalsIgnoreCase(role)) {
            Integer profileCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM user_profiles WHERE user_id = ?",
                    Integer.class,
                    accountId
            );
            if (profileCount == null || profileCount == 0) {
                return error("PROFILE_NOT_FOUND", "Profile not found", HttpStatus.NOT_FOUND);
            }

            LocalDate dateOfBirth = null;
            if (request != null && request.dateOfBirth() != null && !request.dateOfBirth().trim().isEmpty()) {
                try {
                    dateOfBirth = LocalDate.parse(request.dateOfBirth().trim());
                } catch (DateTimeParseException exception) {
                    return error("VALIDATION_ERROR", "Date of birth must be a valid ISO date", HttpStatus.BAD_REQUEST);
                }

                if (dateOfBirth.isAfter(LocalDate.now())) {
                    return error("VALIDATION_ERROR", "Date of birth must not be in the future", HttpStatus.BAD_REQUEST);
                }
            }

            String address = null;
            if (request != null && request.address() != null && !request.address().trim().isEmpty()) {
                address = request.address().trim();
                if (address.length() > 255) {
                    return error("VALIDATION_ERROR", "Address must be at most 255 characters", HttpStatus.BAD_REQUEST);
                }
            }

            jdbcTemplate.update(
                    "UPDATE user_profiles SET full_name = ?, date_of_birth = ?, address = ? WHERE user_id = ?",
                    fullName,
                    dateOfBirth,
                    address,
                    accountId
            );
        } else if ("admin".equalsIgnoreCase(role)) {
            Integer profileCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM admin_profiles WHERE user_id = ?",
                    Integer.class,
                    accountId
            );
            if (profileCount == null || profileCount == 0) {
                return error("PROFILE_NOT_FOUND", "Profile not found", HttpStatus.NOT_FOUND);
            }

            jdbcTemplate.update(
                    "UPDATE admin_profiles SET full_name = ? WHERE user_id = ?",
                    fullName,
                    accountId
            );
        } else {
            return error("FORBIDDEN_ROLE", "Unsupported account role", HttpStatus.FORBIDDEN);
        }

        return getCurrentAccount(authorizationHeader);
    }

    private AccountAuthResult authenticate(String authorizationHeader) {
        Integer accountId = extractUserId(authorizationHeader);
        if (accountId == null) {
            return new AccountAuthResult(null, error("UNAUTHORIZED", "Unauthorized", HttpStatus.UNAUTHORIZED));
        }

        List<Map<String, Object>> accounts = jdbcTemplate.queryForList(
                """
                SELECT
                    u.id,
                    u.phone,
                    u.role,
                    u.status
                FROM users u
                WHERE u.id = ?
                LIMIT 1
                """,
                accountId
        );

        if (accounts.isEmpty()) {
            return new AccountAuthResult(null, error("UNAUTHORIZED", "Unauthorized", HttpStatus.UNAUTHORIZED));
        }

        Map<String, Object> account = accounts.get(0);
        if ("blocked".equalsIgnoreCase(String.valueOf(account.get("status")))) {
            return new AccountAuthResult(null, error(
                    "ACCOUNT_BLOCKED",
                    "Your account has been blocked by an administrator.",
                    HttpStatus.FORBIDDEN
            ));
        }

        if (!"active".equalsIgnoreCase(String.valueOf(account.get("status")))) {
            return new AccountAuthResult(null, error("UNAUTHORIZED", "Unauthorized", HttpStatus.UNAUTHORIZED));
        }

        return new AccountAuthResult(account, null);
    }

    private Integer extractUserId(String authorizationHeader) {
        return SecurityAccount.currentId();
    }

    private Map<String, Object> buildAccount(Map<String, Object> row) {
        Map<String, Object> account = new HashMap<>();
        account.put("id", row.get("id"));
        account.put("phone", row.get("phone"));
        account.put("email", row.get("email"));
        account.put("emailVerified", "admin".equalsIgnoreCase(String.valueOf(row.get("role"))) || isTruthy(row.get("email_verified")));
        account.put("fullName", row.get("full_name"));
        account.put("role", row.get("role"));
        account.put("status", row.get("status"));
        if ("admin".equalsIgnoreCase(String.valueOf(row.get("role")))) {
            account.put("position", row.get("position"));
        }
        account.put("profile", buildProfile(row));
        account.put("wallet", buildWallet(row));
        return account;
    }

    private Map<String, Object> buildProfile(Map<String, Object> row) {
        Map<String, Object> profile = new HashMap<>();
        profile.put("fullName", row.get("full_name"));

        if ("admin".equalsIgnoreCase(String.valueOf(row.get("role")))) {
            profile.put("position", row.get("position"));
        } else {
            Object dateOfBirth = row.get("date_of_birth");
            profile.put("dateOfBirth", dateOfBirth == null ? null : dateOfBirth.toString());
            profile.put("address", row.get("address"));
        }

        return profile;
    }

    private Map<String, Object> buildWallet(Map<String, Object> row) {
        if (row.get("wallet_id") == null) {
            return null;
        }

        Map<String, Object> wallet = new HashMap<>();
        wallet.put("id", row.get("wallet_id"));
        wallet.put("balance", row.get("balance"));
        return wallet;
    }

    private String validateFullName(String fullName) {
        if (fullName.isBlank()) {
            return "Full name is required";
        }

        if (fullName.length() < 2) {
            return "Full name must be at least 2 characters";
        }

        if (fullName.length() > 100) {
            return "Full name must be at most 100 characters";
        }

        return null;
    }

    private boolean isTruthy(Object value) {
        return Boolean.TRUE.equals(value) || (value instanceof Number number && number.intValue() != 0);
    }

    private ResponseEntity<Map<String, Object>> error(String code, String message, HttpStatus status) {
        Map<String, Object> response = new HashMap<>();
        response.put("code", code);
        response.put("message", message);
        return ResponseEntity.status(status).body(response);
    }

    private record AccountAuthResult(
            Map<String, Object> account,
            ResponseEntity<Map<String, Object>> errorResponse
    ) {
    }

    public record ProfileUpdateRequest(String fullName, String dateOfBirth, String address) {
    }
}
