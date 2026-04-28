package com.example.chat.auth.controller;

import com.example.chat.auth.dto.request.LoginRequest;
import com.example.chat.auth.dto.request.RegisterRequest;
import com.example.chat.auth.dto.response.AuthResponse;
import com.example.chat.auth.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        log.info("REST request to register new user: {}", request.getUsername());
        AuthResponse tokens = authService.register(request);
        log.info("Successfully processed registration for user: {}", request.getUsername());
        return createCookieResponse(tokens);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        log.info("REST request to login user: {}", request.getUsername());
        AuthResponse tokens = authService.login(request);
        log.info("Successfully processed login for user: {}", request.getUsername());
        return createCookieResponse(tokens);
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refreshToken(@CookieValue(name = "refreshToken") String refreshToken) {
        log.info("REST request to refresh access token");
        AuthResponse newTokens = authService.refreshToken(refreshToken);
        log.info("Successfully refreshed token for user: {}", newTokens.getUsername());

        AuthResponse responseBody = AuthResponse.builder()
                .accessToken(newTokens.getAccessToken())
                .username(newTokens.getUsername())
                .build();

        return ResponseEntity.ok(responseBody);
    }

    @PostMapping("/logout")
    public ResponseEntity<String> logout(@CookieValue(name = "refreshToken", required = false) String refreshToken) {
        log.info("REST request to logout user");

        if (refreshToken != null) {
            authService.logout(refreshToken);
        } else {
            log.debug("Logout request received but no refresh token cookie was found.");
        }

        ResponseCookie deleteCookie = ResponseCookie.from("refreshToken", "")
                .httpOnly(true)
                .secure(false)
                .path("/api/auth/refresh")
                .maxAge(0)
                .sameSite("Strict")
                .build();

        log.info("Successfully processed logout, returning cleared cookie");
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, deleteCookie.toString())
                .body("Logged out successfully");
    }

    private ResponseEntity<AuthResponse> createCookieResponse(AuthResponse tokens) {
        ResponseCookie springCookie = ResponseCookie.from("refreshToken", tokens.getRefreshToken())
                .httpOnly(true)
                .secure(false)
                .path("/api/auth/refresh")
                .maxAge(7 * 24 * 60 * 60)
                .sameSite("Strict")
                .build();

        AuthResponse responseBody = AuthResponse.builder()
                .accessToken(tokens.getAccessToken())
                .username(tokens.getUsername())
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, springCookie.toString())
                .body(responseBody);
    }
}