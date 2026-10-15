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
class SeguridadEndpointsTest {
    @Autowired MockMvc mvc;
    @Test void detallesYAuditoriaRequierenSesion()throws Exception{
        String id="75d4013d-fd9a-4de8-a4c0-a103cc9f61b7";
        mvc.perform(get("/api/v1/registros/"+id)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/registros/"+id+"/auditoria")).andExpect(status().isUnauthorized());
        mvc.perform(patch("/api/v1/registros/"+id).contentType(MediaType.APPLICATION_JSON)
         .content("{\"cambios\":{\"placa\":\"ABC123\"},\"motivo\":\"Corregir placa\"}"))
         .andExpect(status().isUnauthorized());
        mvc.perform(patch("/api/v1/registros/"+id+"/anular").contentType(MediaType.APPLICATION_JSON)
         .content("{\"motivo\":\"Registro incorrecto\"}")).andExpect(status().isUnauthorized());
    }
    @Test void administracionRequiereSupervisor()throws Exception{
        mvc.perform(get("/api/v1/admin/usuarios")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/admin/vias")).andExpect(status().isUnauthorized());
        mvc.perform(patch("/api/v1/admin/usuarios/75d4013d-fd9a-4de8-a4c0-a103cc9f61b7")
         .contentType(MediaType.APPLICATION_JSON).content("{\"activo\":false}"))
         .andExpect(status().isUnauthorized());
    }
}
