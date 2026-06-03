package com.simon.campus.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("department_contacts")
public class DepartmentContact {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String departmentName;
    private String departmentCode;
    private String contactPerson;
    private String phone;
    private String email;
    private String officeLocation;
    private String officeHours;
    private LocalDateTime createdAt;
}
