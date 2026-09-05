package org.example.ruankao.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.example.ruankao.common.ApiResponse;
import org.example.ruankao.dto.QuestionDtos;
import org.example.ruankao.service.FavoriteService;
import org.example.ruankao.service.WrongBookService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "错题本与收藏", description = "错题本与收藏夹管理")
@RestController
@RequestMapping("/api")
public class WrongBookAndFavoriteController {

    private final WrongBookService wrongBookService;
    private final FavoriteService favoriteService;

    public WrongBookAndFavoriteController(WrongBookService wrongBookService, FavoriteService favoriteService) {
        this.wrongBookService = wrongBookService;
        this.favoriteService = favoriteService;
    }

    // ==================== 错题本 ====================

    @Operation(summary = "错题本列表")
    @GetMapping("/wrong-book")
    public ApiResponse<List<QuestionDtos.ListItem>> wrongList(
            @RequestParam(required = false) Long subjectId) {
        return ApiResponse.ok(wrongBookService.list(subjectId));
    }

    @Operation(summary = "从错题本移除")
    @DeleteMapping("/wrong-book/{questionId}")
    public ApiResponse<Void> removeWrong(@PathVariable Long questionId) {
        wrongBookService.remove(questionId);
        return ApiResponse.ok("已移除", null);
    }

    @Operation(summary = "清空错题本（可按科目）")
    @DeleteMapping("/wrong-book")
    public ApiResponse<Integer> clearWrong(@RequestParam(required = false) Long subjectId) {
        return ApiResponse.ok("已清空", wrongBookService.clear(subjectId));
    }

    // ==================== 收藏夹 ====================

    @Operation(summary = "收藏题目")
    @PostMapping("/favorites/{questionId}")
    public ApiResponse<Void> addFavorite(@PathVariable Long questionId) {
        favoriteService.add(questionId);
        return ApiResponse.ok("已收藏", null);
    }

    @Operation(summary = "取消收藏")
    @DeleteMapping("/favorites/{questionId}")
    public ApiResponse<Void> removeFavorite(@PathVariable Long questionId) {
        favoriteService.remove(questionId);
        return ApiResponse.ok("已取消收藏", null);
    }

    @Operation(summary = "收藏夹列表")
    @GetMapping("/favorites")
    public ApiResponse<List<QuestionDtos.ListItem>> favoriteList(
            @RequestParam(required = false) Long subjectId) {
        return ApiResponse.ok(favoriteService.list(subjectId));
    }
}
