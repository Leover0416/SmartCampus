package com.simon.campus.service.ingest;

import com.simon.campus.mapper.KnowledgeDocMapper;
import com.simon.campus.model.entity.KnowledgeDoc;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("KnowledgeService - 文本预览")
class KnowledgeServicePreviewTextTest {

    @Test
    @DisplayName("从原始文件解析预览文本")
    void parsesPreviewTextFromOriginalFile() throws Exception {
        KnowledgeDocMapper docMapper = mock(KnowledgeDocMapper.class);
        MinioService minioService = mock(MinioService.class);
        DocumentParser documentParser = mock(DocumentParser.class);

        KnowledgeDoc doc = new KnowledgeDoc();
        doc.setDocId("doc-001");
        doc.setFileName("选课安排.xlsx");
        doc.setFileType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        doc.setMinioKey("docs/doc-001.xlsx");
        doc.setAccessLevel(1);

        when(docMapper.selectById("doc-001")).thenReturn(doc);
        when(minioService.download("docs/doc-001.xlsx")).thenReturn(
            new ByteArrayInputStream("binary".getBytes(StandardCharsets.UTF_8))
        );
        when(documentParser.parse(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(doc.getFileType())))
            .thenReturn(List.of(new DocumentParser.ParsedSection("全文", "第一轮选课 2026-05-10", 1, 0)));

        KnowledgeService service = new KnowledgeService(
            docMapper,
            null,
            null,
            null,
            minioService,
            null,
            null,
            null,
            null,
            documentParser
        );

        String text = service.previewTextDoc("doc-001", 1);

        assertThat(text).contains("全文", "第一轮选课 2026-05-10");
    }
}
