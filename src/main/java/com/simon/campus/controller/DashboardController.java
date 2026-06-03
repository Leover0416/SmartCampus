package com.simon.campus.controller;

import com.simon.campus.common.R;
import com.simon.campus.service.admin.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping
    public R<Map<String, Object>> dashboard(@RequestParam(defaultValue = "7") int days) {
        return R.ok(dashboardService.getDashboard(days));
    }
}
