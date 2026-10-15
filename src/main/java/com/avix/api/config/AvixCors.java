package com.avix.api.config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import java.util.Arrays;

@Configuration
public class AvixCors implements WebMvcConfigurer {
    private final String[] origins;
    public AvixCors(@Value("$"+"{avix.cors-origins:}") String configured) {
        origins = Arrays.stream(configured.split(",")).map(String::trim)
                .filter(s -> !s.isBlank()).toArray(String[]::new);
    }
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        if (origins.length > 0)
            registry.addMapping("/api/**")
                    .allowedOrigins(origins)
                    .allowedMethods("GET","POST","PATCH","OPTIONS")
                    .allowedHeaders("Content-Type","Authorization","X-Setup-Secret")
                    .maxAge(3600);
    }
}
