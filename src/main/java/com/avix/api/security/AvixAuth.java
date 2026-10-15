package com.avix.api.security;

import com.avix.api.data.SupabaseData;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AvixAuth {
    public record Principal(String id, String codigo, String rol, String cliente) {}
    private final SupabaseData db;
    private final String setupSecret;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(12);
    private final SecureRandom secureRandom = new SecureRandom();
    private final ConcurrentHashMap<String, Deque<Long>> loginAttempts = new ConcurrentHashMap<>();

    public AvixAuth(SupabaseData db, @Value("$"+"{avix.setup-secret:}") String setupSecret) {
        this.db = db;
        this.setupSecret = setupSecret;
    }

    public static ResponseStatusException unauthenticated() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Credenciales incorrectas o sesión expirada.");
    }

    public static ResponseStatusException forbidden() {
        return new ResponseStatusException(HttpStatus.FORBIDDEN, "No tienes permiso para esta operación.");
    }

    public static ResponseStatusException bad(String reason) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, reason);
    }

    public static void checkCode(String code) {
        if (code == null || !code.matches("[A-Za-z0-9_-]{3,30}")) throw bad("Código de usuario inválido.");
    }

    private static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }

    // Protección elemental; en producción multi-instancia reemplazar con Redis/WAF.
    private void throttle(String ip, String code, String client) {
        String key = ip + ":" + code + ":" + client;
        long now = System.currentTimeMillis();
        Deque<Long> entries = loginAttempts.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        synchronized (entries) {
            while (!entries.isEmpty() && now - entries.peekFirst() > 900_000L) entries.removeFirst();
            if (entries.size() >= 5) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Demasiados intentos de acceso.");
            entries.addLast(now);
        }
    }

    public Map<String, Object> login(String codigo, String password, String client, String ip) {
        checkCode(codigo);
        throttle(ip, codigo, client);
        JsonNode user = SupabaseData.first(db.select("usuarios", Map.of(
                "codigo", "eq." + codigo, "activo", "eq.true",
                "select", "id,codigo,nombres,rol,password_hash", "limit", "1")));
        if (user == null) throw unauthenticated();

        String rol = user.path("rol").asText();
        if ("WEB".equals(client)) {
            if (!rol.equals("SUPERVISOR") && !rol.equals("CONTROLADOR")) throw unauthenticated();
            String storedHash = user.path("password_hash").asText("");
            if (password == null || storedHash.isBlank() || !encoder.matches(password, storedHash))
                throw unauthenticated();
        } else if (!"MOVIL".equals(client)) throw unauthenticated();

        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        OffsetDateTime expiry = OffsetDateTime.now(ZoneOffset.UTC).plusHours(client.equals("WEB") ? 8 : 12);
        db.insert("sesiones", Map.of(
                "usuario_id", user.path("id").asText(), "tipo_cliente", client,
                "token_hash", hash(token), "expira_en", expiry.toString()));

        return Map.of("token", token, "tipo", "Bearer", "expiraEn", expiry,
                "usuario", Map.of("id", user.path("id").asText(),
                    "codigo", user.path("codigo").asText(), "nombres", user.path("nombres").asText(),
                    "rol", rol));
    }

    public Principal require(String bearer) {
        if (bearer == null || !bearer.matches("Bearer [A-Za-z0-9_-]{43}")) throw unauthenticated();
        String tokenHash = hash(bearer.substring(7));
        JsonNode session = SupabaseData.first(db.select("sesiones", Map.of(
                "token_hash", "eq." + tokenHash, "revocado_en", "is.null",
                "select", "usuario_id,tipo_cliente,expira_en", "limit", "1")));
        if (session == null) throw unauthenticated();
        try {
            if (!OffsetDateTime.parse(session.path("expira_en").asText()).isAfter(OffsetDateTime.now(ZoneOffset.UTC)))
                throw unauthenticated();
        } catch (java.time.format.DateTimeParseException ex) { throw unauthenticated(); }

        JsonNode user = SupabaseData.first(db.select("usuarios", Map.of(
                "id", "eq." + session.path("usuario_id").asText(), "activo", "eq.true",
                "select", "id,codigo,rol", "limit", "1")));
        if (user == null) throw unauthenticated();
        String rol = user.path("rol").asText();
        String client = session.path("tipo_cliente").asText();
        if (client.equals("WEB") && rol.equals("AGENTE")) throw forbidden();
        return new Principal(user.path("id").asText(), user.path("codigo").asText(), rol, client);
    }

    public Principal admin(String bearer) {
        Principal p = require(bearer);
        if (!p.rol().equals("SUPERVISOR") || !p.cliente().equals("WEB")) throw forbidden();
        return p;
    }

    public void logout(String bearer) {
        require(bearer);
        db.update("sesiones", Map.of("token_hash", "eq." + hash(bearer.substring(7))),
                Map.of("revocado_en", OffsetDateTime.now(ZoneOffset.UTC).toString()));
    }

    public JsonNode createUser(String codigo, String nombres, String apellidos, String rol, String password) {
        checkCode(codigo);
        if (nombres == null || nombres.isBlank() || nombres.length() > 120 ||
                apellidos != null && apellidos.length() > 120) throw bad("Nombres inválidos.");
        if (rol == null || !Set.of("AGENTE", "CONTROLADOR", "SUPERVISOR").contains(rol))
            throw bad("Rol inválido.");
        if (!rol.equals("AGENTE") && (password == null || password.length() < 10))
            throw bad("La contraseña web debe tener al menos 10 caracteres.");

        Map<String, Object> values = new HashMap<>();
        values.put("codigo", codigo);
        values.put("nombres", nombres.trim());
        values.put("apellidos", apellidos == null ? "" : apellidos.trim());
        values.put("rol", rol);
        values.put("password_hash", rol.equals("AGENTE") ? null : encoder.encode(password));

        JsonNode created = SupabaseData.first(db.insert("usuarios", values));
        if (created == null) throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Sin confirmación de usuario.");
        return db.select("usuarios", Map.of("id", "eq." + created.path("id").asText(),
                "select", "id,codigo,nombres,apellidos,rol,activo", "limit", "1"));
    }

    public JsonNode bootstrap(String supplied, String codigo, String password) {
        if (setupSecret == null || setupSecret.length() < 32 || supplied == null ||
                !MessageDigest.isEqual(setupSecret.getBytes(StandardCharsets.UTF_8),
                        supplied.getBytes(StandardCharsets.UTF_8))) throw forbidden();
        JsonNode existing = SupabaseData.first(db.select("usuarios", Map.of(
                "rol", "eq.SUPERVISOR", "select", "id", "limit", "1")));
        if (existing != null) throw forbidden();
        return createUser(codigo, "Supervisor", "", "SUPERVISOR", password);
    }
}
