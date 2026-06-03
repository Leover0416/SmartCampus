package com.simon.campus.service.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.simon.campus.service.admin.SystemConfigService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("ToolCaller - Prompt 配置")
class ToolCallerPromptConfigTest {

    @Test
    @DisplayName("工具调用系统提示词来自 system_config")
    void buildMessagesUsesAcademicPromptConfig() {
        SystemConfigService configService = mock(SystemConfigService.class);
        when(configService.get("prompt.academic_default", ToolCaller.DEFAULT_ACADEMIC_SYSTEM_PROMPT))
            .thenReturn("自定义教务工具提示词");

        ToolCaller caller = new ToolCaller(
            mock(AcademicCalendarTool.class),
            mock(CourseSelectionTool.class),
            mock(DepartmentContactTool.class),
            mock(HumanTicketTool.class),
            configService,
            new ObjectMapper()
        );

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> messages = ReflectionTestUtils.invokeMethod(
            caller, "buildMessages", "查一下校历", null);

        assertThat(messages).isNotNull();
        assertThat(messages.get(0).get("content")).isEqualTo("自定义教务工具提示词");
    }
}
