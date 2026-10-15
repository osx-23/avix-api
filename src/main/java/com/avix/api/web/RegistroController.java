package com.avix.api.web;

import com.avix.api.security.AvixAuth;
import com.avix.api.service.RegistroService;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/registros")
public class RegistroController {
    private final AvixAuth auth;
    private final RegistroService registros;
    public RegistroController(AvixAuth auth, RegistroService registros) {
        this.auth=auth; this.registros=registros;
    }

    public record RegistroNuevo(
            @NotBlank String plaza, @NotNull @Min(1) @Max(9999) Integer via,
            @NotBlank String accion, @NotBlank String placa,
            @NotNull UUID idempotenciaId, OffsetDateTime ocurridoEn,
            String origen, @Size(max=1000) String observaciones) {}

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public JsonNode crear(@RequestHeader(value="Authorization",required=false) String bearer,
                          @Valid @RequestBody RegistroNuevo req) {
        return registros.registrar(auth.require(bearer), req);
    }

    @GetMapping
    public JsonNode listar(@RequestHeader(value="Authorization",required=false) String bearer,
                           @RequestParam(defaultValue="0") int page,
                           @RequestParam(defaultValue="20") int size,
                           @RequestParam(required=false) String plaza,
                           @RequestParam(required=false) String accion,
                           @RequestParam(required=false) String fecha) {
        return registros.historial(auth.require(bearer), page, size, plaza, accion, fecha);
    }
    @GetMapping("/consulta")
    public JsonNode consultar(@RequestHeader(value="Authorization",required=false) String bearer,
                              @RequestParam(defaultValue="0") int page,
                              @RequestParam(defaultValue="500") int size,
                              @RequestParam String desde,
                              @RequestParam String hasta,
                              @RequestParam(required=false) String plaza,
                              @RequestParam(required=false) String accion,
                              @RequestParam(required=false) Integer via) {
        return registros.consultarRango(auth.require(bearer),page,size,desde,hasta,plaza,accion,via);
    }

}
