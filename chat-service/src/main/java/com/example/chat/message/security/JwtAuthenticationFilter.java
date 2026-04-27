package com.example.chat.message.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.io.Decoders;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

@Slf4j
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    @Value("${APP_JWT_SECRET}")
    private String jwtSecret;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        try {
            log.debug("Processing request for URI: {}", request.getRequestURI());
            String header = request.getHeader("Authorization");

            if (header != null && header.startsWith("Bearer ")) {
                log.debug("JWT Token found in request. Attempting to parse claims.");
                String token = header.substring(7);

                byte[] keyBytes = Decoders.BASE64.decode(jwtSecret);

                Claims claims = Jwts.parser()
                        .verifyWith(Keys.hmacShaKeyFor(keyBytes))
                        .build()
                        .parseSignedClaims(token)
                        .getPayload();

                String username = claims.getSubject();

                if (username != null) {
                    UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                            username, null, Collections.emptyList());
                    SecurityContextHolder.getContext().setAuthentication(auth);
                    log.debug("Successfully authenticated user '{}' for URI: {}", username, request.getRequestURI());
                }
            } else {
                log.debug("No JWT token found in request headers for URI: {}", request.getRequestURI());
            }
        } catch (Exception e) {
            log.warn("Cannot set user authentication: {}", e.getMessage());
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }
}