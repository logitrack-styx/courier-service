package com.courier.service.service;

import java.util.List;
import java.util.UUID;

import com.courier.service.dto.CourierEstadoRequest;
import com.courier.service.dto.CourierRequest;
import com.courier.service.dto.CourierResponse;
import com.courier.service.dto.CourierUpdateRequest;
import com.courier.service.dto.DisponibilidadResponse;
import com.courier.service.enums.EstadoCourier;

/**
 * Operaciones de gestión de couriers (repartidores).
 *
 * @author TheMiggaCool
 */
public interface CourierService {
    /**
     * @param estado filtro opcional
     * @param page página desde cero
     * @param size tamaño entre 1 y 100
     * @return página limitada
     */
    org.springframework.data.domain.Page<CourierResponse> findAll(EstadoCourier estado, int page, int size);


    /** Alta: valida que el userId sea un REPARTIDOR y crea el courier ACTIVO con id = userId. */
    CourierResponse create(CourierRequest courierRequest);

    /** Busca por id (= userId). 404 si no existe. */
    CourierResponse findById(UUID id);

    /** Lista todos los couriers o solo los del estado indicado (null = sin filtro). */
    List<CourierResponse> findAll(EstadoCourier estado);

    /** Actualiza nombre y tipo de vehículo. 404 si no existe. */
    CourierResponse update(UUID id, CourierUpdateRequest request);

    /** Cambia el estado operativo (ACTIVO / INACTIVO). 404 si no existe. */
    CourierResponse cambiarEstado(UUID id, CourierEstadoRequest request);

    /** Indica si el courier puede recibir asignaciones (disponible == ACTIVO). 404 si no existe. */
    DisponibilidadResponse consultarDisponibilidad(UUID id);
}
