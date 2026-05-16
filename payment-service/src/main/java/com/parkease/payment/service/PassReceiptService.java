package com.parkease.payment.service;

import com.itextpdf.text.*;
import com.itextpdf.text.pdf.*;
import com.parkease.payment.entity.PassBalance;
import com.parkease.payment.entity.PassTransaction;
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

/**
 * Generates PDF receipts for pass-based payments.
 * Mirrors the structure of ReceiptService but carries pass-specific fields
 * (Parkings Remaining, Pass Valid Until, Payment Method = ParkEase Pass).
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PassReceiptService {

    @Value("${app.receipt.storage-path}")
    private String storagePath;

    private final PdfReceiptHelper helper;

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a");
    private static final DateTimeFormatter DATE_ONLY =
            DateTimeFormatter.ofPattern("dd MMM yyyy");

    public String generatePassReceipt(PassTransaction txn, PassBalance pass) {
        // Resolve to absolute path so the stored path works regardless of JVM working directory
        String absoluteStoragePath = Paths.get(storagePath).toAbsolutePath().toString();
        if (!absoluteStoragePath.endsWith(java.io.File.separator)) {
            absoluteStoragePath += java.io.File.separator;
        }

        try {
            Path dir = Paths.get(absoluteStoragePath);
            if (!Files.exists(dir)) Files.createDirectories(dir);
        } catch (IOException e) {
            log.error("Failed to create receipts directory: {}", e.getMessage());
        }

        String fileName = absoluteStoragePath + "ParkEase_PassReceipt_" + txn.getTransactionId() + ".pdf";

        try {
            Document document = new Document(PageSize.A5, 40f, 40f, 36f, 28f);
            PdfWriter.getInstance(document, new FileOutputStream(fileName));
            document.open();

            Font titleFont   = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 22f, BLACK);
            Font subFont     = FontFactory.getFont(FontFactory.HELVETICA,       9f, MID_GRAY);
            Font receiptFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13f, BLACK);
            Font amtFont     = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 20f, BLACK);
            Font statusFont  = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10f, GOLD);
            Font labelFont   = FontFactory.getFont(FontFactory.HELVETICA_BOLD,  9f, BLACK);
            Font valueFont   = FontFactory.getFont(FontFactory.HELVETICA,       9f, DARK_GRAY);
            Font footerFont  = FontFactory.getFont(FontFactory.HELVETICA,       7f, MID_GRAY);

            // Brand
            helper.addBrandHeader(document, titleFont, subFont);

            Paragraph receiptHead = new Paragraph("PAYMENT RECEIPT \u2014 PREMIUM PASS", receiptFont);
            receiptHead.setAlignment(Element.ALIGN_CENTER);
            receiptHead.setSpacingBefore(10f);
            receiptHead.setSpacingAfter(8f);
            document.add(receiptHead);

            Paragraph amount = new Paragraph(
                    "\u20B9 " + String.format("%.2f", txn.getAmount()), amtFont);
            amount.setAlignment(Element.ALIGN_CENTER);
            amount.setSpacingAfter(4f);
            document.add(amount);

            Paragraph statusPara = new Paragraph("PAYMENT SUCCESSFUL  \u2605  PARKEASE PASS", statusFont);
            statusPara.setAlignment(Element.ALIGN_CENTER);
            statusPara.setSpacingAfter(12f);
            document.add(statusPara);

            helper.addDivider(document, BLACK, 1.0f);

            PdfPTable table = new PdfPTable(2);
            table.setWidthPercentage(100);
            table.setSpacingBefore(12f);
            table.setSpacingAfter(12f);
            table.setWidths(new float[]{ 42f, 58f });

            boolean shade = true;
            helper.addRow(table, "Receipt No.",       "RCP-PASS-" + txn.getTransactionId(),          labelFont, valueFont, shade); shade = !shade;
            helper.addRow(table, "Booking ID",        "#" + txn.getBookingId(),                       labelFont, valueFont, shade); shade = !shade;
            helper.addRow(table, "Driver",            txn.getDriverEmail(),                           labelFont, valueFont, shade); shade = !shade;
            helper.addRow(table, "Amount Paid",       "\u20B9 " + String.format("%.2f", txn.getAmount()), labelFont, valueFont, shade); shade = !shade;
            helper.addRow(table, "Payment Method",    "ParkEase Pass",                                labelFont, valueFont, shade); shade = !shade;
            helper.addRow(table, "Transaction ID",    txn.getPassTransactionRef(),                    labelFont, valueFont, shade); shade = !shade;
            helper.addRow(table, "Paid At",           txn.getCreatedAt().format(FORMATTER),           labelFont, valueFont, shade); shade = !shade;
            helper.addRow(table, "Parkings Remaining", txn.getCountAfter() + " / 150 used \u2192 "
                    + (150 - txn.getCountAfter()) + " remaining",                                     labelFont, valueFont, shade); shade = !shade;
            helper.addRow(table, "Pass Valid Until",  pass.getExpiresAt().format(DATE_ONLY),          labelFont, valueFont, shade);

            document.add(table);

            helper.addDivider(document, LIGHT_GRAY, 0.6f);

            helper.addFooter(document, footerFont);

            document.close();
            log.info("Pass receipt generated: {}", fileName);
            return fileName;

        } catch (Exception e) {
            log.error("Failed to generate pass receipt for txn {}: {}", txn.getTransactionId(), e.getMessage());
            return null;
        }
    }
}
