package com.lab.edms.pdf;

import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;

import java.util.Set;

@Component
public class GotenbergClient {

    private static final Set<String> SUPPORTED_EXT = Set.of(
        "docx", "doc", "xlsx", "xls", "pptx", "ppt", "odt", "ods", "odp", "txt", "rtf");

    private final RestClient http;

    public GotenbergClient(GotenbergProperties props) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int)(props.timeoutSeconds() * 1000L));
        factory.setReadTimeout((int)(props.timeoutSeconds() * 1000L));
        this.http = RestClient.builder()
            .baseUrl(props.url())
            .requestFactory(factory)
            .build();
    }

    public byte[] convertOfficeToPdf(String fileName, byte[] content) {
        String ext = extOf(fileName);
        if (!SUPPORTED_EXT.contains(ext)) {
            throw new UnsupportedFormatException(ext);
        }
        try {
            MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
            form.add("files", new NamedByteArrayResource(fileName, content));
            return http.post()
                .uri("/forms/libreoffice/convert")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(form)
                .retrieve()
                .body(byte[].class);
        } catch (HttpStatusCodeException e) {
            throw new ConversionFailedException("gotenberg HTTP " + e.getStatusCode(), e);
        } catch (Exception e) {
            throw new ConversionFailedException("gotenberg error: " + e.getMessage(), e);
        }
    }

    private static String extOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot >= 0 ? fileName.substring(dot + 1).toLowerCase() : "";
    }

    private static final class NamedByteArrayResource extends org.springframework.core.io.ByteArrayResource {
        private final String filename;

        NamedByteArrayResource(String filename, byte[] content) {
            super(content);
            this.filename = filename;
        }

        @Override
        public String getFilename() {
            return filename;
        }
    }

    public static class UnsupportedFormatException extends RuntimeException {
        public UnsupportedFormatException(String ext) {
            super("Unsupported format: " + ext + ". Only Office documents are supported.");
        }
    }

    public static class ConversionFailedException extends RuntimeException {
        public ConversionFailedException(String msg, Throwable cause) {
            super(msg, cause);
        }
    }
}
