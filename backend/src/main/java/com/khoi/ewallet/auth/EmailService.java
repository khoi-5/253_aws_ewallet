package com.khoi.ewallet.auth;

public interface EmailService {
    void sendVerificationEmail(String email, String verificationUrl);
    void sendPasswordResetEmail(String email, String resetUrl);
}
