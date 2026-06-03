package com.simon.campus.service.user;

import com.simon.campus.model.dto.LoginRequest;
import com.simon.campus.model.dto.RegisterRequest;
import com.simon.campus.model.vo.LoginResponse;

public interface UserService {

    LoginResponse register(RegisterRequest request);

    LoginResponse login(LoginRequest request);

    void changePassword(Long userId, String oldPassword, String newPassword);

    void resetPassword(String username, String email);
}
