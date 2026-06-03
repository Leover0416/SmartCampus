package com.simon.campus.service.ingest;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.simon.campus.common.BizException;
import com.simon.campus.mapper.KnowledgeCategoryMapper;
import com.simon.campus.mapper.KnowledgeDocMapper;
import com.simon.campus.model.entity.KnowledgeCategory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("KnowledgeService - 知识分类")
class KnowledgeCategoryServiceTest {

    @Test
    @DisplayName("只返回启用分类并按排序返回")
    void listEnabledCategories() {
        KnowledgeCategoryMapper categoryMapper = mock(KnowledgeCategoryMapper.class);
        KnowledgeCategory category = new KnowledgeCategory();
        category.setCode("student_handbook");
        category.setName("学生手册");

        when(categoryMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(category));

        KnowledgeService service = service(categoryMapper);

        assertThat(service.listCategories()).containsExactly(category);
        verify(categoryMapper).selectList(any(LambdaQueryWrapper.class));
    }

    @Test
    @DisplayName("创建分类时校验编码唯一")
    void createCategoryRejectsDuplicateCode() {
        KnowledgeCategoryMapper categoryMapper = mock(KnowledgeCategoryMapper.class);
        KnowledgeCategory existing = new KnowledgeCategory();
        existing.setCode("student_handbook");
        when(categoryMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existing);

        KnowledgeService service = service(categoryMapper);

        assertThatThrownBy(() -> service.createCategory("学生手册", "student_handbook"))
            .isInstanceOf(BizException.class)
            .hasMessageContaining("分类编码已存在");
    }

    @Test
    @DisplayName("创建分类时写入启用状态和默认排序")
    void createCategoryInsertsEnabledCategory() {
        KnowledgeCategoryMapper categoryMapper = mock(KnowledgeCategoryMapper.class);
        when(categoryMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(categoryMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        KnowledgeService service = service(categoryMapper);

        KnowledgeCategory created = service.createCategory("奖助学金", "scholarship");

        assertThat(created.getName()).isEqualTo("奖助学金");
        assertThat(created.getCode()).isEqualTo("scholarship");
        assertThat(created.getStatus()).isEqualTo(1);
        assertThat(created.getSortOrder()).isEqualTo(100);
        verify(categoryMapper).insert(created);
    }

    @Test
    @DisplayName("删除分类时如果仍有关联文档则拒绝")
    void deleteCategoryRejectsWhenDocsExist() {
        KnowledgeCategoryMapper categoryMapper = mock(KnowledgeCategoryMapper.class);
        KnowledgeDocMapper docMapper = mock(KnowledgeDocMapper.class);
        KnowledgeCategory existing = new KnowledgeCategory();
        existing.setCode("student_handbook");
        existing.setStatus(1);
        when(categoryMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existing);
        when(docMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(2L);

        KnowledgeService service = service(categoryMapper, docMapper);

        assertThatThrownBy(() -> service.deleteCategory("student_handbook"))
            .isInstanceOf(BizException.class)
            .hasMessageContaining("该分类下还有文档");
    }

    @Test
    @DisplayName("删除空分类时置为禁用")
    void deleteCategorySoftDeletesEmptyCategory() {
        KnowledgeCategoryMapper categoryMapper = mock(KnowledgeCategoryMapper.class);
        KnowledgeDocMapper docMapper = mock(KnowledgeDocMapper.class);
        KnowledgeCategory existing = new KnowledgeCategory();
        existing.setCode("scholarship");
        existing.setStatus(1);
        when(categoryMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existing);
        when(docMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(categoryMapper.update(any(), any(LambdaUpdateWrapper.class))).thenReturn(1);

        KnowledgeService service = service(categoryMapper, docMapper);

        service.deleteCategory("scholarship");

        verify(categoryMapper).update(any(), any(LambdaUpdateWrapper.class));
    }

    private KnowledgeService service(KnowledgeCategoryMapper categoryMapper) {
        return service(categoryMapper, null);
    }

    private KnowledgeService service(KnowledgeCategoryMapper categoryMapper, KnowledgeDocMapper docMapper) {
        return new KnowledgeService(
            docMapper,
            categoryMapper,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null
        );
    }
}
