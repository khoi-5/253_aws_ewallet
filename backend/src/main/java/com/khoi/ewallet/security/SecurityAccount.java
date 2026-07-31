package com.khoi.ewallet.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class SecurityAccount {
    private SecurityAccount() {}
    public static Integer currentId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getPrincipal() instanceof AuthenticatedAccount account
                ? account.id() : null;
    }
}
