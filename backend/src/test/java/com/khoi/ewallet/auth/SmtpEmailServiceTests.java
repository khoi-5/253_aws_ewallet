package com.khoi.ewallet.auth;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class SmtpEmailServiceTests {

    @Test
    void sendsVerificationEmailThroughJavaMailSender() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        SmtpEmailService service = new SmtpEmailService(
                mailSender, ProductionMailConfigurationTests.complete("ses"));

        service.sendVerificationEmail("user@example.test", "https://wallet.example.test/verify-email?token=token");

        SimpleMailMessage message = captureMessage(mailSender);
        assertEquals("ses-sender@example.test", message.getFrom());
        assertArrayEquals(new String[]{"user@example.test"}, message.getTo());
        assertEquals("Verify your Cloud E-Wallet email", message.getSubject());
        assertTrue(message.getText().contains("https://wallet.example.test/verify-email?token=token"));
    }

    @Test
    void sendsPasswordResetEmailThroughJavaMailSender() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        SmtpEmailService service = new SmtpEmailService(
                mailSender, ProductionMailConfigurationTests.complete("resend"));

        service.sendPasswordResetEmail("user@example.test", "https://wallet.example.test/reset-password?token=token");

        SimpleMailMessage message = captureMessage(mailSender);
        assertEquals("resend-sender@example.test", message.getFrom());
        assertArrayEquals(new String[]{"user@example.test"}, message.getTo());
        assertEquals("Reset your Cloud E-Wallet password", message.getSubject());
        assertTrue(message.getText().contains("https://wallet.example.test/reset-password?token=token"));
    }

    private SimpleMailMessage captureMessage(JavaMailSender mailSender) {
        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        return captor.getValue();
    }
}
