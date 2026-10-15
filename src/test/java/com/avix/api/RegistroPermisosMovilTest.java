package com.avix.api;

import com.avix.api.data.SupabaseData;
import com.avix.api.security.AvixAuth;
import com.avix.api.security.PermisosPlaza;
import com.avix.api.service.RegistroService;
import com.avix.api.web.RegistroController.RegistroNuevo;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RegistroPermisosMovilTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private SupabaseData db;
    private RegistroService servicio;

    @BeforeEach
    void configurar() throws Exception {
        db = mock(SupabaseData.class);
        servicio = new RegistroService(db, new PermisosPlaza(db));
        when(db.select(anyString(), anyMap())).thenAnswer(invocation -> {
            String tabla = invocation.getArgument(0);
            return switch (tabla) {
                case "registros" -> mapper.readTree("[]");
                case "plazas" -> mapper.readTree("[{\"id\":1}]");
                case "vias" -> mapper.readTree("[{\"id\":11}]");
                case "acciones" -> mapper.readTree("[{\"id\":2}]");
                case "turnos" -> mapper.readTree("[{\"id\":3}]");
                case "usuario_plazas" -> mapper.readTree("[]");
                default -> throw new AssertionError("Consulta inesperada: " + tabla);
            };
        });
        when(db.insert(eq("registros"), anyMap()))
                .thenReturn(mapper.readTree("[{\"id\":\"0d477b30-3963-4469-a7df-6024286d25bd\"}]"));
    }

    private RegistroNuevo registro() {
        return new RegistroNuevo(
                "P1", 151, "FUGA", "X1Z505", UUID.randomUUID(),
                OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(2), "VOZ", null);
    }

    @Test
    void supervisorConSesionMovilRegistraSinAsignacionDePlaza() {
        var user = new AvixAuth.Principal(UUID.randomUUID().toString(), "SUP001", "SUPERVISOR", "MOVIL");
        JsonNode result = servicio.registrar(user, registro());

        assertEquals("0d477b30-3963-4469-a7df-6024286d25bd", result.path("id").asText());
        verify(db, never()).select(eq("usuario_plazas"), anyMap());
        verify(db).insert(eq("registros"), argThat((Map<String, Object> body) ->
                body.get("plaza_id").equals(1L)
                && body.get("via_id").equals(11L)
                && body.get("registrado_por").equals(user.id())));
    }

    @Test
    void agenteSinPlazaAsignadaConservaProhibicion() {
        var user = new AvixAuth.Principal(UUID.randomUUID().toString(), "AG001", "AGENTE", "MOVIL");
        var error = assertThrows(ResponseStatusException.class, () -> servicio.registrar(user, registro()));
        assertEquals(403, error.getStatusCode().value());
        verify(db, never()).insert(eq("registros"), anyMap());
    }

    @Test
    void controladorConPlazaAsignadaPuedeRegistrar() throws Exception {
        var user = new AvixAuth.Principal(UUID.randomUUID().toString(), "CTRL001", "CONTROLADOR", "MOVIL");
        when(db.select(eq("usuario_plazas"), anyMap()))
                .thenReturn(mapper.readTree("[{\"plaza_id\":1}]"));
        assertNotNull(servicio.registrar(user, registro()));
        verify(db).insert(eq("registros"), anyMap());
    }

    @Test
    void tokenWebNoPuedeRegistrar() {
        var user = new AvixAuth.Principal(UUID.randomUUID().toString(), "SUP001", "SUPERVISOR", "WEB");
        var error = assertThrows(ResponseStatusException.class, () -> servicio.registrar(user, registro()));
        assertEquals(403, error.getStatusCode().value());
        verify(db, never()).insert(eq("registros"), anyMap());
    }
}
