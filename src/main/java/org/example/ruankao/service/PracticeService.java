package org.example.ruankao.service;

import org.example.ruankao.common.BusinessException;
import org.example.ruankao.common.QuestionType;
import org.example.ruankao.dto.QuestionDtos;
import org.example.ruankao.entity.PracticeRecord;
import org.example.ruankao.entity.Question;
import org.example.ruankao.entity.WrongQuestion;
import org.example.ruankao.repository.PracticeRecordRepository;
import org.example.ruankao.repository.QuestionRepository;
import org.example.ruankao.repository.WrongQuestionRepository;
import org.example.ruankao.service.ai.EssayEvaluationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 刷题练习：提交作答、判分、写记录、错题自动收录。
 */
@Service
public class PracticeService {

    private static final Logger log = LoggerFactory.getLogger(PracticeService.class);

    private final QuestionRepository questionRepository;
    private final PracticeRecordRepository practiceRecordRepository;
    private final WrongQuestionRepository wrongQuestionRepository;
    private final EssayEvaluationService essayEvaluationService;

    public PracticeService(QuestionRepository questionRepository,
                           PracticeRecordRepository practiceRecordRepository,
                           WrongQuestionRepository wrongQuestionRepository,
                           EssayEvaluationService essayEvaluationService) {
        this.questionRepository = questionRepository;
        this.practiceRecordRepository = practiceRecordRepository;
        this.wrongQuestionRepository = wrongQuestionRepository;
        this.essayEvaluationService = essayEvaluationService;
    }

    /**
     * 提交单题作答：判分、写记录、错题自动收录。
     * 主观题（ESSAY）优先使用 AI 评分与评语；AI 不可用时回退自评。
     */
    @Transactional
    public QuestionDtos.SubmitResult submit(QuestionDtos.SubmitRequest request) {
        Question question = questionRepository.findById(request.questionId())
                .orElseThrow(() -> new BusinessException("题目不存在: id=" + request.questionId()));

        boolean correct;
        Integer finalScore = request.selfScore();
        String feedback = null;
        boolean aiEvaluated = false;
        if (question.getType() == QuestionType.ESSAY) {
            String answer = request.userAnswer();
            boolean unanswered = answer == null || answer.isBlank() || "（未作答）".equals(answer.trim());
            if (unanswered) {
                finalScore = 0;
                correct = false;
                feedback = "本题未作答，请对照参考答案补齐要点。";
            } else if (essayEvaluationService.isAvailable()) {
                try {
                    EssayEvaluationService.Evaluation evaluation = essayEvaluationService.evaluate(
                            question.getStem(), question.getAnswer(), answer.trim());
                    finalScore = evaluation.score();
                    correct = finalScore >= 60;
                    feedback = evaluation.feedback();
                    aiEvaluated = true;
                } catch (Exception e) {
                    log.warn("主观题 AI 评分失败，回退自评: {}", e.getMessage());
                    if (request.selfScore() == null) {
                        throw new BusinessException("问答题 AI 评分失败，请先自评分数（0-100）");
                    }
                    finalScore = request.selfScore();
                    correct = finalScore >= 60;
                    feedback = "AI 评分暂不可用，本次按自评计分。";
                }
            } else {
                if (request.selfScore() == null) {
                    throw new BusinessException("问答题请先自评分数（0-100）");
                }
                finalScore = request.selfScore();
                correct = finalScore >= 60;
                feedback = "已按自评计分，建议逐条对照参考答案检查遗漏。";
            }
        } else {
            correct = judge(question, request.userAnswer());
        }

        PracticeRecord record = new PracticeRecord();
        record.setQuestion(question);
        record.setType(question.getType());
        record.setUserAnswer(request.userAnswer());
        record.setCorrect(correct);
        record.setSelfScore(finalScore);
        practiceRecordRepository.save(record);

        // 错题本与本次作答同步：答错收录并累计，答对则视为已掌握并移出
        boolean removedFromWrongBook = false;
        if (!correct) {
            recordWrong(question, request.userAnswer());
        } else {
            removedFromWrongBook = clearWrong(question);
        }

        log.debug("提交作答: questionId={}, correct={}", question.getId(), correct);
        return new QuestionDtos.SubmitResult(record.getId(), correct,
                question.getAnswer(), question.getAnalysis(), question.getType(),
                finalScore, feedback, aiEvaluated, removedFromWrongBook);
    }

    /** 答错自动收录/累计错题本 */
    private void recordWrong(Question question, String userAnswer) {
        WrongQuestion wrong = wrongQuestionRepository.findByQuestionId(question.getId())
                .orElseGet(() -> {
                    WrongQuestion w = new WrongQuestion();
                    w.setQuestion(question);
                    return w;
                });
        wrong.setWrongCount(wrong.getWrongCount() + 1);
        wrong.setLastWrongAnswer(userAnswer);
        wrongQuestionRepository.save(wrong);
    }

    /** 答对后从错题本移除，返回是否确有移除 */
    private boolean clearWrong(Question question) {
        return wrongQuestionRepository.findByQuestionId(question.getId())
                .map(wrong -> {
                    wrongQuestionRepository.delete(wrong);
                    log.info("已掌握，移出错题本: questionId={}", question.getId());
                    return true;
                })
                .orElse(false);
    }

    /** 供 QuestionService 复用的判分入口 */
    public boolean judge(Question question, String userAnswer) {
        String standard = normalize(question.getAnswer());
        String submitted = normalize(userAnswer);
        return switch (question.getType()) {
            case SINGLE, JUDGE -> standard.equalsIgnoreCase(submitted);
            case MULTIPLE -> sameOptionSet(standard, submitted);
            case ESSAY -> false;
        };
    }

    private String normalize(String answer) {
        return answer == null ? "" : answer.replaceAll("[^0-9A-Za-z\\u4e00-\\u9fa5]", "");
    }

    private boolean sameOptionSet(String standard, String submitted) {
        java.util.Set<String> a = standard.toUpperCase().chars()
                .mapToObj(c -> String.valueOf((char) c)).collect(java.util.stream.Collectors.toSet());
        java.util.Set<String> b = submitted.toUpperCase().chars()
                .mapToObj(c -> String.valueOf((char) c)).collect(java.util.stream.Collectors.toSet());
        return !a.isEmpty() && a.equals(b);
    }

    /** 答题历史（分页，倒序） */
    @Transactional(readOnly = true)
    public QuestionDtos.PageResponse<QuestionDtos.HistoryItem> history(int page, int size) {
        Page<PracticeRecord> result = practiceRecordRepository.findAll(
                org.springframework.data.domain.PageRequest.of(Math.max(page - 1, 0),
                        Math.min(Math.max(size, 1), 100),
                        org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "id")));
        List<QuestionDtos.HistoryItem> items = result.getContent().stream().map(r -> {
            Question q = r.getQuestion();
            return new QuestionDtos.HistoryItem(r.getId(), q.getId(), q.getStem(), q.getType(),
                    r.getUserAnswer(), r.isCorrect(), r.getSelfScore(),
                    r.getAnsweredAt().toString());
        }).toList();
        return new QuestionDtos.PageResponse<>(items, result.getTotalElements(), page, size);
    }

    /** 题目的答题历史 */
    @Transactional(readOnly = true)
    public QuestionDtos.PageResponse<QuestionDtos.HistoryItem> historyOfQuestion(Long questionId, int page, int size) {
        Page<PracticeRecord> result = practiceRecordRepository.findByQuestionIdOrderByIdDesc(questionId,
                PageRequest.of(Math.max(page - 1, 0), Math.min(Math.max(size, 1), 100)));
        List<QuestionDtos.HistoryItem> items = result.getContent().stream().map(r -> new QuestionDtos.HistoryItem(
                r.getId(), questionId, r.getQuestion().getStem(), r.getType(),
                r.getUserAnswer(), r.isCorrect(), r.getSelfScore(), r.getAnsweredAt().toString())).toList();
        return new QuestionDtos.PageResponse<>(items, result.getTotalElements(), page, size);
    }
}
