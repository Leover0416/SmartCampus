package com.simon.campus.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginRequest {

    @NotBlank(message = "用户名不能为空")
    private String username; // 支持用户名或邮箱

    @NotBlank(message = "密码不能为空")
    private String password;
}
