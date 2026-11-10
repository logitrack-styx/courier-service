package com.courier.service.messaging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.courier.service.enums.EstadoCourier;
import com.courier.service.model.Courier;
import com.courier.service.repository.CourierRepository;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

/**
 * Verifies that replay preserves the persisted result and rejects mismatched commands.
 * @author dominox45
 */
class AssignmentEligibilityTest {
    @Test
    void storesDecisionsAndReplaysTheSameResponseAfterAvailabilityChanges() {
        var db = new EmbeddedDatabaseBuilder().generateUniqueName(true).setType(EmbeddedDatabaseType.H2)
                .addScript(new ClassPathResource("db/migration/V1__assignment_eligibility_inbox.sql").getPath())
                .build();
        try {
            var repository = mock(CourierRepository.class);
            var decisions = new AssignmentEligibility(new JdbcTemplate(db), repository);
            var courier = UUID.randomUUID();
            var command = new AssignmentEvent(UUID.randomUUID(), UUID.randomUUID(), Instant.now(), 1,
                    UUID.randomUUID(), courier);
            when(repository.findById(courier)).thenReturn(Optional.of(Courier.builder()
                    .estado(EstadoCourier.ACTIVO).build()));
            var accepted = decisions.decide(command);
            assertTrue(accepted.approved());
            when(repository.findById(courier)).thenReturn(Optional.empty());
            assertEquals(accepted, decisions.decide(command));
            assertThrows(IllegalArgumentException.class, () -> decisions.decide(new AssignmentEvent(
                    command.eventId(), command.operationId(), command.occurredAt(), 1, command.shipmentId(),
                    UUID.randomUUID())));
            assertFalse(decisions.decide(new AssignmentEvent(UUID.randomUUID(), UUID.randomUUID(), Instant.now(),
                    1, command.shipmentId(), courier)).approved());
        } finally {
            db.shutdown();
        }
    }
}
