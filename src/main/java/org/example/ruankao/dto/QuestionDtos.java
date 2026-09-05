package org.example.ruankao.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.example.ruankao.common.QuestionType;

import java.util.List;
import java.util.Map;

/**
 * 题目相关 DTO。
 */
public final class QuestionDtos {

    private QuestionDtos() {
    }

    /** 创建/更新题目请求 */
    public record SaveRequest(
            @NotNull(message = "科目不能为空") Long subjectId,
            Long chapterId,
            @NotNull(message = "题型不能为空") QuestionType type,
            @NotBlank(message = "题干不能为空") String stem,
            Map<String, String> options,
            @NotBlank(message = "答案不能为空") String answer,
            String analysis,
            @Min(value = 1, message = "难度最小为1") @Max(value = 5, message = "难度最大为5") Integer difficulty,
            String source) {
    }

    /** 题目列表项（不含答案，避免刷题时泄漏） */
    public record ListItem(Long id, Long subjectId, String subjectName, Long chapterId, String chapterName,
                           QuestionType type, String stem, Map<String, String> options,
                           Integer difficulty, String source, boolean wrong, boolean favorite) {
    }

    /** 题目详情（含答案与解析，用于编辑或提交后查看） */
    public record Detail(Long id, Long subjectId, String subjectName, Long chapterId, String chapterName,
                         QuestionType type, String stem, Map<String, String> options,
                         String answer, String analysis, Integer difficulty, String source,
                         boolean wrong, boolean favorite) {
    }

    /** 刷题取题请求 */
    public record PracticeRequest(
            @NotNull(message = "科目不能为空") Long subjectId,
            Long chapterId,
            QuestionType type,
            /** normal | wrong | favorite */
            String source,
            /** order | random */
            String mode,
            Integer limit) {
    }

    /** 提交单题作答请求 */
    public record SubmitRequest(
            @NotNull(message = "题目ID不能为空") Long questionId,
            @NotBlank(message = "作答内容不能为空") String userAnswer,
            /** 主观题自评得分 0-100 */
            @Min(0) @Max(100) Integer selfScore) {
    }

    /** 判分结果 */
    public record SubmitResult(Long recordId, boolean correct, String correctAnswer,
                               String analysis, QuestionType type,
                               Integer score, String feedback, boolean aiEvaluated,
                               /** 本次答对后是否已从错题本移出 */
                               boolean removedFromWrongBook) {
    }

    /** 答题历史分页项 */
    public record HistoryItem(Long id, Long questionId, String stem, QuestionType type,
                              String userAnswer, boolean correct, Integer selfScore, String answeredAt) {
    }

    /** 批量删除请求 */
    public record BatchDeleteRequest(
            @NotEmpty(message = "请先选择要删除的题目") List<Long> ids) {
    }

    /** 筛选条件（内部使用） */
    public record Filter(Long subjectId, Long chapterId, QuestionType type, String keyword) {
    }

    /** 分页响应 */
    public record PageResponse<T>(List<T> content, long total, int page, int size) {
    }
}
