package com.courier.service.security;

import java.io.IOException;
import java.time.OffsetDateTime;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import com.courier.service.exceptions.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/**
 * Responde 401 y 403 con el MISMO formato JSON que el resto de errores del contrato
 * ({@link ErrorResponse}). Estos errores ocurren en la cadena de filtros de Spring Security,
 * antes de llegar al controlador, por eso el {@code GlobalExceptionHandler} no los ve.
 * @author TheMiggaCool
 */
@Component
@RequiredArgsConstructor
public class JsonSecurityErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException ex) throws IOException {
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        escribir(response, HttpStatus.UNAUTHORIZED, "Token ausente, inválido o expirado");
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
            AccessDeniedException ex) throws IOException {
        escribir(response, HttpStatus.FORBIDDEN, "No tiene permisos para acceder a este recurso");
    }

    private void escribir(HttpServletResponse response, HttpStatus status, String mensaje) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), ErrorResponse.builder()
                .status(status.value())
                .error(status.getReasonPhrase())
                .mensaje(mensaje)
                .timestamp(OffsetDateTime.now(java.time.ZoneOffset.UTC))
                .build());
    }
}
