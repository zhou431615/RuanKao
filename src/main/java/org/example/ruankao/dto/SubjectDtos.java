package org.example.ruankao.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

/**
 * 科目与章节相关 DTO。
 */
public final class SubjectDtos {

    private SubjectDtos() {
    }

    public record CreateRequest(@NotBlank(message = "科目名称不能为空") String name, String description) {
    }

    public record UpdateRequest(String name, String description) {
    }

    public record ChapterCreateRequest(@NotBlank(message = "章节名称不能为空") String name, Integer sortOrder) {
    }

    public record ChapterUpdateRequest(String name, Integer sortOrder) {
    }

    public record ChapterResponse(Long id, String name, Integer sortOrder, long questionCount) {
    }

    public record Response(Long id, String name, String description,
                           long questionCount, List<ChapterResponse> chapters) {
    }
}
