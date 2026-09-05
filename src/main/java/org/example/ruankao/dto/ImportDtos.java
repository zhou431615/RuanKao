package org.example.ruankao.dto;

import java.util.List;

/**
 * 导入相关 DTO。
 */
public final class ImportDtos {

    private ImportDtos() {
    }

    /** 单条导入明细（成功或失败原因） */
    public record RowResult(int row, String stem, boolean success, String message) {
    }

    /** 导入结果汇总 */
    public record ImportResult(String fileName, int total, int successCount, int failCount,
                               List<RowResult> details) {
    }

    /** JSON 导入的题目条目（与导出格式一致） */
    public record JsonQuestion(
            String subject,
            String chapter,
            String type,
            String stem,
            List<OptionItem> options,
            String answer,
            String analysis,
            Integer difficulty,
            String source,
            Boolean wrong) {
    }

    public record OptionItem(String key, String content) {
    }
}
