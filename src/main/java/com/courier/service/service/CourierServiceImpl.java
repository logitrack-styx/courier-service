package com.courier.service.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.courier.service.client.UsuarioRolVerifier;
import com.courier.service.dto.CourierEstadoRequest;
import com.courier.service.dto.CourierRequest;
import com.courier.service.dto.CourierResponse;
import com.courier.service.dto.CourierUpdateRequest;
import com.courier.service.dto.DisponibilidadResponse;
import com.courier.service.enums.EstadoCourier;
import com.courier.service.exceptions.RecursoNoEncontradoException;
import com.courier.service.exceptions.RecursoYaExistenteException;
import com.courier.service.model.Courier;
import com.courier.service.repository.CourierRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class CourierServiceImpl implements CourierService {

    /**
     * @param estado filtro opcional
     * @param page página desde cero
     * @param size límite entre 1 y 100
     * @return página ordenada de perfiles
     */
    @Override
    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<CourierResponse> findAll(EstadoCourier estado, int page, int size) {
        var pageable = com.courier.service.config.BoundedPage.of(page, size);
        return (estado == null ? courierRepository.findAll(pageable) : courierRepository.findByEstado(estado, pageable))
                .map(this::toResponse);
    }

    private final CourierRepository courierRepository;
    private final UsuarioRolVerifier usuarioRolVerifier;

    /**
     * Sin {@code @Transactional} a propósito: la llamada HTTP a security-service no debe
     * mantener una transacción (ni una conexión de BD) abierta. Si dos altas del mismo
     * userId compiten, la PK hace fallar a la segunda y el handler global responde 409.
     */
    @Override
    public CourierResponse create(CourierRequest request) {
        if (courierRepository.existsById(request.getUserId())) {
            throw new RecursoYaExistenteException("Ya existe un courier para el usuario " + request.getUserId());
        }
        usuarioRolVerifier.verificarRepartidor(request.getUserId());

        Courier courier = Courier.builder()
                .id(request.getUserId())
                .nombre(request.getNombre())
                .tipoVehiculo(request.getTipoVehiculo())
                .estado(EstadoCourier.ACTIVO)
                .build();
        Courier guardado = courierRepository.save(courier);
        log.info("Se ha creado el Courier: {}", guardado.getId());
        return toResponse(guardado);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CourierResponse> findAll(EstadoCourier estado) {
        return findAll(estado, 0, 20).getContent();
    }

    @Override
    @Transactional(readOnly = true)
    public CourierResponse findById(UUID id) {
        return toResponse(buscar(id));
    }

    @Override
    @Transactional
    public CourierResponse update(UUID id, CourierUpdateRequest request) {
        Courier courier = buscar(id);
        courier.setNombre(request.getNombre());
        courier.setTipoVehiculo(request.getTipoVehiculo());
        log.info("Se actualizaron los datos del Courier: {}", id);
        return toResponse(courierRepository.save(courier));
    }

    @Override
    @Transactional
    public CourierResponse cambiarEstado(UUID id, CourierEstadoRequest request) {
        Courier courier = buscar(id);
        courier.setEstado(request.getEstado());
        log.info("Courier {} pasó a estado {}", id, request.getEstado());
        return toResponse(courierRepository.save(courier));
    }

    @Override
    @Transactional(readOnly = true)
    public DisponibilidadResponse consultarDisponibilidad(UUID id) {
        Courier courier = buscar(id);
        return DisponibilidadResponse.builder()
                .id(courier.getId())
                .disponible(courier.getEstado() == EstadoCourier.ACTIVO)
                .estado(courier.getEstado())
                .build();
    }

    private Courier buscar(UUID id) {
        return courierRepository.findById(id).orElseThrow(() -> {
            log.info("NO se ha encontrado el Courier: {}", id);
            return new RecursoNoEncontradoException("Courier no encontrado: " + id);
        });
    }

    private CourierResponse toResponse(Courier courier) {
        return CourierResponse.builder()
                .id(courier.getId())
                .nombre(courier.getNombre())
                .tipoVehiculo(courier.getTipoVehiculo())
                .estado(courier.getEstado())
                .build();
    }
}
