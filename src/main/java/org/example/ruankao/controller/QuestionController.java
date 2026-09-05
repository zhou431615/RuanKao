package org.example.ruankao.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.example.ruankao.common.ApiResponse;
import org.example.ruankao.dto.QuestionDtos;
import org.example.ruankao.service.QuestionService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "题目", description = "题目管理与刷题取题")
@RestController
@RequestMapping("/api/questions")
public class QuestionController {

    private final QuestionService questionService;

    public QuestionController(QuestionService questionService) {
        this.questionService = questionService;
    }

    @Operation(summary = "分页筛选题目（列表不含答案）")
    @GetMapping
    public ApiResponse<QuestionDtos.PageResponse<QuestionDtos.ListItem>> page(
            @RequestParam(required = false) Long subjectId,
            @RequestParam(required = false) Long chapterId,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        QuestionDtos.Filter filter = new QuestionDtos.Filter(subjectId, chapterId,
                parseType(type), keyword);
        return ApiResponse.ok(questionService.page(filter, page, size));
    }

    @Operation(summary = "题目详情（含答案与解析）")
    @GetMapping("/{id}")
    public ApiResponse<QuestionDtos.Detail> get(@PathVariable Long id) {
        return ApiResponse.ok(questionService.getDetail(id));
    }

    @Operation(summary = "创建题目")
    @PostMapping
    public ApiResponse<QuestionDtos.Detail> create(@Valid @RequestBody QuestionDtos.SaveRequest request) {
        return ApiResponse.ok("创建成功", questionService.create(request));
    }

    @Operation(summary = "更新题目")
    @PutMapping("/{id}")
    public ApiResponse<QuestionDtos.Detail> update(@PathVariable Long id,
                                                   @Valid @RequestBody QuestionDtos.SaveRequest request) {
        return ApiResponse.ok("更新成功", questionService.update(id, request));
    }

    @Operation(summary = "删除题目")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        questionService.delete(id);
        return ApiResponse.ok("删除成功", null);
    }

    @Operation(summary = "批量删除题目（同步清理错题本与收藏记录）")
    @PostMapping("/batch-delete")
    public ApiResponse<Integer> deleteBatch(@Valid @RequestBody QuestionDtos.BatchDeleteRequest request) {
        int deleted = questionService.deleteBatch(request.ids());
        return ApiResponse.ok("已删除 " + deleted + " 道题目", deleted);
    }

    @Operation(summary = "刷题取题（source: normal/wrong/favorite, mode: order/random）")
    @PostMapping("/practice")
    public ApiResponse<List<QuestionDtos.ListItem>> practice(
            @Valid @RequestBody QuestionDtos.PracticeRequest request) {
        return ApiResponse.ok(questionService.fetchForPractice(request));
    }

    private org.example.ruankao.common.QuestionType parseType(String type) {
        if (type == null || type.isBlank()) {
            return null;
        }
        try {
            return org.example.ruankao.common.QuestionType.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
