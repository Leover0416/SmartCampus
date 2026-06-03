package com.simon.campus.controller;

import com.simon.campus.common.R;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class HealthController {

    @GetMapping("/hello")
    public R<String> hello() {
        return R.ok("SmartCampus Backend is running 🎓");
    }
}
