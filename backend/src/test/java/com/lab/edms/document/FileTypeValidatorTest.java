package com.lab.edms.document;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileTypeValidatorTest {

    private final FileTypeValidator validator = new FileTypeValidator();

    // PK magic bytes (used by DOCX, XLSX, PPTX)
    private static final byte[] PK_MAGIC = {'P', 'K', 0x03, 0x04, 0, 0, 0, 0};
    // PDF magic bytes
    private static final byte[] PDF_MAGIC = {'%', 'P', 'D', 'F', '-', '1', '.', '4'};
    // EXE magic bytes (MZ header)
    private static final byte[] EXE_MAGIC = {'M', 'Z', 0, 0, 0, 0, 0, 0};
    // TXT content
    private static final byte[] TXT_CONTENT = "Hello World".getBytes();

    @Test
    void validDocx_returnsDocxType() {
        FileTypeValidator.AllowedFileType result =
                validator.validate("document.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", PK_MAGIC);
        assertThat(result).isEqualTo(FileTypeValidator.AllowedFileType.DOCX);
    }

    @Test
    void exeRenamedToDocx_throws422() {
        assertThatThrownBy(() ->
                validator.validate("malware.docx", "application/octet-stream", EXE_MAGIC))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
    }

    @Test
    void docxWithWrongContentType_returnsDocxType() {
        // Content-Type is not validated — suffix + magic only
        FileTypeValidator.AllowedFileType result =
                validator.validate("document.docx", "application/octet-stream", PK_MAGIC);
        assertThat(result).isEqualTo(FileTypeValidator.AllowedFileType.DOCX);
    }

    @Test
    void txtFile_throws415() {
        assertThatThrownBy(() ->
                validator.validate("readme.txt", "text/plain", TXT_CONTENT))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE));
    }

    @Test
    void validPdf_returnsPdfType() {
        FileTypeValidator.AllowedFileType result =
                validator.validate("report.pdf", "application/pdf", PDF_MAGIC);
        assertThat(result).isEqualTo(FileTypeValidator.AllowedFileType.PDF);
    }

    @Test
    void validXlsx_returnsXlsxType() {
        FileTypeValidator.AllowedFileType result =
                validator.validate("data.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", PK_MAGIC);
        assertThat(result).isEqualTo(FileTypeValidator.AllowedFileType.XLSX);
    }
}
