package org.example.ruankao.service.importer;

import org.example.ruankao.common.BusinessException;
import org.example.ruankao.common.QuestionType;
import org.example.ruankao.dto.ImportDtos;
import org.example.ruankao.entity.Chapter;
import org.example.ruankao.entity.Question;
import org.example.ruankao.entity.Subject;
import org.example.ruankao.entity.WrongQuestion;
import org.example.ruankao.repository.QuestionRepository;
import org.example.ruankao.repository.WrongQuestionRepository;
import org.example.ruankao.service.SubjectService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 导入服务：Excel / JSON / PDF / Word 统一入口，负责题目落库与结果汇总。
 */
@Service
public class ImportService {

    private static final Logger log = LoggerFactory.getLogger(ImportService.class);

    /** 判断题答案别名 -> 标准值 */
    private static final Map<String, String> JUDGE_ALIASES = Map.ofEntries(
            Map.entry("TRUE", "TRUE"), Map.entry("T", "TRUE"), Map.entry("Y", "TRUE"),
            Map.entry("对", "TRUE"), Map.entry("正确", "TRUE"), Map.entry("√", "TRUE"),
            Map.entry("FALSE", "FALSE"), Map.entry("F", "FALSE"), Map.entry("N", "FALSE"),
            Map.entry("错", "FALSE"), Map.entry("错误", "FALSE"), Map.entry("×", "FALSE"));

    /** 多选答案中的分隔符 */
    private static final Pattern NON_LETTER = Pattern.compile("[^A-Za-z]");

    private final SubjectService subjectService;
    private final QuestionRepository questionRepository;
    private final WrongQuestionRepository wrongQuestionRepository;

    public ImportService(SubjectService subjectService,
                         QuestionRepository questionRepository,
                         WrongQuestionRepository wrongQuestionRepository) {
        this.subjectService = subjectService;
        this.questionRepository = questionRepository;
        this.wrongQuestionRepository = wrongQuestionRepository;
    }

    /** 将解析出的题目条目批量落库，返回导入结果 */
    @Transactional
    public ImportDtos.ImportResult saveAll(String fileName, List<ImportDtos.JsonQuestion> items) {
        List<ImportDtos.RowResult> details = new ArrayList<>();
        int success = 0;
        int row = 0;
        for (ImportDtos.JsonQuestion item : items) {
            row++;
            String stemPreview = item.stem() == null ? "" :
                    (item.stem().length() > 30 ? item.stem().substring(0, 30) + "..." : item.stem());
            try {
                Question question = convert(item);
                questionRepository.save(question);
                String message = "导入成功";
                if (Boolean.TRUE.equals(item.wrong())) {
                    markAsWrong(question);
                    message = "导入成功（已标记错题）";
                }
                success++;
                details.add(new ImportDtos.RowResult(row, stemPreview, true, message));
            } catch (Exception e) {
                log.warn("导入第 {} 条失败: {}", row, e.getMessage());
                details.add(new ImportDtos.RowResult(row, stemPreview, false, e.getMessage()));
            }
        }
        log.info("导入完成: file={}, total={}, success={}, fail={}", fileName, items.size(), success, items.size() - success);
        return new ImportDtos.ImportResult(fileName, items.size(), success, items.size() - success, details);
    }

    private void markAsWrong(Question question) {
        WrongQuestion wrong = wrongQuestionRepository.findByQuestionId(question.getId())
                .orElseGet(() -> {
                    WrongQuestion created = new WrongQuestion();
                    created.setQuestion(question);
                    return created;
                });
        wrong.setWrongCount(wrong.getWrongCount() + 1);
        wrong.setLastWrongAnswer("导入标记");
        wrongQuestionRepository.save(wrong);
    }

    /** DTO -> 实体，含答案归一化与校验 */
    public Question convert(ImportDtos.JsonQuestion item) {
        if (item.stem() == null || item.stem().isBlank()) {
            throw new BusinessException("题干为空");
        }
        QuestionType type = parseType(item.type());
        if (type == null) {
            throw new BusinessException("未知题型: " + item.type() + "（应为 SINGLE/MULTIPLE/JUDGE/ESSAY）");
        }
        Subject subject = subjectService.ensureSubject(
                item.subject() == null || item.subject().isBlank() ? "未分类" : item.subject());
        Chapter chapter = subjectService.ensureChapter(subject, item.chapter());

        Question question = new Question();
        question.setSubject(subject);
        question.setChapter(chapter);
        question.setType(type);
        question.setStem(item.stem().trim());
        question.setAnswer(normalizeAnswer(type, item.answer()));
        question.setAnalysis(item.analysis());
        question.setDifficulty(item.difficulty() == null ? 3 : Math.max(1, Math.min(5, item.difficulty())));
        question.setSource(item.source());

        if (type == QuestionType.SINGLE || type == QuestionType.MULTIPLE) {
            if (item.options() == null || item.options().size() < 2) {
                throw new BusinessException("选择题至少需要两个选项");
            }
            Map<String, String> options = new TreeMap<>();
            for (ImportDtos.OptionItem option : item.options()) {
                if (option.key() == null || option.key().isBlank()) {
                    throw new BusinessException("选项缺少字母标识");
                }
                options.put(option.key().trim().toUpperCase(), option.content() == null ? "" : option.content());
            }
            question.setOptions(options);
            validateChoiceAnswer(type, question.getAnswer(), options.keySet());
        } else if (type == QuestionType.JUDGE) {
            validateJudgeAnswer(question.getAnswer());
        }
        return question;
    }

    private QuestionType parseType(String type) {
        if (type == null) {
            return null;
        }
        return switch (type.trim().toUpperCase()) {
            case "SINGLE", "单选", "单选题" -> QuestionType.SINGLE;
            case "MULTIPLE", "多选", "多选题" -> QuestionType.MULTIPLE;
            case "JUDGE", "判断", "判断题" -> QuestionType.JUDGE;
            case "ESSAY", "问答", "问答题", "主观", "主观题" -> QuestionType.ESSAY;
            default -> null;
        };
    }

    /** 答案归一化：单选/多选仅留字母并大写；判断映射 TRUE/FALSE；问答原文 */
    public String normalizeAnswer(QuestionType type, String answer) {
        if (answer == null || answer.isBlank()) {
            throw new BusinessException("答案为空");
        }
        String trimmed = answer.trim();
        return switch (type) {
            case SINGLE -> {
                String letters = NON_LETTER.matcher(trimmed.toUpperCase()).replaceAll("");
                if (letters.length() != 1) {
                    throw new BusinessException("单选题答案格式错误: " + answer);
                }
                yield letters;
            }
            case MULTIPLE -> {
                String letters = NON_LETTER.matcher(trimmed.toUpperCase()).replaceAll("");
                if (letters.length() < 2) {
                    throw new BusinessException("多选题答案格式错误: " + answer);
                }
                yield letters;
            }
            case JUDGE -> {
                String mapped = JUDGE_ALIASES.get(trimmed.toUpperCase());
                if (mapped == null) {
                    mapped = JUDGE_ALIASES.get(trimmed);
                }
                if (mapped == null) {
                    throw new BusinessException("判断题答案无法识别: " + answer);
                }
                yield mapped;
            }
            case ESSAY -> trimmed;
        };
    }

    private void validateChoiceAnswer(QuestionType type, String answer, java.util.Set<String> optionKeys) {
        for (char c : answer.toCharArray()) {
            if (!optionKeys.contains(String.valueOf(c))) {
                throw new BusinessException("答案 " + c + " 不在选项 " + optionKeys + " 中");
            }
        }
        if (type == QuestionType.SINGLE && answer.length() != 1) {
            throw new BusinessException("单选题答案只能是一个字母: " + answer);
        }
    }

    private void validateJudgeAnswer(String answer) {
        if (!answer.equals("TRUE") && !answer.equals("FALSE")) {
            throw new BusinessException("判断题答案必须为 TRUE/FALSE: " + answer);
        }
    }
}
