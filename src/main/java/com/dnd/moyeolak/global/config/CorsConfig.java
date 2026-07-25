package com.dnd.moyeolak.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
public class CorsConfig implements WebMvcConfigurer {

    public static List<String> allowedOrigins() {
        return List.of(
                "http://localhost:5173",
                "http://127.0.0.1:5173",
                "https://dnd-14th-8-frontend.pages.dev",
                "https://moyeorak.site"
        );
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins().toArray(String[]::new))
                .allowedOriginPatterns(
                        "https://*.dnd-14th-8-frontend.pages.dev"
                )
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
