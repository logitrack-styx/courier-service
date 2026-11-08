package com.courier.service.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Publica el contrato de Courier con autenticación Bearer RS256 y permisos locales.
 * @author dominox45
 */
@Configuration
public class OpenApiConfig {
    /** @return definición OpenAPI del servicio de repartidores. */
    @Bean
    public OpenAPI courierOpenApi() {
        return new OpenAPI().info(new Info().title("LogiTrack Courier API").version("3.0")
                .description("ADMIN gestiona perfiles; REPARTIDOR consulta y actualiza su propio perfil. Errores 400, 401, 403, 404, 409 y 503 en JSON."))
                .components(new Components().addSecuritySchemes("bearerAuth", new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT RS256")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }
}
