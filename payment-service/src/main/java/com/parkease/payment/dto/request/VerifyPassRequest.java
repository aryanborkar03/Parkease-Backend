package com.parkease.payment.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Same shape as VerifyPaymentRequest — reused for pass purchase verification.
 * Kept as a separate class so pass and regular payment verification are independently evolvable.
 */
@Data
public class VerifyPassRequest {

    @NotBlank(message = "Razorpay Order ID is required")
    private String razorpayOrderId;

    @NotBlank(message = "Razorpay Payment ID is required")
    private String razorpayPaymentId;

    @NotBlank(message = "Razorpay Signature is required")
    private String razorpaySignature;
}
