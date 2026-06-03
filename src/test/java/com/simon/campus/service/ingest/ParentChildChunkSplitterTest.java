package com.simon.campus.service.ingest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * ParentChildChunkSplitter 单元测试。
 * <p>
 * 被测切分策略（见 {@link ParentChildChunkSplitter}）：
 * Parent 400~800 字、Child 80~160 字、Child 重叠 25 字；
 * 入库后 Parent 供生成上下文，Child 供 Milvus/BM25 检索。
 */
@DisplayName("ParentChildChunkSplitter — 分块逻辑单元测试")
class ParentChildChunkSplitterTest {

    private ParentChildChunkSplitter splitter;

    @BeforeEach
    void setUp() {
        splitter = new ParentChildChunkSplitter();
    }

    /**
     * 边界：正文短于 Parent 最小长度（400 字）时，仍应产出至少 1 个 Parent 及其下属 Child，
     * 避免短章节被丢弃导致无法检索。
     */
    @Test
    @DisplayName("短文本：产生至少一个 Parent 和一个 Child")
    void shortText_producesAtLeastOneParentAndChild() {
        String content = "本校学生手册规定，学生在校期间须遵守学校各项规章制度。" +
            "违反规定者将受到相应处分，严重者可予以开除学籍。" +
            "学生应按时参加课程学习，无故缺席超过三分之一者取消考试资格。";

        var section = new DocumentParser.ParsedSection("第一章 学生纪律", content, 1, 1);
        var result = splitter.split("doc-001", "学生手册", 0, List.of(section));

        assertThat(result.parents()).isNotEmpty();
        assertThat(result.children()).isNotEmpty();
    }

    /**
     * 结构完整性：每个 Child 的 parentId 必须指向本次 split 产出的某个 Parent，
     * 保证后续 RRF/Rerank 命中 Child 后能正确回捞 Parent 全文。
     */
    @Test
    @DisplayName("每个 Child 必须关联到一个 Parent")
    void everyChildLinksToAParent() {
        // 600 个「一」≈ 600 字，会切出多个 Parent，便于验证多 Parent 场景下的关联
        String content = "一".repeat(600);
        var section = new DocumentParser.ParsedSection("标题", content, 1, 1);
        var result = splitter.split("doc-002", "测试文档", 0, List.of(section));

        var parentIds = result.parents().stream()
            .map(p -> p.getParentId())
            .toList();

        for (var child : result.children()) {
            assertThat(parentIds).contains(child.getParentId());
        }
    }

    /**
     * 切分粒度：Child 理论上限 160 字 + 25 字 overlap，合并尾段等逻辑可能略超；
     * 断言上限 300 字作为宽松护栏，防止切分器回归产生超大 Child 影响 embedding 质量。
     */
    @Test
    @DisplayName("Child 内容长度不超过最大阈值")
    void childContentWithinSizeLimit() {
        String longContent = "校园生活规范及管理细则。".repeat(100);
        var section = new DocumentParser.ParsedSection("管理细则", longContent, 1, 1);
        var result = splitter.split("doc-003", "管理手册", 0, List.of(section));

        for (var child : result.children()) {
            assertThat(child.getContent().length())
                .as("Child '%s' 长度超限", child.getChildId())
                .isLessThanOrEqualTo(300); // 允许 overlap 小幅超出理论上限 160
        }
    }

    /**
     * 多章节：每个 ParsedSection 独立走 Parent→Child 流水线；
     * 3 个 section 应至少产生 3 个 Parent，且 Child 数应多于 Parent（每 Parent 通常多个 Child）。
     */
    @Test
    @DisplayName("多个 Section 都被处理")
    void multipleSectionsAllProcessed() {
        var sections = List.of(
            new DocumentParser.ParsedSection("第一节 入学须知", "入学须知内容。".repeat(40), 1, 1),
            new DocumentParser.ParsedSection("第二节 学籍管理", "学籍管理相关规定。".repeat(40), 5, 1),
            new DocumentParser.ParsedSection("第三节 考核制度", "考核与评价制度说明。".repeat(40), 10, 1)
        );

        var result = splitter.split("doc-004", "学生手册", 0, sections);

        assertThat(result.parents()).hasSizeGreaterThanOrEqualTo(3);
        assertThat(result.children()).hasSizeGreaterThan(result.parents().size());
    }

    /**
     * 元数据透传：split 入参的 docId、docTitle、accessLevel 应写入 Parent，
     * 供权限过滤（按 accessLevel）与引用展示（docTitle）使用。
     */
    @Test
    @DisplayName("docId 和 docTitle 正确传递到 Parent")
    void docMetadataPropagatedToParent() {
        var section = new DocumentParser.ParsedSection("测试章节", "测试内容。".repeat(50), 1, 1);
        var result = splitter.split("DOC-XYZ-123", "测试文档标题", 1, List.of(section));

        assertThat(result.parents()).allSatisfy(p -> {
            assertThat(p.getDocId()).isEqualTo("DOC-XYZ-123");
            assertThat(p.getDocTitle()).isEqualTo("测试文档标题");
            assertThat(p.getAccessLevel()).isEqualTo(1);
        });
    }

    /**
     * 空输入：无 section 时不应 NPE，返回空列表（如解析失败或空文档的降级行为）。
     */
    @Test
    @DisplayName("空 Section 列表不抛异常且返回空结果")
    void emptySection_returnsEmptyResult() {
        var result = splitter.split("doc-005", "空文档", 0, List.of());

        assertThat(result.parents()).isEmpty();
        assertThat(result.children()).isEmpty();
    }
}
