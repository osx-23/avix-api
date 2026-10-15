package com.avix.api.web;

import com.avix.api.security.AvixAuth;
import com.avix.api.service.RegistroGestionService;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/registros")
public class RegistroGestionController {
    private final RegistroGestionService service;
    private final AvixAuth auth;
    public RegistroGestionController(RegistroGestionService service, AvixAuth auth) {
        this.service=service; this.auth=auth;
    }
    public record Modificacion(JsonNode cambios, @NotBlank String motivo) {}
    public record Anulacion(@NotBlank String motivo) {}

    @GetMapping("/{id}")
    public JsonNode detalle(@RequestHeader(value="Authorization",required=false) String bearer,
                            @PathVariable UUID id) {
        return service.detalle(auth.require(bearer), id);
    }
    @GetMapping("/{id}/auditoria")
    public JsonNode auditoria(@RequestHeader(value="Authorization",required=false) String bearer,
                              @PathVariable UUID id) {
        return service.auditoria(auth.require(bearer), id);
    }
    @PatchMapping("/{id}")
    public JsonNode editar(@RequestHeader(value="Authorization",required=false) String bearer,
                           @PathVariable UUID id, @Valid @RequestBody Modificacion req) {
        return service.editar(auth.require(bearer), id, req.cambios(), req.motivo());
    }
    @PatchMapping("/{id}/anular")
    public JsonNode anular(@RequestHeader(value="Authorization",required=false) String bearer,
                           @PathVariable UUID id, @Valid @RequestBody Anulacion req) {
        return service.anular(auth.require(bearer), id, req.motivo());
    }
}
