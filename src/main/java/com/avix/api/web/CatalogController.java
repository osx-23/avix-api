package com.avix.api.web;

import com.avix.api.data.SupabaseData;
import com.avix.api.security.AvixAuth;
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
    public CatalogController(AvixAuth auth, SupabaseData db) { this.auth=auth; this.db=db; }

    @GetMapping("/{tipo}")
    public JsonNode catalogo(@RequestHeader(value="Authorization", required=false) String bearer,
                             @PathVariable String tipo, @RequestParam(required=false) String plaza) {
        auth.require(bearer);
        if (!Set.of("plazas","vias","acciones","turnos").contains(tipo))
            throw AvixAuth.bad("Catálogo desconocido.");
        Map<String,String> filtros = new HashMap<>();
        filtros.put(tipo.equals("turnos") ? "activo" : "activa", "eq.true");
        filtros.put("order", tipo.equals("vias") ? "numero.asc" : "id.asc");
        if (tipo.equals("vias")) {
            if (plaza == null || !plaza.matches("P[1-9][0-9]{0,2}"))
                throw AvixAuth.bad("Indica una plaza válida.");
            JsonNode found = SupabaseData.first(db.select("plazas", Map.of(
                    "codigo","eq."+plaza,"activa","eq.true","select","id","limit","1")));
            if (found == null) throw AvixAuth.bad("La plaza no existe.");
            filtros.put("plaza_id", "eq."+found.path("id").asLong());
        }
        return db.select(tipo, filtros);
    }
}
