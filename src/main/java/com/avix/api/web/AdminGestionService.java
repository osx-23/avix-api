package com.avix.api.web;

import com.avix.api.data.SupabaseData;
import com.avix.api.security.AvixAuth;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import java.util.UUID;

@Service
public class AdminGestionService {
    private final SupabaseData db;
    private final BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder(12);
    public AdminGestionService(SupabaseData db) { this.db=db; }

    private JsonNode usuario(UUID id) {
        JsonNode found = SupabaseData.first(db.select("usuarios", Map.of(
                "id","eq."+id,"select","id,codigo,nombres,apellidos,rol,activo,creado_en,actualizado_en","limit","1")));
        if (found == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Usuario inexistente.");
        return found;
    }

    public JsonNode usuarios(int page,int size,String rol,Boolean activo) {
        if (page<0 || page>100_000 || size<1 || size>100) throw AvixAuth.bad("Paginación inválida.");
        Map<String,String> query = new HashMap<>();
        query.put("select","id,codigo,nombres,apellidos,rol,activo,creado_en,actualizado_en");
        query.put("order","codigo.asc");
        query.put("limit", Integer.toString(size));
        query.put("offset", Long.toString((long) page*size));
        if (rol!=null&&!rol.isBlank()) {
            if (!Set.of("AGENTE","CONTROLADOR","SUPERVISOR").contains(rol))
                throw AvixAuth.bad("Rol inválido.");
            query.put("rol","eq."+rol);
        }
        if (activo!=null) query.put("activo","eq."+activo);
        return db.select("usuarios",query);
    }
    public JsonNode obtener(UUID id) { return usuario(id); }

    public JsonNode modificar(AvixAuth.Principal actor,UUID id,JsonNode cambios) {
        usuario(id);
        if(cambios==null || !cambios.isObject() || cambios.isEmpty())
            throw AvixAuth.bad("Debes indicar los datos a modificar.");
        Set<String> allowed=Set.of("nombres","apellidos","rol","activo","contrasena");
        Map<String,Object> sql=new HashMap<>();
        var it=cambios.fields();
        while(it.hasNext()) {
            var e=it.next();String k=e.getKey();JsonNode val=e.getValue();
            if(!allowed.contains(k))throw AvixAuth.bad("Campo no editable: "+k);
            if(k.equals("contrasena")){
                if(!val.isTextual() || val.asText().length()<10 || val.asText().length()>256)
                    throw AvixAuth.bad("La contraseña debe tener entre 10 y 256 caracteres.");
                sql.put("passwordHash",bcrypt.encode(val.asText()));
            } else if (k.equals("activo")) {
                if(!val.isBoolean())throw AvixAuth.bad("El estado activo debe ser booleano.");
                sql.put(k,val.booleanValue());
            } else {
                if(!val.isTextual() && !(k.equals("apellidos")&&val.isNull()))
                    throw AvixAuth.bad("Texto inválido en "+k);
                String text=val.isNull()?"":val.asText().trim();
                if(k.equals("nombres")&&(text.isBlank()||text.length()>120))
                    throw AvixAuth.bad("Nombres inválidos.");
                if(k.equals("apellidos")&&text.length()>120)
                    throw AvixAuth.bad("Apellidos demasiado largos.");
                if(k.equals("rol")&&!Set.of("AGENTE","CONTROLADOR","SUPERVISOR").contains(text))
                    throw AvixAuth.bad("Rol desconocido.");
                sql.put(k,text);
            }
        }
        return db.insert("rpc/avix_administrar_usuario",Map.of(
                "p_actor",actor.id(),"p_usuario",id.toString(),"p_cambios",sql));
    }

    public JsonNode asignaciones(UUID id) {
        usuario(id);
        return db.select("usuario_plazas",Map.of(
                "usuario_id","eq."+id,"select","plaza_id,activa,asignado_en,plazas(codigo,nombre)",
                "order","plaza_id.asc"));
    }

    public JsonNode cambiarAsignacion(UUID id,String plaza,boolean activa) {
        usuario(id);
        if (plaza==null || !plaza.matches("P[1-9][0-9]{0,2}"))
            throw AvixAuth.bad("Plaza inválida.");
        JsonNode p=SupabaseData.first(db.select("plazas",Map.of(
                "codigo","eq."+plaza,"select","id","limit","1")));
        if(p==null)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Plaza inexistente.");
        JsonNode updated = db.update("usuario_plazas", Map.of(
                "usuario_id","eq."+id,"plaza_id","eq."+p.path("id").asLong()),
                Map.of("activa",activa));
        if(updated==null||updated.isEmpty())
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,"El usuario no tiene esta plaza asignada.");
        return updated;
    }

    public JsonNode vias(String plaza,int page,int size) {
        if(page<0 || page>100_000 || size<1 || size>100)throw AvixAuth.bad("Paginación inválida.");
        Map<String,String> query=new HashMap<>();
        query.put("select","id,plaza_id,numero,descripcion,activa,plazas(codigo)");
        query.put("order","plaza_id.asc,numero.asc");
        query.put("limit",Integer.toString(size));query.put("offset",Long.toString((long)page*size));
        if(plaza!=null&&!plaza.isBlank()){
            if(!plaza.matches("P[1-9][0-9]{0,2}"))throw AvixAuth.bad("Plaza inválida.");
            JsonNode p=SupabaseData.first(db.select("plazas",Map.of(
                    "codigo","eq."+plaza,"select","id","limit","1")));
            if(p==null)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Plaza inexistente.");
            query.put("plaza_id","eq."+p.path("id").asLong());
        }
        return db.select("vias",query);
    }

    public JsonNode editarVia(long id, JsonNode cambios){
        JsonNode actual=SupabaseData.first(db.select("vias",Map.of("id","eq."+id,"select","id","limit","1")));
        if(actual==null)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Vía inexistente.");
        if(cambios==null||!cambios.isObject()||cambios.isEmpty())throw AvixAuth.bad("No hay cambios.");
        Map<String,Object> body=new HashMap<>();
        var it=cambios.fields();
        while(it.hasNext()){
            var e=it.next();JsonNode val=e.getValue();
            switch(e.getKey()){
                case "numero" -> {
                    if(!val.canConvertToInt()||val.asInt()<1||val.asInt()>9999)
                        throw AvixAuth.bad("Número de vía inválido.");
                    body.put("numero",val.intValue());
                }
                case "activa" -> {
                    if(!val.isBoolean())throw AvixAuth.bad("Estado de vía inválido.");
                    body.put("activa",val.booleanValue());
                }
                case "descripcion" -> {
                    if(!val.isNull()&&!val.isTextual())throw AvixAuth.bad("Descripción inválida.");
                    if(val.isTextual()&&val.asText().length()>120)throw AvixAuth.bad("Descripción demasiado larga.");
                    body.put("descripcion",val.isNull()?null:val.asText().trim());
                }
                default -> throw AvixAuth.bad("Campo no permitido para vía.");
            }
        }
        return db.update("vias",Map.of("id","eq."+id),body);
    }
}
