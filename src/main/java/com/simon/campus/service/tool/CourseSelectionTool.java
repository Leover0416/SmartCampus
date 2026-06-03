package com.simon.campus.service.tool;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.simon.campus.mapper.CourseSelectionScheduleMapper;
import com.simon.campus.model.entity.CourseSelectionSchedule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CourseSelectionTool {

    private final CourseSelectionScheduleMapper mapper;
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public ToolResult query(String term) {
        try {
            String resolvedTerm = term == null || term.isBlank() ? "2025-2026-2" : term;
            LambdaQueryWrapper<CourseSelectionSchedule> qw = new LambdaQueryWrapper<CourseSelectionSchedule>()
                .eq(CourseSelectionSchedule::getTerm, resolvedTerm)
                .orderByAsc(CourseSelectionSchedule::getStartTime);
            List<CourseSelectionSchedule> phases = mapper.selectList(qw);

            List<Map<String, Object>> rows = phases.stream().map(p -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("phase", p.getPhaseName());
                m.put("type", p.getPhaseType());
                m.put("targets", p.getTargetGrades());
                m.put("start", p.getStartTime().format(FMT));
                m.put("end", p.getEndTime().format(FMT));
                if (p.getNotes() != null) m.put("notes", p.getNotes());
                return m;
            }).collect(Collectors.toList());

            String summary = phases.isEmpty()
                ? "未找到学期 " + resolvedTerm + " 的选课安排"
                : resolvedTerm + " 学期选课共 " + phases.size() + " 个阶段";

            return ToolResult.builder()
                .success(true)
                .toolName("query_course_selection")
                .params(Map.of("term", resolvedTerm))
                .data(rows)
                .summary(summary)
                .dataSource("教务处选课系统")
                .updatedAt("2026-01-10")
                .build();
        } catch (Exception e) {
            log.error("CourseSelectionTool error: {}", e.getMessage());
            return ToolResult.builder().success(false).toolName("query_course_selection")
                .error("查询选课安排失败：" + e.getMessage()).build();
        }
    }
}
