package com.modernshopflow.catalog.dashboard;

import com.modernshopflow.catalog.dashboard.DashboardDtos.DashboardSummary;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Owner-only overview metrics endpoint. */
@RestController
@RequestMapping("/dashboard")
public class DashboardController {
    private final DashboardService service;

    public DashboardController(DashboardService service) {
        this.service = service;
    }

    @GetMapping("/summary")
    public DashboardSummary summary(@RequestParam(defaultValue = "7d") String range) {
        return service.summary(range);
    }
}