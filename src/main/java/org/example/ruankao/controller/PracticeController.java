package org.example.ruankao.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.example.ruankao.common.ApiResponse;
import org.example.ruankao.dto.QuestionDtos;
import org.example.ruankao.service.PracticeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "刷题", description = "提交作答与答题历史")
@RestController
@RequestMapping("/api/practice")
public class PracticeController {

    private final PracticeService practiceService;

    public PracticeController(PracticeService practiceService) {
        this.practiceService = practiceService;
    }

    @Operation(summary = "提交单题作答（自动判分并收录错题）")
    @PostMapping("/submit")
    public ApiResponse<QuestionDtos.SubmitResult> submit(
            @Valid @RequestBody QuestionDtos.SubmitRequest request) {
        return ApiResponse.ok(practiceService.submit(request));
    }

    @Operation(summary = "答题历史（分页）")
    @GetMapping("/history")
    public ApiResponse<QuestionDtos.PageResponse<QuestionDtos.HistoryItem>> history(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(practiceService.history(page, size));
    }

    @Operation(summary = "单题答题历史")
    @GetMapping("/history/{questionId}")
    public ApiResponse<QuestionDtos.PageResponse<QuestionDtos.HistoryItem>> historyOfQuestion(
            @PathVariable Long questionId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(practiceService.historyOfQuestion(questionId, page, size));
    }
}
