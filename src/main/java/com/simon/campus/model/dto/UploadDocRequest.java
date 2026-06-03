package com.simon.campus.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UploadDocRequest {
    @NotBlank
    private String title;
    private String categoryCode;
    @NotNull
    private Integer accessLevel; // 0=全部可见, 1=教师及以上, 2=管理员
}
