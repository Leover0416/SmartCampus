package com.simon.campus.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("course_selection_schedule")
public class CourseSelectionSchedule {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String term;
    private String phaseName;
    private String phaseType;
    private String targetGrades;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String notes;
    private LocalDateTime createdAt;
}
