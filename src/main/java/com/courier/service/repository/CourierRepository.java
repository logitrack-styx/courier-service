package com.courier.service.repository;

import com.courier.service.enums.EstadoCourier;
import com.courier.service.model.Courier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Clase con métodos del negocio implementados con funciones de JpaRepository
 * @author TheMiggaCool
 */
@Repository
public interface CourierRepository extends JpaRepository<Courier, UUID> {

    // ============================================
    // Búsqueda básica por identificador único
    // ============================================
    /**
     * Buscar un Courier por su estado
     *
     * @param estado
     * @return {@link Optional} con datos del Courier.
     */
    //Optional<Courier> findByEstadoCourier(EstadoCourier estado);

    default List<Courier> findByEstado(EstadoCourier estado) {
        return findByEstado(estado, com.courier.service.config.BoundedPage.of(0, 20)).getContent();
    }
    org.springframework.data.domain.Page<Courier> findByEstado(EstadoCourier estado, org.springframework.data.domain.Pageable pageable);
}
