package com.example.k8lab.users;

import com.example.k8lab.common.BearerTokenFilter;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping
public class UserController {
    private final Map<String, Account> usersByEmail = new ConcurrentHashMap<>();
    private final Map<String, Account> usersById = new ConcurrentHashMap<>();
    private final BCryptPasswordEncoder passwords = new BCryptPasswordEncoder();

    @PostMapping("/internal/users")
    public ResponseEntity<UserProfile> register(@RequestBody Registration request) {
        String name = required(request.name(), "Name is required");
        String email = normalizeEmail(request.email());
        String password = required(request.password(), "Password is required");
        if (password.length() < 8) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password must be at least 8 characters");
        }

        Account account = new Account(UUID.randomUUID().toString(), email, name, passwords.encode(password));
        if (usersByEmail.putIfAbsent(email, account) != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An account with this email already exists");
        }
        usersById.put(account.id(), account);
        return ResponseEntity.status(HttpStatus.CREATED).body(profile(account));
    }

    @PostMapping("/internal/users/authenticate")
    public UserProfile authenticate(@RequestBody Credentials request) {
        String email = normalizeEmail(request.email());
        Account account = usersByEmail.get(email);
        if (account == null || request.password() == null || !passwords.matches(request.password(), account.passwordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
        }
        return profile(account);
    }

    @GetMapping("/api/users/me")
    public UserProfile currentUser(@RequestAttribute(BearerTokenFilter.USER_ID_ATTRIBUTE) String userId) {
        Account account = usersById.get(userId);
        if (account == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        }
        return profile(account);
    }

    private String normalizeEmail(String value) {
        String email = required(value, "Email is required").toLowerCase(Locale.ROOT);
        if (!email.contains("@") || email.endsWith("@")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A valid email is required");
        }
        return email;
    }

    private String required(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
        return value.trim();
    }

    private UserProfile profile(Account account) {
        return new UserProfile(account.id(), account.email(), account.name());
    }

    public record Registration(String name, String email, String password) {}
    public record Credentials(String email, String password) {}
    public record UserProfile(String id, String email, String name) {}
    private record Account(String id, String email, String name, String passwordHash) {}
}
