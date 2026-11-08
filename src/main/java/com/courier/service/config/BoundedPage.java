package com.courier.service.config;
import org.springframework.data.domain.*;
/**
 * Limita cada consulta a 100 filas y fija un orden estable por UUID.
 * @author dominox45
 */
public final class BoundedPage {
    private BoundedPage() { }
    /**
     * @param page número de página desde cero
     * @param size tamaño entre 1 y 100
     * @return página ordenada por identificador
     * @throws IllegalArgumentException para límites inválidos
     */
    public static Pageable of(int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw new IllegalArgumentException("page >= 0; size entre 1 y 100");
        return PageRequest.of(page, size, Sort.by("id"));
    }
}
