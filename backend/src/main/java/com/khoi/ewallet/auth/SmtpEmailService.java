package com.khoi.ewallet.auth;

import org.springframework.context.annotation.Profile;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@Profile("prod")
public class SmtpEmailService implements EmailService {
    private final JavaMailSender mailSender;
    private final String fromAddress;

    public SmtpEmailService(JavaMailSender mailSender,
            ProductionMailConfiguration configuration) {
        this.mailSender = mailSender;
        this.fromAddress = configuration.selected().fromAddress();
    }

    @Override
    public void sendVerificationEmail(String email, String verificationUrl) {
        send(email, "Verify your Cloud E-Wallet email",
                "Verify your email by opening this link:\n\n" + verificationUrl
                        + "\n\nIf you did not create this account, you can ignore this email.");
    }

    @Override
    public void sendPasswordResetEmail(String email, String resetUrl) {
        send(email, "Reset your Cloud E-Wallet password",
                "Reset your password by opening this link:\n\n" + resetUrl
                        + "\n\nIf you did not request a password reset, you can ignore this email.");
    }

    private void send(String recipient, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(recipient);
        message.setSubject(subject);
        message.setText(body);
        mailSender.send(message);
    }
}
