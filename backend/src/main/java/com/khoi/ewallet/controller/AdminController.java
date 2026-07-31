package com.khoi.ewallet.controller;

import com.khoi.ewallet.security.SecurityAccount;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final JdbcTemplate jdbcTemplate;

    public AdminController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/dashboard")
    public ResponseEntity<Map<String, Object>> getDashboard(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader
    ) {
        AuthResult authResult = authenticateAdmin(authorizationHeader);
        if (authResult.errorResponse() != null) {
            return authResult.errorResponse();
        }

        Map<String, Object> response = new HashMap<>();
        response.put("admin", buildAdmin(authResult.account()));
        response.put("summary", buildSummary());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/users")
    public ResponseEntity<Map<String, Object>> getUsers(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader
    ) {
        AuthResult authResult = authenticateAdmin(authorizationHeader);
        if (authResult.errorResponse() != null) {
            return authResult.errorResponse();
        }

        List<Map<String, Object>> users = jdbcTemplate.queryForList(
                """
                SELECT
                    u.id,
                    u.phone,
                    u.role,
                    u.status,
                    u.created_at,
                    u.updated_at,
                    up.full_name,
                    w.id AS wallet_id,
                    w.balance
                FROM users u
                LEFT JOIN user_profiles up ON u.id = up.user_id
                LEFT JOIN wallets w ON u.id = w.user_id
                WHERE u.role = 'user'
                ORDER BY u.id DESC
                """
        ).stream().map(this::buildUser).toList();

        Map<String, Object> response = new HashMap<>();
        response.put("users", users);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/transactions")
    public ResponseEntity<Map<String, Object>> getTransactions(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "all") String type,
            @RequestParam(defaultValue = "all") String status,
            @RequestParam(defaultValue = "") String search,
            @RequestParam(defaultValue = "") String phone,
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo,
            @RequestParam(defaultValue = "desc") String sortDirection
    ) {
        AuthResult authResult = authenticateAdmin(authorizationHeader);
        if (authResult.errorResponse() != null) {
            return authResult.errorResponse();
        }

        type = type.trim().toLowerCase();
        status = status.trim().toLowerCase();
        sortDirection = sortDirection.trim().toLowerCase();
        if (page < 0) return error("Page must be at least 0", HttpStatus.BAD_REQUEST);
        if (size < 1 || size > 100) return error("Size must be between 1 and 100", HttpStatus.BAD_REQUEST);
        if (!List.of("all", "deposit", "transfer", "payment").contains(type)) {
            return error("Type must be all, deposit, transfer, or payment", HttpStatus.BAD_REQUEST);
        }
        if (!List.of("all", "success", "failed").contains(status)) {
            return error("Status must be all, success, or failed", HttpStatus.BAD_REQUEST);
        }
        if (!List.of("asc", "desc").contains(sortDirection)) {
            return error("Sort direction must be asc or desc", HttpStatus.BAD_REQUEST);
        }

        LocalDate from;
        LocalDate to;
        try {
            from = dateFrom == null || dateFrom.isBlank() ? null : LocalDate.parse(dateFrom);
            to = dateTo == null || dateTo.isBlank() ? null : LocalDate.parse(dateTo);
        } catch (DateTimeParseException exception) {
            return error("Dates must use ISO format YYYY-MM-DD", HttpStatus.BAD_REQUEST);
        }
        if (from != null && to != null && from.isAfter(to)) {
            return error("Date from must not be after date to", HttpStatus.BAD_REQUEST);
        }

        String joins = """
                FROM transactions t
                LEFT JOIN wallets sw ON t.sender_wallet_id = sw.id
                LEFT JOIN users su ON sw.user_id = su.id
                LEFT JOIN user_profiles sp ON su.id = sp.user_id
                LEFT JOIN wallets rw ON t.receiver_wallet_id = rw.id
                LEFT JOIN users ru ON rw.user_id = ru.id
                LEFT JOIN user_profiles rp ON ru.id = rp.user_id
                LEFT JOIN services s ON t.service_id = s.id
                LEFT JOIN users cu ON t.created_by = cu.id
                LEFT JOIN user_profiles cup ON cu.id = cup.user_id
                LEFT JOIN admin_profiles cap ON cu.id = cap.user_id
                """;
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        List<Object> parameters = new ArrayList<>();
        if (!"all".equals(type)) { where.append(" AND t.type = ?"); parameters.add(type); }
        if (!"all".equals(status)) { where.append(" AND t.status = ?"); parameters.add(status); }
        if (!search.isBlank()) {
            where.append("""
                     AND (LOWER(t.transaction_code) LIKE ? OR LOWER(COALESCE(su.phone, '')) LIKE ?
                     OR LOWER(COALESCE(ru.phone, '')) LIKE ? OR LOWER(COALESCE(cu.phone, '')) LIKE ?
                     OR LOWER(COALESCE(sp.full_name, '')) LIKE ? OR LOWER(COALESCE(rp.full_name, '')) LIKE ?
                     OR LOWER(COALESCE(s.name, '')) LIKE ? OR LOWER(COALESCE(t.description, '')) LIKE ?)
                    """);
            String pattern = "%" + search.trim().toLowerCase() + "%";
            for (int index = 0; index < 8; index++) parameters.add(pattern);
        }
        if (!phone.isBlank()) {
            where.append(" AND (su.phone LIKE ? OR ru.phone LIKE ? OR cu.phone LIKE ?)");
            String pattern = "%" + phone.trim() + "%";
            parameters.add(pattern); parameters.add(pattern); parameters.add(pattern);
        }
        if (from != null) { where.append(" AND t.created_at >= ?"); parameters.add(Timestamp.valueOf(from.atStartOfDay())); }
        if (to != null) { where.append(" AND t.created_at < ?"); parameters.add(Timestamp.valueOf(to.plusDays(1).atStartOfDay())); }

        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) " + joins + where, Long.class, parameters.toArray());
        String select = """
                SELECT t.id, t.transaction_code, t.type, t.status, t.amount, t.description, t.created_at,
                    t.sender_wallet_id, su.id AS sender_user_id, su.phone AS sender_phone, sp.full_name AS sender_name,
                    t.receiver_wallet_id, ru.id AS receiver_user_id, ru.phone AS receiver_phone, rp.full_name AS receiver_name,
                    t.service_id, s.name AS service_name, t.created_by AS created_by_user_id,
                    cu.phone AS created_by_phone, COALESCE(cup.full_name, cap.full_name) AS created_by_name,
                    t.balance_before, t.balance_after
                """;
        List<Map<String, Object>> transactions = jdbcTemplate.queryForList(
                select + joins + where + " ORDER BY t.created_at " + sortDirection.toUpperCase()
                        + ", t.id " + sortDirection.toUpperCase() + " LIMIT ? OFFSET ?",
                append(parameters, size, page * size).toArray()
        ).stream().map(this::buildAdminTransaction).toList();

        long totalElements = total == null ? 0 : total;
        long totalPages = totalElements == 0 ? 0 : (totalElements + size - 1) / size;
        Map<String, Object> pagination = new HashMap<>();
        pagination.put("page", page); pagination.put("size", size);
        pagination.put("totalElements", totalElements); pagination.put("totalPages", totalPages);
        pagination.put("hasPrevious", page > 0); pagination.put("hasNext", page + 1 < totalPages);
        Map<String, Object> response = new HashMap<>();
        response.put("transactions", transactions); response.put("pagination", pagination);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/services")
    public ResponseEntity<Map<String, Object>> getServices(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader
    ) {
        AuthResult authResult = authenticateAdmin(authorizationHeader);
        if (authResult.errorResponse() != null) return authResult.errorResponse();
        List<Map<String, Object>> services = jdbcTemplate.queryForList("""
                SELECT id, name, price, description, is_active, created_at, updated_at
                FROM services ORDER BY id DESC
                """).stream().map(this::buildService).toList();
        Map<String, Object> response = new HashMap<>();
        response.put("services", services);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/services")
    @Transactional
    public ResponseEntity<Map<String, Object>> createService(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @RequestBody ServiceRequest request
    ) {
        AuthResult authResult = authenticateAdmin(authorizationHeader);
        if (authResult.errorResponse() != null) return authResult.errorResponse();
        String validation = validateService(request, true);
        if (validation != null) return error(validation, HttpStatus.BAD_REQUEST);
        String name = request.name().trim();
        if (serviceNameExists(name, null)) return error("A service with this name already exists", HttpStatus.CONFLICT);
        String description = normalizeDescription(request.description());
        jdbcTemplate.update("INSERT INTO services (name, price, description, is_active) VALUES (?, ?, ?, ?)",
                name, request.price(), description, request.isActive());
        Map<String, Object> service = buildService(jdbcTemplate.queryForMap("""
                SELECT id, name, price, description, is_active, created_at, updated_at
                FROM services WHERE LOWER(TRIM(name)) = LOWER(TRIM(?)) ORDER BY id DESC LIMIT 1
                """, name));
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Service created successfully"); response.put("service", service);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PatchMapping("/services/{serviceId}")
    @Transactional
    public ResponseEntity<Map<String, Object>> updateService(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @PathVariable int serviceId, @RequestBody ServiceRequest request
    ) {
        AuthResult authResult = authenticateAdmin(authorizationHeader);
        if (authResult.errorResponse() != null) return authResult.errorResponse();
        if (!serviceExists(serviceId)) return error("Service not found", HttpStatus.NOT_FOUND);
        if (request == null || (request.name() == null && request.price() == null && request.description() == null)) {
            return error("At least one editable field is required", HttpStatus.BAD_REQUEST);
        }
        String validation = validateService(request, false);
        if (validation != null) return error(validation, HttpStatus.BAD_REQUEST);
        if (request.name() != null && serviceNameExists(request.name().trim(), serviceId)) {
            return error("A service with this name already exists", HttpStatus.CONFLICT);
        }
        List<String> assignments = new ArrayList<>();
        List<Object> values = new ArrayList<>();
        if (request.name() != null) { assignments.add("name = ?"); values.add(request.name().trim()); }
        if (request.price() != null) { assignments.add("price = ?"); values.add(request.price()); }
        if (request.description() != null) { assignments.add("description = ?"); values.add(normalizeDescription(request.description())); }
        assignments.add("updated_at = CURRENT_TIMESTAMP"); values.add(serviceId);
        jdbcTemplate.update("UPDATE services SET " + String.join(", ", assignments) + " WHERE id = ?", values.toArray());
        return serviceResponse("Service updated successfully", serviceId);
    }

    @PatchMapping("/services/{serviceId}/status")
    @Transactional
    public ResponseEntity<Map<String, Object>> updateServiceStatus(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @PathVariable int serviceId, @RequestBody ServiceStatusRequest request
    ) {
        AuthResult authResult = authenticateAdmin(authorizationHeader);
        if (authResult.errorResponse() != null) return authResult.errorResponse();
        if (!serviceExists(serviceId)) return error("Service not found", HttpStatus.NOT_FOUND);
        if (request == null || request.isActive() == null) return error("isActive must be a boolean", HttpStatus.BAD_REQUEST);
        jdbcTemplate.update("UPDATE services SET is_active = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
                request.isActive(), serviceId);
        return serviceResponse(request.isActive() ? "Service activated successfully" : "Service deactivated successfully", serviceId);
    }

    private ResponseEntity<Map<String, Object>> serviceResponse(String message, int serviceId) {
        Map<String, Object> row = jdbcTemplate.queryForMap("""
                SELECT id, name, price, description, is_active, created_at, updated_at FROM services WHERE id = ?
                """, serviceId);
        Map<String, Object> response = new HashMap<>(); response.put("message", message);
        response.put("service", buildService(row)); return ResponseEntity.ok(response);
    }

    private boolean serviceExists(int serviceId) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM services WHERE id = ?", Integer.class, serviceId);
        return count != null && count > 0;
    }

    private boolean serviceNameExists(String name, Integer excludedId) {
        String sql = "SELECT COUNT(*) FROM services WHERE LOWER(TRIM(name)) = LOWER(TRIM(?))";
        Integer count = excludedId == null
                ? jdbcTemplate.queryForObject(sql, Integer.class, name)
                : jdbcTemplate.queryForObject(sql + " AND id <> ?", Integer.class, name, excludedId);
        return count != null && count > 0;
    }

    private String validateService(ServiceRequest request, boolean creating) {
        if (request == null) return "Request body is required";
        if (creating && request.name() == null) return "Name is required";
        if (request.name() != null) {
            int length = request.name().trim().length();
            if (length < 2 || length > 100) return "Name must be between 2 and 100 characters";
        }
        if (creating && request.price() == null) return "Price is required";
        if (request.price() != null && (request.price().compareTo(BigDecimal.ZERO) <= 0
                || request.price().compareTo(new BigDecimal("10000000")) > 0
                || request.price().scale() > 2)) return "Price must be greater than 0, at most 10,000,000, and have at most 2 decimal places";
        if (request.description() != null && request.description().trim().length() > 255) return "Description must be at most 255 characters";
        if (creating && request.isActive() == null) return "isActive must be a boolean";
        return null;
    }

    private String normalizeDescription(String description) {
        if (description == null || description.trim().isEmpty()) return null;
        return description.trim();
    }

    private Map<String, Object> buildService(Map<String, Object> row) {
        Map<String, Object> service = new HashMap<>();
        service.put("id", row.get("id")); service.put("name", row.get("name"));
        service.put("price", row.get("price")); service.put("description", row.get("description"));
        Object active = row.get("is_active");
        service.put("isActive", active instanceof Boolean value ? value : active instanceof Number number && number.intValue() != 0);
        service.put("createdAt", row.get("created_at")); service.put("updatedAt", row.get("updated_at"));
        return service;
    }

    private List<Object> append(List<Object> source, Object... values) {
        List<Object> result = new ArrayList<>(source);
        result.addAll(List.of(values));
        return result;
    }

    private Map<String, Object> buildAdminTransaction(Map<String, Object> row) {
        Map<String, Object> transaction = new HashMap<>();
        Map.ofEntries(
                Map.entry("id", "id"), Map.entry("transactionCode", "transaction_code"),
                Map.entry("type", "type"), Map.entry("status", "status"), Map.entry("amount", "amount"),
                Map.entry("description", "description"), Map.entry("createdAt", "created_at"),
                Map.entry("senderWalletId", "sender_wallet_id"), Map.entry("senderUserId", "sender_user_id"),
                Map.entry("senderPhone", "sender_phone"), Map.entry("senderName", "sender_name"),
                Map.entry("receiverWalletId", "receiver_wallet_id"), Map.entry("receiverUserId", "receiver_user_id"),
                Map.entry("receiverPhone", "receiver_phone"), Map.entry("receiverName", "receiver_name"),
                Map.entry("serviceId", "service_id"), Map.entry("serviceName", "service_name"),
                Map.entry("createdByUserId", "created_by_user_id"), Map.entry("createdByPhone", "created_by_phone"),
                Map.entry("createdByName", "created_by_name"), Map.entry("balanceBefore", "balance_before"),
                Map.entry("balanceAfter", "balance_after")
        ).forEach((key, column) -> transaction.put(key, row.get(column)));
        return transaction;
    }

    @PatchMapping("/users/{userId}/status")
    @Transactional
    public ResponseEntity<Map<String, Object>> updateUserStatus(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @PathVariable int userId,
            @RequestBody StatusUpdateRequest request
    ) {
        AuthResult authResult = authenticateAdmin(authorizationHeader);
        if (authResult.errorResponse() != null) {
            return authResult.errorResponse();
        }

        String status = request == null || request.status() == null
                ? ""
                : request.status().trim().toLowerCase();
        if (!"active".equals(status) && !"blocked".equals(status)) {
            return error("Invalid status value", HttpStatus.BAD_REQUEST);
        }

        List<Map<String, Object>> targets = jdbcTemplate.queryForList(
                """
                SELECT
                    u.id,
                    u.phone,
                    u.role,
                    u.status,
                    u.created_at,
                    u.updated_at,
                    up.full_name,
                    w.id AS wallet_id,
                    w.balance
                FROM users u
                LEFT JOIN user_profiles up ON u.id = up.user_id
                LEFT JOIN wallets w ON u.id = w.user_id
                WHERE u.id = ?
                LIMIT 1
                """,
                userId
        );

        if (targets.isEmpty()) {
            return error("Target user not found", HttpStatus.NOT_FOUND);
        }

        Map<String, Object> target = targets.get(0);
        if (!"user".equalsIgnoreCase(String.valueOf(target.get("role")))) {
            return error("Only regular user accounts can be updated", HttpStatus.FORBIDDEN);
        }

        jdbcTemplate.update("UPDATE users SET status = ? WHERE id = ?", status, userId);

        Map<String, Object> updatedUser = buildStatusUser(userId);
        Map<String, Object> response = new HashMap<>();
        response.put("message", "User status updated successfully");
        response.put("user", updatedUser);
        return ResponseEntity.ok(response);
    }

    private AuthResult authenticateAdmin(String authorizationHeader) {
        Integer accountId = extractUserId(authorizationHeader);
        if (accountId == null) {
            return new AuthResult(null, error("UNAUTHORIZED", "Unauthorized", HttpStatus.UNAUTHORIZED));
        }

        List<Map<String, Object>> accounts = jdbcTemplate.queryForList(
                """
                SELECT
                    u.id,
                    u.phone,
                    u.role,
                    u.status,
                    ap.full_name,
                    ap.position
                FROM users u
                LEFT JOIN admin_profiles ap ON u.id = ap.user_id
                WHERE u.id = ?
                LIMIT 1
                """,
                accountId
        );

        if (accounts.isEmpty()) {
            return new AuthResult(null, error("UNAUTHORIZED", "Unauthorized", HttpStatus.UNAUTHORIZED));
        }

        Map<String, Object> account = accounts.get(0);
        if ("blocked".equalsIgnoreCase(String.valueOf(account.get("status")))) {
            return new AuthResult(null, error(
                    "ACCOUNT_BLOCKED",
                    "Your account has been blocked by an administrator.",
                    HttpStatus.FORBIDDEN
            ));
        }

        if (!"active".equalsIgnoreCase(String.valueOf(account.get("status")))) {
            return new AuthResult(null, error("UNAUTHORIZED", "Unauthorized", HttpStatus.UNAUTHORIZED));
        }

        if (!"admin".equalsIgnoreCase(String.valueOf(account.get("role")))) {
            return new AuthResult(null, error("FORBIDDEN_ROLE", "Admin access required", HttpStatus.FORBIDDEN));
        }

        return new AuthResult(account, null);
    }

    private Integer extractUserId(String authorizationHeader) {
        return SecurityAccount.currentId();
    }

    private Map<String, Object> buildAdmin(Map<String, Object> row) {
        Map<String, Object> admin = new HashMap<>();
        admin.put("id", row.get("id"));
        admin.put("phone", row.get("phone"));
        admin.put("fullName", row.get("full_name"));
        admin.put("role", row.get("role"));
        admin.put("status", row.get("status"));
        admin.put("position", row.get("position"));
        return admin;
    }

    private Map<String, Object> buildSummary() {
        Map<String, Object> summary = new HashMap<>();
        summary.put("totalUsers", count("SELECT COUNT(*) FROM users WHERE role = 'user'"));
        summary.put("activeUsers", count("SELECT COUNT(*) FROM users WHERE role = 'user' AND status = 'active'"));
        summary.put("blockedUsers", count("SELECT COUNT(*) FROM users WHERE role = 'user' AND status = 'blocked'"));
        summary.put("totalTransactions", count("SELECT COUNT(*) FROM transactions"));
        return summary;
    }

    private Integer count(String sql) {
        Integer value = jdbcTemplate.queryForObject(sql, Integer.class);
        return value == null ? 0 : value;
    }

    private Map<String, Object> buildUser(Map<String, Object> row) {
        Map<String, Object> user = new HashMap<>();
        user.put("id", row.get("id"));
        user.put("phone", row.get("phone"));
        user.put("fullName", row.get("full_name"));
        user.put("role", row.get("role"));
        user.put("status", row.get("status"));
        user.put("walletId", row.get("wallet_id"));
        user.put("balance", row.get("balance"));
        user.put("createdAt", row.get("created_at"));
        user.put("updatedAt", row.get("updated_at"));
        return user;
    }

    private Map<String, Object> buildStatusUser(int userId) {
        Map<String, Object> row = jdbcTemplate.queryForMap(
                """
                SELECT
                    u.id,
                    u.phone,
                    u.status,
                    up.full_name
                FROM users u
                LEFT JOIN user_profiles up ON u.id = up.user_id
                WHERE u.id = ?
                """,
                userId
        );

        Map<String, Object> user = new HashMap<>();
        user.put("id", row.get("id"));
        user.put("phone", row.get("phone"));
        user.put("fullName", row.get("full_name"));
        user.put("status", row.get("status"));
        return user;
    }

    private ResponseEntity<Map<String, Object>> error(String message, HttpStatus status) {
        Map<String, Object> response = new HashMap<>();
        response.put("message", message);
        return ResponseEntity.status(status).body(response);
    }

    private ResponseEntity<Map<String, Object>> error(String code, String message, HttpStatus status) {
        Map<String, Object> response = new HashMap<>();
        response.put("code", code);
        response.put("message", message);
        return ResponseEntity.status(status).body(response);
    }

    private record AuthResult(
            Map<String, Object> account,
            ResponseEntity<Map<String, Object>> errorResponse
    ) {
    }

    public record StatusUpdateRequest(String status) {
    }

    public record ServiceRequest(String name, BigDecimal price, String description, Boolean isActive) {
    }

    public record ServiceStatusRequest(Boolean isActive) {
    }
}
