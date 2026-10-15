package com.avix.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class AvixApiApplicationTests {
    @Autowired MockMvc mvc;

    @Test void statusPublico() throws Exception {
        mvc.perform(get("/api/v1/status")).andExpect(status().isOk())
                .andExpect(jsonPath("$.api").value("ok"))
                .andExpect(jsonPath("$.version").value("0.1.0"))
                .andExpect(jsonPath("$.supabaseConfigurado").isBoolean());
    }

    @Test void registrosExigenSesion() throws Exception {
        mvc.perform(get("/api/v1/registros")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/registros").contentType(MediaType.APPLICATION_JSON)
                .content("{\"plaza\":\"P1\",\"via\":151,\"accion\":\"FUGA\",\"placa\":\"X1Z505\",\"idempotenciaId\":\"00000000-0000-4000-8000-000000000001\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test void catalogosExigenSesion() throws Exception {
        mvc.perform(get("/api/v1/catalogos/plazas")).andExpect(status().isUnauthorized());
    }

    @Test void altaExigeSupervisor() throws Exception {
        mvc.perform(post("/api/v1/admin/usuarios").contentType(MediaType.APPLICATION_JSON)
                .content("{\"codigo\":\"AG001\",\"nombres\":\"Agente\",\"rol\":\"AGENTE\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test void rechazaCodigoInvalidoSinConsultarSupabase() throws Exception {
        mvc.perform(post("/api/v1/auth/movil").contentType(MediaType.APPLICATION_JSON)
                .content("{\"codigo\":\"x\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test void rechazaJsonIncorrecto() throws Exception {
        mvc.perform(post("/api/v1/auth/web").contentType(MediaType.APPLICATION_JSON)
                .content("{"))
                .andExpect(status().isBadRequest());
    }
}
