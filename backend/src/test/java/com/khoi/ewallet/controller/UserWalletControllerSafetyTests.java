package com.khoi.ewallet.controller;

import com.khoi.ewallet.security.AuthenticatedAccount;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.locks.ReentrantLock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserWalletControllerSafetyTests {

    private static final String TOKEN = "Bearer demo-token-1";

    @BeforeEach
    void authenticateTestUser() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(new AuthenticatedAccount(1, "user", "active"), null, List.of())
        );
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void successfulTransferUpdatesBothWalletsAndCreatesTransaction() {
        LedgerJdbcTemplate jdbc = new LedgerJdbcTemplate(new BigDecimal("100.00"));
        UserWalletController controller = new UserWalletController(jdbc);

        ResponseEntity<Map<String, Object>> response = controller.transferMoney(
                TOKEN, new UserWalletController.TransferRequest("0987654321", new BigDecimal("25.00"), "Kiểm tra chuyển tiền")
        );
        jdbc.releaseTransactionLock();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(new BigDecimal("75.00"), response.getBody().get("balance"));
        assertEquals(new BigDecimal("75.00"), jdbc.senderBalance);
        assertEquals(new BigDecimal("25.00"), jdbc.receiverCredits);
        assertEquals(1, jdbc.transactionsInserted);
    }

    @Test
    void insufficientBalanceDoesNotMutateWalletsOrCreateTransaction() {
        LedgerJdbcTemplate jdbc = new LedgerJdbcTemplate(new BigDecimal("20.00"));
        UserWalletController controller = new UserWalletController(jdbc);

        ResponseEntity<Map<String, Object>> response = controller.transferMoney(
                TOKEN, new UserWalletController.TransferRequest("0987654321", new BigDecimal("25.00"), null)
        );
        jdbc.releaseTransactionLock();

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(new BigDecimal("20.00"), jdbc.senderBalance);
        assertEquals(BigDecimal.ZERO, jdbc.receiverCredits);
        assertEquals(0, jdbc.transactionsInserted);
    }

    @Test
    void paymentForInactiveServiceIsRejectedBeforeWalletMutation() {
        InactiveServiceJdbcTemplate jdbc = new InactiveServiceJdbcTemplate();
        UserWalletController controller = new UserWalletController(jdbc);

        ResponseEntity<Map<String, Object>> response = controller.payService(
                TOKEN, new UserWalletController.PaymentRequest(9, null)
        );

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals(0, jdbc.updateCalls);
    }

    @Test
    void blockedUserCannotAccessWalletApi() {
        BlockedUserJdbcTemplate jdbc = new BlockedUserJdbcTemplate();
        UserWalletController controller = new UserWalletController(jdbc);

        ResponseEntity<Map<String, Object>> response = controller.depositMoney(
                TOKEN, new UserWalletController.DepositRequest(BigDecimal.ONE, null)
        );

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertEquals("ACCOUNT_BLOCKED", response.getBody().get("code"));
        assertEquals(0, jdbc.updateCalls);
    }

    @Test
    void unverifiedUserCannotDepositTransferOrPay() {
        UnverifiedUserJdbcTemplate jdbc = new UnverifiedUserJdbcTemplate();
        UserWalletController controller = new UserWalletController(jdbc);

        assertEmailNotVerified(controller.depositMoney(TOKEN,
                new UserWalletController.DepositRequest(BigDecimal.ONE, null)));
        assertEmailNotVerified(controller.transferMoney(TOKEN,
                new UserWalletController.TransferRequest("0987654321", BigDecimal.ONE, null)));
        assertEmailNotVerified(controller.payService(TOKEN,
                new UserWalletController.PaymentRequest(1, null)));
        assertEquals(0, jdbc.updateCalls);
    }

    @Test
    void vietnameseServiceTextIsPreservedInApiResponse() {
        VietnameseServiceJdbcTemplate jdbc = new VietnameseServiceJdbcTemplate();
        UserWalletController controller = new UserWalletController(jdbc);

        ResponseEntity<Map<String, Object>> response = controller.getActiveServices(TOKEN);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> services = (List<Map<String, Object>>) response.getBody().get("services");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("Mua thẻ điện thoại", services.get(0).get("name"));
        assertEquals("Thanh toán mô phỏng dịch vụ thẻ điện thoại", services.get(0).get("description"));
    }

    @Test
    void outgoingTransferHistoryShowsSenderBalanceBeforeAndAfter() {
        Map<String, Object> row = transactionRow("transfer", 11, 22, "25.00");
        Map<String, Object> transaction = getOnlyHistoryTransaction(new HistoryJdbcTemplate("75.00", List.of(row)));

        assertEquals(new BigDecimal("100.00"), transaction.get("balanceBefore"));
        assertEquals(new BigDecimal("75.00"), transaction.get("balanceAfter"));
        assertFalse(transaction.containsKey("senderBalanceAfter"));
        assertFalse(transaction.containsKey("receiverBalanceAfter"));
    }

    @Test
    void incomingTransferHistoryShowsReceiverBalanceBeforeAndAfter() {
        Map<String, Object> row = transactionRow("transfer", 22, 11, "25.00");
        row.put("balance_after", new BigDecimal("975.00"));
        Map<String, Object> transaction = getOnlyHistoryTransaction(new HistoryJdbcTemplate("125.00", List.of(row)));

        assertEquals(new BigDecimal("100.00"), transaction.get("balanceBefore"));
        assertEquals(new BigDecimal("125.00"), transaction.get("balanceAfter"));
        assertFalse(transaction.containsValue(new BigDecimal("975.00")));
    }

    @Test
    void paymentHistoryShowsAuthenticatedUserBalanceBeforeAndAfter() {
        Map<String, Object> payment = transactionRow("payment", 11, null, "20.00");
        Map<String, Object> transaction = getOnlyHistoryTransaction(
                new HistoryJdbcTemplate("130.00", List.of(payment))
        );

        assertEquals(new BigDecimal("150.00"), transaction.get("balanceBefore"));
        assertEquals(new BigDecimal("130.00"), transaction.get("balanceAfter"));
    }

    @Test
    void depositHistoryShowsAuthenticatedUserBalanceBeforeAndAfter() {
        Map<String, Object> deposit = transactionRow("deposit", null, 11, "50.00");
        Map<String, Object> transaction = getOnlyHistoryTransaction(
                new HistoryJdbcTemplate("150.00", List.of(deposit))
        );

        assertEquals(new BigDecimal("100.00"), transaction.get("balanceBefore"));
        assertEquals(new BigDecimal("150.00"), transaction.get("balanceAfter"));
    }

    @Test
    void transactionBoundaryRollsBackWhenWalletOperationFails() {
        FailingDepositJdbcTemplate jdbc = new FailingDepositJdbcTemplate();
        RecordingTransactionManager transactionManager = new RecordingTransactionManager();
        UserWalletController target = new UserWalletController(jdbc);
        TransactionInterceptor interceptor = new TransactionInterceptor(
                transactionManager, new AnnotationTransactionAttributeSource()
        );
        ProxyFactory proxyFactory = new ProxyFactory(target);
        proxyFactory.addAdvice(interceptor);
        UserWalletController controller = (UserWalletController) proxyFactory.getProxy();

        assertThrows(DataAccessResourceFailureException.class, () -> controller.depositMoney(
                TOKEN, new UserWalletController.DepositRequest(new BigDecimal("10.00"), null)
        ));

        assertEquals(1, transactionManager.begins);
        assertEquals(0, transactionManager.commits);
        assertEquals(1, transactionManager.rollbacks);
    }

    @Test
    void concurrentTransfersCannotProduceNegativeSenderBalance() throws Exception {
        LedgerJdbcTemplate jdbc = new LedgerJdbcTemplate(new BigDecimal("100.00"));
        UserWalletController controller = new UserWalletController(jdbc);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<ResponseEntity<Map<String, Object>>> first = executor.submit(() -> transferAfter(start, controller, jdbc));
            Future<ResponseEntity<Map<String, Object>>> second = executor.submit(() -> transferAfter(start, controller, jdbc));
            start.countDown();

            List<HttpStatus> statuses = List.of(
                    (HttpStatus) first.get().getStatusCode(),
                    (HttpStatus) second.get().getStatusCode()
            );
            assertTrue(statuses.contains(HttpStatus.OK));
            assertTrue(statuses.contains(HttpStatus.BAD_REQUEST));
            assertEquals(new BigDecimal("20.00"), jdbc.senderBalance);
            assertTrue(jdbc.senderBalance.signum() >= 0);
            assertEquals(1, jdbc.transactionsInserted);
        } finally {
            executor.shutdownNow();
        }
    }

    private ResponseEntity<Map<String, Object>> transferAfter(
            CountDownLatch start, UserWalletController controller, LedgerJdbcTemplate jdbc
    ) throws Exception {
        start.await();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(new AuthenticatedAccount(1, "user", "active"), null, List.of())
        );
        try {
            return controller.transferMoney(
                    TOKEN, new UserWalletController.TransferRequest("0987654321", new BigDecimal("80.00"), null)
            );
        } finally {
            jdbc.releaseTransactionLock();
            SecurityContextHolder.clearContext();
        }
    }

    private static Map<String, Object> activeUser() {
        return Map.of(
                "id", 1,
                "role", "user",
                "status", "active",
                "email_verified", true
        );
    }

    private void assertEmailNotVerified(ResponseEntity<Map<String, Object>> response) {
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertEquals("EMAIL_VERIFICATION_REQUIRED", response.getBody().get("code"));
        assertEquals(
                "Please verify your email before performing this action.",
                response.getBody().get("message")
        );
    }

    private static class UnverifiedUserJdbcTemplate extends JdbcTemplate {
        private int updateCalls;
        @Override public List<Map<String, Object>> queryForList(String sql, Object... args) {
            if (sql.contains("SELECT id, role, status")) return List.of(Map.of(
                    "id", 1, "role", "user", "status", "active", "email_verified", false));
            throw new AssertionError("Unexpected query: " + sql);
        }
        @Override public int update(String sql, Object... args) { updateCalls++; return 1; }
    }

    private Map<String, Object> getOnlyHistoryTransaction(JdbcTemplate jdbc) {
        return getHistoryTransactions(jdbc).get(0);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> getHistoryTransactions(JdbcTemplate jdbc) {
        UserWalletController controller = new UserWalletController(jdbc);
        ResponseEntity<Map<String, Object>> response = controller.getTransactions(TOKEN);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        return (List<Map<String, Object>>) response.getBody().get("transactions");
    }

    private static Map<String, Object> transactionRow(
            String type, Integer senderWalletId, Integer receiverWalletId, String amount
    ) {
        Map<String, Object> row = new HashMap<>();
        row.put("id", 1);
        row.put("transaction_code", "TXN-1");
        row.put("type", type);
        row.put("sender_wallet_id", senderWalletId);
        row.put("receiver_wallet_id", receiverWalletId);
        row.put("amount", new BigDecimal(amount));
        row.put("balance_before", new BigDecimal("1000.00"));
        row.put("balance_after", new BigDecimal("975.00"));
        row.put("status", "success");
        return row;
    }

    private static class HistoryJdbcTemplate extends JdbcTemplate {
        private final BigDecimal currentBalance;
        private final List<Map<String, Object>> transactions;

        private HistoryJdbcTemplate(String currentBalance, List<Map<String, Object>> transactions) {
            this.currentBalance = new BigDecimal(currentBalance);
            this.transactions = transactions;
        }

        @Override
        public List<Map<String, Object>> queryForList(String sql, Object... args) {
            if (sql.contains("SELECT id, role, status")) return List.of(activeUser());
            if (sql.contains("SELECT id, balance FROM wallets")) {
                return List.of(Map.of("id", 11, "balance", currentBalance));
            }
            if (sql.contains("FROM transactions t")) return transactions;
            throw new AssertionError("Unexpected query: " + sql);
        }
    }

    private static class LedgerJdbcTemplate extends JdbcTemplate {
        private final ReentrantLock senderLock = new ReentrantLock();
        private final ThreadLocal<Boolean> lockHeld = ThreadLocal.withInitial(() -> false);
        private BigDecimal senderBalance;
        private BigDecimal receiverCredits = BigDecimal.ZERO;
        private int transactionsInserted;

        private LedgerJdbcTemplate(BigDecimal senderBalance) {
            this.senderBalance = senderBalance;
        }

        @Override
        public List<Map<String, Object>> queryForList(String sql, Object... args) {
            if (sql.contains("SELECT id, role, status")) return List.of(activeUser());
            if (sql.contains("WHERE u.phone = ?")) {
                return List.of(Map.of(
                        "id", 2, "phone", "0987654321", "status", "active",
                        "wallet_id", 22, "balance", receiverCredits
                ));
            }
            if (sql.contains("FROM wallets WHERE user_id = ? FOR UPDATE")) {
                senderLock.lock();
                lockHeld.set(true);
                return List.of(Map.of("id", 11, "balance", senderBalance));
            }
            throw new AssertionError("Unexpected query: " + sql);
        }

        @Override
        public int update(String sql, Object... args) {
            if (sql.startsWith("UPDATE wallets SET balance = balance -")) {
                senderBalance = senderBalance.subtract((BigDecimal) args[0]);
                return 1;
            }
            if (sql.startsWith("UPDATE wallets SET balance = balance +")) {
                receiverCredits = receiverCredits.add((BigDecimal) args[0]);
                return 1;
            }
            if (sql.contains("INSERT INTO transactions")) {
                transactionsInserted++;
                return 1;
            }
            throw new AssertionError("Unexpected update: " + sql);
        }

        private void releaseTransactionLock() {
            if (lockHeld.get()) {
                lockHeld.set(false);
                senderLock.unlock();
            }
        }
    }

    private static class InactiveServiceJdbcTemplate extends JdbcTemplate {
        private int updateCalls;

        @Override
        public List<Map<String, Object>> queryForList(String sql, Object... args) {
            if (sql.contains("SELECT id, role, status")) return List.of(activeUser());
            if (sql.contains("FROM services")) {
                return List.of(Map.of(
                        "id", 9, "name", "Dịch vụ tạm dừng", "price", BigDecimal.TEN,
                        "description", "Kiểm tra UTF-8", "is_active", false
                ));
            }
            throw new AssertionError("Unexpected query: " + sql);
        }

        @Override
        public int update(String sql, Object... args) {
            updateCalls++;
            return 1;
        }
    }

    private static class BlockedUserJdbcTemplate extends JdbcTemplate {
        private int updateCalls;

        @Override
        public List<Map<String, Object>> queryForList(String sql, Object... args) {
            return List.of(Map.of("id", 1, "role", "user", "status", "blocked"));
        }

        @Override
        public int update(String sql, Object... args) {
            updateCalls++;
            return 1;
        }
    }

    private static class FailingDepositJdbcTemplate extends JdbcTemplate {
        @Override
        public List<Map<String, Object>> queryForList(String sql, Object... args) {
            if (sql.contains("SELECT id, role, status")) return List.of(activeUser());
            if (sql.contains("FROM wallets WHERE user_id = ? FOR UPDATE")) {
                return List.of(Map.of("id", 11, "balance", new BigDecimal("50.00")));
            }
            throw new AssertionError("Unexpected query: " + sql);
        }

        @Override
        public int update(String sql, Object... args) {
            if (sql.contains("INSERT INTO transactions")) {
                throw new DataAccessResourceFailureException("Simulated transaction insert failure");
            }
            return 1;
        }
    }

    private static class VietnameseServiceJdbcTemplate extends JdbcTemplate {
        @Override
        public List<Map<String, Object>> queryForList(String sql) {
            return vietnameseServices(sql);
        }

        @Override
        public List<Map<String, Object>> queryForList(String sql, Object... args) {
            if (sql.contains("SELECT id, role, status")) return List.of(activeUser());
            return vietnameseServices(sql);
        }

        private List<Map<String, Object>> vietnameseServices(String sql) {
            if (sql.contains("FROM services")) {
                return List.of(Map.of(
                        "id", 1,
                        "name", "Mua thẻ điện thoại",
                        "price", BigDecimal.TEN,
                        "description", "Thanh toán mô phỏng dịch vụ thẻ điện thoại",
                        "is_active", true
                ));
            }
            throw new AssertionError("Unexpected query: " + sql);
        }
    }

    private static class RecordingTransactionManager extends AbstractPlatformTransactionManager {
        private int begins;
        private int commits;
        private int rollbacks;

        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
            begins++;
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
            commits++;
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
            rollbacks++;
        }
    }
}
