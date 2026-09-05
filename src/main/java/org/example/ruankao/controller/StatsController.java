package org.example.ruankao.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.example.ruankao.common.ApiResponse;
import org.example.ruankao.dto.StatsDtos;
import org.example.ruankao.service.StatsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "统计", description = "学习统计")
@RestController
@RequestMapping("/api/stats")
public class StatsController {

    private final StatsService statsService;

    public StatsController(StatsService statsService) {
        this.statsService = statsService;
    }

    @Operation(summary = "统计总览：总览指标 + 近30天趋势 + 科目进度")
    @GetMapping
    public ApiResponse<StatsDtos.StatsResponse> overview() {
        return ApiResponse.ok(statsService.overview());
    }
}
