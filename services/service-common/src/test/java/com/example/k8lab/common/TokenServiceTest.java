package com.example.k8lab.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class TokenServiceTest {
    private final TokenService tokens =
            new TokenService("local-development-secret-with-at-least-32-bytes");

    @Test
    void verifiesIssuedToken() {
        String userId = "9ac7e218-1374-4c93-8803-cb9aaee2832a";

        assertEquals(userId, tokens.verify(tokens.issue(userId)));
    }

    @Test
    void rejectsTamperedToken() {
        String token = tokens.issue("9ac7e218-1374-4c93-8803-cb9aaee2832a");

        assertThrows(IllegalArgumentException.class, () -> tokens.verify(token + "x"));
    }
}
