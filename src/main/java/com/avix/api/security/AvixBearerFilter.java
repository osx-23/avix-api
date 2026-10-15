package com.avix.api.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public final class AvixBearerFilter extends OncePerRequestFilter {
    private final AvixAuth auth;
    private final ObjectMapper mapper;

    public AvixBearerFilter(AvixAuth auth, ObjectMapper mapper) {
        this.auth = auth;
        this.mapper = mapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if (request.getRequestURI().startsWith("/api/v1/auth/")) {
            response.setHeader("Cache-Control", "no-store");
        }

        String bearer = request.getHeader("Authorization");
        if (bearer != null) {
            try {
                AvixAuth.Principal principal = auth.require(bearer);
                var authorities = List.of(
                        new SimpleGrantedAuthority("ROLE_" + principal.rol()),
                        new SimpleGrantedAuthority("CLIENT_" + principal.cliente()));
                var authentication = new UsernamePasswordAuthenticationToken(principal, null, authorities);
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContext context = SecurityContextHolder.createEmptyContext();
                context.setAuthentication(authentication);
                SecurityContextHolder.setContext(context);
            } catch (ResponseStatusException ex) {
                SecurityContextHolder.clearContext();
                int status = ex.getStatusCode().value();
                String message = status == 401 ? "Credenciales incorrectas o sesión expirada."
                        : status == 403 ? "No tienes permiso para esta operación."
                        : status >= 500 ? "Servicio de autenticación temporalmente no disponible."
                        : "Autenticación inválida.";
                error(response, mapper, status, message);
                return;
            }
        }
        try {
            chain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    public static void error(HttpServletResponse response, ObjectMapper mapper, int status, String message)
            throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json");
        response.setHeader("Cache-Control", "no-store");
        mapper.writeValue(response.getWriter(), Map.of("status", status, "mensaje", message));
    }
}
