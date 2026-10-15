package com.avix.api.security;

import com.avix.api.data.SupabaseData;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class PermisosPlaza {
    private final SupabaseData db;
    public PermisosPlaza(SupabaseData db) { this.db = db; }

    public List<Long> asignadas(AvixAuth.Principal user) {
        JsonNode rows = db.select("usuario_plazas", Map.of(
                "usuario_id", "eq." + user.id(),
                "activa", "eq.true", "select", "plaza_id",
                "order", "plaza_id.asc", "limit", "500"));
        List<Long> ids = new ArrayList<>();
        if (rows != null && rows.isArray()) {
            for (JsonNode row : rows) ids.add(row.path("plaza_id").asLong());
        }
        return ids;
    }

    public void puedeVer(AvixAuth.Principal user, JsonNode registro) {
        if (user.rol().equals("SUPERVISOR")) return;
        if (user.rol().equals("AGENTE")) {
            if (!user.id().equals(registro.path("registrado_por").asText()))
                throw AvixAuth.forbidden();
            return;
        }
        if (!asignadas(user).contains(registro.path("plaza_id").asLong()))
            throw AvixAuth.forbidden();
    }

    public void puedeModificar(AvixAuth.Principal user, JsonNode registro) {
        if (!user.cliente().equals("WEB") || user.rol().equals("AGENTE"))
            throw AvixAuth.forbidden();
        puedeVer(user, registro);
    }

    public String filtro(AvixAuth.Principal user) {
        List<Long> ids = asignadas(user);
        if (ids.isEmpty()) return "eq.-1";
        return "in.(" + ids.stream().map(String::valueOf).reduce((a,b)->a+","+b).orElse("") + ")";
    }

    public void requireAsignada(AvixAuth.Principal user, long plazaId) {
        if (user.rol().equals("SUPERVISOR")) return;
        if (!asignadas(user).contains(plazaId)) throw AvixAuth.forbidden();
    }
}
