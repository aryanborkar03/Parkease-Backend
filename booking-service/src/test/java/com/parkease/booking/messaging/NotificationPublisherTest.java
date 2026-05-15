package com.parkease.booking.messaging;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationPublisherTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    @InjectMocks
    private NotificationPublisher publisher;

    @Test
    void publish_Success() {
        NotificationEvent event = NotificationEvent.builder()
                .recipientEmail("test@test.com")
                .type("BOOKING")
                .build();

        doNothing().when(rabbitTemplate).convertAndSend(anyString(), anyString(), eq(event));

        publisher.publish(event);

        verify(rabbitTemplate, times(1)).convertAndSend(anyString(), anyString(), eq(event));
    }

    @Test
    void publish_Exception() {
        NotificationEvent event = NotificationEvent.builder()
                .recipientEmail("test@test.com")
                .type("BOOKING")
                .build();

        doThrow(new RuntimeException("Rabbit error"))
                .when(rabbitTemplate).convertAndSend(anyString(), anyString(), eq(event));

        // Verifies that publish failures are handled gracefully without propagating an exception
        publisher.publish(event);

        verify(rabbitTemplate, times(1)).convertAndSend(anyString(), anyString(), eq(event));
    }
}
