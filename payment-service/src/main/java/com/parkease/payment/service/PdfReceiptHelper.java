package com.parkease.payment.service;

import com.itextpdf.text.*;
import com.itextpdf.text.pdf.*;
import com.itextpdf.text.pdf.draw.LineSeparator;
import org.springframework.stereotype.Component;

/**
 * Shared PDF-rendering helpers used by both {@link ReceiptService} and
 * {@link PassReceiptService}.
 *
 * <p>Extracted to eliminate duplication in:
 * <ul>
 *   <li>The colour palette constants (BLACK / DARK_GRAY / MID_GRAY / LIGHT_GRAY / WHITE)</li>
 *   <li>{@code addDivider()} — identical in both services</li>
 *   <li>{@code addRow()}    — identical in both services</li>
 *   <li>{@code addBrandHeader()} — identical brand title + tagline block</li>
 * </ul>
 *
 * <p>Public method signatures (and therefore all existing callers) are unchanged.
 */
@Component
public class PdfReceiptHelper {

    // ── Monochrome palette shared by both receipt types ─────────────────────
    public static final BaseColor BLACK      = new BaseColor(10,  10,  10);
    public static final BaseColor DARK_GRAY  = new BaseColor(50,  50,  50);
    public static final BaseColor MID_GRAY   = new BaseColor(110, 110, 110);
    public static final BaseColor LIGHT_GRAY = new BaseColor(235, 235, 235);
    public static final BaseColor WHITE      = BaseColor.WHITE;
    public static final BaseColor GOLD       = new BaseColor(180, 130, 10);

    /**
     * Adds the "ParkEase / Smart Parking Management Platform" brand block,
     * followed by a solid black divider.
     */
    public void addBrandHeader(Document doc, Font titleFont, Font subFont) throws DocumentException {
        Paragraph title = new Paragraph("ParkEase", titleFont);
        title.setAlignment(Element.ALIGN_CENTER);
        title.setSpacingBefore(6f);
        title.setSpacingAfter(3f);
        doc.add(title);

        Paragraph tagline = new Paragraph("Smart Parking Management Platform", subFont);
        tagline.setAlignment(Element.ALIGN_CENTER);
        tagline.setSpacingAfter(12f);
        doc.add(tagline);

        addDivider(doc, BLACK, 1.0f);
    }

    /**
     * Adds a horizontal divider line with the given colour and width.
     */
    public void addDivider(Document doc, BaseColor color, float width) throws DocumentException {
        LineSeparator line = new LineSeparator();
        line.setLineColor(color);
        line.setLineWidth(width);
        doc.add(new Chunk(line));
    }

    /**
     * Adds a two-cell row to the given table with optional alternating shading.
     */
    public void addRow(PdfPTable table, String label, String value,
                       Font labelFont, Font valueFont, boolean shaded) {
        BaseColor bg = shaded ? LIGHT_GRAY : WHITE;

        PdfPCell lc = new PdfPCell(new Phrase(label, labelFont));
        lc.setBorder(Rectangle.NO_BORDER);
        lc.setPaddingTop(5f);
        lc.setPaddingBottom(5f);
        lc.setPaddingLeft(8f);
        lc.setPaddingRight(4f);
        lc.setBackgroundColor(bg);

        PdfPCell vc = new PdfPCell(new Phrase(value != null ? value : "-", valueFont));
        vc.setBorder(Rectangle.NO_BORDER);
        vc.setPaddingTop(5f);
        vc.setPaddingBottom(5f);
        vc.setPaddingLeft(8f);
        vc.setPaddingRight(8f);
        vc.setBackgroundColor(bg);

        table.addCell(lc);
        table.addCell(vc);
    }

    /**
     * Adds the standard footer paragraph (computer-generated notice + support email + timestamp).
     */
    public void addFooter(Document doc, Font footerFont) throws DocumentException {
        Paragraph footer = new Paragraph(
                "This is a computer-generated receipt and does not require a signature.\n" +
                "For support, contact support@parkease.com  \u2022  " +
                "Generated: " + java.time.LocalDateTime.now()
                        .format(java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a")),
                footerFont);
        footer.setAlignment(Element.ALIGN_CENTER);
        footer.setSpacingBefore(8f);
        doc.add(footer);
    }
}
