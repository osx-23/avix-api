package com.avix.api;

import com.avix.api.data.SupabaseData;
import com.avix.api.security.AvixAuth;
import com.avix.api.security.PermisosPlaza;
import com.avix.api.service.RegistroService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RegistroConsultaRangoTest {
  private final ObjectMapper json = new ObjectMapper();
  private final AvixAuth.Principal supervisor = new AvixAuth.Principal("u1","SUP001","SUPERVISOR","WEB");
  private final AvixAuth.Principal controlador = new AvixAuth.Principal("u2","CTRL001","CONTROLADOR","WEB");

  @Test void consultaMensualUnaSolaPeticionConFiltroEnServidor() throws Exception {
    SupabaseData db=mock(SupabaseData.class);
    RegistroService service=new RegistroService(db,mock(PermisosPlaza.class));
    when(db.select(eq("registros"),anyMap())).thenReturn(json.readTree("[]"));
    service.consultarRango(supervisor,0,500,"2026-10-01T06:00:00-05:00",
      "2026-11-01T06:00:00-05:00",null,null,null);
    @SuppressWarnings("unchecked")
    var filters=org.mockito.ArgumentCaptor.forClass(Map.class);
    verify(db,times(1)).select(eq("registros"),filters.capture());
    Map<String,String> query=(Map<String,String>)filters.getValue();
    assertEquals("500",query.get("limit"));
    assertTrue(query.get("and").contains("ocurrido_en.gte."));
    assertTrue(query.get("and").contains("ocurrido_en.lt."));
    assertEquals("ocurrido_en.desc,id.desc",query.get("order"));
  }

  @Test void controladorFiltraPlazasAntesDePaginar() throws Exception {
    SupabaseData db=mock(SupabaseData.class);
    PermisosPlaza permisos=mock(PermisosPlaza.class);
    when(permisos.filtro(controlador)).thenReturn("in.(1,2)");
    when(db.select(eq("registros"),anyMap())).thenReturn(json.readTree("[]"));
    RegistroService service=new RegistroService(db,permisos);
    service.consultarRango(controlador,1,100,"2026-10-14T06:00:00-05:00",
      "2026-10-15T06:00:00-05:00",null,null,null);
    @SuppressWarnings("unchecked")
    var filters=org.mockito.ArgumentCaptor.forClass(Map.class);
    verify(db).select(eq("registros"),filters.capture());
    Map<String,String> query=(Map<String,String>)filters.getValue();
    assertEquals("in.(1,2)",query.get("plaza_id"));
    assertEquals("100",query.get("offset"));
  }

  @Test void fechasSinZonaYRangoExcesivoSeRechazan() {
    SupabaseData db=mock(SupabaseData.class);
    RegistroService service=new RegistroService(db,mock(PermisosPlaza.class));
    assertThrows(ResponseStatusException.class,()->service.consultarRango(
      supervisor,0,500,"2026-10-01T06:00:00","2026-10-02T06:00:00-05:00",null,null,null));
    assertThrows(ResponseStatusException.class,()->service.consultarRango(
      supervisor,0,500,"2026-09-01T06:00:00-05:00","2026-11-01T06:00:00-05:00",null,null,null));
    verify(db,never()).select(anyString(),anyMap());
  }

  @Test void agenteSoloConsultaRegistrosPropios() throws Exception {
    SupabaseData db=mock(SupabaseData.class);
    when(db.select(eq("registros"),anyMap())).thenReturn(json.readTree("[]"));
    var agente=new AvixAuth.Principal("agente1","AG001","AGENTE","MOVIL");
    new RegistroService(db,mock(PermisosPlaza.class)).consultarRango(
      agente,0,50,"2026-10-14T06:00:00-05:00","2026-10-15T06:00:00-05:00",null,null,null);
    @SuppressWarnings("unchecked")
    var filters=org.mockito.ArgumentCaptor.forClass(Map.class);
    verify(db).select(eq("registros"),filters.capture());
    assertEquals("eq.agente1",((Map<?,?>)filters.getValue()).get("registrado_por"));
  }
}
