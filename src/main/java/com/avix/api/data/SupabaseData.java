package com.avix.api.data;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.StringJoiner;

@Service
public class SupabaseData {
    private final String url;
    private final String secret;
    private final RestClient client;

    public SupabaseData(@Value("$"+"{avix.supabase-url}") String url,
                        @Value("$"+"{avix.supabase-secret:}") String secret) {
        this.url = url.replaceAll("/+$", "");
        this.secret = secret;
        // Reutilizar el cliente HTTP evita recrear infraestructura por cada consulta.
        this.client = RestClient.builder().defaultHeader("apikey", secret == null ? "" : secret).build();
    }

    public boolean configured() {
        return url.startsWith("https://") && secret != null && secret.startsWith("sb_secret_")
                && !secret.equals("sb_secret_REEMPLAZAR");
    }

    private RestClient http() {
        if (!configured()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Falta configurar la clave privada de Supabase en el servidor.");
        return client;
    }

    private URI resource(String table, Map<String, String> query) {
        // Los nombres de tabla solo los define el servidor. No se aceptan nombres arbitrarios del cliente.
        StringJoiner params = new StringJoiner("&");
        query.forEach((k, v) -> params.add(URLEncoder.encode(k, StandardCharsets.UTF_8)
                + "=" + URLEncoder.encode(v, StandardCharsets.UTF_8)));
        return URI.create(url + "/rest/v1/" + table + (query.isEmpty() ? "" : "?" + params));
    }

    public JsonNode select(String table, Map<String, String> query) {
        try {
            return http().get().uri(resource(table, query)).retrieve().body(JsonNode.class);
        } catch (RestClientResponseException ex) { throw upstream(ex); }
    }

    public JsonNode insert(String table, Object payload) {
        try {
            return http().post().uri(resource(table, Map.of()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Prefer", "return=representation")
                    .body(payload).retrieve().body(JsonNode.class);
        } catch (RestClientResponseException ex) { throw upstream(ex); }
    }

    public JsonNode upsert(String table, Map<String, String> query, Object payload) {
        try {
            return http().post().uri(resource(table, query))
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Prefer", "resolution=merge-duplicates,return=representation")
                    .body(payload).retrieve().body(JsonNode.class);
        } catch (RestClientResponseException ex) { throw upstream(ex); }
    }

    public JsonNode update(String table, Map<String, String> query, Object payload) {
        try {
            return http().patch().uri(resource(table, query))
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Prefer", "return=representation")
                    .body(payload).retrieve().body(JsonNode.class);
        } catch (RestClientResponseException ex) { throw upstream(ex); }
    }

    public static JsonNode first(JsonNode array) {
        return array != null && array.isArray() && !array.isEmpty() ? array.get(0) : null;
    }

    private ResponseStatusException upstream(RestClientResponseException ex) {
        // No incluir texto de errores SQL ni claves en respuestas públicas.
        if (ex.getStatusCode().value() == 409)
            return new ResponseStatusException(HttpStatus.CONFLICT, "El recurso ya existe.");
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                "La base de datos de AVIX no pudo completar la operación.", ex);
    }
}
