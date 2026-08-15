package com.betai.security;

import com.betai.config.SecurityProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

@Component
@RequiredArgsConstructor
public class AdminApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private final SecurityProperties securityProperties;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/v1/admin/")
                || HttpMethod.OPTIONS.matches(request.getMethod());
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String expectedKey = securityProperties.adminApiKey();
        String headerName = StringUtils.hasText(securityProperties.adminHeaderName())
                ? securityProperties.adminHeaderName()
                : "X-BETAI-ADMIN-KEY";

        if (!StringUtils.hasText(expectedKey)) {
            unauthorized(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE, "Admin API key is not configured.");
            return;
        }

        String providedKey = request.getHeader(headerName);
        if (!matches(expectedKey, providedKey)) {
            response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "ApiKey realm=\"bet-ai-admin\"");
            unauthorized(response, HttpServletResponse.SC_UNAUTHORIZED, "Admin API key is missing or invalid.");
            return;
        }

        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                "bet-ai-admin",
                null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);
        filterChain.doFilter(request, response);
    }

    private boolean matches(String expectedKey, String providedKey) {
        if (!StringUtils.hasText(providedKey)) {
            return false;
        }
        byte[] expected = expectedKey.getBytes(StandardCharsets.UTF_8);
        byte[] provided = providedKey.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, provided);
    }

    private void unauthorized(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"status\":" + status + ",\"error\":\"Unauthorized\",\"message\":\"" + message + "\"}");
    }
}
