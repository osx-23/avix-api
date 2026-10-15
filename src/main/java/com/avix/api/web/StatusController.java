package com.avix.api.web;

import com.avix.api.data.SupabaseData;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class StatusController {
    private final SupabaseData db;
    public StatusController(SupabaseData db) { this.db=db; }
    @GetMapping("/status")
    public Map<String,Object> status() {
        return Map.of("api","ok","version","0.1.0","supabaseConfigurado",db.configured());
    }
}
