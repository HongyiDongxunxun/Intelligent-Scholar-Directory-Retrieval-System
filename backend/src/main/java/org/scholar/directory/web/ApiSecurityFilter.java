package org.scholar.directory.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ApiSecurityFilter extends OncePerRequestFilter {
    private final byte[] apiToken;
    private final long maxRequestBytes;

    public ApiSecurityFilter(@Value("${app.security.api-token:}") String apiToken,
                             @Value("${app.security.max-request-bytes:65536}") long maxRequestBytes) {
        this.apiToken = apiToken.getBytes(StandardCharsets.UTF_8);
        this.maxRequestBytes = maxRequestBytes;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        addSecurityHeaders(response);
        String requestUri = request.getRequestURI();
        boolean protectedPath = requestUri.equals("/api") || requestUri.startsWith("/api/")
                || requestUri.equals("/actuator") || requestUri.startsWith("/actuator/");
        if (protectedPath) {
            response.setHeader("Cache-Control", "no-store");
            response.addHeader("Vary", "Authorization");
            long contentLength = request.getContentLengthLong();
            if (contentLength > maxRequestBytes) {
                reject(response, HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE,
                        "PAYLOAD_TOO_LARGE", "Request body exceeds the configured limit.");
                return;
            }
            if (apiToken.length > 0 && !"OPTIONS".equalsIgnoreCase(request.getMethod()) && !authorized(request)) {
                reject(response, HttpServletResponse.SC_UNAUTHORIZED,
                        "UNAUTHORIZED", "A valid bearer token is required.");
                return;
            }
        }
        chain.doFilter(request, response);
    }

    private boolean authorized(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) return false;
        byte[] supplied = header.substring(7).getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(apiToken, supplied);
    }

    private static void addSecurityHeaders(HttpServletResponse response) {
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("X-Frame-Options", "DENY");
        response.setHeader("Referrer-Policy", "no-referrer");
        response.setHeader("Permissions-Policy", "camera=(), microphone=(), geolocation=()");
    }

    private static void reject(HttpServletResponse response, int status, String code, String message) throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json");
        response.getWriter().write("{\"success\":false,\"code\":\"" + code
                + "\",\"message\":\"" + message + "\",\"data\":null}");
    }
}
