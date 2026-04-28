package com.example.chat.auth.service;

import com.example.chat.auth.dto.request.LoginRequest;
import com.example.chat.auth.dto.request.RegisterRequest;
import com.example.chat.auth.dto.response.AuthResponse;
import com.example.chat.auth.exception.InvalidCredentialsException;
import com.example.chat.auth.exception.TokenRefreshException;
import com.example.chat.auth.exception.UserAlreadyExistsException;
import com.example.chat.auth.model.RefreshToken;
import com.example.chat.auth.model.User;
import com.example.chat.auth.repository.RefreshTokenRepository;
import com.example.chat.auth.repository.UserRepository;
import com.example.chat.auth.security.JwtProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        log.info("Attempting to register new user with username: {}", request.getUsername());

        if (userRepository.existsByUsername(request.getUsername())) {
            throw new UserAlreadyExistsException("Username is already taken");
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new UserAlreadyExistsException("Email is already in use");
        }

        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .build();

        user = userRepository.save(user);
        log.info("Successfully registered new user: {}", user.getUsername());

        return createAuthSession(user);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        log.info("Login attempt for username: {}", request.getUsername());

        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new InvalidCredentialsException("Invalid username or password"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new InvalidCredentialsException("Invalid username or password");
        }

        log.info("User {} successfully authenticated", user.getUsername());
        return createAuthSession(user);
    }

    private AuthResponse createAuthSession(User user) {
        String accessToken = jwtProvider.generateAccessToken(user.getUsername());

        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .token(UUID.randomUUID().toString())
                .expiryDate(Instant.now().plus(7, ChronoUnit.DAYS))
                .build();

        refreshTokenRepository.deleteByUser(user);
        refreshTokenRepository.save(refreshToken);

        log.debug("Created new auth session (Access & Refresh tokens) for user: {}", user.getUsername());

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken.getToken())
                .username(user.getUsername())
                .build();
    }

    @Transactional(readOnly = true)
    public AuthResponse refreshToken(String requestRefreshToken) {
        RefreshToken refreshToken = refreshTokenRepository.findByToken(requestRefreshToken)
                .orElseThrow(() -> new TokenRefreshException("Refresh token is not in database!"));

        if (refreshToken.getExpiryDate().compareTo(Instant.now()) < 0) {
            throw new TokenRefreshException("Refresh token was expired. Please make a new sign in request.");
        }

        User user = refreshToken.getUser();
        log.info("Generating new access token for user: {} via refresh token", user.getUsername());

        String newAccessToken = jwtProvider.generateAccessToken(user.getUsername());

        return AuthResponse.builder()
                .accessToken(newAccessToken)
                .username(user.getUsername())
                .build();
    }

    @Transactional
    public void logout(String refreshToken) {
        log.info("Processing logout. Deleting refresh token from database.");
        refreshTokenRepository.deleteByToken(refreshToken);
    }
}