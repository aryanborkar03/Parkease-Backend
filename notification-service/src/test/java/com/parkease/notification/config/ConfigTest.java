package com.parkease.notification.config;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.MessageConverter;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

class ConfigTest {

    @Test
    void testRabbitMQConfig() {
        RabbitMQConfig config = new RabbitMQConfig();
        Queue queue = config.notificationQueue();
        assertNotNull(queue);
        
        TopicExchange exchange = config.notificationExchange();
        assertNotNull(exchange);
        
        Binding binding = config.notificationBinding();
        assertNotNull(binding);
        
        MessageConverter converter = config.jsonMessageConverter();
        assertNotNull(converter);
        
        ConnectionFactory factory = mock(ConnectionFactory.class);
        RabbitTemplate template = config.rabbitTemplate(factory);
        assertNotNull(template);
    }

    @Test
    void testAsyncConfig() {
        AsyncConfig config = new AsyncConfig();
        assertNotNull(config.emailExecutor());
    }
}
