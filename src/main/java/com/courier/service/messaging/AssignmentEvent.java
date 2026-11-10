package com.courier.service.messaging;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Versioned assignment command and eligibility response shared with Shipment.
 * @author dominox45
 */
record AssignmentEvent(UUID eventId, UUID operationId, Instant occurredAt,
                       int schemaVersion, UUID shipmentId, UUID courierId) {
    AssignmentEvent {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(operationId, "operationId");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(shipmentId, "shipmentId");
        Objects.requireNonNull(courierId, "courierId");
        if (schemaVersion != 1) {
            throw new IllegalArgumentException("Versión de evento no soportada");
        }
    }
}
