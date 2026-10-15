package com.avix.api;

import com.avix.api.data.SupabaseData;
import com.avix.api.security.AvixAuth;
import com.avix.api.security.PermisosPlaza;
import com.avix.api.web.CatalogController;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PlazasAsignadasMovilTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test void supervisorConPlazaP2ObtieneSoloSuAsignacion() throws Exception {
        var auth = mock(AvixAuth.class);
        var db = mock(SupabaseData.class);
        var permisos = mock(PermisosPlaza.class);
        var principal = new AvixAuth.Principal("uuid", "2396", "SUPERVISOR", "MOVIL");

        when(auth.require("Bearer token")).thenReturn(principal);
        when(permisos.asignadas(principal)).thenReturn(List.of(2L));
        when(db.select(eq("plazas"), anyMap()))
                .thenReturn(mapper.readTree("[{\"id\":2,\"codigo\":\"P2\",\"activa\":true}]"));

        var response = new CatalogController(auth, db, permisos).plazasAsignadas("Bearer token");
        assertEquals("P2", response.get(0).path("codigo").asText());
        verify(db).select(eq("plazas"), argThat((Map<String, String> filter) ->
                "in.(2)".equals(filter.get("id")) && "eq.true".equals(filter.get("activa"))));
    }

    @Test void usuarioSinAsignacionesRecibeListaVacia() {
        var auth = mock(AvixAuth.class);
        var db = mock(SupabaseData.class);
        var permisos = mock(PermisosPlaza.class);
        var principal = new AvixAuth.Principal("uuid", "2396", "SUPERVISOR", "MOVIL");
        when(auth.require(anyString())).thenReturn(principal);
        when(permisos.asignadas(principal)).thenReturn(List.of());

        var response = new CatalogController(auth, db, permisos).plazasAsignadas("Bearer token");
        assertTrue(response.isArray());
        assertEquals(0, response.size());
        verify(db, never()).select(eq("plazas"), anyMap());
    }
}
