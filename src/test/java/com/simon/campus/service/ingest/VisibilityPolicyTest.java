package com.simon.campus.service.ingest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("VisibilityPolicy - 学生/教师可见范围")
class VisibilityPolicyTest {

    @Test
    @DisplayName("学生只能看到全部可见和仅学生可见")
    void studentCanOnlySeeAllAndStudentDocs() {
        assertThat(VisibilityPolicy.visibleLevelsForViewer(VisibilityPolicy.STUDENT))
            .containsExactly(VisibilityPolicy.ALL, VisibilityPolicy.STUDENT);
        assertThat(VisibilityPolicy.canView(VisibilityPolicy.TEACHER, VisibilityPolicy.STUDENT)).isFalse();
    }

    @Test
    @DisplayName("教师只能看到全部可见和教师可见内容")
    void teacherCanOnlySeeAllAndTeacherDocs() {
        assertThat(VisibilityPolicy.visibleLevelsForViewer(VisibilityPolicy.TEACHER))
            .containsExactly(
                VisibilityPolicy.ALL,
                VisibilityPolicy.TEACHER,
                VisibilityPolicy.LEGACY_ADMIN_AS_TEACHER
            );
        assertThat(VisibilityPolicy.canView(VisibilityPolicy.STUDENT, VisibilityPolicy.TEACHER)).isFalse();
    }

    @Test
    @DisplayName("上传文档只允许全部、学生、教师三种范围")
    void uploadVisibilityExcludesLegacyAdminLevel() {
        assertThat(VisibilityPolicy.isValidDocumentLevel(VisibilityPolicy.ALL)).isTrue();
        assertThat(VisibilityPolicy.isValidDocumentLevel(VisibilityPolicy.STUDENT)).isTrue();
        assertThat(VisibilityPolicy.isValidDocumentLevel(VisibilityPolicy.TEACHER)).isTrue();
        assertThat(VisibilityPolicy.isValidDocumentLevel(VisibilityPolicy.LEGACY_ADMIN_AS_TEACHER)).isFalse();
    }
}
