package org.example.ruankao.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.example.ruankao.common.QuestionType;

import java.util.List;

/**
 * AI 相关 DTO。
 */
public final class AiDtos {

    private AiDtos() {
    }

    /** AI 生成题目请求 */
    public record GenerateRequest(
            @NotNull(message = "科目不能为空") Long subjectId,
            Long chapterId,
            @NotNull(message = "题型不能为空") QuestionType type,
            @Min(1) @Max(20) Integer count,
            @NotBlank(message = "知识点不能为空") String topic,
            String extraRequirement) {
    }

    /** AI 状态响应 */
    public record StatusResponse(boolean configured, String baseUrl, String model,
                                 Double temperature, int timeoutSeconds) {
    }

    /** AI 配置保存请求 */
    public record ConfigRequest(
            @NotBlank(message = "API 地址不能为空") String baseUrl,
            String apiKey,
            @NotBlank(message = "模型名称不能为空") String model,
            Double temperature,
            @Min(value = 30, message = "超时时间不能小于 30 秒") Integer timeoutSeconds,
            Boolean clearApiKey) {
    }

    /** AI 连接测试响应 */
    public record TestResponse(String reply, String model) {
    }

    /** AI 生成结果（预览用，与 JSON 导入条目同构） */
    public record GeneratedQuestion(
            String type, String stem, List<ImportDtos.OptionItem> options,
            String answer, String analysis, Integer difficulty) {
    }

    /** AI 生成响应 */
    public record GenerateResponse(Long subjectId, Long chapterId, List<GeneratedQuestion> questions) {
    }

    /** 确认入库请求 */
    public record ConfirmRequest(
            @NotNull(message = "科目不能为空") Long subjectId,
            Long chapterId,
            @NotNull(message = "题目列表不能为空") List<GeneratedQuestion> questions,
            String source) {
    }

    /** 文档解析请求 */
    public record ParseDocumentRequest(
            @NotNull(message = "科目不能为空") Long subjectId,
            Long chapterId,
            @NotBlank(message = "文档文本不能为空") String text) {
    }
}
