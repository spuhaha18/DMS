package com.lab.edms.document;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class FileTypeValidator {

    public enum AllowedFileType {
        DOCX(".docx",
             "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
             new byte[]{'P', 'K', 0x03, 0x04}),
        XLSX(".xlsx",
             "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
             new byte[]{'P', 'K', 0x03, 0x04}),
        PPTX(".pptx",
             "application/vnd.openxmlformats-officedocument.presentationml.presentation",
             new byte[]{'P', 'K', 0x03, 0x04}),
        PDF(".pdf",
            "application/pdf",
            new byte[]{'%', 'P', 'D', 'F'});

        final String suffix;
        final String mimeType;
        final byte[] magic;

        AllowedFileType(String suffix, String mimeType, byte[] magic) {
            this.suffix = suffix;
            this.mimeType = mimeType;
            this.magic = magic;
        }

        public String getSuffix() { return suffix; }
        public String getMimeType() { return mimeType; }
    }

    /**
     * Validates file by extension + magic bytes.
     * Throws ResponseStatusException:
     *   415 — file extension not in allowlist
     *   422 — magic bytes mismatch (e.g. renamed exe)
     *
     * @param filename   original filename (used for suffix check)
     * @param contentType Content-Type header (not strictly validated, suffix+magic are the gates)
     * @param firstBytes  first N bytes of the file (at least 4 bytes recommended)
     * @return matched AllowedFileType
     */
    public AllowedFileType validate(String filename, String contentType, byte[] firstBytes) {
        String lower = filename.toLowerCase();
        AllowedFileType matched = null;
        for (AllowedFileType t : AllowedFileType.values()) {
            if (lower.endsWith(t.suffix)) {
                matched = t;
                break;
            }
        }
        if (matched == null) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    "File type not allowed: " + filename);
        }
        // Magic bytes check
        for (int i = 0; i < matched.magic.length; i++) {
            if (i >= firstBytes.length || firstBytes[i] != matched.magic[i]) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "File content does not match its extension: " + filename);
            }
        }
        return matched;
    }
}
