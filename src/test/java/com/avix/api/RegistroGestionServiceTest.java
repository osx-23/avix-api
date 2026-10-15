package com.avix.api;
import com.avix.api.data.SupabaseData;
import com.avix.api.security.*;
import com.avix.api.service.RegistroGestionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RegistroGestionServiceTest {
 private final ObjectMapper json=new ObjectMapper();
 private final UUID id=UUID.fromString("75d4013d-fd9a-4de8-a4c0-a103cc9f61b7");
 @Test void edicionConAuditoriaAtomica()throws Exception{
  SupabaseData db=mock(SupabaseData.class);
  PermisosPlaza permisos=mock(PermisosPlaza.class);
  RegistroGestionService service=new RegistroGestionService(db,permisos);
  AvixAuth.Principal user=new AvixAuth.Principal("actor","CTRL","CONTROLADOR","WEB");
  when(db.select(eq("registros"),anyMap())).thenReturn(json.readTree("[{\"id\":\""+id+"\",\"plaza_id\":1,\"anulado\":false}]"));
  when(db.insert(eq("rpc/avix_editar_registro"),any())).thenReturn(json.readTree("{\"placa\":\"ABC123\"}"));
  assertEquals("ABC123",service.editar(user,id,json.readTree("{\"placa\":\"ABC123\"}"),"Placa corregida").path("placa").asText());
  verify(permisos).puedeModificar(eq(user),any());
  verify(db).insert(eq("rpc/avix_editar_registro"),
   argThat(payload->payload instanceof Map<?,?> map && "actor".equals(map.get("p_actor"))));
 }
 @Test void camposNoPermitidosSonRechazados()throws Exception{
  SupabaseData db=mock(SupabaseData.class);
  RegistroGestionService service=new RegistroGestionService(db,mock(PermisosPlaza.class));
  when(db.select(eq("registros"),anyMap())).thenReturn(json.readTree("[{\"id\":\""+id+"\",\"plaza_id\":1,\"anulado\":false}]"));
  assertThrows(ResponseStatusException.class,()->service.editar(
   new AvixAuth.Principal("actor","CTRL","CONTROLADOR","WEB"),id,
   json.readTree("{\"registrado_por\":\"otro\"}"),"Motivo de prueba"));
  verify(db,never()).insert(eq("rpc/avix_editar_registro"),any());
 }
 @Test void anulacionRequiereMotivo()throws Exception{
  SupabaseData db=mock(SupabaseData.class);
  RegistroGestionService service=new RegistroGestionService(db,mock(PermisosPlaza.class));
  when(db.select(eq("registros"),anyMap())).thenReturn(json.readTree("[{\"id\":\""+id+"\",\"plaza_id\":1,\"anulado\":false}]"));
  AvixAuth.Principal user=new AvixAuth.Principal("actor","CTRL","CONTROLADOR","WEB");
  assertThrows(ResponseStatusException.class,()->service.anular(user,id,"x"));
  when(db.insert(eq("rpc/avix_anular_registro"),any())).thenReturn(json.readTree("{\"anulado\":true}"));
  assertTrue(service.anular(user,id,"Motivo de anulación").path("anulado").asBoolean());
 }
}
