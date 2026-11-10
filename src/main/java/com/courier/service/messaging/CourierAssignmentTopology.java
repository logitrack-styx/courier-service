package com.courier.service.messaging;

import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Declares the durable command queue and the destination for exhausted deliveries.
 * @author dominox45
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(name = "courier.assignment.messaging-enabled", havingValue = "true")
class CourierAssignmentTopology {
    static final String EXCHANGE = "logitrack.domain.v1";
    static final String COMMANDS = "courier.assignment.requests.v1";
    static final String REQUESTED = "courier.assignment.requested.v1";
    static final String APPROVED = "courier.assignment.eligibility-approved.v1";
    static final String REJECTED = "courier.assignment.eligibility-rejected.v1";

    @Bean
    Declarables courierAssignmentQueues() {
        var exchange = new TopicExchange(EXCHANGE);
        var deadLetters = QueueBuilder.durable(COMMANDS + ".dlq").build();
        var commands = QueueBuilder.durable(COMMANDS).deadLetterExchange("")
                .deadLetterRoutingKey(deadLetters.getName()).build();
        return new Declarables(exchange, commands, deadLetters,
                BindingBuilder.bind(commands).to(exchange).with(REQUESTED));
    }
}
