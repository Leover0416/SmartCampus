package com.simon.campus.service.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.simon.campus.service.admin.SystemConfigService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("ToolCaller - 工具开关配置")
class ToolCallerConfigKeysTest {

    @Test
    @DisplayName("工具列表使用和前端一致的 query_ 开关Key")
    void enabledToolsUseFrontendConfigKeys() {
        SystemConfigService configService = mock(SystemConfigService.class);
        when(configService.getBool(eq("tool.query_academic_calendar.enabled"), anyBoolean())).thenReturn(true);
        when(configService.getBool(eq("tool.query_course_selection.enabled"), anyBoolean())).thenReturn(true);
        when(configService.getBool(eq("tool.query_department_contact.enabled"), anyBoolean())).thenReturn(true);
        when(configService.getBool(eq("tool.create_human_ticket.enabled"), anyBoolean())).thenReturn(true);

        ToolCaller caller = new ToolCaller(
            mock(AcademicCalendarTool.class),
            mock(CourseSelectionTool.class),
            mock(DepartmentContactTool.class),
            mock(HumanTicketTool.class),
            configService,
            new ObjectMapper()
        );

        ReflectionTestUtils.invokeMethod(caller, "getEnabledTools");

        verify(configService).getBool("tool.query_academic_calendar.enabled", true);
        verify(configService, never()).getBool(eq("tool.academic_calendar.enabled"), anyBoolean());
    }
}
