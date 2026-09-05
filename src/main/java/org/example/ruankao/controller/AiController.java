package org.example.ruankao.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.example.ruankao.common.ApiResponse;
import org.example.ruankao.dto.AiDtos;
import org.example.ruankao.dto.ImportDtos;
import org.example.ruankao.service.ai.AiQuestionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "AI 能力", description = "AI 生成题目与文档解析（DeepSeek/OpenAI 兼容）")
@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final AiQuestionService aiQuestionService;

    public AiController(AiQuestionService aiQuestionService) {
        this.aiQuestionService = aiQuestionService;
    }

    @Operation(summary = "AI 配置状态（是否已配置 API Key）")
    @GetMapping("/status")
    public ApiResponse<AiDtos.StatusResponse> status() {
        return ApiResponse.ok(aiQuestionService.status());
    }

    @Operation(summary = "获取 AI 配置状态")
    @GetMapping("/config")
    public ApiResponse<AiDtos.StatusResponse> config() {
        return ApiResponse.ok(aiQuestionService.status());
    }

    @Operation(summary = "保存 AI 配置（API Key 等，立即生效并持久化）")
    @PutMapping("/config")
    public ApiResponse<AiDtos.StatusResponse> updateConfig(
            @Valid @RequestBody AiDtos.ConfigRequest request) {
        return ApiResponse.ok("AI 配置已保存", aiQuestionService.updateConfig(request));
    }

    @Operation(summary = "测试当前 AI 配置是否可用")
    @PostMapping("/test")
    public ApiResponse<AiDtos.TestResponse> testConnection() {
        return ApiResponse.ok("连接成功", aiQuestionService.testConnection());
    }

    @Operation(summary = "AI 生成题目（返回预览，确认后调用 /confirm 入库）")
    @PostMapping("/generate")
    public ApiResponse<AiDtos.GenerateResponse> generate(@Valid @RequestBody AiDtos.GenerateRequest request) {
        return ApiResponse.ok(aiQuestionService.generate(request));
    }

    @Operation(summary = "确认 AI 生成的题目入库")
    @PostMapping("/confirm")
    public ApiResponse<ImportDtos.ImportResult> confirm(@Valid @RequestBody AiDtos.ConfirmRequest request) {
        return ApiResponse.ok("入库完成", aiQuestionService.confirm(request));
    }
}
