package com.khoi.ewallet.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"spring.profiles.active=local",
        "spring.autoconfigure.exclude=org.springframework.boot.batch.jdbc.autoconfigure.BatchJdbcAutoConfiguration",
        "management.health.db.enabled=false",
        "jwt.secret=test-only-secret!that-is-at-least-32-bytes-long", "jwt.expiration-seconds=3600"})
@AutoConfigureMockMvc
class JwtAuthenticationIntegrationTests {
    private static final String SECRET = "test-only-secret!that-is-at-least-32-bytes-long";
    @Autowired MockMvc mockMvc;
    @Autowired JwtService jwtService;
    @Autowired ApplicationContext applicationContext;
    @MockitoBean JdbcTemplate jdbcTemplate;
    private final AtomicReference<String> accountStatus = new AtomicReference<>("active");

    @BeforeEach
    void configureDatabaseResponses() {
        accountStatus.set("active");
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class))).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            Object parameter = invocation.getArgument(1);
            if (sql.contains("u.password")) return List.of(Map.of(
                    "id", 1, "phone", "0912345678", "password", new BCryptPasswordEncoder().encode("password123"),
                    "role", "user", "status", accountStatus.get(), "full_name", "JWT Test User"));
            int id = parameter instanceof Number number ? number.intValue() : 1;
            if (id == 999) return List.of();
            if (sql.contains("SELECT id, role, status"))
                return List.of(Map.of("id", id, "role", "user", "status", accountStatus.get()));
            if (sql.contains("COALESCE(up.full_name")) return List.of(Map.of(
                    "id", id, "phone", "0912345678", "role", "user", "status", "active",
                    "full_name", "JWT Test User", "wallet_id", 11, "balance", 100));
            if (sql.contains("FROM wallets WHERE user_id")) return List.of();
            return List.of();
        });
    }

    @Test void loginReturnsSignedJwt() throws Exception {
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"0912345678\",\"password\":\"password123\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer")).andExpect(jsonPath("$.expiresIn").value(3600));
    }
    @Test void blockedUserCannotLogin() throws Exception {
        accountStatus.set("blocked");
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"0912345678\",\"password\":\"password123\"}"))
                .andExpect(status().isForbidden());
    }
    @Test void defaultUserDetailsServiceIsNotConfigured() {
        org.junit.jupiter.api.Assertions.assertTrue(
                applicationContext.getBeansOfType(UserDetailsService.class).isEmpty());
    }
    @Test void basicAuthenticationCannotAccessProtectedEndpoints() throws Exception {
        mockMvc.perform(get("/api/account/me").header("Authorization", "Basic dXNlcjpnZW5lcmF0ZWQtcGFzc3dvcmQ="))
                .andExpect(status().isUnauthorized());
    }
    @Test void validJwtAccessesAuthenticatedEndpoint() throws Exception {
        mockMvc.perform(get("/api/account/me").header("Authorization", bearer(jwtService.generateAccessToken(1))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(1));
    }
    @Test void changedExistingSignatureCharacterReturns401() throws Exception {
        String valid = jwtService.generateAccessToken(1);
        int signatureStart = valid.lastIndexOf('.') + 1;
        char original = valid.charAt(signatureStart);
        String changed = valid.substring(0, signatureStart) + (original == 'A' ? 'B' : 'A') + valid.substring(signatureStart + 1);
        mockMvc.perform(get("/api/account/me").header("Authorization", bearer(changed)))
                .andExpect(status().isUnauthorized());
    }
    @Test void appendedBase64UrlCharactersInSignatureReturn401() throws Exception {
        String valid = jwtService.generateAccessToken(1);
        mockMvc.perform(get("/api/account/me").header("Authorization", bearer(valid + "4")))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/account/me").header("Authorization", bearer(valid + "5")))
                .andExpect(status().isUnauthorized());
    }
    @Test void nonCanonicalCompactJwtFormsReturn401() throws Exception {
        String valid = jwtService.generateAccessToken(1);
        String[] invalidTokens = {
                valid + ".", "." + valid, valid.replaceFirst("\\.", ".."),
                valid + "=", valid + " ", valid + "!"
        };
        for (String invalid : invalidTokens) {
            mockMvc.perform(get("/api/account/me").header("Authorization", bearer(invalid)))
                    .andExpect(status().isUnauthorized());
        }
    }
    @Test void missingTokenReturns401() throws Exception { mockMvc.perform(get("/api/account/me")).andExpect(status().isUnauthorized()); }
    @Test void emptyBearerTokenReturns401() throws Exception { mockMvc.perform(get("/api/account/me").header("Authorization", "Bearer ")).andExpect(status().isUnauthorized()); }
    @Test void malformedJwtReturns401() throws Exception { mockMvc.perform(get("/api/account/me").header("Authorization", "Bearer not-a-jwt")).andExpect(status().isUnauthorized()); }
    @Test void forgedSignatureReturns401() throws Exception { mockMvc.perform(get("/api/account/me").header("Authorization", bearer(token(1, Instant.now().plusSeconds(60), "different-test-secret-that-is-long-enough-123")))).andExpect(status().isUnauthorized()); }
    @Test void expiredJwtReturns401() throws Exception { mockMvc.perform(get("/api/account/me").header("Authorization", bearer(token(1, Instant.now().minusSeconds(1), SECRET)))).andExpect(status().isUnauthorized()); }
    @Test void nonexistentAccountReturns401() throws Exception { mockMvc.perform(get("/api/account/me").header("Authorization", bearer(jwtService.generateAccessToken(999)))).andExpect(status().isUnauthorized()); }
    @Test void blockedAccountReturns403() throws Exception { accountStatus.set("blocked"); mockMvc.perform(get("/api/account/me").header("Authorization", bearer(jwtService.generateAccessToken(1)))).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCOUNT_BLOCKED")); }
    @Test void regularUserCannotAccessAdminEndpoint() throws Exception { mockMvc.perform(get("/api/admin/dashboard").header("Authorization", bearer(jwtService.generateAccessToken(1)))).andExpect(status().isForbidden()); }

    @Test void adminCannotAccessUserWalletEndpoint() throws Exception {
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(Map.of("id", 6, "role", "admin", "status", "active")));
        mockMvc.perform(get("/api/user/wallet/me").header("Authorization", bearer(jwtService.generateAccessToken(6)))).andExpect(status().isForbidden());
    }
    @Test void recipientPreviewRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/user/wallet/recipient").param("phone", "0945141298"))
                .andExpect(status().isUnauthorized());
    }
    @Test void tokenIssuedBeforeBlockingIsRejectedAfterStatusChange() throws Exception {
        String token = jwtService.generateAccessToken(1);
        mockMvc.perform(get("/api/account/me").header("Authorization", bearer(token))).andExpect(status().isOk());
        accountStatus.set("blocked");
        mockMvc.perform(get("/api/account/me").header("Authorization", bearer(token))).andExpect(status().isForbidden());
    }
    @Test void publicAuthEndpointsDoNotRequireJwt() throws Exception {
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test void actuatorHealthEndpointIsPublic() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    private String token(int id, Instant expiration, String secret) {
        return Jwts.builder().subject(String.valueOf(id)).issuedAt(Date.from(Instant.now().minusSeconds(60)))
                .expiration(Date.from(expiration)).signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8))).compact();
    }
    private String bearer(String token) { return "Bearer " + token; }
}
