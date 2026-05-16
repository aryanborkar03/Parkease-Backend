package com.parkease.payment.service;

import com.parkease.payment.entity.PassBalance;
import com.parkease.payment.entity.PassStatus;
import com.parkease.payment.entity.PassTransaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.File;
import java.nio.file.Path;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class PassReceiptServiceTest {

    private PdfReceiptHelper helper;
    private PassReceiptService passReceiptService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        helper = new PdfReceiptHelper();
        passReceiptService = new PassReceiptService(helper);
        ReflectionTestUtils.setField(passReceiptService, "storagePath", tempDir.toString() + File.separator);
    }

    private PassTransaction buildTxn(long txnId) {
        PassTransaction txn = new PassTransaction();
        txn.setTransactionId(txnId);
        txn.setPassId(1L);
        txn.setDriverEmail("aryan@test.com");
        txn.setBookingId(100L);
        txn.setAmount(50.0);
        txn.setPassTransactionRef("PASS-ABC123");
        txn.setCountBefore(5);
        txn.setCountAfter(6);
        txn.setCreatedAt(LocalDateTime.now());
        return txn;
    }

    private PassBalance buildPass() {
        return PassBalance.builder()
                .passId(1L)
                .driverEmail("aryan@test.com")
                .status(PassStatus.ACTIVE)
                .parkingCountLimit(150)
                .parkingCountUsed(6)
                .purchasedAt(LocalDateTime.now().minusDays(5))
                .expiresAt(LocalDateTime.now().plusDays(25))
                .build();
    }

    @Test
    void shouldGeneratePassReceiptSuccessfully() {
        PassTransaction txn = buildTxn(10L);
        PassBalance pass    = buildPass();

        String result = passReceiptService.generatePassReceipt(txn, pass);

        assertNotNull(result);
        assertTrue(result.contains("ParkEase_PassReceipt_10.pdf"));
        assertTrue(new File(result).exists());
    }

    @Test
    void shouldReturnNullWhenExceptionThrown() {
        ReflectionTestUtils.setField(passReceiptService, "storagePath", "Z:/bad_path/invalid/");
        PassTransaction txn = buildTxn(11L);
        PassBalance pass    = buildPass();

        String result = passReceiptService.generatePassReceipt(txn, pass);

        assertNull(result);
    }

    @Test
    void shouldGenerateReceiptWhenStoragePathHasNoTrailingSeparator() {
        // storagePath without trailing slash — helper must append File.separator
        ReflectionTestUtils.setField(passReceiptService, "storagePath",
                tempDir.toString()); // no trailing separator

        PassTransaction txn = buildTxn(12L);
        PassBalance pass    = buildPass();

        String result = passReceiptService.generatePassReceipt(txn, pass);

        assertNotNull(result);
        assertTrue(new File(result).exists());
    }
}
