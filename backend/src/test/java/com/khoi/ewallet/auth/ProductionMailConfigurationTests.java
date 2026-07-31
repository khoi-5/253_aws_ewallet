package com.khoi.ewallet.auth;

import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductionMailConfigurationTests {
    private static final String SECRET = "test-only-secret-value";

    @Test
    void sesSelectionResolvesSenderHostPortAndTls() {
        ProductionMailConfiguration configuration = complete("SeS");
        configuration.validate();

        JavaMailSenderImpl sender = (JavaMailSenderImpl)
                new ProductionMailSenderConfiguration().javaMailSender(configuration);
        assertEquals("ses.example.test", sender.getHost());
        assertEquals(587, sender.getPort());
        assertEquals("ses-user", sender.getUsername());
        assertEquals("ses-sender@example.test", configuration.selected().fromAddress());
        assertEquals("true", sender.getJavaMailProperties().getProperty("mail.smtp.auth"));
        assertEquals("true", sender.getJavaMailProperties().getProperty("mail.smtp.starttls.enable"));
        assertEquals("true", sender.getJavaMailProperties().getProperty("mail.smtp.starttls.required"));
        assertEquals("UTF-8", sender.getDefaultEncoding());
    }

    @Test
    void resendSelectionResolvesSenderHostAndPort() {
        ProductionMailConfiguration configuration = complete("RESEND");
        configuration.validate();

        JavaMailSenderImpl sender = (JavaMailSenderImpl)
                new ProductionMailSenderConfiguration().javaMailSender(configuration);
        assertEquals("resend.example.test", sender.getHost());
        assertEquals(587, sender.getPort());
        assertEquals("resend-sender@example.test", configuration.selected().fromAddress());
    }

    @Test
    void unsupportedProviderIsRejected() {
        IllegalStateException exception = assertThrows(
                IllegalStateException.class, () -> complete("other").validate());
        assertEquals("Unsupported EMAIL_PROVIDER value. Supported values: ses, resend.",
                exception.getMessage());
    }

    @Test
    void missingSesUsernameIsRejected() {
        assertMissing(ses("", SECRET, "ses-sender@example.test"), "SES_SMTP_USERNAME");
    }

    @Test
    void missingSesPasswordIsRejectedWithoutLeakingOtherSecrets() {
        IllegalStateException exception = assertMissing(
                ses("ses-user", "", "ses-sender@example.test"), "SES_SMTP_PASSWORD");
        assertFalse(exception.getMessage().contains(SECRET));
    }

    @Test
    void missingSesSenderIsRejected() {
        assertMissing(ses("ses-user", SECRET, " "), "SES_MAIL_FROM_ADDRESS");
    }

    @Test
    void missingResendPasswordIsRejected() {
        ProductionMailConfiguration configuration = new ProductionMailConfiguration(
                "resend", "", 0, "", "", "",
                "resend.example.test", 587, "resend", "", "resend-sender@example.test");
        assertMissing(configuration, "RESEND_SMTP_PASSWORD");
    }

    @Test
    void inactiveProviderCredentialsAreNotRequired() {
        ses("ses-user", SECRET, "ses-sender@example.test").validate();

        ProductionMailConfiguration resendOnly = new ProductionMailConfiguration(
                "resend", "", 0, "", "", "",
                "resend.example.test", 587, "resend", SECRET, "resend-sender@example.test");
        resendOnly.validate();
    }

    private IllegalStateException assertMissing(
            ProductionMailConfiguration configuration, String variable) {
        IllegalStateException exception = assertThrows(
                IllegalStateException.class, configuration::validate);
        assertTrue(exception.getMessage().contains("provider '" + configuration.provider() + "'"));
        assertTrue(exception.getMessage().contains(variable));
        return exception;
    }

    private ProductionMailConfiguration ses(String username, String password, String sender) {
        return new ProductionMailConfiguration(
                "ses", "ses.example.test", 587, username, password, sender,
                "", 0, "", "", "");
    }

    static ProductionMailConfiguration complete(String provider) {
        return new ProductionMailConfiguration(
                provider,
                "ses.example.test", 587, "ses-user", SECRET, "ses-sender@example.test",
                "resend.example.test", 587, "resend", SECRET, "resend-sender@example.test");
    }
}
