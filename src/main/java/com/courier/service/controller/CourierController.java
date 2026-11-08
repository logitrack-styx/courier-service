package com.courier.service.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.courier.service.dto.CourierEstadoRequest;
import com.courier.service.dto.CourierRequest;
import com.courier.service.dto.CourierResponse;
import com.courier.service.dto.CourierUpdateRequest;
import com.courier.service.dto.DisponibilidadResponse;
import com.courier.service.enums.EstadoCourier;
import com.courier.service.service.CourierService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Endpoints REST de couriers.
 *
 * <p>Autorización: ADMIN gestiona todo. Un REPARTIDOR solo puede ver/editar SU propio perfil
 * (el id del recurso debe coincidir con el {@code sub} del JWT).</p>
 * @author TheMiggaCool
 */
@RestController
@RequestMapping("/couriers")
@RequiredArgsConstructor
@io.swagger.v3.oas.annotations.tags.Tag(name = "Couriers", description = "Perfiles logísticos; ADMIN administra, REPARTIDOR opera sobre su propio UUID")
@io.swagger.v3.oas.annotations.responses.ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Entrada o paginación inválida"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "JWT ausente o inválido"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Rol o propietario no autorizado"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Perfil inexistente"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Perfil duplicado"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "503", description = "Autoridad de identidad no disponible")
})
public class CourierController {

    private static final String ADMIN = "hasRole('ADMIN')";
    private static final String ADMIN_O_PROPIETARIO =
            "hasRole('ADMIN') or (hasRole('REPARTIDOR') and #id.toString() == authentication.name)";

    private final CourierService courierService;

    @PostMapping
    @io.swagger.v3.oas.annotations.Operation(summary = "Crear perfil para un usuario REPARTIDOR existente (ADMIN)")
    @PreAuthorize(ADMIN)
    public ResponseEntity<CourierResponse> create(@Valid @RequestBody CourierRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(courierService.create(request));
    }

    @GetMapping
    @io.swagger.v3.oas.annotations.Operation(summary = "Listar perfiles por estado con page y size, máximo 100; totales en headers (ADMIN)")
    @PreAuthorize(ADMIN)
    public ResponseEntity<List<CourierResponse>> findAll(@RequestParam(required = false) EstadoCourier estado,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        var result = courierService.findAll(estado, page, size);
        return ResponseEntity.ok().header("X-Total-Count", Long.toString(result.getTotalElements()))
                .header("X-Total-Pages", Integer.toString(result.getTotalPages())).body(result.getContent());
    }

    @GetMapping("/{id}")
    @io.swagger.v3.oas.annotations.Operation(summary = "Consultar perfil (ADMIN o propietario)")
    @PreAuthorize(ADMIN_O_PROPIETARIO)
    public ResponseEntity<CourierResponse> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(courierService.findById(id));
    }

    @PutMapping("/{id}")
    @io.swagger.v3.oas.annotations.Operation(summary = "Actualizar nombre y vehículo (ADMIN o propietario)")
    @PreAuthorize(ADMIN_O_PROPIETARIO)
    public ResponseEntity<CourierResponse> update(@PathVariable UUID id,
                                                  @Valid @RequestBody CourierUpdateRequest request) {
        return ResponseEntity.ok(courierService.update(id, request));
    }

    @PatchMapping("/{id}/estado")
    @io.swagger.v3.oas.annotations.Operation(summary = "Cambiar estado operativo (ADMIN)")
    @PreAuthorize(ADMIN)
    public ResponseEntity<CourierResponse> cambiarEstado(@PathVariable UUID id,
                                                         @Valid @RequestBody CourierEstadoRequest request) {
        return ResponseEntity.ok(courierService.cambiarEstado(id, request));
    }

    @GetMapping("/{id}/disponibilidad")
    @io.swagger.v3.oas.annotations.Operation(summary = "Consultar disponibilidad para asignación (ADMIN o propietario)")
    @PreAuthorize(ADMIN_O_PROPIETARIO)
    public ResponseEntity<DisponibilidadResponse> disponibilidad(@PathVariable UUID id) {
        return ResponseEntity.ok(courierService.consultarDisponibilidad(id));
    }
}
