package com.khoi.ewallet.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
@Profile("prod")
public class ProductionMailConfiguration {
    private final String provider;
    private final ProviderSettings ses;
    private final ProviderSettings resend;

    public ProductionMailConfiguration(
            @Value("${mail.provider:ses}") String provider,
            @Value("${mail.providers.ses.host:}") String sesHost,
            @Value("${mail.providers.ses.port:0}") int sesPort,
            @Value("${mail.providers.ses.username:}") String sesUsername,
            @Value("${mail.providers.ses.password:}") String sesPassword,
            @Value("${mail.providers.ses.from-address:}") String sesFromAddress,
            @Value("${mail.providers.resend.host:}") String resendHost,
            @Value("${mail.providers.resend.port:0}") int resendPort,
            @Value("${mail.providers.resend.username:}") String resendUsername,
            @Value("${mail.providers.resend.password:}") String resendPassword,
            @Value("${mail.providers.resend.from-address:}") String resendFromAddress) {
        this.provider = normalizeProvider(provider);
        this.ses = new ProviderSettings(sesHost, sesPort, sesUsername, sesPassword, sesFromAddress);
        this.resend = new ProviderSettings(
                resendHost, resendPort, resendUsername, resendPassword, resendFromAddress);
    }

    public String provider() {
        return provider;
    }

    public ProviderSettings selected() {
        return switch (provider) {
            case "ses" -> ses;
            case "resend" -> resend;
            default -> throw unsupportedProvider();
        };
    }

    public void validate() {
        ProviderSettings selected = selected();
        String prefix = provider.equals("ses") ? "SES" : "RESEND";
        requireText(selected.host(), prefix + "_SMTP_HOST");
        if (selected.port() < 1 || selected.port() > 65535) {
            throw invalid(prefix + "_SMTP_PORT");
        }
        requireText(selected.username(), prefix + "_SMTP_USERNAME");
        requireText(selected.password(), prefix + "_SMTP_PASSWORD");
        requireText(selected.fromAddress(), prefix + "_MAIL_FROM_ADDRESS");
    }

    private String normalizeProvider(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private IllegalStateException unsupportedProvider() {
        return new IllegalStateException(
                "Unsupported EMAIL_PROVIDER value. Supported values: ses, resend.");
    }

    private void requireText(String value, String variable) {
        if (value == null || value.isBlank()) {
            throw invalid(variable);
        }
    }

    private IllegalStateException invalid(String variable) {
        return new IllegalStateException(
                "Email provider '" + provider + "' has a missing or invalid " + variable + " value.");
    }

    public record ProviderSettings(
            String host, int port, String username, String password, String fromAddress) {
    }
}
