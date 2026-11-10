package com.courier.service.messaging;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import com.courier.service.enums.EstadoCourier;
import com.courier.service.repository.CourierRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists one immutable eligibility decision per assignment operation.
 * @author dominox45
 */
@Service
@RequiredArgsConstructor
class AssignmentEligibility {
    private final JdbcTemplate jdbc;
    private final CourierRepository couriers;

    @Transactional(isolation = Isolation.READ_COMMITTED)
    Decision decide(AssignmentEvent command) {
        var existing = find(command);
        if (existing != null) {
            return existing;
        }
        var resultId = UUID.randomUUID();
        var now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        boolean approved = available(command.courierId());
        try {
            jdbc.update("""
                    INSERT INTO courier_assignment_eligibility
                    (command_event_id, operation_id, shipment_id, courier_id, result_event_id, occurred_at, approved)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    """, command.eventId().toString(), command.operationId().toString(),
                    command.shipmentId().toString(), command.courierId().toString(),
                    resultId.toString(), Timestamp.from(now), approved);
        } catch (DuplicateKeyException duplicate) {
            var concurrent = find(command);
            if (concurrent != null) {
                return concurrent;
            }
            throw new IllegalArgumentException("El evento ya corresponde a otra operación", duplicate);
        }
        return new Decision(response(command, resultId, now), approved);
    }

    private Decision find(AssignmentEvent command) {
        return jdbc.query("SELECT * FROM courier_assignment_eligibility WHERE operation_id = ?", (row, index) -> {
            if (!command.eventId().toString().equals(row.getString("command_event_id"))
                    || !command.shipmentId().toString().equals(row.getString("shipment_id"))
                    || !command.courierId().toString().equals(row.getString("courier_id"))) {
                throw new IllegalArgumentException("El comando no coincide con la operación persistida");
            }
            return new Decision(response(command, UUID.fromString(row.getString("result_event_id")),
                    row.getTimestamp("occurred_at").toInstant()), row.getBoolean("approved"));
        }, command.operationId().toString()).stream().findFirst().orElse(null);
    }

    private boolean available(UUID courierId) {
        return couriers.findById(courierId).filter(courier -> courier.getEstado() == EstadoCourier.ACTIVO)
                .isPresent();
    }

    private AssignmentEvent response(AssignmentEvent command, UUID resultId, Instant now) {
        return new AssignmentEvent(resultId, command.operationId(), now, 1,
                command.shipmentId(), command.courierId());
    }

    record Decision(AssignmentEvent event, boolean approved) {}
}
