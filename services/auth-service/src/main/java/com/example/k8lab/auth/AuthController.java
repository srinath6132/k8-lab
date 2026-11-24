package com.example.k8lab.auth;

import com.example.k8lab.common.TokenService;
import com.example.k8lab.common.CorrelationIdClientInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final RestClient users;
    private final TokenService tokens;

    public AuthController(
            RestClient.Builder builder,
            TokenService tokens,
            @Value("${app.user-service.url}") String userServiceUrl) {
        this.users = builder
                .requestInterceptor(new CorrelationIdClientInterceptor())
                .baseUrl(userServiceUrl)
                .build();
        this.tokens = tokens;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@RequestBody Registration request) {
        UserProfile user;
        try {
            user = users.post().uri("/internal/users").body(request).retrieve().body(UserProfile.class);
        } catch (RestClientResponseException exception) {
            throw new ResponseStatusException(exception.getStatusCode(), "User service request failed", exception);
        }
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "User service returned an empty response");
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(response(user));
    }

    @PostMapping("/login")
    public AuthResponse login(@RequestBody Credentials request) {
        UserProfile user;
        try {
            user = users.post().uri("/internal/users/authenticate").body(request).retrieve().body(UserProfile.class);
        } catch (RestClientResponseException exception) {
            throw new ResponseStatusException(exception.getStatusCode(), "User service request failed", exception);
        }
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "User service returned an empty response");
        }
        return response(user);
    }

    private AuthResponse response(UserProfile user) {
        return new AuthResponse(tokens.issue(user.id()), user);
    }

    public record Registration(String name, String email, String password) {}
    public record Credentials(String email, String password) {}
    public record UserProfile(String id, String email, String name) {}
    public record AuthResponse(String token, UserProfile user) {}
}
