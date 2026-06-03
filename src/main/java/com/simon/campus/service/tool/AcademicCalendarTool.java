package com.simon.campus.service.tool;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.simon.campus.mapper.AcademicCalendarMapper;
import com.simon.campus.model.entity.AcademicCalendar;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AcademicCalendarTool {

    private final AcademicCalendarMapper mapper;

    public ToolResult query(String term) {
        try {
            String resolvedTerm = term == null || term.isBlank() ? currentTerm() : term;
            LambdaQueryWrapper<AcademicCalendar> qw = new LambdaQueryWrapper<AcademicCalendar>()
                .eq(AcademicCalendar::getTerm, resolvedTerm)
                .orderByAsc(AcademicCalendar::getStartDate);
            List<AcademicCalendar> events = mapper.selectList(qw);

            List<Map<String, Object>> rows = events.stream().map(e -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("event", e.getEventName());
                m.put("type", e.getEventType());
                m.put("start", e.getStartDate().toString());
                if (e.getEndDate() != null) m.put("end", e.getEndDate().toString());
                if (e.getDescription() != null) m.put("note", e.getDescription());
                return m;
            }).collect(Collectors.toList());

            String summary = events.isEmpty()
                ? "未找到学期 " + resolvedTerm + " 的校历信息"
                : resolvedTerm + " 学期校历共 " + events.size() + " 项安排";

            return ToolResult.builder()
                .success(true)
                .toolName("query_academic_calendar")
                .params(Map.of("term", resolvedTerm))
                .data(rows)
                .summary(summary)
                .dataSource("教务处校历系统")
                .updatedAt("2026-02-01")
                .build();
        } catch (Exception e) {
            log.error("AcademicCalendarTool error: {}", e.getMessage());
            return ToolResult.builder().success(false).toolName("query_academic_calendar")
                .error("查询校历失败：" + e.getMessage()).build();
        }
    }

    private String currentTerm() {
        return "2025-2026-2";
    }
}
