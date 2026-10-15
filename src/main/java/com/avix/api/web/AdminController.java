package com.avix.api.web;

import com.avix.api.data.SupabaseData;
import com.avix.api.security.AvixAuth;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {
    private final AvixAuth auth;
    private final SupabaseData db;
    private final AdminGestionService gestion;
    public AdminController(AvixAuth auth, SupabaseData db, AdminGestionService gestion) { this.auth=auth; this.db=db; this.gestion=gestion; }

    public record UsuarioNuevo(@NotBlank String codigo, @NotBlank String nombres,
                               String apellidos, @NotBlank String rol, String contrasena) {}
    public record ViaNueva(@NotBlank String plaza, @NotNull @Min(1) @Max(9999) Integer numero,
                           String descripcion) {}
    public record PlazaAsignada(@NotBlank String plaza) {}

    @PostMapping("/usuarios")
    @ResponseStatus(HttpStatus.CREATED)
    public JsonNode usuario(@RequestHeader(value="Authorization", required=false) String bearer,
                            @Valid @RequestBody UsuarioNuevo req) {
        auth.admin(bearer);
        return auth.createUser(req.codigo(), req.nombres(), req.apellidos(), req.rol(), req.contrasena());
    }

    @PostMapping("/usuarios/{id}/plazas")
    @ResponseStatus(HttpStatus.CREATED)
    public JsonNode asignar(@RequestHeader(value="Authorization", required=false) String bearer,
                            @PathVariable UUID id, @Valid @RequestBody PlazaAsignada req) {
        auth.admin(bearer);
        if (!req.plaza().matches("P[1-9][0-9]{0,2}")) throw AvixAuth.bad("Plaza inválida.");
        JsonNode plaza = SupabaseData.first(db.select("plazas", Map.of(
                "codigo", "eq." + req.plaza(), "activa", "eq.true", "select", "id", "limit", "1")));
        if (plaza == null) throw AvixAuth.bad("La plaza no existe.");
        if (SupabaseData.first(db.select("usuarios", Map.of(
                "id","eq."+id,"activo","eq.true","select","id","limit","1"))) == null)
            throw AvixAuth.bad("El usuario no existe.");
        return db.upsert("usuario_plazas", Map.of("on_conflict", "usuario_id,plaza_id"),
                Map.of("usuario_id", id.toString(), "plaza_id", plaza.path("id").asLong(), "activa", true));
    }

    @PostMapping("/vias")
    @ResponseStatus(HttpStatus.CREATED)
    public JsonNode via(@RequestHeader(value="Authorization", required=false) String bearer,
                        @Valid @RequestBody ViaNueva req) {
        auth.admin(bearer);
        if (!req.plaza().matches("P[1-9][0-9]{0,2}")) throw AvixAuth.bad("Plaza inválida.");
        if (req.descripcion() != null && req.descripcion().length() > 120)
            throw AvixAuth.bad("Descripción demasiado larga.");
        JsonNode plaza = SupabaseData.first(db.select("plazas", Map.of(
                "codigo", "eq."+req.plaza(), "activa", "eq.true", "select", "id", "limit", "1")));
        if (plaza == null) throw AvixAuth.bad("La plaza no existe.");
        return db.upsert("vias", Map.of("on_conflict", "plaza_id,numero"),
                Map.of("plaza_id", plaza.path("id").asLong(), "numero", req.numero(),
                        "descripcion", req.descripcion() == null ? "" : req.descripcion(), "activa", true));
    }

    @GetMapping("/usuarios")
    public JsonNode listarUsuarios(
            @RequestHeader(value="Authorization",required=false) String bearer,
            @RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="20") int size,
            @RequestParam(required=false) String rol,
            @RequestParam(required=false) Boolean activo) {
        auth.admin(bearer);
        return gestion.usuarios(page,size,rol,activo);
    }

    @GetMapping("/usuarios/{id}")
    public JsonNode obtenerUsuario(
            @RequestHeader(value="Authorization",required=false) String bearer,
            @PathVariable UUID id) {
        auth.admin(bearer);
        return gestion.obtener(id);
    }

    @PatchMapping("/usuarios/{id}")
    public JsonNode modificarUsuario(
            @RequestHeader(value="Authorization",required=false) String bearer,
            @PathVariable UUID id, @RequestBody JsonNode cambios) {
        AvixAuth.Principal actor=auth.admin(bearer);
        return gestion.modificar(actor,id,cambios);
    }

    @GetMapping("/usuarios/{id}/plazas")
    public JsonNode asignacionesUsuario(
            @RequestHeader(value="Authorization",required=false) String bearer,
            @PathVariable UUID id) {
        auth.admin(bearer);
        return gestion.asignaciones(id);
    }

    public record Estado(@NotNull Boolean activa) {}

    @PatchMapping("/usuarios/{id}/plazas/{codigoPlaza}")
    public JsonNode cambiarAsignacion(
            @RequestHeader(value="Authorization",required=false) String bearer,
            @PathVariable UUID id,@PathVariable String codigoPlaza,
            @Valid @RequestBody Estado estado) {
        auth.admin(bearer);
        return gestion.cambiarAsignacion(id,codigoPlaza,estado.activa());
    }

    @GetMapping("/vias")
    public JsonNode listarVias(
            @RequestHeader(value="Authorization",required=false) String bearer,
            @RequestParam(required=false) String plaza,
            @RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="20") int size) {
        auth.admin(bearer);
        return gestion.vias(plaza,page,size);
    }

    @PatchMapping("/vias/{id}")
    public JsonNode modificarVia(
            @RequestHeader(value="Authorization",required=false) String bearer,
            @PathVariable long id, @RequestBody JsonNode cambios) {
        auth.admin(bearer);
        return gestion.editarVia(id,cambios);
    }
}
