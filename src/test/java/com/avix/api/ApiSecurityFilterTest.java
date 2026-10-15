package com.avix.api;

import com.avix.api.data.SupabaseData;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class ApiSecurityFilterTest {
    private static final String BEARER = "Bearer " + "a".repeat(43);

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @MockitoBean SupabaseData db;

    private void autenticar(String rol, String cliente) throws Exception {
        String sesion = "[{\"usuario_id\":\"117c1b17-4fa4-41bb-b5a7-c981563ced91\","
                + "\"tipo_cliente\":\"" + cliente + "\",\"expira_en\":\"2099-10-14T18:00:00Z\"}]";
        String usuario = "[{\"id\":\"117c1b17-4fa4-41bb-b5a7-c981563ced91\","
                + "\"codigo\":\"TEST001\",\"rol\":\"" + rol + "\"}]";
        when(db.select(eq("sesiones"), anyMap())).thenReturn(mapper.readTree(sesion));
        when(db.select(eq("usuarios"), anyMap())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Map<String,String> query = invocation.getArgument(1);
            return query.getOrDefault("select","").equals("id,codigo,rol")
                    ? mapper.readTree(usuario) : mapper.readTree("[]");
        });
    }

    @Test void rutasDesconocidasYPrivadasBloqueadasSinToken() throws Exception {
        mvc.perform(get("/api/v1/registros/consulta").param("desde","2026-10-14T06:00:00-05:00")
                .param("hasta","2026-10-15T06:00:00-05:00"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/admin/usuarios")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/nueva-ruta-futura")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/status")).andExpect(status().isOk());
    }

    @Test void tokenMalformadoEsRechazado() throws Exception {
        mvc.perform(get("/api/v1/admin/usuarios").header("Authorization","Bearer invalid"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test void controladorWebNoPuedeAdministrar() throws Exception {
        autenticar("CONTROLADOR","WEB");
        mvc.perform(get("/api/v1/admin/usuarios").header("Authorization", BEARER))
                .andExpect(status().isForbidden());
    }

    @Test void supervisorMovilNoPuedeAdministrar() throws Exception {
        autenticar("SUPERVISOR","MOVIL");
        mvc.perform(get("/api/v1/admin/usuarios").header("Authorization", BEARER))
                .andExpect(status().isForbidden());
    }

    @Test void supervisorWebPuedeAdministrar() throws Exception {
        autenticar("SUPERVISOR","WEB");
        mvc.perform(get("/api/v1/admin/usuarios").header("Authorization", BEARER))
                .andExpect(status().isOk());
    }

    @Test void clienteWebNoPuedeRegistrarEventosMoviles() throws Exception {
        autenticar("SUPERVISOR","WEB");
        mvc.perform(post("/api/v1/registros").header("Authorization",BEARER)
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test void clienteMovilNoPuedeEditarRegistros() throws Exception {
        autenticar("AGENTE","MOVIL");
        mvc.perform(patch("/api/v1/registros/75d4013d-fd9a-4de8-a4c0-a103cc9f61b7")
                .header("Authorization",BEARER)
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test void respuestasDenegadasNoSeAlmacenanEnCache() throws Exception {
        mvc.perform(get("/api/v1/admin/usuarios"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Cache-Control", "no-store"));
    }

    @Test void camposDeLoginSiguenDisponiblesSinToken() throws Exception {
        mvc.perform(post("/api/v1/auth/web").contentType(MediaType.APPLICATION_JSON)
                .content("{\"codigo\":\"ABC\",\"contrasena\":\"" + "x".repeat(257) + "\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/auth/movil").contentType(MediaType.APPLICATION_JSON)
                .content("{\"codigo\":\"x\"}"))
                .andExpect(status().isBadRequest());
    }
}
