package com.simon.campus.service.tool;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.simon.campus.mapper.DepartmentContactMapper;
import com.simon.campus.model.entity.DepartmentContact;
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
public class DepartmentContactTool {

    private final DepartmentContactMapper mapper;

    public ToolResult query(String department) {
        try {
            List<DepartmentContact> contacts;
            if (department == null || department.isBlank()) {
                contacts = mapper.selectList(null);
            } else {
                LambdaQueryWrapper<DepartmentContact> qw = new LambdaQueryWrapper<DepartmentContact>()
                    .like(DepartmentContact::getDepartmentName, department)
                    .or().like(DepartmentContact::getDepartmentCode, department);
                contacts = mapper.selectList(qw);
            }

            List<Map<String, Object>> rows = contacts.stream().map(c -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("department", c.getDepartmentName());
                if (c.getContactPerson() != null) m.put("contact", c.getContactPerson());
                if (c.getPhone() != null)          m.put("phone", c.getPhone());
                if (c.getEmail() != null)           m.put("email", c.getEmail());
                if (c.getOfficeLocation() != null)  m.put("office", c.getOfficeLocation());
                if (c.getOfficeHours() != null)     m.put("hours", c.getOfficeHours());
                return m;
            }).collect(Collectors.toList());

            String summary = contacts.isEmpty()
                ? "未找到部门\"" + department + "\"的联系方式"
                : "找到 " + contacts.size() + " 个部门联系方式";



            return ToolResult.builder()
                .success(true)
                .toolName("query_department_contact")
                .params(Map.of("department", department != null ? department : "all"))
                .data(rows)
                .summary(summary)
                .dataSource("学校通讯录")
                .updatedAt("2026-03-01")
                .build();
        } catch (Exception e) {
            log.error("DepartmentContactTool error: {}", e.getMessage());
            return ToolResult.builder().success(false).toolName("query_department_contact")
                .error("查询部门联系方式失败：" + e.getMessage()).build();
        }
    }
}
