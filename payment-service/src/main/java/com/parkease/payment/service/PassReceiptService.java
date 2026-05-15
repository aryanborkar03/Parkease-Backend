package com.parkease.payment.service;

import com.itextpdf.text.*;
import com.itextpdf.text.pdf.*;
import com.itextpdf.text.pdf.draw.LineSeparator;
import com.parkease.payment.entity.PassBalance;
import com.parkease.payment.entity.PassTransaction;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.format.DateTimeFormatter;

/**
 * Generates PDF receipts for pass-based payments.
 * Mirrors the structure of ReceiptService but carries pass-specific fields
 * (Parkings Remaining, Pass Valid Until, Payment Method = ParkEase Pass).
 */
@Service
@Slf4j
public class PassReceiptService {

    @Value("${app.receipt.storage-path}")
    private String storagePath;

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a");
    private static final DateTimeFormatter DATE_ONLY =
            DateTimeFormatter.ofPattern("dd MMM yyyy");

    private static final BaseColor BLACK      = new BaseColor(10,  10,  10);
    private static final BaseColor DARK_GRAY  = new BaseColor(50,  50,  50);
    private static final BaseColor MID_GRAY   = new BaseColor(110, 110, 110);
    private static final BaseColor LIGHT_GRAY = new BaseColor(235, 235, 235);
    private static final BaseColor WHITE      = BaseColor.WHITE;
    private static final BaseColor GOLD       = new BaseColor(180, 130, 10);

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
            Paragraph title = new Paragraph("ParkEase", titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            title.setSpacingBefore(6f);
            title.setSpacingAfter(3f);
            document.add(title);

            Paragraph tagline = new Paragraph("Smart Parking Management Platform", subFont);
            tagline.setAlignment(Element.ALIGN_CENTER);
            tagline.setSpacingAfter(12f);
            document.add(tagline);

            addDivider(document, BLACK, 1.0f);

            Paragraph receiptHead = new Paragraph("PAYMENT RECEIPT — PREMIUM PASS", receiptFont);
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

            addDivider(document, BLACK, 1.0f);

            PdfPTable table = new PdfPTable(2);
            table.setWidthPercentage(100);
            table.setSpacingBefore(12f);
            table.setSpacingAfter(12f);
            table.setWidths(new float[]{ 42f, 58f });

            boolean shade = true;
            addRow(table, "Receipt No.",       "RCP-PASS-" + txn.getTransactionId(),          labelFont, valueFont, shade); shade = !shade;
            addRow(table, "Booking ID",        "#" + txn.getBookingId(),                       labelFont, valueFont, shade); shade = !shade;
            addRow(table, "Driver",            txn.getDriverEmail(),                           labelFont, valueFont, shade); shade = !shade;
            addRow(table, "Amount Paid",       "\u20B9 " + String.format("%.2f", txn.getAmount()), labelFont, valueFont, shade); shade = !shade;
            addRow(table, "Payment Method",    "ParkEase Pass",                                labelFont, valueFont, shade); shade = !shade;
            addRow(table, "Transaction ID",    txn.getPassTransactionRef(),                    labelFont, valueFont, shade); shade = !shade;
            addRow(table, "Paid At",           txn.getCreatedAt().format(FORMATTER),           labelFont, valueFont, shade); shade = !shade;
            addRow(table, "Parkings Remaining", txn.getCountAfter() + " / 150 used \u2192 "
                    + (150 - txn.getCountAfter()) + " remaining",                              labelFont, valueFont, shade); shade = !shade;
            addRow(table, "Pass Valid Until",  pass.getExpiresAt().format(DATE_ONLY),          labelFont, valueFont, shade);

            document.add(table);

            addDivider(document, LIGHT_GRAY, 0.6f);

            Paragraph footer = new Paragraph(
                "This is a computer-generated receipt and does not require a signature.\n" +
                "For support, contact support@parkease.com  \u2022  " +
                "Generated: " + java.time.LocalDateTime.now().format(FORMATTER),
                footerFont);
            footer.setAlignment(Element.ALIGN_CENTER);
            footer.setSpacingBefore(8f);
            document.add(footer);

            document.close();
            log.info("Pass receipt generated: {}", fileName);
            return fileName;

        } catch (Exception e) {
            log.error("Failed to generate pass receipt for txn {}: {}", txn.getTransactionId(), e.getMessage());
            return null;
        }
    }

    private void addDivider(Document doc, BaseColor color, float width) throws DocumentException {
        LineSeparator line = new LineSeparator();
        line.setLineColor(color);
        line.setLineWidth(width);
        doc.add(new Chunk(line));
    }

    private void addRow(PdfPTable table, String label, String value,
            Font labelFont, Font valueFont, boolean shaded) {
        BaseColor bg = shaded ? LIGHT_GRAY : WHITE;

        PdfPCell lc = new PdfPCell(new Phrase(label, labelFont));
        lc.setBorder(Rectangle.NO_BORDER);
        lc.setPaddingTop(5f); lc.setPaddingBottom(5f);
        lc.setPaddingLeft(8f); lc.setPaddingRight(4f);
        lc.setBackgroundColor(bg);

        PdfPCell vc = new PdfPCell(new Phrase(value != null ? value : "-", valueFont));
        vc.setBorder(Rectangle.NO_BORDER);
        vc.setPaddingTop(5f); vc.setPaddingBottom(5f);
        vc.setPaddingLeft(8f); vc.setPaddingRight(8f);
        vc.setBackgroundColor(bg);

        table.addCell(lc);
        table.addCell(vc);
    }
}
