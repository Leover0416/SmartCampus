package com.simon.campus.controller;

import com.simon.campus.common.R;
import com.simon.campus.model.dto.ChangePasswordRequest;
import com.simon.campus.model.dto.LoginRequest;
import com.simon.campus.model.dto.RegisterRequest;
import com.simon.campus.model.dto.ResetPasswordRequest;
import com.simon.campus.model.vo.LoginResponse;
import com.simon.campus.service.user.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;

    @PostMapping("/register")
    public R<LoginResponse> register(@Valid @RequestBody RegisterRequest request) {
        return R.ok(userService.register(request));
    }

    @PostMapping("/login")
    public R<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return R.ok(userService.login(request));
    }

    @PostMapping("/change-password")
    public R<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request, Authentication auth) {
        userService.changePassword((Long) auth.getCredentials(), request.getOldPassword(), request.getNewPassword());
        return R.ok(null);
    }

    @PostMapping("/reset-password")
    public R<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        userService.resetPassword(request.getUsername(), request.getEmail());
        return R.ok(null);
    }
}
