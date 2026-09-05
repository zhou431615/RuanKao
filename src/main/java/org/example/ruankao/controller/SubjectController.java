package org.example.ruankao.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.example.ruankao.common.ApiResponse;
import org.example.ruankao.dto.SubjectDtos;
import org.example.ruankao.service.SubjectService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "科目章节", description = "科目与章节管理")
@RestController
@RequestMapping("/api/subjects")
public class SubjectController {

    private final SubjectService subjectService;

    public SubjectController(SubjectService subjectService) {
        this.subjectService = subjectService;
    }

    @Operation(summary = "科目列表（含章节与题目数）")
    @GetMapping
    public ApiResponse<List<SubjectDtos.Response>> list() {
        return ApiResponse.ok(subjectService.listAll());
    }

    @Operation(summary = "科目详情")
    @GetMapping("/{id}")
    public ApiResponse<SubjectDtos.Response> get(@PathVariable Long id) {
        return ApiResponse.ok(subjectService.getById(id));
    }

    @Operation(summary = "创建科目")
    @PostMapping
    public ApiResponse<SubjectDtos.Response> create(@Valid @RequestBody SubjectDtos.CreateRequest request) {
        return ApiResponse.ok("创建成功", subjectService.create(request));
    }

    @Operation(summary = "更新科目")
    @PutMapping("/{id}")
    public ApiResponse<SubjectDtos.Response> update(@PathVariable Long id,
                                                    @RequestBody SubjectDtos.UpdateRequest request) {
        return ApiResponse.ok("更新成功", subjectService.update(id, request));
    }

    @Operation(summary = "删除科目（科目下有题目时拒绝）")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        subjectService.delete(id);
        return ApiResponse.ok("删除成功", null);
    }

    @Operation(summary = "创建章节")
    @PostMapping("/{id}/chapters")
    public ApiResponse<SubjectDtos.ChapterResponse> createChapter(@PathVariable Long id,
                                                                  @Valid @RequestBody SubjectDtos.ChapterCreateRequest request) {
        return ApiResponse.ok("创建成功", subjectService.createChapter(id, request));
    }

    @Operation(summary = "更新章节")
    @PutMapping("/chapters/{chapterId}")
    public ApiResponse<SubjectDtos.ChapterResponse> updateChapter(@PathVariable Long chapterId,
                                                                  @RequestBody SubjectDtos.ChapterUpdateRequest request) {
        return ApiResponse.ok("更新成功", subjectService.updateChapter(chapterId, request));
    }

    @Operation(summary = "删除章节（章节下有题目时拒绝）")
    @DeleteMapping("/chapters/{chapterId}")
    public ApiResponse<Void> deleteChapter(@PathVariable Long chapterId) {
        subjectService.deleteChapter(chapterId);
        return ApiResponse.ok("删除成功", null);
    }
}
