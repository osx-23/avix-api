package com.avix.api.service;

import com.avix.api.data.SupabaseData;
import com.avix.api.security.AvixAuth;
import com.avix.api.security.PermisosPlaza;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class RegistroGestionService {
    private final SupabaseData db;
    private final PermisosPlaza permisos;
    public RegistroGestionService(SupabaseData db, PermisosPlaza permisos) {
        this.db = db;
        this.permisos = permisos;
    }

    public JsonNode detalle(AvixAuth.Principal user, UUID id) {
        JsonNode found = SupabaseData.first(db.select("registros", Map.of(
                "id", "eq." + id, "select", "*,plazas(codigo),vias!registros_via_pertenece_a_plaza(numero),"
                + "acciones(codigo),turnos(codigo),usuarios!registros_registrado_por_fkey(codigo)",
                "limit", "1")));
        if (found == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Registro no encontrado.");
        permisos.puedeVer(user, found);
        return found;
    }

    public JsonNode auditoria(AvixAuth.Principal user, UUID id) {
        detalle(user, id);
        return db.select("registro_cambios", Map.of(
                "registro_id", "eq." + id,
                "select", "id,registro_id,operacion,motivo,valores_anteriores,valores_nuevos,creado_en,"
                    + "usuarios!registro_cambios_modificado_por_fkey(codigo)",
                "order", "creado_en.asc", "limit", "500"));
    }

    public JsonNode editar(AvixAuth.Principal user, UUID id, JsonNode cambios, String motivo) {
        JsonNode found = detalle(user, id);
        permisos.puedeModificar(user, found);
        if (found.path("anulado").asBoolean())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Un registro anulado no puede editarse.");
        validarMotivo(motivo);
        if (cambios == null || !cambios.isObject() || cambios.isEmpty())
            throw AvixAuth.bad("Debes enviar al menos un campo.");
        Set<String> permitidos = Set.of("placa", "via", "accion", "ocurridoEn", "observaciones");
        var names = cambios.fieldNames();
        while (names.hasNext()) {
            if (!permitidos.contains(names.next())) throw AvixAuth.bad("Campo de edición no permitido.");
        }
        if (cambios.has("placa") && (!cambios.get("placa").isTextual() ||
                !cambios.path("placa").asText().matches("[A-Za-z0-9]{4,12}")))
            throw AvixAuth.bad("Placa inválida.");
        if (cambios.has("via") && (!cambios.get("via").canConvertToInt() ||
                cambios.path("via").asInt() < 1 || cambios.path("via").asInt() > 9999))
            throw AvixAuth.bad("Vía inválida.");
        if (cambios.has("accion") && (!cambios.get("accion").isTextual() ||
                !cambios.path("accion").asText().matches("(?i)FUGA|DERIVADO|LIBERADO")))
            throw AvixAuth.bad("Acción inválida.");
        if (cambios.has("ocurridoEn")) {
            if (!cambios.get("ocurridoEn").isTextual()) throw AvixAuth.bad("Fecha inválida.");
            try {
                if (OffsetDateTime.parse(cambios.path("ocurridoEn").asText()).toInstant()
                        .isAfter(Instant.now().plusSeconds(300)))
                    throw AvixAuth.bad("Fecha futura no permitida.");
            } catch (java.time.format.DateTimeParseException ex) {
                throw AvixAuth.bad("Formato de fecha inválido; incluye zona horaria.");
            }
        }
        if (cambios.has("observaciones") &&
                !cambios.get("observaciones").isNull() &&
                (!cambios.get("observaciones").isTextual() ||
                        cambios.path("observaciones").asText().length() > 1000))
            throw AvixAuth.bad("Observaciones inválidas.");

        return db.insert("rpc/avix_editar_registro", Map.of(
                "p_registro", id.toString(), "p_actor", user.id(),
                "p_cambios", cambios, "p_motivo", motivo.trim()));
    }

    public JsonNode anular(AvixAuth.Principal user, UUID id, String motivo) {
        JsonNode found = detalle(user, id);
        permisos.puedeModificar(user, found);
        if (found.path("anulado").asBoolean())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Registro ya anulado.");
        validarMotivo(motivo);
        return db.insert("rpc/avix_anular_registro", Map.of(
                "p_registro", id.toString(), "p_actor", user.id(), "p_motivo", motivo.trim()));
    }

    private void validarMotivo(String motivo) {
        if (motivo == null || motivo.trim().length() < 5 || motivo.length() > 1000)
            throw AvixAuth.bad("El motivo es obligatorio y debe tener entre 5 y 1000 caracteres.");
    }
}
