package com.attendance.controller;

import com.attendance.service.AnalyticsService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping("/insights")
    public List<AnalyticsService.StudentInsight> insights() {
        return analyticsService.computeInsights();
    }

    @GetMapping("/weekly-summary")
    public AnalyticsService.WeeklySummary weeklySummary() {
        return analyticsService.getLastReport();
    }

    /** Faculty can force-regenerate instead of waiting for the Monday cron. */
    @PostMapping("/weekly-summary/regenerate")
    public AnalyticsService.WeeklySummary regenerate() {
        return analyticsService.generateWeeklySummary();
    }
}
