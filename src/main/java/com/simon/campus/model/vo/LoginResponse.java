package com.simon.campus.model.vo;

import lombok.Data;

@Data
public class LoginResponse {

    private String token;

    private Long userId;

    private String username;

    private String role;

    private String nickname;

    private String avatarUrl;
}
