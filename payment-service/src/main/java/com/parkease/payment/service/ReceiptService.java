package com.parkease.payment.service;

import com.itextpdf.text.*;
import com.itextpdf.text.pdf.*;
import com.parkease.payment.entity.Payment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.format.DateTimeFormatter;

import static com.parkease.payment.service.PdfReceiptHelper.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class ReceiptService {

    @Value("${app.receipt.storage-path}")
    private String storagePath;

    private final PdfReceiptHelper helper;

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a");

    public String generateReceipt(Payment payment) {
        try {
            Path dir = Paths.get(storagePath);
            if (!Files.exists(dir)) Files.createDirectories(dir);
        } catch (IOException e) {
            log.error("Failed to create receipts directory: {}", e.getMessage());
        }

        String fileName = storagePath + "ParkEase_Receipt_" + payment.getPaymentId() + ".pdf";

        try {
            Document document = new Document(PageSize.A5, 40f, 40f, 36f, 28f);
            PdfWriter.getInstance(document, new FileOutputStream(fileName));
            document.open();

            // ── Fonts ──
            Font titleFont   = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 22f, BLACK);
            Font subFont     = FontFactory.getFont(FontFactory.HELVETICA,       9f, MID_GRAY);
            Font receiptFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13f, BLACK);
            Font amtFont     = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 20f, BLACK);
            Font statusFont  = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10f, DARK_GRAY);
            Font labelFont   = FontFactory.getFont(FontFactory.HELVETICA_BOLD,  9f, BLACK);
            Font valueFont   = FontFactory.getFont(FontFactory.HELVETICA,       9f, DARK_GRAY);
            Font footerFont  = FontFactory.getFont(FontFactory.HELVETICA,       7f, MID_GRAY);

            // ── Brand header ──
            helper.addBrandHeader(document, titleFont, subFont);

            // ── PAYMENT RECEIPT heading ──
            Paragraph receiptHead = new Paragraph("PAYMENT RECEIPT", receiptFont);
            receiptHead.setAlignment(Element.ALIGN_CENTER);
            receiptHead.setSpacingBefore(10f);
            receiptHead.setSpacingAfter(8f);
            document.add(receiptHead);

            // ── Amount ──
            Paragraph amount = new Paragraph("\u20B9 " + String.format("%.2f", payment.getAmount()), amtFont);
            amount.setAlignment(Element.ALIGN_CENTER);
            amount.setSpacingAfter(4f);
            document.add(amount);

            // ── Status ──
            Paragraph statusPara = new Paragraph("PAYMENT SUCCESSFUL", statusFont);
            statusPara.setAlignment(Element.ALIGN_CENTER);
            statusPara.setSpacingAfter(12f);
            document.add(statusPara);

            helper.addDivider(document, BLACK, 1.0f);

            // ── Details table ──
            PdfPTable table = new PdfPTable(2);
            table.setWidthPercentage(100);
            table.setSpacingBefore(12f);
            table.setSpacingAfter(12f);
            table.setWidths(new float[]{ 38f, 62f });

            boolean shade = true;
            helper.addRow(table, "Receipt No.",  "RCP-" + payment.getPaymentId(),                         labelFont, valueFont, shade); shade = !shade;
            helper.addRow(table, "Booking ID",   "#" + payment.getBookingId(),                             labelFont, valueFont, shade); shade = !shade;
            helper.addRow(table, "Driver",       payment.getDriverEmail(),                                 labelFont, valueFont, shade); shade = !shade;
            helper.addRow(table, "Amount Paid",  "\u20B9 " + String.format("%.2f", payment.getAmount()), labelFont, valueFont, shade); shade = !shade;
            helper.addRow(table, "Currency",     payment.getCurrency(),                                    labelFont, valueFont, shade); shade = !shade;

            if (payment.getMode() != null) {
                helper.addRow(table, "Payment Mode",   payment.getMode().name(),              labelFont, valueFont, shade); shade = !shade;
            }
            if (payment.getRazorpayPaymentId() != null) {
                helper.addRow(table, "Transaction ID", payment.getRazorpayPaymentId(),        labelFont, valueFont, shade); shade = !shade;
            }
            if (payment.getRazorpayOrderId() != null) {
                helper.addRow(table, "Order ID",       payment.getRazorpayOrderId(),          labelFont, valueFont, shade); shade = !shade;
            }
            if (payment.getPaidAt() != null) {
                helper.addRow(table, "Paid At",        payment.getPaidAt().format(FORMATTER), labelFont, valueFont, shade); shade = !shade;
            }
            if (payment.getDescription() != null && !payment.getDescription().isBlank()) {
                helper.addRow(table, "Description",    payment.getDescription(),              labelFont, valueFont, shade);
            }

            document.add(table);

            helper.addDivider(document, LIGHT_GRAY, 0.6f);

            // ── Footer ──
            helper.addFooter(document, footerFont);

            document.close();
            log.info("Receipt generated: {}", fileName);
            return fileName;

        } catch (Exception e) {
            log.error("Failed to generate receipt for payment {}: {}", payment.getPaymentId(), e.getMessage());
            return null;
        }
    }
}
