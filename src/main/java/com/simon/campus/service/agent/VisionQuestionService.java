package com.simon.campus.service.agent;

import com.simon.campus.common.BizException;
import com.simon.campus.service.ingest.VisionTextExtractor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class VisionQuestionService {

    private final VisionTextExtractor visionTextExtractor;

    public String buildImageQuestion(String question, byte[] imageBytes, String mimeType) throws Exception {
        if (!visionTextExtractor.isAvailable()) {
            throw new BizException("图片理解服务未配置，请检查 DashScope API Key");
        }
        String imageText = visionTextExtractor.extractImageAsMarkdown(imageBytes, mimeType);
        if (imageText == null || imageText.isBlank()) {
            throw new BizException("未能识别图片内容，请换一张更清晰的图片");
        }
        String normalizedQuestion = question == null || question.isBlank()
            ? "请分析这张图片，并结合校园事务场景回答。"
            : question.strip();
        return """
            用户上传了一张图片，请先基于图片解析内容理解图片，再回答用户问题。

            【图片解析内容】
            %s

            【用户问题】
            %s
            """.formatted(imageText.strip(), normalizedQuestion);
    }
}
