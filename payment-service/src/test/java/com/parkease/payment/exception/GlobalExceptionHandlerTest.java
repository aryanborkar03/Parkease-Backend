package com.parkease.payment.exception;

import com.parkease.payment.dto.response.ApiResponse;
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
        ResourceNotFoundException ex = new ResourceNotFoundException("Not found test");
        ApiResponse response = handler.handleNotFound(ex);
        assertFalse(response.isSuccess());
        assertEquals("Not found test", response.getMessage());
    }

    @Test
    void handlePayment() {
        PaymentException ex = new PaymentException("Payment error test");
        ApiResponse response = handler.handlePayment(ex);
        assertFalse(response.isSuccess());
        assertEquals("Payment error test", response.getMessage());
    }

    @Test
    void handleValidation() {
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        BindingResult bindingResult = mock(BindingResult.class);
        FieldError fieldError = new FieldError("object", "field", "default message");
        
        when(ex.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getFieldErrors()).thenReturn(List.of(fieldError));

        Map<String, String> response = handler.handleValidation(ex);
        assertEquals(1, response.size());
        assertEquals("default message", response.get("field"));
    }

    @Test
    void handleGeneral() {
        Exception ex = new Exception("General error");
        ApiResponse response = handler.handleGeneral(ex);
        assertFalse(response.isSuccess());
        assertEquals("Something went wrong. Please try again.", response.getMessage());
    }
}



