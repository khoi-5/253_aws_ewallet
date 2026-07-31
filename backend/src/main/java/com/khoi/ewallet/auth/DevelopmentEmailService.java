package com.khoi.ewallet.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("local")
public class DevelopmentEmailService implements EmailService {
    private static final Logger LOGGER = LoggerFactory.getLogger(DevelopmentEmailService.class);
    private final boolean logUrls;

    public DevelopmentEmailService(@Value("${mail.development-log-enabled:true}") boolean logUrls) {
        this.logUrls = logUrls;
    }

    @Override
    public void sendVerificationEmail(String email, String verificationUrl) {
        if (logUrls) LOGGER.info("Development verification URL for {}: {}", email, verificationUrl);
    }

    @Override
    public void sendPasswordResetEmail(String email, String resetUrl) {
        if (logUrls) LOGGER.info("Development password-reset URL for {}: {}", email, resetUrl);
    }
}
