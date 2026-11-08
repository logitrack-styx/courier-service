package com.courier.service.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.converter.RsaKeyConverters;

/**
 * Seguridad de courier-service: resource server JWT, sin estado.
 *
 * <p>El {@code JwtDecoder} lo crea Spring Boot a partir de
 * {@code spring.security.oauth2.resourceserver.jwt.public-key-location} (clave pública RSA, RS256):
 * la firma y la expiración se validan localmente, sin llamar a security-service.</p>
 * @author TheMiggaCool
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JsonSecurityErrorHandler errorHandler;

    /**
     * Valida RS256, issuer, vigencia e identidad sin depender de Security en cada request.
     * @param key clave pública montada
     * @param issuer emisor autorizado
     * @return decoder local
     * @throws Exception si no puede cargar la clave
     */
    @Bean
    JwtDecoder jwtDecoder(
            @Value("${spring.security.oauth2.resourceserver.jwt.public-key-location}") org.springframework.core.io.Resource key,
            @Value("${security.jwt.issuer:logitrack-security}") String issuer) throws Exception {
        java.security.interfaces.RSAPublicKey publicKey;
        try (var stream = key.getInputStream()) {
            publicKey = RsaKeyConverters.x509().convert(stream);
        }
        var decoder = NimbusJwtDecoder.withPublicKey(publicKey)
                .signatureAlgorithm(org.springframework.security.oauth2.jose.jws.SignatureAlgorithm.RS256).build();

        OAuth2TokenValidator<Jwt> identity = jwt -> {
            try {
                String sub = jwt.getSubject();
                Object claim = jwt.getClaims().get("roles");
                if (sub == null || !java.util.UUID.fromString(sub).toString().equals(sub)
                        || jwt.getExpiresAt() == null
                        || !(claim instanceof java.util.List<?> roles) || roles.size() != 1
                        || !(roles.get(0) instanceof String role)
                        || !java.util.List.of("ADMIN", "CLIENTE", "REPARTIDOR").contains(role)) {
                    throw new IllegalArgumentException();
                }
                return org.springframework.security.oauth2.core.OAuth2TokenValidatorResult.success();
            } catch (IllegalArgumentException ex) {
                return org.springframework.security.oauth2.core.OAuth2TokenValidatorResult.failure(
                        new org.springframework.security.oauth2.core.OAuth2Error("invalid_token"));
            }
        };

        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(issuer), identity));
        return decoder;
    }


    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                .anyRequest().authenticated())
            .exceptionHandling(e -> e
                .authenticationEntryPoint(errorHandler)
                .accessDeniedHandler(errorHandler))
            .oauth2ResourceServer(o -> o
                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                .authenticationEntryPoint(errorHandler)
                .accessDeniedHandler(errorHandler));
        return http.build();
    }

    /**
     * Convierte el claim {@code roles} del JWT (["ADMIN"], ["REPARTIDOR"], ...) en authorities
     * {@code ROLE_ADMIN}, {@code ROLE_REPARTIDOR}, ... para que funcione {@code hasRole('ADMIN')}.
     * El nombre del claim debe coincidir con el que emita security-service.
     */
    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("roles");
        authorities.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }
}
