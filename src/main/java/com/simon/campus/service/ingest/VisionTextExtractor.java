package com.simon.campus.service.ingest;

public interface VisionTextExtractor {

    boolean isAvailable();

    String extractPdfImagesAsMarkdown(byte[] pdfBytes) throws Exception;

    default String extractImageAsMarkdown(byte[] imageBytes, String mimeType) throws Exception {
        return "";
    }

    static VisionTextExtractor noop() {
        return new VisionTextExtractor() {
            @Override
            public boolean isAvailable() {
                return false;
            }

            @Override
            public String extractPdfImagesAsMarkdown(byte[] pdfBytes) {
                return "";
            }

            @Override
            public String extractImageAsMarkdown(byte[] imageBytes, String mimeType) {
                return "";
            }
        };
    }
}
