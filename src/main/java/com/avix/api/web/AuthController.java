package com.avix.api.web;

import com.avix.api.security.AvixAuth;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class AuthController {
    private final AvixAuth auth;
    public AuthController(AvixAuth auth) { this.auth = auth; }

    public record CodigoLogin(@NotBlank String codigo) {}
    public record WebLogin(@NotBlank String codigo, @NotBlank @Size(max=256) String contrasena) {}

    @PostMapping("/auth/movil")
    public Map<String, Object> movil(@Valid @RequestBody CodigoLogin req, HttpServletRequest http) {
        return auth.login(req.codigo(), null, "MOVIL", http.getRemoteAddr());
    }

    @PostMapping("/auth/web")
    public Map<String, Object> web(@Valid @RequestBody WebLogin req, HttpServletRequest http) {
        return auth.login(req.codigo(), req.contrasena(), "WEB", http.getRemoteAddr());
    }

    @GetMapping("/auth/yo")
    public AvixAuth.Principal yo(@RequestHeader(value="Authorization", required=false) String bearer) {
        return auth.require(bearer);
    }

    @PostMapping("/auth/salir")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void salir(@RequestHeader(value="Authorization", required=false) String bearer) {
        auth.logout(bearer);
    }

    @PostMapping("/setup/supervisor")
    @ResponseStatus(HttpStatus.CREATED)
    public JsonNode setup(@RequestHeader(value="X-Setup-Secret", required=false) String secret,
                          @Valid @RequestBody WebLogin req) {
        return auth.bootstrap(secret, req.codigo(), req.contrasena());
    }
}
