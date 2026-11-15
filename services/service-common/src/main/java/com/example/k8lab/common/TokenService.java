package com.example.k8lab.common;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class TokenService {
    private static final String HEADER = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9";
    private static final Pattern CLAIMS =
            Pattern.compile("\\{\"sub\":\"([0-9a-fA-F-]{36})\",\"exp\":([0-9]+)\\}");
    private final byte[] secret;

    public TokenService(@Value("${app.jwt.secret}") String secret) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        if (this.secret.length < 32) {
            throw new IllegalArgumentException("JWT secret must be at least 32 bytes");
        }
    }

    public String issue(String userId) {
        String subject = UUID.fromString(userId).toString();
        long expiresAt = System.currentTimeMillis() / 1000 + 3600;
        String payload = Base64.getUrlEncoder().withoutPadding().encodeToString(
                ("{\"sub\":\"" + subject + "\",\"exp\":" + expiresAt + "}")
                        .getBytes(StandardCharsets.UTF_8));
        String signingInput = HEADER + "." + payload;
        return signingInput + "." + sign(signingInput);
    }

    public String verify(String token) {
        String[] parts = token.split("\\.", -1);
        if (parts.length != 3 || !HEADER.equals(parts[0])) {
            throw new IllegalArgumentException("Invalid bearer token");
        }

        String expectedSignature = sign(parts[0] + "." + parts[1]);
        byte[] expected = expectedSignature.getBytes(StandardCharsets.US_ASCII);
        byte[] actual = parts[2].getBytes(StandardCharsets.US_ASCII);
        if (!MessageDigest.isEqual(expected, actual)) {
            throw new IllegalArgumentException("Invalid bearer token");
        }

        try {
            String claims = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            Matcher matcher = CLAIMS.matcher(claims);
            if (!matcher.matches() || Long.parseLong(matcher.group(2)) <= System.currentTimeMillis() / 1000) {
                throw new IllegalArgumentException("Expired or invalid bearer token");
            }
            return UUID.fromString(matcher.group(1)).toString();
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Expired or invalid bearer token", exception);
        }
    }

    private String sign(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(value.getBytes(StandardCharsets.US_ASCII)));
        } catch (java.security.GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to sign bearer token", exception);
        }
    }
}
