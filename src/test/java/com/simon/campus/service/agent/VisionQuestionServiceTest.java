package com.simon.campus.service.agent;

import com.simon.campus.service.ingest.VisionTextExtractor;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VisionQuestionServiceTest {

    @Test
    void buildsAugmentedQueryFromUploadedImageAndQuestion() throws Exception {
        VisionTextExtractor extractor = new VisionTextExtractor() {
            @Override
            public boolean isAvailable() {
                return true;
            }

            @Override
            public String extractPdfImagesAsMarkdown(byte[] pdfBytes) {
                return "";
            }

            @Override
            public String extractImageAsMarkdown(byte[] imageBytes, String mimeType) {
                assertThat(mimeType).isEqualTo("image/png");
                assertThat(imageBytes).containsExactly(1, 2, 3);
                return "图片中有一张成绩单，课程为高等数学，成绩为 86。";
            }
        };
        VisionQuestionService service = new VisionQuestionService(extractor);

        String query = service.buildImageQuestion("这张图里的成绩是多少？", new byte[]{1, 2, 3}, "image/png");

        assertThat(query)
            .contains("用户上传了一张图片")
            .contains("图片中有一张成绩单")
            .contains("这张图里的成绩是多少？");
    }
}
