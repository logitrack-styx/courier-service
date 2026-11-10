package com.courier.service.messaging;

import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Exposes aggregate result and dead-letter counts for Courier messaging.
 * @author dominox45
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "courier.assignment.messaging-enabled", havingValue = "true")
class CourierAssignmentMetrics {
    @Bean
    MeterBinder assignmentQueueMetrics(JdbcTemplate jdbc, AmqpAdmin rabbit) {
        return registry -> {
            registry.gauge("logitrack.courier.assignment.outbox.pending", jdbc, database -> database.queryForObject(
                    "SELECT COUNT(*) FROM courier_assignment_eligibility WHERE publication_status = 'PENDING'", Long.class));
            registry.gauge("logitrack.courier.assignment.outbox.failed", jdbc, database -> database.queryForObject(
                    "SELECT COUNT(*) FROM courier_assignment_eligibility WHERE publication_status = 'FAILED'", Long.class));
            registry.gauge("logitrack.courier.assignment.dlq", rabbit, admin -> {
                try {
                    var queue = admin.getQueueInfo(CourierAssignmentTopology.COMMANDS + ".dlq");
                    return queue == null ? Double.NaN : queue.getMessageCount();
                } catch (AmqpException unavailable) {
                    return Double.NaN;
                }
            });
        };
    }
}
