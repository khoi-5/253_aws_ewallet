package com.khoi.ewallet.auth;

import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("prod")
public class ProductionMailConfigurationValidator {
    private final ProductionMailConfiguration configuration;

    public ProductionMailConfigurationValidator(ProductionMailConfiguration configuration) {
        this.configuration = configuration;
    }

    @PostConstruct
    void validate() {
        configuration.validate();
    }
}
