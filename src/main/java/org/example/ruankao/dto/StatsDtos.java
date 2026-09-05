package org.example.ruankao.dto;

import java.util.List;

/**
 * 统计相关 DTO。
 */
public final class StatsDtos {

    private StatsDtos() {
    }

    /** 顶部总览 */
    public record Overview(long totalQuestions, long totalAnswered, long totalCorrect,
                           double accuracy, long todayAnswered, long todayCorrect, long wrongCount) {
    }

    /** 每日趋势点 */
    public record DailyPoint(String date, long answered, long correct, double accuracy) {
    }

    /** 科目进度 */
    public record SubjectProgress(Long subjectId, String subjectName, long totalQuestions,
                                  long answeredQuestions, long totalAnswered, long correctAnswered,
                                  double accuracy, double progress) {
    }

    /** 统计汇总响应 */
    public record StatsResponse(Overview overview, List<DailyPoint> dailyTrend,
                                List<SubjectProgress> subjectProgress) {
    }
}
