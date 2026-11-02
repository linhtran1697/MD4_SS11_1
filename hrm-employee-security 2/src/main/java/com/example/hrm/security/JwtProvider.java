package com.example.hrm.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.UnsupportedJwtException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtProvider {

    private final byte[] secretKey;
    private final long expirationMillis;

    public JwtProvider(@Value("${jwt.secret}") String secret,
                       @Value("${jwt.expiration}") long expirationMillis) {
        if (secret == null || secret.isBlank()
                || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("jwt.secret phải có ít nhất 32 byte");
        }
        if (expirationMillis <= 0) {
            throw new IllegalArgumentException("jwt.expiration phải lớn hơn 0 (mili giây)");
        }
        this.secretKey = secret.getBytes(StandardCharsets.UTF_8);
        this.expirationMillis = expirationMillis;
    }

    public String generateToken(Authentication authentication) {
        // Only call this method AFTER AuthenticationManager authenticates successfully.
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new IllegalArgumentException("Không thể cấp token khi chưa xác thực");
        }

        // The role comes from Spring Security, never from the client's JSON body.
        String role = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> authority.startsWith("ROLE_"))
                .map(authority -> authority.substring(5))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Tài khoản chưa có role"));

        Date issuedAt = new Date();
        Date expiresAt = new Date(issuedAt.getTime() + expirationMillis);

        return Jwts.builder()
                .setSubject(authentication.getName())
                .claim("role", role)
                .setIssuedAt(issuedAt)
                .setExpiration(expiresAt)
                // Use byte[]: the String overload in JJWT 0.9.1 expects Base64.
                .signWith(SignatureAlgorithm.HS256, secretKey)
                .compact();
    }

    public Claims parseToken(String token) {
        // parseClaimsJws verifies the signature and rejects expired tokens.
        Jws<Claims> signedToken = Jwts.parser()
                .setSigningKey(secretKey)
                .parseClaimsJws(token);

        if (!SignatureAlgorithm.HS256.getValue().equals(signedToken.getHeader().getAlgorithm())) {
            throw new UnsupportedJwtException("Chỉ chấp nhận thuật toán HS256");
        }

        Claims claims = signedToken.getBody();
        Object role = claims.get("role");
        if (claims.getSubject() == null || claims.getSubject().isBlank()
                || claims.getIssuedAt() == null || claims.getExpiration() == null
                || !(role instanceof String roleName) || roleName.isBlank()) {
            throw new MalformedJwtException("Token thiếu sub, role, iat hoặc exp");
        }
        return claims;
    }
}
