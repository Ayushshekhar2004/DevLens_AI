package com.devlensai.backend.controller;

import com.devlensai.backend.dto.AnalyticsOverviewResponse;
import com.devlensai.backend.entity.User;
import com.devlensai.backend.service.AnalyticsService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping("/overview")
    public AnalyticsOverviewResponse overview(@AuthenticationPrincipal User user) {
        return analyticsService.overview(user);
    }
}
