package com.khoi.ewallet.auth;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.nio.charset.StandardCharsets;
import java.util.Properties;

@Configuration
@Profile("prod")
public class ProductionMailSenderConfiguration {

    @Bean
    JavaMailSender javaMailSender(ProductionMailConfiguration configuration) {
        ProductionMailConfiguration.ProviderSettings selected = configuration.selected();
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(selected.host());
        sender.setPort(selected.port());
        sender.setUsername(selected.username());
        sender.setPassword(selected.password());
        sender.setDefaultEncoding(StandardCharsets.UTF_8.name());

        Properties properties = sender.getJavaMailProperties();
        properties.setProperty("mail.smtp.auth", "true");
        properties.setProperty("mail.smtp.starttls.enable", "true");
        properties.setProperty("mail.smtp.starttls.required", "true");
        return sender;
    }
}
