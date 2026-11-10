package com.courier.service.messaging;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Publishes committed eligibility results with leases, routing confirmation and bounded retries.
 * @author dominox45
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "courier.assignment.messaging-enabled", havingValue = "true")
class CourierResultPublisher {
    private static final int MAX_ATTEMPTS = 5;
    private final JdbcTemplate jdbc;
    private final RabbitTemplate rabbit;
    private final ObjectMapper mapper;

    @Scheduled(fixedDelayString = "${courier.assignment.poll-delay-ms:1000}")
    void publishDue() {
        var due = jdbc.query("""
                SELECT result_event_id, operation_id, occurred_at, shipment_id, courier_id, approved, attempts
                FROM courier_assignment_eligibility
                WHERE publication_status = 'PENDING' AND (available_at IS NULL OR available_at <= ?)
                ORDER BY occurred_at, result_event_id LIMIT 20
                """, (row, index) -> new Pending(new AssignmentEvent(
                        UUID.fromString(row.getString("result_event_id")), UUID.fromString(row.getString("operation_id")),
                        row.getTimestamp("occurred_at").toInstant(), 1, UUID.fromString(row.getString("shipment_id")),
                        UUID.fromString(row.getString("courier_id"))), row.getBoolean("approved"), row.getInt("attempts")),
                Timestamp.from(Instant.now()));
        for (var row : due) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }
            publish(row);
        }
    }

    private void publish(Pending row) {
        var event = row.event();
        String id = event.eventId().toString();
        String lease = UUID.randomUUID().toString();
        var now = Instant.now();
        int claimed = jdbc.update("""
                UPDATE courier_assignment_eligibility
                SET attempts = attempts + 1, lease_token = ?, available_at = ?
                WHERE result_event_id = ? AND publication_status = 'PENDING' AND attempts = ?
                  AND (available_at IS NULL OR available_at <= ?)
                """, lease, Timestamp.from(now.plusSeconds(60)), id, row.attempts(), Timestamp.from(now));
        if (claimed == 0) {
            return;
        }
        if (row.attempts() >= MAX_ATTEMPTS) {
            finish(id, lease, "FAILED", null);
            return;
        }
        try {
            var response = MessageBuilder.withBody(mapper.writeValueAsBytes(event))
                    .setContentType("application/json").setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                    .setMessageId(id).setCorrelationId(event.operationId().toString()).build();
            var confirm = new CorrelationData(lease);
            rabbit.send(CourierAssignmentTopology.EXCHANGE,
                    row.approved() ? CourierAssignmentTopology.APPROVED : CourierAssignmentTopology.REJECTED,
                    response, confirm);
            if (!confirm.getFuture().get(5, TimeUnit.SECONDS).isAck() || confirm.getReturned() != null) {
                throw new AmqpException("RabbitMQ no confirmó el enrutamiento del resultado");
            }
            finish(id, lease, "SENT", null);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } catch (AmqpException | JsonProcessingException | ExecutionException | TimeoutException failure) {
            int attempt = row.attempts() + 1;
            if (attempt >= MAX_ATTEMPTS) {
                finish(id, lease, "FAILED", null);
            } else {
                finish(id, lease, "PENDING", Timestamp.from(Instant.now().plusSeconds(1L << attempt)));
            }
            log.warn("Courier result event {} publication failed on attempt {}", id, attempt);
        }
    }

    private void finish(String id, String lease, String status, Timestamp availableAt) {
        jdbc.update("""
                UPDATE courier_assignment_eligibility
                SET publication_status = ?, lease_token = NULL, available_at = ?
                WHERE result_event_id = ? AND lease_token = ?
                """, status, availableAt, id, lease);
    }

    private record Pending(AssignmentEvent event, boolean approved, int attempts) {}
}
