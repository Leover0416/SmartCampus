package com.simon.campus.service.ingest;

import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFTextBox;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.Rectangle;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DocumentParser - 主流文档解析")
class DocumentParserTest {

    private DocumentParser parser;

    @BeforeEach
    void setUp() {
        parser = new DocumentParser();
    }

    @Test
    @DisplayName("解析 Markdown 文档")
    void parsesMarkdown() throws Exception {
        String markdown = "# 学生手册\n\n## 缓考规定\n学生因病可以申请缓考。";

        var sections = parser.parse(
            new ByteArrayInputStream(markdown.getBytes(StandardCharsets.UTF_8)),
            "text/markdown"
        );

        assertThat(joinContent(sections)).contains("缓考规定", "学生因病可以申请缓考");
    }

    @Test
    @DisplayName("解析未带 Content-Type 的 Markdown 文档")
    void parsesMarkdownWithoutContentType() throws Exception {
        String markdown = "# 学生手册\n\n## 缓考规定\n学生因病可以申请缓考。";

        var sections = parser.parse(
            new ByteArrayInputStream(markdown.getBytes(StandardCharsets.UTF_8)),
            null
        );

        assertThat(joinContent(sections)).contains("缓考规定", "学生因病可以申请缓考");
    }

    @Test
    @DisplayName("解析 CSV 表格")
    void parsesCsv() throws Exception {
        String csv = "课程,教师\n人工智能导论,张老师\n";

        var sections = parser.parse(
            new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)),
            "text/csv"
        );

        assertThat(joinContent(sections)).contains("人工智能导论", "张老师");
    }

    @Test
    @DisplayName("解析 XLSX 表格")
    void parsesXlsx() throws Exception {
        byte[] bytes;
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("选课安排");
            var row = sheet.createRow(0);
            row.createCell(0).setCellValue("选课阶段");
            row.createCell(1).setCellValue("第一轮选课");
            workbook.write(out);
            bytes = out.toByteArray();
        }

        var sections = parser.parse(
            new ByteArrayInputStream(bytes),
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        );

        assertThat(joinContent(sections)).contains("选课阶段", "第一轮选课");
    }

    @Test
    @DisplayName("解析 PPTX 演示文稿")
    void parsesPptx() throws Exception {
        byte[] bytes;
        try (XMLSlideShow ppt = new XMLSlideShow(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            XSLFSlide slide = ppt.createSlide();
            XSLFTextBox textBox = slide.createTextBox();
            textBox.setAnchor(new Rectangle(50, 50, 500, 120));
            textBox.setText("智慧校园答疑系统");
            ppt.write(out);
            bytes = out.toByteArray();
        }

        var sections = parser.parse(
            new ByteArrayInputStream(bytes),
            "application/vnd.openxmlformats-officedocument.presentationml.presentation"
        );

        assertThat(joinContent(sections)).contains("智慧校园答疑系统");
    }

    @Test
    @DisplayName("入库解析 PDF 文本层为空时使用视觉模型解析页面图片")
    void parsesImageOnlyPdfWithVisionFallbackForIngest() throws Exception {
        byte[] bytes;
        try (PDDocument pdf = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            pdf.addPage(new PDPage());
            pdf.save(out);
            bytes = out.toByteArray();
        }

        DocumentParser visionParser = new DocumentParser(new VisionTextExtractor() {
            @Override
            public boolean isAvailable() {
                return true;
            }

            @Override
            public String extractPdfImagesAsMarkdown(byte[] pdfBytes) {
                return "## 第 1 页图片解析\n\n学生手册图片内容：奖学金申请时间为每年 9 月。";
            }
        });

        var sections = visionParser.parseForIngest(
            new ByteArrayInputStream(bytes),
            "application/pdf"
        );

        assertThat(joinContent(sections)).contains("学生手册图片内容", "奖学金申请时间");
    }

    private String joinContent(java.util.List<DocumentParser.ParsedSection> sections) {
        return sections.stream()
            .map(s -> s.heading() + "\n" + s.content())
            .reduce("", (a, b) -> a + "\n" + b);
    }
}
