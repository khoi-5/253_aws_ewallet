package com.khoi.ewallet.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.regex.Pattern;

@Service
public class JwtService {
    private static final Pattern BASE64URL_SEGMENT = Pattern.compile("[A-Za-z0-9_-]+");
    private final String configuredSecret;
    private final long expirationSeconds;
    private SecretKey key;

    public JwtService(@Value("${jwt.secret:}") String configuredSecret,
                      @Value("${jwt.expiration-seconds:3600}") long expirationSeconds) {
        this.configuredSecret = configuredSecret;
        this.expirationSeconds = expirationSeconds;
    }

    @PostConstruct
    void initialize() {
        if (configuredSecret == null || configuredSecret.isBlank()) {
            throw new IllegalStateException("JWT_SECRET environment variable is required");
        }
        byte[] bytes;
        try {
            bytes = Decoders.BASE64.decode(configuredSecret.trim());
        } catch (RuntimeException exception) {
            bytes = configuredSecret.getBytes(StandardCharsets.UTF_8);
        }
        if (bytes.length < 32) throw new IllegalStateException("JWT_SECRET must contain at least 32 bytes");
        if (expirationSeconds < 60) throw new IllegalStateException("JWT_EXPIRATION must be at least 60 seconds");
        key = Keys.hmacShaKeyFor(bytes);
    }

    public String generateAccessToken(int accountId) {
        Instant now = Instant.now();
        return Jwts.builder().subject(String.valueOf(accountId)).issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(expirationSeconds))).signWith(key).compact();
    }

    public int extractAccountId(String token) {
        if (token == null || token.isBlank()) throw new JwtException("Invalid token");
        validateCanonicalCompactJwt(token);
        Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
        try {
            int id = Integer.parseInt(claims.getSubject());
            if (id <= 0) throw new NumberFormatException();
            return id;
        } catch (RuntimeException exception) {
            throw new JwtException("Invalid token subject");
        }
    }

    private void validateCanonicalCompactJwt(String token) {
        String[] segments = token.split("\\.", -1);
        if (segments.length != 3) throw new JwtException("Invalid token");

        Base64.Decoder decoder = Base64.getUrlDecoder();
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        for (String segment : segments) {
            if (!BASE64URL_SEGMENT.matcher(segment).matches() || segment.length() % 4 == 1) {
                throw new JwtException("Invalid token");
            }
            try {
                if (!encoder.encodeToString(decoder.decode(segment)).equals(segment)) {
                    throw new JwtException("Invalid token");
                }
            } catch (IllegalArgumentException exception) {
                throw new JwtException("Invalid token", exception);
            }
        }
    }

    public long getExpirationSeconds() { return expirationSeconds; }
}
