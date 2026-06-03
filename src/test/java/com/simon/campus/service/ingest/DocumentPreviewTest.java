package com.simon.campus.service.ingest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DocumentPreview - 文档预览响应")
class DocumentPreviewTest {

    @Test
    @DisplayName("PDF 使用 inline disposition 供浏览器预览")
    void pdfUsesInlineDisposition() {
        DocumentPreview preview = DocumentPreview.of("docs/a.pdf", "学生手册.pdf", "application/pdf", null);

        assertThat(preview.mediaType()).isEqualTo(MediaType.APPLICATION_PDF);
        assertThat(preview.contentDisposition()).contains("inline");
        assertThat(preview.contentDisposition()).contains("filename*=UTF-8''");
    }

    @Test
    @DisplayName("未知类型降级为 octet-stream")
    void unknownTypeFallsBackToOctetStream() {
        DocumentPreview preview = DocumentPreview.of("docs/a.bin", "a.bin", null, null);

        assertThat(preview.mediaType()).isEqualTo(MediaType.APPLICATION_OCTET_STREAM);
    }
}
