package com.khoi.ewallet.controller;

import com.khoi.ewallet.security.SecurityAccount;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/user/wallet")
public class UserWalletController {

    private static final Pattern PHONE_PATTERN = Pattern.compile("^0[0-9]{9}$");
    private static final BigDecimal MAX_TRANSFER_AMOUNT = new BigDecimal("10000000");
    private static final BigDecimal MIN_DEPOSIT_AMOUNT = new BigDecimal("1.00");
    private static final BigDecimal MAX_DEPOSIT_AMOUNT = new BigDecimal("10000000.00");
    private static final DateTimeFormatter TRANSACTION_CODE_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    private final JdbcTemplate jdbcTemplate;

    public UserWalletController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> getMyWallet(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader
    ) {
        AuthResult authResult = authenticate(authorizationHeader);
        if (authResult.errorResponse() != null) {
            return authResult.errorResponse();
        }

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                SELECT
                    u.id,
                    u.phone,
                    u.email,
                    u.email_verified,
                    u.role,
                    u.status,
                    up.full_name,
                    w.id AS wallet_id,
                    w.balance
                FROM users u
                JOIN user_profiles up ON u.id = up.user_id
                JOIN wallets w ON u.id = w.user_id
                WHERE u.id = ?
                LIMIT 1
                """,
                authResult.user().id()
        );

        if (rows.isEmpty()) {
            return error("Wallet not found", HttpStatus.BAD_REQUEST);
        }

        Map<String, Object> row = rows.get(0);
        Map<String, Object> response = new HashMap<>();
        response.put("user", buildUser(row));
        response.put("wallet", buildWallet(row));
        return ResponseEntity.ok(response);
    }

    @GetMapping("/recipient")
    public ResponseEntity<Map<String, Object>> getRecipient(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @RequestParam(value = "phone", required = false) String phone
    ) {
        AuthResult authResult = authenticate(authorizationHeader);
        if (authResult.errorResponse() != null) {
            return authResult.errorResponse();
        }

        String recipientPhone = phone == null ? "" : phone.trim();
        if (!PHONE_PATTERN.matcher(recipientPhone).matches()) {
            return error("VALIDATION_ERROR", "Receiver phone invalid", HttpStatus.BAD_REQUEST);
        }

        List<Map<String, Object>> recipients = jdbcTemplate.queryForList(
                """
                SELECT
                    u.id,
                    u.phone,
                    u.role,
                    u.status,
                    up.full_name,
                    w.id AS wallet_id
                FROM users u
                LEFT JOIN user_profiles up ON u.id = up.user_id
                LEFT JOIN wallets w ON u.id = w.user_id
                WHERE u.phone = ?
                LIMIT 1
                """,
                recipientPhone
        );

        if (recipients.isEmpty()) {
            return recipientUnavailable();
        }

        Map<String, Object> recipient = recipients.get(0);
        int recipientId = ((Number) recipient.get("id")).intValue();
        if (recipientId == authResult.user().id()) {
            return error(
                    "SELF_TRANSFER",
                    "You cannot transfer money to your own wallet.",
                    HttpStatus.BAD_REQUEST
            );
        }

        boolean eligible = "user".equalsIgnoreCase(String.valueOf(recipient.get("role")))
                && "active".equalsIgnoreCase(String.valueOf(recipient.get("status")))
                && recipient.get("wallet_id") != null
                && recipient.get("full_name") != null
                && !String.valueOf(recipient.get("full_name")).isBlank();
        if (!eligible) {
            return recipientUnavailable();
        }

        Map<String, Object> response = new HashMap<>();
        response.put("phone", recipient.get("phone"));
        response.put("fullName", recipient.get("full_name"));
        return ResponseEntity.ok(response);
    }

    @PostMapping("/deposit")
    @Transactional
    public ResponseEntity<Map<String, Object>> depositMoney(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @RequestBody DepositRequest request
    ) {
        AuthResult authResult = authenticate(authorizationHeader);
        if (authResult.errorResponse() != null) {
            return authResult.errorResponse();
        }
        if (!authResult.user().emailVerified()) {
            return emailNotVerified();
        }

        BigDecimal amount = request == null ? null : request.amount();
        String amountError = validateDepositAmount(amount);
        if (amountError != null) {
            return error(amountError, HttpStatus.BAD_REQUEST);
        }

        String description = request.description() == null || request.description().isBlank()
                ? "Simulated deposit"
                : request.description().trim();
        if (description.length() > 255) {
            return error("Description must be at most 255 characters", HttpStatus.BAD_REQUEST);
        }

        List<Map<String, Object>> wallets = jdbcTemplate.queryForList(
                "SELECT id, balance FROM wallets WHERE user_id = ? FOR UPDATE",
                authResult.user().id()
        );

        if (wallets.isEmpty()) {
            return error("Wallet not found", HttpStatus.BAD_REQUEST);
        }

        Map<String, Object> wallet = wallets.get(0);
        int walletId = ((Number) wallet.get("id")).intValue();
        BigDecimal balanceBefore = (BigDecimal) wallet.get("balance");
        BigDecimal balanceAfter = balanceBefore.add(amount);
        String transactionCode = buildTransactionCode(authResult.user().id());

        jdbcTemplate.update(
                "UPDATE wallets SET balance = ? WHERE id = ?",
                balanceAfter,
                walletId
        );

        jdbcTemplate.update(
                """
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
                    created_by
                )
                VALUES (?, NULL, ?, NULL, ?, ?, ?, 'deposit', 'success', ?, ?)
                """,
                transactionCode,
                walletId,
                amount,
                balanceBefore,
                balanceAfter,
                description,
                authResult.user().id()
        );

        Map<String, Object> transactionRow = jdbcTemplate.queryForMap(
                """
                SELECT
                    id,
                    transaction_code,
                    type,
                    sender_wallet_id,
                    receiver_wallet_id,
                    service_id,
                    amount,
                    balance_before,
                    balance_after,
                    status,
                    description,
                    created_at
                FROM transactions
                WHERE transaction_code = ?
                """,
                transactionCode
        );

        Map<String, Object> response = new HashMap<>();
        response.put("message", "Deposit completed successfully");
        response.put("balance", balanceAfter);
        response.put("transaction", buildTransaction(transactionRow));
        return ResponseEntity.ok(response);
    }

    @PostMapping("/transfer")
    @Transactional
    public ResponseEntity<Map<String, Object>> transferMoney(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @RequestBody TransferRequest request
    ) {
        AuthResult authResult = authenticate(authorizationHeader);
        if (authResult.errorResponse() != null) {
            return authResult.errorResponse();
        }
        if (!authResult.user().emailVerified()) {
            return emailNotVerified();
        }

        String receiverPhone = request.receiverPhone() == null ? "" : request.receiverPhone().trim();
        BigDecimal amount = request.amount();

        if (!PHONE_PATTERN.matcher(receiverPhone).matches()) {
            return error("Receiver phone invalid", HttpStatus.BAD_REQUEST);
        }

        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0
                || amount.compareTo(MAX_TRANSFER_AMOUNT) > 0) {
            return error("Amount invalid", HttpStatus.BAD_REQUEST);
        }

        List<Map<String, Object>> receivers = jdbcTemplate.queryForList(
                """
                SELECT
                    u.id,
                    u.phone,
                    u.status,
                    w.id AS wallet_id,
                    w.balance
                FROM users u
                JOIN user_profiles up ON u.id = up.user_id
                JOIN wallets w ON u.id = w.user_id
                WHERE u.phone = ?
                LIMIT 1
                """,
                receiverPhone
        );

        if (receivers.isEmpty()) {
            return error("Receiver does not exist", HttpStatus.BAD_REQUEST);
        }

        Map<String, Object> receiver = receivers.get(0);
        int receiverUserId = ((Number) receiver.get("id")).intValue();

        if (receiverUserId == authResult.user().id()) {
            return error("Sender cannot transfer to self", HttpStatus.BAD_REQUEST);
        }

        List<Map<String, Object>> senderWallets = jdbcTemplate.queryForList(
                "SELECT id, balance FROM wallets WHERE user_id = ? FOR UPDATE",
                authResult.user().id()
        );

        if (senderWallets.isEmpty()) {
            return error("Wallet not found", HttpStatus.BAD_REQUEST);
        }

        Map<String, Object> senderWallet = senderWallets.get(0);
        int senderWalletId = ((Number) senderWallet.get("id")).intValue();
        int receiverWalletId = ((Number) receiver.get("wallet_id")).intValue();
        BigDecimal balanceBefore = (BigDecimal) senderWallet.get("balance");

        if (balanceBefore.compareTo(amount) < 0) {
            return error("Balance is not enough", HttpStatus.BAD_REQUEST);
        }

        BigDecimal balanceAfter = balanceBefore.subtract(amount);
        String description = request.description() == null || request.description().isBlank()
                ? "Transfer money"
                : request.description().trim();
        if (description.length() > 255) {
            return error("Description must be at most 255 characters", HttpStatus.BAD_REQUEST);
        }
        String transactionCode = buildTransactionCode(authResult.user().id());

        jdbcTemplate.update(
                "UPDATE wallets SET balance = balance - ? WHERE id = ?",
                amount,
                senderWalletId
        );

        jdbcTemplate.update(
                "UPDATE wallets SET balance = balance + ? WHERE id = ?",
                amount,
                receiverWalletId
        );

        jdbcTemplate.update(
                """
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
                    created_by
                )
                VALUES (?, ?, ?, NULL, ?, ?, ?, 'transfer', 'success', ?, ?)
                """,
                transactionCode,
                senderWalletId,
                receiverWalletId,
                amount,
                balanceBefore,
                balanceAfter,
                description,
                authResult.user().id()
        );

        Map<String, Object> transaction = new HashMap<>();
        transaction.put("transactionCode", transactionCode);
        transaction.put("amount", amount);
        transaction.put("receiverPhone", receiverPhone);

        Map<String, Object> response = new HashMap<>();
        response.put("message", "Transfer successfully");
        response.put("balance", balanceAfter);
        response.put("transaction", transaction);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/transactions")
    public ResponseEntity<Map<String, Object>> getTransactions(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader
    ) {
        AuthResult authResult = authenticate(authorizationHeader);
        if (authResult.errorResponse() != null) {
            return authResult.errorResponse();
        }

        List<Map<String, Object>> wallets = jdbcTemplate.queryForList(
                "SELECT id, balance FROM wallets WHERE user_id = ? LIMIT 1",
                authResult.user().id()
        );

        if (wallets.isEmpty()) {
            return error("Wallet not found", HttpStatus.BAD_REQUEST);
        }

        Map<String, Object> wallet = wallets.get(0);
        int walletId = ((Number) wallet.get("id")).intValue();
        BigDecimal runningBalance = (BigDecimal) wallet.get("balance");

        List<Map<String, Object>> transactionRows = jdbcTemplate.queryForList(
                """
                SELECT
                    t.id,
                    t.transaction_code,
                    t.type,
                    t.sender_wallet_id,
                    sender_wallet.user_id AS sender_user_id,
                    sender_user.phone AS sender_phone,
                    sender_profile.full_name AS sender_name,
                    t.receiver_wallet_id,
                    receiver_wallet.user_id AS receiver_user_id,
                    receiver_user.phone AS receiver_phone,
                    receiver_profile.full_name AS receiver_name,
                    t.service_id,
                    s.name AS service_name,
                    t.amount,
                    t.balance_before,
                    t.balance_after,
                    t.status,
                    t.description,
                    t.created_at
                FROM transactions t
                LEFT JOIN wallets sender_wallet ON t.sender_wallet_id = sender_wallet.id
                LEFT JOIN users sender_user ON sender_wallet.user_id = sender_user.id
                LEFT JOIN user_profiles sender_profile ON sender_user.id = sender_profile.user_id
                LEFT JOIN wallets receiver_wallet ON t.receiver_wallet_id = receiver_wallet.id
                LEFT JOIN users receiver_user ON receiver_wallet.user_id = receiver_user.id
                LEFT JOIN user_profiles receiver_profile ON receiver_user.id = receiver_profile.user_id
                LEFT JOIN services s ON t.service_id = s.id
                WHERE t.sender_wallet_id = ?
                   OR t.receiver_wallet_id = ?
                ORDER BY t.created_at DESC, t.id DESC
                """,
                walletId,
                walletId
        );

        List<Map<String, Object>> transactions = new ArrayList<>();
        for (Map<String, Object> row : transactionRows) {
            BigDecimal balanceBefore = balanceBeforeUserTransaction(row, walletId, runningBalance);
            transactions.add(buildUserTransaction(row, balanceBefore, runningBalance));
            runningBalance = balanceBefore;
        }

        Map<String, Object> response = new HashMap<>();
        response.put("transactions", transactions);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/services")
    public ResponseEntity<Map<String, Object>> getActiveServices(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader
    ) {
        AuthResult authResult = authenticate(authorizationHeader);
        if (authResult.errorResponse() != null) {
            return authResult.errorResponse();
        }

        List<Map<String, Object>> serviceRows = jdbcTemplate.queryForList(
                """
                SELECT
                    id,
                    name,
                    price,
                    description,
                    is_active
                FROM services
                WHERE is_active = TRUE
                ORDER BY id ASC
                """
        );

        List<Map<String, Object>> services = serviceRows.stream()
                .map(this::buildService)
                .toList();

        Map<String, Object> response = new HashMap<>();
        response.put("services", services);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/payments")
    @Transactional
    public ResponseEntity<Map<String, Object>> payService(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @RequestBody PaymentRequest request
    ) {
        AuthResult authResult = authenticate(authorizationHeader);
        if (authResult.errorResponse() != null) {
            return authResult.errorResponse();
        }
        if (!authResult.user().emailVerified()) {
            return emailNotVerified();
        }

        if (request == null || request.serviceId() == null || request.serviceId() <= 0) {
            return error("Service is required", HttpStatus.BAD_REQUEST);
        }

        List<Map<String, Object>> serviceRows = jdbcTemplate.queryForList(
                """
                SELECT
                    id,
                    name,
                    price,
                    description,
                    is_active
                FROM services
                WHERE id = ?
                LIMIT 1
                """,
                request.serviceId()
        );

        if (serviceRows.isEmpty()) {
            return error("Service not found", HttpStatus.NOT_FOUND);
        }

        Map<String, Object> service = serviceRows.get(0);
        if (!isTruthy(service.get("is_active"))) {
            return error("Service is not available", HttpStatus.CONFLICT);
        }

        BigDecimal amount = (BigDecimal) service.get("price");
        String serviceName = String.valueOf(service.get("name"));
        String description = request.description() == null || request.description().isBlank()
                ? "Payment for " + serviceName
                : request.description().trim();
        if (description.length() > 255) {
            return error("Description must be at most 255 characters", HttpStatus.BAD_REQUEST);
        }

        List<Map<String, Object>> wallets = jdbcTemplate.queryForList(
                "SELECT id, balance FROM wallets WHERE user_id = ? FOR UPDATE",
                authResult.user().id()
        );

        if (wallets.isEmpty()) {
            return error("Wallet not found", HttpStatus.BAD_REQUEST);
        }

        Map<String, Object> wallet = wallets.get(0);
        int walletId = ((Number) wallet.get("id")).intValue();
        BigDecimal balanceBefore = (BigDecimal) wallet.get("balance");

        if (balanceBefore.compareTo(amount) < 0) {
            return error("Insufficient wallet balance.", HttpStatus.BAD_REQUEST);
        }

        BigDecimal balanceAfter = balanceBefore.subtract(amount);
        String transactionCode = buildTransactionCode(authResult.user().id());

        jdbcTemplate.update(
                "UPDATE wallets SET balance = ? WHERE id = ?",
                balanceAfter,
                walletId
        );

        jdbcTemplate.update(
                """
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
                    created_by
                )
                VALUES (?, ?, NULL, ?, ?, ?, ?, 'payment', 'success', ?, ?)
                """,
                transactionCode,
                walletId,
                request.serviceId(),
                amount,
                balanceBefore,
                balanceAfter,
                description,
                authResult.user().id()
        );

        Map<String, Object> transactionRow = jdbcTemplate.queryForMap(
                """
                SELECT
                    t.id,
                    t.transaction_code,
                    t.type,
                    t.sender_wallet_id,
                    sender_wallet.user_id AS sender_user_id,
                    sender_user.phone AS sender_phone,
                    sender_profile.full_name AS sender_name,
                    t.receiver_wallet_id,
                    receiver_wallet.user_id AS receiver_user_id,
                    receiver_user.phone AS receiver_phone,
                    receiver_profile.full_name AS receiver_name,
                    t.service_id,
                    s.name AS service_name,
                    t.amount,
                    t.balance_before,
                    t.balance_after,
                    t.status,
                    t.description,
                    t.created_at
                FROM transactions t
                LEFT JOIN wallets sender_wallet ON t.sender_wallet_id = sender_wallet.id
                LEFT JOIN users sender_user ON sender_wallet.user_id = sender_user.id
                LEFT JOIN user_profiles sender_profile ON sender_user.id = sender_profile.user_id
                LEFT JOIN wallets receiver_wallet ON t.receiver_wallet_id = receiver_wallet.id
                LEFT JOIN users receiver_user ON receiver_wallet.user_id = receiver_user.id
                LEFT JOIN user_profiles receiver_profile ON receiver_user.id = receiver_profile.user_id
                LEFT JOIN services s ON t.service_id = s.id
                WHERE t.transaction_code = ?
                """,
                transactionCode
        );

        Map<String, Object> response = new HashMap<>();
        response.put("message", "Payment completed successfully");
        response.put("balance", balanceAfter);
        response.put("transaction", buildTransaction(transactionRow));
        return ResponseEntity.ok(response);
    }

    private AuthResult authenticate(String authorizationHeader) {
        Integer userId = extractUserId(authorizationHeader);
        if (userId == null) {
            return new AuthResult(null, error("UNAUTHORIZED", "Unauthorized", HttpStatus.UNAUTHORIZED));
        }

        List<Map<String, Object>> users = jdbcTemplate.queryForList(
                "SELECT id, role, status, email_verified FROM users WHERE id = ? LIMIT 1",
                userId
        );

        if (users.isEmpty()) {
            return new AuthResult(null, error("UNAUTHORIZED", "Unauthorized", HttpStatus.UNAUTHORIZED));
        }

        Map<String, Object> user = users.get(0);
        String status = String.valueOf(user.get("status"));
        String role = String.valueOf(user.get("role"));

        if ("blocked".equalsIgnoreCase(status)) {
            return new AuthResult(null, error(
                    "ACCOUNT_BLOCKED",
                    "Your account has been blocked by an administrator.",
                    HttpStatus.FORBIDDEN
            ));
        }

        if (!"active".equalsIgnoreCase(status)) {
            return new AuthResult(null, error("UNAUTHORIZED", "Unauthorized", HttpStatus.UNAUTHORIZED));
        }

        if (!"user".equalsIgnoreCase(role)) {
            return new AuthResult(null, error("FORBIDDEN_ROLE", "User wallet access required", HttpStatus.FORBIDDEN));
        }

        boolean emailVerified = isTruthy(user.get("email_verified"));
        return new AuthResult(new AuthenticatedUser(userId, emailVerified), null);
    }

    private Integer extractUserId(String authorizationHeader) {
        return SecurityAccount.currentId();
    }

    private String buildTransactionCode(int userId) {
        return "TXN" + LocalDateTime.now().format(TRANSACTION_CODE_FORMAT) + userId;
    }

    private String validateDepositAmount(BigDecimal amount) {
        if (amount == null) {
            return "Amount is required";
        }

        if (amount.compareTo(MIN_DEPOSIT_AMOUNT) < 0) {
            return "Amount must be at least 1.00";
        }

        if (amount.compareTo(MAX_DEPOSIT_AMOUNT) > 0) {
            return "Amount must be at most 10000000.00";
        }

        if (amount.stripTrailingZeros().scale() > 2) {
            return "Amount can have at most 2 decimal places";
        }

        return null;
    }

    private Map<String, Object> buildUser(Map<String, Object> row) {
        Map<String, Object> user = new HashMap<>();
        user.put("id", row.get("id"));
        user.put("phone", row.get("phone"));
        user.put("email", row.get("email"));
        user.put("emailVerified", isTruthy(row.get("email_verified")));
        user.put("fullName", row.get("full_name"));
        user.put("role", row.get("role"));
        user.put("status", row.get("status"));
        return user;
    }

    private Map<String, Object> buildWallet(Map<String, Object> row) {
        Map<String, Object> wallet = new HashMap<>();
        wallet.put("id", row.get("wallet_id"));
        wallet.put("balance", row.get("balance"));
        return wallet;
    }

    private Map<String, Object> buildService(Map<String, Object> row) {
        Map<String, Object> service = new HashMap<>();
        service.put("id", row.get("id"));
        service.put("name", row.get("name"));
        service.put("price", row.get("price"));
        service.put("description", row.get("description"));
        service.put("isActive", isTruthy(row.get("is_active")));
        return service;
    }

    private Map<String, Object> buildTransaction(Map<String, Object> row) {
        Map<String, Object> transaction = new HashMap<>();
        transaction.put("id", row.get("id"));
        transaction.put("transactionCode", row.get("transaction_code"));
        transaction.put("type", row.get("type"));
        transaction.put("senderWalletId", row.get("sender_wallet_id"));
        transaction.put("senderUserId", row.get("sender_user_id"));
        transaction.put("senderPhone", row.get("sender_phone"));
        transaction.put("senderName", row.get("sender_name"));
        transaction.put("receiverWalletId", row.get("receiver_wallet_id"));
        transaction.put("receiverUserId", row.get("receiver_user_id"));
        transaction.put("receiverPhone", row.get("receiver_phone"));
        transaction.put("receiverName", row.get("receiver_name"));
        transaction.put("serviceId", row.get("service_id"));
        transaction.put("serviceName", row.get("service_name"));
        transaction.put("amount", row.get("amount"));
        transaction.put("balanceBefore", row.get("balance_before"));
        transaction.put("balanceAfter", row.get("balance_after"));
        transaction.put("status", row.get("status"));
        transaction.put("description", row.get("description"));
        transaction.put("createdAt", row.get("created_at"));
        return transaction;
    }

    private Map<String, Object> buildUserTransaction(
            Map<String, Object> row, BigDecimal balanceBefore, BigDecimal balanceAfter
    ) {
        Map<String, Object> transaction = buildTransaction(row);
        transaction.put("balanceBefore", balanceBefore);
        transaction.put("balanceAfter", balanceAfter);
        return transaction;
    }

    private BigDecimal balanceBeforeUserTransaction(
            Map<String, Object> row, int walletId, BigDecimal balanceAfter
    ) {
        if (!"success".equals(row.get("status"))) {
            return balanceAfter;
        }

        BigDecimal amount = (BigDecimal) row.get("amount");
        Integer senderWalletId = numberAsInteger(row.get("sender_wallet_id"));
        Integer receiverWalletId = numberAsInteger(row.get("receiver_wallet_id"));

        if (senderWalletId != null && senderWalletId == walletId) {
            return balanceAfter.add(amount);
        }
        if (receiverWalletId != null && receiverWalletId == walletId) {
            return balanceAfter.subtract(amount);
        }
        return balanceAfter;
    }

    private Integer numberAsInteger(Object value) {
        return value instanceof Number number ? number.intValue() : null;
    }

    private ResponseEntity<Map<String, Object>> error(String message, HttpStatus status) {
        Map<String, Object> response = new HashMap<>();
        response.put("message", message);
        return ResponseEntity.status(status).body(response);
    }

    private ResponseEntity<Map<String, Object>> emailNotVerified() {
        return error(
                "EMAIL_VERIFICATION_REQUIRED",
                "Please verify your email before performing this action.",
                HttpStatus.FORBIDDEN
        );
    }

    private ResponseEntity<Map<String, Object>> recipientUnavailable() {
        return error(
                "RECIPIENT_UNAVAILABLE",
                "No active wallet account was found for this phone number.",
                HttpStatus.NOT_FOUND
        );
    }

    private ResponseEntity<Map<String, Object>> error(String code, String message, HttpStatus status) {
        Map<String, Object> response = new HashMap<>();
        response.put("code", code);
        response.put("message", message);
        return ResponseEntity.status(status).body(response);
    }

    private boolean isTruthy(Object value) {
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }

        if (value instanceof Number numberValue) {
            return numberValue.intValue() != 0;
        }

        return Boolean.parseBoolean(String.valueOf(value));
    }

    private record AuthenticatedUser(int id, boolean emailVerified) {
    }

    private record AuthResult(
            AuthenticatedUser user,
            ResponseEntity<Map<String, Object>> errorResponse
    ) {
    }

    public record TransferRequest(String receiverPhone, BigDecimal amount, String description) {
    }

    public record DepositRequest(BigDecimal amount, String description) {
    }

    public record PaymentRequest(Integer serviceId, String description) {
    }
}
