package com.parkease.notification.messaging;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NotificationEventTest {

    @Test
    void testNotificationEvent() {
        NotificationEvent e1 = NotificationEvent.builder()
                .recipientEmail("test@test.com")
                .type("TYPE")
                .title("TITLE")
                .message("MSG")
                .relatedId(1L)
                .relatedType("BOOKING")
                .build();
        
        NotificationEvent e2 = new NotificationEvent();
        e2.setRecipientEmail("test@test.com");
        e2.setType("TYPE");
        e2.setTitle("TITLE");
        e2.setMessage("MSG");
        e2.setRelatedId(1L);
        e2.setRelatedType("BOOKING");

        assertEquals(e1.getRecipientEmail(), e2.getRecipientEmail());
        assertEquals(e1.getType(), e2.getType());
        assertEquals(e1.getTitle(), e2.getTitle());
        assertEquals(e1.getMessage(), e2.getMessage());
        assertEquals(e1.getRelatedId(), e2.getRelatedId());
        assertEquals(e1.getRelatedType(), e2.getRelatedType());
        
        assertEquals(e1, e2);
        assertEquals(e1.hashCode(), e2.hashCode());
        assertNotNull(e1.toString());
    }
}
