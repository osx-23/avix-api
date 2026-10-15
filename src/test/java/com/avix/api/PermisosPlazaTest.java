package com.avix.api;

import com.avix.api.data.SupabaseData;
import com.avix.api.security.AvixAuth;
import com.avix.api.security.PermisosPlaza;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PermisosPlazaTest {
    private final ObjectMapper json=new ObjectMapper();

    @Test void restringeControladorASusPlazas() throws Exception {
        SupabaseData db=mock(SupabaseData.class);
        when(db.select(eq("usuario_plazas"),anyMap())).thenReturn(json.readTree("[{\"plaza_id\":3},{\"plaza_id\":7}]"));
        PermisosPlaza permisos=new PermisosPlaza(db);
        AvixAuth.Principal controller=new AvixAuth.Principal("uuid-controlador","CTRL001","CONTROLADOR","WEB");
        assertEquals("in.(3,7)",permisos.filtro(controller));
        assertDoesNotThrow(()->permisos.requireAsignada(controller,3));
        assertThrows(ResponseStatusException.class,()->permisos.requireAsignada(controller,9));
    }

    @Test void agenteSoloVeSusRegistros() throws Exception {
        PermisosPlaza permisos=new PermisosPlaza(mock(SupabaseData.class));
        AvixAuth.Principal agente=new AvixAuth.Principal("agente-1","AG001","AGENTE","MOVIL");
        assertDoesNotThrow(()->permisos.puedeVer(agente,json.readTree("{\"registrado_por\":\"agente-1\",\"plaza_id\":1}")));
        assertThrows(ResponseStatusException.class,()->permisos.puedeVer(agente,json.readTree("{\"registrado_por\":\"otro\",\"plaza_id\":1}")));
        assertThrows(ResponseStatusException.class,()->permisos.puedeModificar(agente,json.readTree("{\"registrado_por\":\"agente-1\",\"plaza_id\":1}")));
    }

    @Test void controladorSinAsignacionesNoVeDatos() throws Exception {
        SupabaseData db=mock(SupabaseData.class);
        when(db.select(eq("usuario_plazas"),anyMap())).thenReturn(json.readTree("[]"));
        PermisosPlaza permisos=new PermisosPlaza(db);
        AvixAuth.Principal controller=new AvixAuth.Principal("controlador","C001","CONTROLADOR","WEB");
        assertEquals("eq.-1",permisos.filtro(controller));
        assertThrows(ResponseStatusException.class,()->permisos.requireAsignada(controller,1));
    }
}
