package com.attendance.service;

import com.attendance.entity.AttendanceSession;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

/**
 * Generates a fresh signed token for a session every ROTATION_SECONDS.
 * Each token embeds: sessionId, issuedAt, a random nonce, and an
 * expiry. The frontend (teacher's display) just calls
 * GET /api/qr/{sessionId}/current every few seconds and re-renders
 * the QR code from the returned token string - so a photo of the
 * screen goes stale almost immediately.
 */
@Service
public class QrRotationService {

    public static final int ROTATION_SECONDS = 12; // 10-15s window as required
    private static final int VALIDITY_SECONDS = ROTATION_SECONDS + 3; // small grace period

    public static String generateSecret() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }

    private SecretKey keyFor(AttendanceSession session) {
        byte[] decoded = Base64.getDecoder().decode(session.getQrSecret());
        return Keys.hmacShaKeyFor(decoded);
    }

    /** Builds the token that should currently be displayed for this session. */
    public String currentToken(AttendanceSession session) {
        Instant now = Instant.now();
        // "bucket" the time so the same token is returned for the whole
        // rotation window instead of generating a new one on every poll
        long bucket = now.getEpochSecond() / ROTATION_SECONDS;

        String nonce = UUID.randomUUID().toString();

        return Jwts.builder()
                .claim("sid", session.getId())
                .claim("bucket", bucket)
                .claim("nonce", nonce)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(VALIDITY_SECONDS, ChronoUnit.SECONDS)))
                .signWith(keyFor(session))
                .compact();
    }

    /**
     * Validates a scanned token against the session's secret and expiry.
     * Returns the parsed claims if valid, throws otherwise.
     */
    public Claims validate(AttendanceSession session, String token) {
        return Jwts.parser()
                .verifyWith(keyFor(session))
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
