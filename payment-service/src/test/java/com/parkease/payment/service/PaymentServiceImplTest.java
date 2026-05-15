package com.parkease.payment.service;

import com.parkease.payment.dto.request.CreateOrderRequest;
import com.parkease.payment.dto.request.RefundRequest;
import com.parkease.payment.dto.response.OrderResponseDTO;
import com.parkease.payment.dto.response.PaymentResponseDTO;
import com.parkease.payment.entity.Payment;
import com.parkease.payment.entity.PaymentStatus;
import com.parkease.payment.exception.PaymentException;
import com.parkease.payment.mapper.PaymentMapper;
import com.parkease.payment.messaging.NotificationPublisher;
import com.parkease.payment.repository.PaymentRepository;
import com.razorpay.Order;
import com.razorpay.OrderClient;
import com.razorpay.PaymentClient;
import com.razorpay.RazorpayClient;
import com.razorpay.Refund;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import com.parkease.payment.dto.request.VerifyPaymentRequest;
import com.parkease.payment.exception.ResourceNotFoundException;
import com.razorpay.RazorpayException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceImplTest {

    @Mock
    private PaymentRepository repo;

    @Mock
    private PaymentMapper mapper;

    @Mock
    private RazorpayClient razorpayClient;

    @Mock
    private OrderClient orderClient;

    @Mock
    private PaymentClient paymentClient;

    @Mock
    private ReceiptService receiptService;

    @Mock
    private NotificationPublisher notificationPublisher;

    @InjectMocks
    private PaymentServiceImpl service;

    private Payment payment;
    private PaymentResponseDTO responseDTO;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "razorpayKeyId", "rzp_test_key");
        ReflectionTestUtils.setField(service, "razorpayKeySecret", "test_secret");

        razorpayClient.orders = orderClient;
        razorpayClient.payments = paymentClient;

        payment = Payment.builder()
                .paymentId(1L)
                .bookingId(100L)
                .driverEmail("aryan@test.com")
                .amount(150.0)
                .currency("INR")
                .status(PaymentStatus.PENDING)
                .razorpayOrderId("order_123")
                .razorpayPaymentId("pay_123")
                .build();

        responseDTO = new PaymentResponseDTO();
    }

    @Test
    void shouldCreateOrderSuccessfully() throws Exception {
        CreateOrderRequest req = new CreateOrderRequest();
        req.setBookingId(100L);
        req.setAmount(150.0);
        req.setDescription("Parking payment");

        Order mockOrder = mock(Order.class);

        when(repo.findByBookingId(100L)).thenReturn(Optional.empty());
        when(orderClient.create(any(JSONObject.class))).thenReturn(mockOrder);
        when(mockOrder.get("id")).thenReturn("order_123");
        when(repo.save(any(Payment.class))).thenReturn(payment);

        OrderResponseDTO result = service.createOrder(req, "aryan@test.com");

        assertNotNull(result);
        assertEquals("order_123", result.getRazorpayOrderId());
        verify(repo).save(any(Payment.class));
    }

    @Test
    void shouldThrowWhenPaymentAlreadyPaid() {
        payment.setStatus(PaymentStatus.PAID);

        CreateOrderRequest req = new CreateOrderRequest();
        req.setBookingId(100L);
        req.setAmount(150.0);

        when(repo.findByBookingId(100L)).thenReturn(Optional.of(payment));

        assertThrows(PaymentException.class,
                () -> service.createOrder(req, "aryan@test.com"));
    }

    @Test
    void shouldRefundPaymentSuccessfully() throws Exception {
        payment.setStatus(PaymentStatus.PAID);

        RefundRequest req = new RefundRequest();
        req.setBookingId(100L);
        req.setReason("User cancelled");

        Refund mockRefund = mock(Refund.class);

        when(repo.findByBookingId(100L)).thenReturn(Optional.of(payment));
        when(paymentClient.refund(eq("pay_123"), any(JSONObject.class)))
                .thenReturn(mockRefund);
        when(mockRefund.get("id")).thenReturn("refund_123");
        when(repo.save(any(Payment.class))).thenReturn(payment);
        when(mapper.toDTO(any(Payment.class))).thenReturn(responseDTO);
        doNothing().when(notificationPublisher).publish(any());

        PaymentResponseDTO result = service.refundPayment(req);

        assertNotNull(result);
        assertEquals(PaymentStatus.REFUNDED, payment.getStatus());
        verify(repo).save(payment);
    }

    @Test
    void shouldThrowWhenRefundingNonPaidPayment() {
        payment.setStatus(PaymentStatus.PENDING);

        RefundRequest req = new RefundRequest();
        req.setBookingId(100L);

        when(repo.findByBookingId(100L)).thenReturn(Optional.of(payment));

        assertThrows(PaymentException.class,
                () -> service.refundPayment(req));
    }

    @Test
    void shouldRegenerateReceiptWhenMissing() {
        payment.setStatus(PaymentStatus.PAID);
        payment.setReceiptPath(null);

        when(repo.findById(1L)).thenReturn(Optional.of(payment));
        when(receiptService.generateReceipt(payment))
                .thenReturn("C:/temp/new_receipt.pdf");

        String result = service.getReceiptPath(1L, "aryan@test.com");

        assertEquals("C:/temp/new_receipt.pdf", result);
        verify(repo).save(payment);
    }

    @Test
    void shouldGetReceiptPathSuccessfully() {
        payment.setStatus(PaymentStatus.PAID);
        payment.setReceiptPath(null);

        when(repo.findById(1L)).thenReturn(Optional.of(payment));
        when(receiptService.generateReceipt(payment))
                .thenReturn("C:/temp/receipt.pdf");

        String result = service.getReceiptPath(1L, "aryan@test.com");

        assertEquals("C:/temp/receipt.pdf", result);
    }

    @Test
    void shouldThrowWhenReceiptRequestedByAnotherDriver() {
        payment.setStatus(PaymentStatus.PAID);

        when(repo.findById(1L)).thenReturn(Optional.of(payment));

        assertThrows(RuntimeException.class,
                () -> service.getReceiptPath(1L, "other@test.com"));
    }

    private String generateSignature(String orderId, String paymentId, String secret) throws Exception {
        String payload = orderId + "|" + paymentId;
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec secretKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        mac.init(secretKey);
        byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(hash);
    }

    @Test
    void shouldVerifyPaymentSuccessfully() throws Exception {
        VerifyPaymentRequest req = new VerifyPaymentRequest();
        req.setRazorpayOrderId("order_123");
        req.setRazorpayPaymentId("pay_123");
        req.setRazorpaySignature(generateSignature("order_123", "pay_123", "test_secret"));

        when(repo.findByRazorpayOrderId("order_123")).thenReturn(Optional.of(payment));
        com.razorpay.Payment mockRzpPayment = mock(com.razorpay.Payment.class);
        when(paymentClient.fetch("pay_123")).thenReturn(mockRzpPayment);
        when(mockRzpPayment.get("method")).thenReturn("card");
        when(repo.save(any(Payment.class))).thenReturn(payment);
        when(receiptService.generateReceipt(any(Payment.class))).thenReturn("receipt.pdf");
        doNothing().when(notificationPublisher).publish(any());
        when(mapper.toDTO(any(Payment.class))).thenReturn(responseDTO);

        PaymentResponseDTO result = service.verifyPayment(req);

        assertNotNull(result);
        assertEquals(PaymentStatus.PAID, payment.getStatus());
    }

    @Test
    void shouldFailVerificationWhenSignatureInvalid() {
        VerifyPaymentRequest req = new VerifyPaymentRequest();
        req.setRazorpayOrderId("order_123");
        req.setRazorpayPaymentId("pay_123");
        req.setRazorpaySignature("invalid_signature");

        when(repo.findByRazorpayOrderId("order_123")).thenReturn(Optional.of(payment));

        assertThrows(PaymentException.class, () -> service.verifyPayment(req));
        assertEquals(PaymentStatus.FAILED, payment.getStatus());
    }

    @Test
    void shouldThrowExceptionWhenCreateOrderFailsWithRazorpayException() throws Exception {
        CreateOrderRequest req = new CreateOrderRequest();
        req.setBookingId(100L);
        req.setAmount(150.0);

        when(repo.findByBookingId(100L)).thenReturn(Optional.empty());
        when(orderClient.create(any(JSONObject.class))).thenThrow(new RazorpayException("Razorpay error"));

        assertThrows(PaymentException.class, () -> service.createOrder(req, "aryan@test.com"));
    }

    @Test
    void shouldThrowExceptionWhenRefundFailsWithRazorpayException() throws Exception {
        payment.setStatus(PaymentStatus.PAID);
        RefundRequest req = new RefundRequest();
        req.setBookingId(100L);

        when(repo.findByBookingId(100L)).thenReturn(Optional.of(payment));
        when(paymentClient.refund(anyString(), any(JSONObject.class))).thenThrow(new RazorpayException("Razorpay error"));

        assertThrows(PaymentException.class, () -> service.refundPayment(req));
    }

    @Test
    void shouldGetPaymentByBookingIdSuccessfully() {
        when(repo.findByBookingId(100L)).thenReturn(Optional.of(payment));
        when(mapper.toDTO(payment)).thenReturn(responseDTO);

        PaymentResponseDTO result = service.getPaymentByBookingId(100L);
        assertNotNull(result);
    }

    @Test
    void shouldThrowWhenPaymentNotFoundByBookingId() {
        when(repo.findByBookingId(100L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.getPaymentByBookingId(100L));
    }

    @Test
    void shouldGetPaymentByIdSuccessfully() {
        when(repo.findById(1L)).thenReturn(Optional.of(payment));
        when(mapper.toDTO(payment)).thenReturn(responseDTO);

        PaymentResponseDTO result = service.getPaymentById(1L);
        assertNotNull(result);
    }

    @Test
    void shouldGetMyPaymentsSuccessfully() {
        when(repo.findByDriverEmailOrderByCreatedAtDesc("aryan@test.com")).thenReturn(List.of(payment));
        when(mapper.toDTO(payment)).thenReturn(responseDTO);

        List<PaymentResponseDTO> result = service.getMyPayments("aryan@test.com");
        assertFalse(result.isEmpty());
    }

    @Test
    void shouldGetAllPaymentsSuccessfully() {
        when(repo.findAll()).thenReturn(List.of(payment));
        when(mapper.toDTO(payment)).thenReturn(responseDTO);

        List<PaymentResponseDTO> result = service.getAllPayments();
        assertFalse(result.isEmpty());
    }

    @Test
    void shouldThrowWhenReceiptNotPaid() {
        payment.setStatus(PaymentStatus.PENDING);
        when(repo.findById(1L)).thenReturn(Optional.of(payment));
        assertThrows(PaymentException.class, () -> service.getReceiptPath(1L, "aryan@test.com"));
    }

    @Test
    void shouldThrowWhenVerifyPaymentNotFound() {
        VerifyPaymentRequest req = new VerifyPaymentRequest();
        req.setRazorpayOrderId("order_not_found");
        when(repo.findByRazorpayOrderId("order_not_found")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.verifyPayment(req));
    }

    @Test
    void shouldVerifyPaymentAndHandleFetchException() throws Exception {
        VerifyPaymentRequest req = new VerifyPaymentRequest();
        req.setRazorpayOrderId("order_123");
        req.setRazorpayPaymentId("pay_123");
        req.setRazorpaySignature(generateSignature("order_123", "pay_123", "test_secret"));

        when(repo.findByRazorpayOrderId("order_123")).thenReturn(Optional.of(payment));
        when(paymentClient.fetch("pay_123")).thenThrow(new RazorpayException("Fetch error"));
        when(repo.save(any(Payment.class))).thenReturn(payment);
        when(receiptService.generateReceipt(any(Payment.class))).thenReturn(null);
        doNothing().when(notificationPublisher).publish(any());
        when(mapper.toDTO(any(Payment.class))).thenReturn(responseDTO);

        PaymentResponseDTO result = service.verifyPayment(req);
        assertNotNull(result);
        assertEquals(PaymentStatus.PAID, payment.getStatus());
        assertNull(payment.getMode());
    }

    @Test
    void shouldVerifyPaymentWithUpiMode() throws Exception {
        VerifyPaymentRequest req = new VerifyPaymentRequest();
        req.setRazorpayOrderId("order_123");
        req.setRazorpayPaymentId("pay_123");
        req.setRazorpaySignature(generateSignature("order_123", "pay_123", "test_secret"));

        when(repo.findByRazorpayOrderId("order_123")).thenReturn(Optional.of(payment));
        com.razorpay.Payment mockRzpPayment = mock(com.razorpay.Payment.class);
        when(paymentClient.fetch("pay_123")).thenReturn(mockRzpPayment);
        when(mockRzpPayment.get("method")).thenReturn("upi");
        when(repo.save(any(Payment.class))).thenReturn(payment);
        when(receiptService.generateReceipt(any(Payment.class))).thenReturn("receipt.pdf");
        doNothing().when(notificationPublisher).publish(any());
        when(mapper.toDTO(any(Payment.class))).thenReturn(responseDTO);

        PaymentResponseDTO result = service.verifyPayment(req);
        assertNotNull(result);
    }

    @Test
    void shouldVerifyPaymentWithNetbankingMode() throws Exception {
        VerifyPaymentRequest req = new VerifyPaymentRequest();
        req.setRazorpayOrderId("order_123");
        req.setRazorpayPaymentId("pay_123");
        req.setRazorpaySignature(generateSignature("order_123", "pay_123", "test_secret"));

        when(repo.findByRazorpayOrderId("order_123")).thenReturn(Optional.of(payment));
        com.razorpay.Payment mockRzpPayment = mock(com.razorpay.Payment.class);
        when(paymentClient.fetch("pay_123")).thenReturn(mockRzpPayment);
        when(mockRzpPayment.get("method")).thenReturn("netbanking");
        when(repo.save(any(Payment.class))).thenReturn(payment);
        when(receiptService.generateReceipt(any(Payment.class))).thenReturn("receipt.pdf");
        doNothing().when(notificationPublisher).publish(any());
        when(mapper.toDTO(any(Payment.class))).thenReturn(responseDTO);

        PaymentResponseDTO result = service.verifyPayment(req);
        assertNotNull(result);
    }

    @Test
    void shouldVerifyPaymentWithUnknownMode() throws Exception {
        VerifyPaymentRequest req = new VerifyPaymentRequest();
        req.setRazorpayOrderId("order_123");
        req.setRazorpayPaymentId("pay_123");
        req.setRazorpaySignature(generateSignature("order_123", "pay_123", "test_secret"));

        when(repo.findByRazorpayOrderId("order_123")).thenReturn(Optional.of(payment));
        com.razorpay.Payment mockRzpPayment = mock(com.razorpay.Payment.class);
        when(paymentClient.fetch("pay_123")).thenReturn(mockRzpPayment);
        when(mockRzpPayment.get("method")).thenReturn("unknown");
        when(repo.save(any(Payment.class))).thenReturn(payment);
        when(receiptService.generateReceipt(any(Payment.class))).thenReturn("receipt.pdf");
        doNothing().when(notificationPublisher).publish(any());
        when(mapper.toDTO(any(Payment.class))).thenReturn(responseDTO);

        PaymentResponseDTO result = service.verifyPayment(req);
        assertNotNull(result);
    }

    @Test
    void shouldThrowWhenRefundPaymentNotFound() {
        RefundRequest req = new RefundRequest();
        req.setBookingId(999L);
        when(repo.findByBookingId(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.refundPayment(req));
    }

    @Test
    void shouldRefundPaymentWithNullReason() throws Exception {
        payment.setStatus(PaymentStatus.PAID);
        RefundRequest req = new RefundRequest();
        req.setBookingId(100L);
        req.setReason(null);

        Refund mockRefund = mock(Refund.class);
        when(repo.findByBookingId(100L)).thenReturn(Optional.of(payment));
        when(paymentClient.refund(eq("pay_123"), any(JSONObject.class))).thenReturn(mockRefund);
        when(mockRefund.get("id")).thenReturn("refund_123");
        when(repo.save(any(Payment.class))).thenReturn(payment);
        when(mapper.toDTO(any(Payment.class))).thenReturn(responseDTO);
        doNothing().when(notificationPublisher).publish(any());

        PaymentResponseDTO result = service.refundPayment(req);
        assertNotNull(result);
        assertEquals(PaymentStatus.REFUNDED, payment.getStatus());
    }

    @Test
    void shouldThrowWhenPaymentNotFoundById() {
        when(repo.findById(999L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.getPaymentById(999L));
    }

    @Test
    void shouldReturnEmptyListForMyPayments() {
        when(repo.findByDriverEmailOrderByCreatedAtDesc("aryan@test.com")).thenReturn(List.of());
        List<PaymentResponseDTO> result = service.getMyPayments("aryan@test.com");
        assertTrue(result.isEmpty());
    }

    @Test
    void shouldReturnEmptyListForAllPayments() {
        when(repo.findAll()).thenReturn(List.of());
        List<PaymentResponseDTO> result = service.getAllPayments();
        assertTrue(result.isEmpty());
    }

    @Test
    void shouldThrowWhenReceiptNotFoundById() {
        when(repo.findById(999L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.getReceiptPath(999L, "aryan@test.com"));
    }

    @Test
    void shouldNotRegenerateReceiptWhenFileExists() throws Exception {
        java.io.File tempFile = java.io.File.createTempFile("receipt", ".pdf");
        tempFile.deleteOnExit();
        
        payment.setStatus(PaymentStatus.PAID);
        payment.setReceiptPath(tempFile.getAbsolutePath());

        when(repo.findById(1L)).thenReturn(Optional.of(payment));

        String result = service.getReceiptPath(1L, "aryan@test.com");
        assertEquals(tempFile.getAbsolutePath(), result);
        verify(receiptService, never()).generateReceipt(any());
    }
}
