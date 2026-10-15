package com.avix.api.service;

import com.avix.api.data.SupabaseData;
import com.avix.api.security.AvixAuth;
import com.avix.api.security.PermisosPlaza;
import com.avix.api.web.RegistroController.RegistroNuevo;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.time.format.DateTimeParseException;
import java.util.*;

@Service
public class RegistroService {
    private final SupabaseData db;
    private final PermisosPlaza permisos;
    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    public RegistroService(SupabaseData db, PermisosPlaza permisos) { this.db = db; this.permisos = permisos; }

    public record TurnoFecha(String turno, LocalDate fechaOperativa) {}

    public static TurnoFecha calcularTurno(OffsetDateTime ocurridoEn) {
        ZonedDateTime t = ocurridoEn.atZoneSameInstant(LIMA);
        int hora = t.getHour();
        String turno = hora >= 6 && hora < 14 ? "A" : hora >= 14 && hora < 22 ? "B" : "C";
        LocalDate fecha = t.toLocalDate();
        if (turno.equals("C") && hora < 6) fecha = fecha.minusDays(1);
        return new TurnoFecha(turno, fecha);
    }

    private JsonNode required(String table, Map<String,String> query, String reason) {
        JsonNode found = SupabaseData.first(db.select(table, query));
        if (found == null) throw AvixAuth.bad(reason);
        return found;
    }

    public JsonNode registrar(AvixAuth.Principal user, RegistroNuevo req) {
        if (!user.cliente().equals("MOVIL")) throw AvixAuth.forbidden();
        if (!req.plaza().matches("P[1-9][0-9]{0,2}") ||
            !req.accion().matches("(?i)FUGA|DERIVADO|LIBERADO") ||
            !req.placa().matches("[A-Za-z0-9]{4,12}"))
            throw AvixAuth.bad("Plaza, acción o placa inválidas.");
        String origen = req.origen() == null ? "VOZ" : req.origen().toUpperCase(Locale.ROOT);
        if (!Set.of("VOZ", "MANUAL").contains(origen)) throw AvixAuth.bad("Origen inválido.");

        JsonNode previous = buscarIdempotencia(req.idempotenciaId());
        if (previous != null) return validarDueno(previous, user);

        JsonNode plaza = required("plazas", Map.of(
                "codigo", "eq."+req.plaza(), "activa", "eq.true",
                "select", "id", "limit", "1"), "Plaza no encontrada.");
        String idPlaza = plaza.path("id").asText();

        // La consulta de catálogos permite al supervisor acceder a cualquier plaza activa.
        // Aplicamos aquí la misma política, sin relajar los permisos de agente/controlador.
        permisos.requireAsignada(user, plaza.path("id").asLong());

        JsonNode via = required("vias", Map.of(
                "plaza_id", "eq."+idPlaza, "numero", "eq."+req.via(),
                "activa", "eq.true", "select", "id", "limit", "1"),
                "La vía no está configurada para la plaza.");
        JsonNode accion = required("acciones", Map.of(
                "codigo", "eq."+req.accion().toUpperCase(Locale.ROOT),
                "activa", "eq.true", "select", "id", "limit", "1"), "Acción inválida.");

        OffsetDateTime ocurrido = req.ocurridoEn() == null
                ? OffsetDateTime.now(ZoneOffset.UTC) : req.ocurridoEn();
        if (ocurrido.toInstant().isAfter(Instant.now().plusSeconds(300)))
            throw AvixAuth.bad("La fecha del registro no puede ser futura.");
        TurnoFecha info = calcularTurno(ocurrido);
        JsonNode turno = required("turnos", Map.of(
                "codigo", "eq."+info.turno(), "activo", "eq.true", "select", "id", "limit", "1"),
                "Turno no disponible.");

        Map<String,Object> body = new HashMap<>();
        body.put("idempotencia_id", req.idempotenciaId().toString());
        body.put("registrado_por", user.id());
        body.put("plaza_id", plaza.path("id").asLong());
        body.put("via_id", via.path("id").asLong());
        body.put("accion_id", accion.path("id").asInt());
        body.put("turno_id", turno.path("id").asInt());
        body.put("placa", req.placa().toUpperCase(Locale.ROOT));
        body.put("fecha_operativa", info.fechaOperativa().toString());
        body.put("ocurrido_en", ocurrido.toInstant().toString());
        body.put("origen", origen);
        if (req.observaciones() != null && !req.observaciones().isBlank())
            body.put("observaciones", req.observaciones().trim());

        try {
            JsonNode result = SupabaseData.first(db.insert("registros", body));
            if (result == null)
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "No se confirmó la inserción.");
            return result;
        } catch (ResponseStatusException ex) {
            // El dispositivo puede reintentar después de perder conectividad.
            if (ex.getStatusCode().value() == 409) {
                JsonNode existing = buscarIdempotencia(req.idempotenciaId());
                if (existing != null) return validarDueno(existing, user);
            }
            throw ex;
        }
    }

    private JsonNode buscarIdempotencia(UUID id) {
        return SupabaseData.first(db.select("registros", Map.of(
                "idempotencia_id", "eq."+id, "select", "*", "limit", "1")));
    }

    private JsonNode validarDueno(JsonNode record, AvixAuth.Principal user) {
        if (!user.id().equals(record.path("registrado_por").asText())) throw AvixAuth.forbidden();
        return record;
    }

    public JsonNode historial(AvixAuth.Principal user, int page, int size,
                             String plaza, String accion, String fecha) {
        if (page < 0 || page > 100_000 || size < 1 || size > 100)
            throw AvixAuth.bad("Paginación inválida.");
        Map<String,String> query = new HashMap<>();
        query.put("select", "id,idempotencia_id,placa,origen,observaciones,ocurrido_en,fecha_operativa,creado_en,anulado,"
                + "plazas(codigo),vias!registros_via_pertenece_a_plaza(numero),"
                + "acciones(codigo),turnos(codigo),usuarios!registros_registrado_por_fkey(codigo)");
        query.put("order", "ocurrido_en.desc");
        query.put("limit", Integer.toString(size));
        query.put("offset", Long.toString((long)page * size));
        if (user.rol().equals("AGENTE")) query.put("registrado_por", "eq."+user.id());
        if (user.rol().equals("CONTROLADOR")) query.put("plaza_id", permisos.filtro(user));

        if (plaza != null && !plaza.isBlank()) {
            if (!plaza.matches("P[1-9][0-9]{0,2}")) throw AvixAuth.bad("Plaza inválida.");
            JsonNode p = required("plazas", Map.of("codigo","eq."+plaza,
                    "select","id", "limit","1"), "Plaza no encontrada.");
            if (user.rol().equals("CONTROLADOR")) permisos.requireAsignada(user, p.path("id").asLong());
            query.put("plaza_id", "eq."+p.path("id").asLong());
        }
        if (accion != null && !accion.isBlank()) {
            if (!accion.matches("(?i)FUGA|DERIVADO|LIBERADO")) throw AvixAuth.bad("Acción inválida.");
            JsonNode a = required("acciones", Map.of("codigo","eq."+accion.toUpperCase(Locale.ROOT),
                    "select","id", "limit","1"), "Acción no encontrada.");
            query.put("accion_id", "eq."+a.path("id").asInt());
        }
        if (fecha != null && !fecha.isBlank()) {
            try { query.put("fecha_operativa", "eq."+LocalDate.parse(fecha)); }
            catch (Exception ex) { throw AvixAuth.bad("Fecha inválida. Usa AAAA-MM-DD."); }
        }
        return db.select("registros", query);
    }

    /**
     * Consulta paginada por instante; evita una petición por jornada operativa.
     * El rango es [desde, hasta) en UTC, con permisos aplicados en PostgREST
     * ANTES de paginar (no hay filtrado posterior en memoria).
     */
    public JsonNode consultarRango(AvixAuth.Principal user, int page, int size,
                                  String desdeTexto, String hastaTexto,
                                  String plaza, String accion, Integer via) {
        if (page < 0 || page > 100_000 || size < 1 || size > 500)
            throw AvixAuth.bad("Paginación inválida.");
        if (desdeTexto == null || hastaTexto == null)
            throw AvixAuth.bad("Indica desde y hasta con zona horaria.");
        final Instant desde, hasta;
        try {
            desde = OffsetDateTime.parse(desdeTexto).toInstant();
            hasta = OffsetDateTime.parse(hastaTexto).toInstant();
        } catch (DateTimeParseException ex) {
            throw AvixAuth.bad("Fechas inválidas. Usa ISO 8601 con zona horaria.");
        }
        Duration duracion = Duration.between(desde, hasta);
        if (duracion.isNegative() || duracion.isZero() || duracion.compareTo(Duration.ofDays(33)) > 0)
            throw AvixAuth.bad("El rango debe ser positivo y no superar 33 días.");
        if (via != null && (via < 1 || via > 9999))
            throw AvixAuth.bad("Vía inválida.");

        Map<String,String> query = new HashMap<>();
        query.put("select", "id,idempotencia_id,placa,origen,observaciones,ocurrido_en,fecha_operativa,creado_en,anulado,"
                + "plazas(codigo),vias!registros_via_pertenece_a_plaza(numero),"
                + "acciones(codigo),turnos(codigo),usuarios!registros_registrado_por_fkey(codigo)");
        query.put("order", "ocurrido_en.desc,id.desc");
        query.put("limit", String.valueOf(size));
        query.put("offset", String.valueOf((long)page * size));
        query.put("and", "(ocurrido_en.gte." + desde + ",ocurrido_en.lt." + hasta + ")");

        if (user.rol().equals("AGENTE"))
            query.put("registrado_por", "eq." + user.id());
        if (user.rol().equals("CONTROLADOR"))
            query.put("plaza_id", permisos.filtro(user));

        if (plaza != null && !plaza.isBlank()) {
            if (!plaza.matches("P[1-9][0-9]{0,2}"))
                throw AvixAuth.bad("Plaza inválida.");
            JsonNode p = required("plazas", Map.of(
                    "codigo", "eq." + plaza, "select", "id", "limit", "1"),
                    "Plaza no encontrada.");
            if (user.rol().equals("CONTROLADOR"))
                permisos.requireAsignada(user, p.path("id").asLong());
            query.put("plaza_id", "eq." + p.path("id").asLong());
        }

        if (accion != null && !accion.isBlank()) {
            if (!accion.matches("(?i)FUGA|DERIVADO|LIBERADO"))
                throw AvixAuth.bad("Acción inválida.");
            JsonNode a = required("acciones", Map.of(
                    "codigo", "eq." + accion.toUpperCase(Locale.ROOT),
                    "select", "id", "limit", "1"),
                    "Acción no encontrada.");
            query.put("accion_id", "eq." + a.path("id").asInt());
        }

        if (via != null) {
            Map<String,String> viasQuery = new HashMap<>();
            viasQuery.put("numero", "eq." + via);
            viasQuery.put("select", "id");
            viasQuery.put("limit", "500");
            JsonNode ids = db.select("vias", viasQuery);
            List<String> viaIds = new ArrayList<>();
            if (ids != null && ids.isArray())
                for (JsonNode row : ids) viaIds.add(row.path("id").asText());
            if (viaIds.isEmpty()) query.put("via_id", "eq.-1");
            else query.put("via_id", "in.(" + String.join(",", viaIds) + ")");
        }
        return db.select("registros", query);
    }

}
