package org.example.ruankao.service;

import org.example.ruankao.dto.StatsDtos;
import org.example.ruankao.repository.PracticeRecordRepository;
import org.example.ruankao.repository.QuestionRepository;
import org.example.ruankao.repository.SubjectRepository;
import org.example.ruankao.repository.WrongQuestionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 学习统计：总览、近 30 天趋势、科目进度。
 */
@Service
public class StatsService {

    private static final Logger log = LoggerFactory.getLogger(StatsService.class);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final QuestionRepository questionRepository;
    private final PracticeRecordRepository practiceRecordRepository;
    private final SubjectRepository subjectRepository;
    private final WrongQuestionRepository wrongQuestionRepository;

    public StatsService(QuestionRepository questionRepository,
                        PracticeRecordRepository practiceRecordRepository,
                        SubjectRepository subjectRepository,
                        WrongQuestionRepository wrongQuestionRepository) {
        this.questionRepository = questionRepository;
        this.practiceRecordRepository = practiceRecordRepository;
        this.subjectRepository = subjectRepository;
        this.wrongQuestionRepository = wrongQuestionRepository;
    }

    @Transactional(readOnly = true)
    public StatsDtos.StatsResponse overview() {
        long totalQuestions = questionRepository.count();
        long totalAnswered = practiceRecordRepository.count();
        long totalCorrect = practiceRecordRepository.countByCorrectTrueAndAnsweredAtAfter(
                LocalDateTime.of(1970, 1, 1, 0, 0));

        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        long todayAnswered = practiceRecordRepository.countByAnsweredAtAfter(todayStart);
        long todayCorrect = practiceRecordRepository.countByCorrectTrueAndAnsweredAtAfter(todayStart);
        long wrongCount = wrongQuestionRepository.count();

        StatsDtos.Overview overview = new StatsDtos.Overview(
                totalQuestions, totalAnswered, totalCorrect,
                totalAnswered == 0 ? 0 : Math.round(totalCorrect * 1000.0 / totalAnswered) / 10.0,
                todayAnswered, todayCorrect, wrongCount);

        return new StatsDtos.StatsResponse(overview, dailyTrend(), subjectProgress());
    }

    /** 近 30 天每日答题数与正确率（缺失日期补零） */
    private List<StatsDtos.DailyPoint> dailyTrend() {
        LocalDateTime since = LocalDate.now().minusDays(29).atStartOfDay();
        Map<String, long[]> byDate = new HashMap<>();
        for (Object[] row : practiceRecordRepository.countDailyStats(since)) {
            String date = String.valueOf(row[0]);
            long answered = ((Number) row[1]).longValue();
            long correct = row[2] == null ? 0 : ((Number) row[2]).longValue();
            byDate.put(date, new long[]{answered, correct});
        }
        List<StatsDtos.DailyPoint> points = new ArrayList<>();
        for (int i = 29; i >= 0; i--) {
            LocalDate day = LocalDate.now().minusDays(i);
            String key = day.format(DATE_FMT);
            long[] stat = byDate.getOrDefault(key, new long[]{0, 0});
            double accuracy = stat[0] == 0 ? 0 : Math.round(stat[1] * 1000.0 / stat[0]) / 10.0;
            points.add(new StatsDtos.DailyPoint(key, stat[0], stat[1], accuracy));
        }
        return points;
    }

    /** 各科目进度：题目总数、已刷题目数、正确率、进度百分比 */
    private List<StatsDtos.SubjectProgress> subjectProgress() {
        Map<Long, long[]> statsBySubject = new HashMap<>();
        for (Object[] row : practiceRecordRepository.countStatsBySubject()) {
            Long subjectId = ((Number) row[0]).longValue();
            long distinctAnswered = ((Number) row[1]).longValue();
            long answered = ((Number) row[2]).longValue();
            long correct = row[3] == null ? 0 : ((Number) row[3]).longValue();
            statsBySubject.put(subjectId, new long[]{distinctAnswered, answered, correct});
        }

        List<StatsDtos.SubjectProgress> result = new ArrayList<>();
        for (var subject : subjectRepository.findAll()) {
            long totalQuestions = questionRepository.countBySubjectId(subject.getId());
            long[] stat = statsBySubject.getOrDefault(subject.getId(), new long[]{0, 0, 0});
            long answeredQuestions = stat[0];
            long answered = stat[1];
            long correct = stat[2];
            // 已刷题目数按题目去重统计，一题多次作答只计一次
            double progress = totalQuestions == 0 ? 0
                    : Math.min(100.0, Math.round(answeredQuestions * 1000.0 / totalQuestions) / 10.0);
            result.add(new StatsDtos.SubjectProgress(
                    subject.getId(), subject.getName(), totalQuestions,
                    answeredQuestions, answered, correct,
                    answered == 0 ? 0 : Math.round(correct * 1000.0 / answered) / 10.0,
                    progress));
        }
        return result;
    }
}
