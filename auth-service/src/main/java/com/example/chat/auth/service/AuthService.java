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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
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
        return createAuthSession(user);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new InvalidCredentialsException("Invalid username or password"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new InvalidCredentialsException("Invalid username or password");
        }

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
        String newAccessToken = jwtProvider.generateAccessToken(user.getUsername());

        return AuthResponse.builder()
                .accessToken(newAccessToken)
                .username(user.getUsername())
                .build();
    }
}