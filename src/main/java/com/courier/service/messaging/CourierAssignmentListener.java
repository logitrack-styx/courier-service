package com.courier.service.messaging;

import java.io.IOException;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Evaluates commands and acknowledges only after persisting the decision.
 * @author dominox45
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "courier.assignment.messaging-enabled", havingValue = "true")
class CourierAssignmentListener {
    private final AssignmentEligibility eligibility;
    private final ObjectMapper mapper;

    @RabbitListener(queues = CourierAssignmentTopology.COMMANDS)
    void receive(Message message) {
        try {
            eligibility.decide(mapper.readValue(message.getBody(), AssignmentEvent.class));
        } catch (IOException | IllegalArgumentException invalid) {
            throw new AmqpRejectAndDontRequeueException("Comando de asignación inválido", invalid);
        }
    }
}
