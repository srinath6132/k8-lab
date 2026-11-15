package com.example.k8lab.users;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class UserControllerTest {
    private final UserController users = new UserController();

    @Test
    void registersProfileAndAuthenticatesHashedPassword() {
        var response = users.register(
                new UserController.Registration("Sam Shopper", "SAM@example.com", "change-me-123"));

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        var profile = response.getBody();
        assertNotNull(profile);
        assertEquals("sam@example.com", profile.email());
        assertEquals(profile, users.authenticate(
                new UserController.Credentials("sam@example.com", "change-me-123")));
    }

    @Test
    void rejectsDuplicateEmailWithoutCaseSensitivity() {
        users.register(new UserController.Registration("Sam Shopper", "sam@example.com", "change-me-123"));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> users.register(
                        new UserController.Registration("Another Shopper", "SAM@example.com", "other-pass-123")));

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
    }
}
