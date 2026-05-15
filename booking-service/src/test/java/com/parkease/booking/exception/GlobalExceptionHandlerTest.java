package com.parkease.booking.exception;

import com.parkease.booking.dto.response.ApiResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

    @InjectMocks
    private GlobalExceptionHandler handler;

    @Test
    void handleNotFound() {
        ResourceNotFoundException ex = new ResourceNotFoundException("Not found");
        ApiResponse res = handler.handleNotFound(ex);
        assertFalse(res.isSuccess());
        assertEquals("Not found", res.getMessage());
    }

    @Test
    void handleBooking() {
        BookingException ex = new BookingException("Booking error");
        ApiResponse res = handler.handleBooking(ex);
        assertFalse(res.isSuccess());
        assertEquals("Booking error", res.getMessage());
    }

    @Test
    void handleBadArg() {
        IllegalArgumentException ex = new IllegalArgumentException("Bad arg");
        ApiResponse res = handler.handleBadArg(ex);
        assertFalse(res.isSuccess());
        assertEquals("Bad arg", res.getMessage());
    }

    @Test
    void handleValidation() {
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        BindingResult bindingResult = mock(BindingResult.class);
        FieldError fieldError = new FieldError("object", "field", "default message");
        
        when(ex.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getFieldErrors()).thenReturn(List.of(fieldError));

        Map<String, String> res = handler.handleValidation(ex);
        assertEquals(1, res.size());
        assertEquals("default message", res.get("field"));
    }

    @Test
    void handleGeneral() {
        Exception ex = new Exception("Error");
        ApiResponse res = handler.handleGeneral(ex);
        assertFalse(res.isSuccess());
        assertEquals("Something went wrong. Please try again.", res.getMessage());
    }
}
