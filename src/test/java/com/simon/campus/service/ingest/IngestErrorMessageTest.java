package com.simon.campus.service.ingest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("IngestErrorMessage - 入库失败信息处理")
class IngestErrorMessageTest {

    @Test
    @DisplayName("长异常信息会被截断到 error_msg 字段可接受范围")
    void longExceptionMessageIsTruncated() {
        RuntimeException ex = new RuntimeException("DashScope Embedding 调用失败: " + "x".repeat(1000));

        String message = IngestErrorMessage.from(ex);

        assertThat(message).hasSizeLessThanOrEqualTo(500);
        assertThat(message).startsWith("DashScope Embedding 调用失败");
    }

    @Test
    @DisplayName("空异常信息会降级为异常类型名")
    void blankExceptionMessageFallsBackToExceptionType() {
        RuntimeException ex = new RuntimeException();

        String message = IngestErrorMessage.from(ex);

        assertThat(message).isEqualTo("RuntimeException");
    }

    @Test
    @DisplayName("LinkageError 等非 Exception 错误也能转换为失败信息")
    void linkageErrorCanBeStoredAsFailureMessage() {
        LinkageError error = new NoSuchMethodError("PDDocument.load");

        String message = IngestErrorMessage.from(error);

        assertThat(message).isEqualTo("PDDocument.load");
    }
}
