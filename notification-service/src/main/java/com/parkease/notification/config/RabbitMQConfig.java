package com.parkease.notification.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    // Constants
    public static final String EXCHANGE        = "parkease.notifications";
    public static final String QUEUE           = "notification.queue";
    public static final String ROUTING_KEY     = "notification.send";

    // Dead-letter queue — messages that fail repeatedly land here instead of
    // being silently dropped. You can inspect / replay them from the RabbitMQ UI.
    public static final String DLQ             = "notification.dlq";
    public static final String DLQ_ROUTING_KEY = "notification.dead";

    // Exchange Configuration
    /**
     * TopicExchange allows routing by pattern (e.g. "notification.*").
     * Publishers send to this exchange with a routing key.
     */
    @Bean
    public TopicExchange notificationExchange() {
        return ExchangeBuilder
                .topicExchange(EXCHANGE)
                .durable(true)   // survives broker restart
                .build();
    }

    // Dead-Letter Exchange & Queue Configuration
    @Bean
    public DirectExchange deadLetterExchange() {
        return ExchangeBuilder
                .directExchange(EXCHANGE + ".dlx")
                .durable(true)
                .build();
    }

    @Bean
    public Queue deadLetterQueue() {
        return QueueBuilder
                .durable(DLQ)
                .build();
    }

    @Bean
    public Binding deadLetterBinding() {
        return BindingBuilder
                .bind(deadLetterQueue())
                .to(deadLetterExchange())
                .with(DLQ_ROUTING_KEY);
    }

    // Main Queue Configuration
    /**
     * Durable queue — survives broker restart.
     * Configured with dead-letter exchange so failed messages are not lost.
     */
    @Bean
    public Queue notificationQueue() {
        return QueueBuilder
                .durable(QUEUE)
                .withArgument("x-dead-letter-exchange", EXCHANGE + ".dlx")
                .withArgument("x-dead-letter-routing-key", DLQ_ROUTING_KEY)
                .build();
    }

    // Binding Configuration
    @Bean
    public Binding notificationBinding() {
        return BindingBuilder
                .bind(notificationQueue())
                .to(notificationExchange())
                .with(ROUTING_KEY);
    }

    // JSON Message Converter Configuration
    /**
     * Tells Spring AMQP to serialize/deserialize messages as JSON using Jackson.
     * Without this, Spring uses Java serialization (fragile & verbose).
     */
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter());
        return template;
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(jsonMessageConverter());
        // Retry failed messages up to 3 times before sending to DLQ
        factory.setDefaultRequeueRejected(false);
        return factory;
    }
}
