package com.avix.api.web;

import com.avix.api.data.SupabaseData;
import com.avix.api.security.AvixAuth;
import com.avix.api.security.PermisosPlaza;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.web.bind.annotation.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/catalogos")
public class CatalogController {
    private final AvixAuth auth;
    private final SupabaseData db;
    private final PermisosPlaza permisos;
    public CatalogController(AvixAuth auth, SupabaseData db, PermisosPlaza permisos) { this.auth=auth; this.db=db; this.permisos=permisos; }

    /**
     * Las plazas asignadas son distintas del catalogo general:
     * un supervisor ve todas las plazas en el catalogo, pero en movil debe
     * priorizar su asignacion de trabajo.
     */
    @GetMapping("/plazas/asignadas")
    public JsonNode plazasAsignadas(@RequestHeader(value="Authorization", required=false) String bearer) {
        AvixAuth.Principal user = auth.require(bearer);
        var ids = permisos.asignadas(user);
        if (ids.isEmpty()) return com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.arrayNode();
        String filtro = "in.(" + ids.stream().map(String::valueOf)
                .reduce((a, b) -> a + "," + b).orElse("") + ")";
        return db.select("plazas", Map.of("id", filtro, "activa", "eq.true",
                "select", "id,codigo,activa", "order", "id.asc"));
    }

    @GetMapping("/{tipo}")
    public JsonNode catalogo(@RequestHeader(value="Authorization", required=false) String bearer,
                             @PathVariable String tipo, @RequestParam(required=false) String plaza) {
        AvixAuth.Principal user = auth.require(bearer);
        if (!Set.of("plazas","vias","acciones","turnos").contains(tipo))
            throw AvixAuth.bad("Catálogo desconocido.");
        Map<String,String> filtros = new HashMap<>();
        filtros.put(tipo.equals("turnos") ? "activo" : "activa", "eq.true");
        filtros.put("order", tipo.equals("vias") ? "numero.asc" : "id.asc");
        if (tipo.equals("plazas") && !user.rol().equals("SUPERVISOR")) filtros.put("id", permisos.filtro(user));
        if (tipo.equals("vias")) {
            if (plaza == null || !plaza.matches("P[1-9][0-9]{0,2}"))
                throw AvixAuth.bad("Indica una plaza válida.");
            JsonNode found = SupabaseData.first(db.select("plazas", Map.of(
                    "codigo","eq."+plaza,"activa","eq.true","select","id","limit","1")));
            if (found == null) throw AvixAuth.bad("La plaza no existe.");
            permisos.requireAsignada(user, found.path("id").asLong());
            filtros.put("plaza_id", "eq."+found.path("id").asLong());
        }
        return db.select(tipo, filtros);
    }
}
