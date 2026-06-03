package com.simon.campus.controller;

import com.simon.campus.common.R;
import com.simon.campus.service.admin.SystemConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/settings")
@RequiredArgsConstructor
public class SettingsController {

    private final SystemConfigService configService;

    @GetMapping("/configs")
    public R<Map<String, Object>> listConfigs() {
        return R.ok(configService.listGrouped());
    }

    @PutMapping("/configs")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public R<Void> updateConfigs(@RequestBody Map<String, String> updates) {
        Long userId = currentUserId();
        configService.batchUpdate(updates, userId);
        return R.ok(null);
    }

    @PostMapping("/configs/reset")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public R<Void> resetConfigs() {
        Long userId = currentUserId();
        configService.resetDefaults(userId);
        return R.ok(null);
    }

    @GetMapping("/configs/export")
    @PreAuthorize("hasRole('ADMIN')")
    public R<Map<String, String>> exportConfigs() {
        return R.ok(configService.export());
    }

    @PostMapping("/configs/import")
    @PreAuthorize("hasRole('ADMIN')")
    public R<Void> importConfigs(@RequestBody Map<String, String> configs) {
        Long userId = currentUserId();
        configService.importConfigs(configs, userId);
        return R.ok(null);
    }

    private Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return 0L;
        Object cred = auth.getCredentials();
        return cred instanceof Long ? (Long) cred : 0L;
    }
}
