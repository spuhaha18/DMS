package com.lab.edms.pdf;

import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSString;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.TimeZone;

/**
 * Locks every non-deterministic surface in PDFBox:
 *  (a) /ID array — both values fixed (COSWriter would otherwise auto-update slot[1])
 *  (b) Producer / CreationDate / ModDate fixed
 *  (c) XMP metadata stripped (PDFBox auto-generates timestamped XMP)
 *  Caller is still responsible for: embedded fonts (no system font cache),
 *  pinned PDFBox version + JDK + container image.
 */
public final class PdfDeterministicConfig {

    private static final String FIXED_PRODUCER = "EDMS-Stamp/1.0";
    private static final Calendar FIXED_DATE;
    static {
        FIXED_DATE = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
        FIXED_DATE.set(2026, Calendar.JANUARY, 1, 0, 0, 0);
        FIXED_DATE.set(Calendar.MILLISECOND, 0);
    }

    private PdfDeterministicConfig() {}

    public static void lock(PDDocument doc, String idSeed) throws Exception {
        // (a) /ID array — both slots from idSeed sha256
        byte[] hash = MessageDigest.getInstance("SHA-256")
            .digest(idSeed.getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder();
        for (byte b : hash) hex.append(String.format("%02x", b));
        COSString id = new COSString(hex.toString());
        COSArray idArray = new COSArray();
        idArray.add(id);
        idArray.add(id);
        doc.getDocument().getTrailer().setItem(org.apache.pdfbox.cos.COSName.ID, idArray);

        // (b) Fixed Producer + dates
        PDDocumentInformation info = doc.getDocumentInformation();
        if (info == null) { info = new PDDocumentInformation(); doc.setDocumentInformation(info); }
        info.setProducer(FIXED_PRODUCER);
        info.setCreationDate(FIXED_DATE);
        info.setModificationDate(FIXED_DATE);

        // (c) XMP metadata 제거 (PDFBox auto-generates timestamped XMP)
        doc.getDocumentCatalog().setMetadata(null);
    }
}
