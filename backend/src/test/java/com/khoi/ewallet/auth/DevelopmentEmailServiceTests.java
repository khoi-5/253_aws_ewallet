package com.khoi.ewallet.auth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class DevelopmentEmailServiceTests {

    @Test
    void disabledDevelopmentLoggingDoesNotRequireSmtp() {
        DevelopmentEmailService service = new DevelopmentEmailService(false);
        assertDoesNotThrow(() -> service.sendVerificationEmail(
                "user@example.test", "https://example.test/verify"));
        assertDoesNotThrow(() -> service.sendPasswordResetEmail(
                "user@example.test", "https://example.test/reset"));
    }
}
