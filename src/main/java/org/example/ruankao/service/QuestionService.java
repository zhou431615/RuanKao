package org.example.ruankao.service;

import org.example.ruankao.common.BusinessException;
import org.example.ruankao.common.QuestionType;
import org.example.ruankao.dto.QuestionDtos;
import org.example.ruankao.entity.Chapter;
import org.example.ruankao.entity.Favorite;
import org.example.ruankao.entity.Question;
import org.example.ruankao.entity.Subject;
import org.example.ruankao.entity.WrongQuestion;
import org.example.ruankao.repository.ChapterRepository;
import org.example.ruankao.repository.FavoriteRepository;
import org.example.ruankao.repository.PracticeRecordRepository;
import org.example.ruankao.repository.QuestionRepository;
import org.example.ruankao.repository.SubjectRepository;
import org.example.ruankao.repository.WrongQuestionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 题目管理：CRUD、分页筛选、刷题取题、判分。
 */
@Service
public class QuestionService {

    private static final Logger log = LoggerFactory.getLogger(QuestionService.class);

    /** 各题型合法答案格式说明（用于校验提示） */
    private static final Map<QuestionType, String> ANSWER_HINT = Map.of(
            QuestionType.SINGLE, "单选题答案应为选项字母，如 A",
            QuestionType.MULTIPLE, "多选题答案应为多个选项字母，如 ABD",
            QuestionType.JUDGE, "判断题答案应为 TRUE 或 FALSE",
            QuestionType.ESSAY, "问答题答案为参考答案文本"
    );

    private final QuestionRepository questionRepository;
    private final SubjectRepository subjectRepository;
    private final ChapterRepository chapterRepository;
    private final WrongQuestionRepository wrongQuestionRepository;
    private final FavoriteRepository favoriteRepository;
    private final PracticeRecordRepository practiceRecordRepository;

    public QuestionService(QuestionRepository questionRepository,
                           SubjectRepository subjectRepository,
                           ChapterRepository chapterRepository,
                           WrongQuestionRepository wrongQuestionRepository,
                           FavoriteRepository favoriteRepository,
                           PracticeRecordRepository practiceRecordRepository) {
        this.questionRepository = questionRepository;
        this.subjectRepository = subjectRepository;
        this.chapterRepository = chapterRepository;
        this.wrongQuestionRepository = wrongQuestionRepository;
        this.favoriteRepository = favoriteRepository;
        this.practiceRecordRepository = practiceRecordRepository;
    }

    // ==================== CRUD ====================

    @Transactional(readOnly = true)
    public QuestionDtos.PageResponse<QuestionDtos.ListItem> page(QuestionDtos.Filter filter, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page - 1, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "id"));
        Specification<Question> spec = buildSpec(filter);
        Page<Question> result = questionRepository.findAll(spec, pageable);
        List<QuestionDtos.ListItem> items = result.getContent().stream().map(this::toListItem).toList();
        return new QuestionDtos.PageResponse<>(items, result.getTotalElements(), page, size);
    }

    @Transactional(readOnly = true)
    public QuestionDtos.Detail getDetail(Long id) {
        Question question = requireQuestion(id);
        return toDetail(question);
    }

    @Transactional
    public QuestionDtos.Detail create(QuestionDtos.SaveRequest request) {
        validateSaveRequest(request);
        Subject subject = subjectRepository.findById(request.subjectId())
                .orElseThrow(() -> new BusinessException("科目不存在: id=" + request.subjectId()));
        Question question = new Question();
        applySaveRequest(question, request, subject);
        Question saved = questionRepository.save(question);
        log.info("创建题目: id={}, type={}, subject={}", saved.getId(), saved.getType(), subject.getName());
        return toDetail(saved);
    }

    @Transactional
    public QuestionDtos.Detail update(Long id, QuestionDtos.SaveRequest request) {
        validateSaveRequest(request);
        Question question = requireQuestion(id);
        Subject subject = subjectRepository.findById(request.subjectId())
                .orElseThrow(() -> new BusinessException("科目不存在: id=" + request.subjectId()));
        applySaveRequest(question, request, subject);
        return toDetail(question);
    }

    @Transactional
    public void delete(Long id) {
        Question question = requireQuestion(id);
        practiceRecordRepository.deleteByQuestionId(id);
        wrongQuestionRepository.findByQuestionId(id).ifPresent(wrongQuestionRepository::delete);
        favoriteRepository.findByQuestionId(id).ifPresent(favoriteRepository::delete);
        questionRepository.delete(question);
        log.info("删除题目: id={}", id);
    }

    /**
     * 批量删除题目：同步清理其错题本与收藏记录，跳过不存在的 id。
     *
     * @return 实际删除的题目数量
     */
    @Transactional
    public int deleteBatch(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new BusinessException("请先选择要删除的题目");
        }
        int deleted = 0;
        for (Long id : ids) {
            if (id == null || questionRepository.findById(id).isEmpty()) {
                continue;
            }
            delete(id);
            deleted++;
        }
        log.info("批量删除题目: 请求 {} 条，实际删除 {} 条", ids.size(), deleted);
        return deleted;
    }

    // ==================== 刷题取题 ====================

    /**
     * 按条件取一组题目用于刷题。
     * source: normal（题库）/ wrong（错题本）/ favorite（收藏夹）
     * mode: order（顺序）/ random（随机）
     */
    @Transactional(readOnly = true)
    public List<QuestionDtos.ListItem> fetchForPractice(QuestionDtos.PracticeRequest request) {
        String source = request.source() == null ? "normal" : request.source();
        boolean random = "random".equalsIgnoreCase(request.mode());
        int limit = request.limit() == null ? 20 : Math.min(Math.max(request.limit(), 1), 200);

        List<Question> questions = switch (source) {
            case "wrong" -> fetchFromWrongBook(request, random, limit);
            case "favorite" -> fetchFromFavorite(request, random, limit);
            default -> fetchFromBank(request, random, limit);
        };
        return questions.stream().map(this::toListItem).toList();
    }

    private List<Question> fetchFromBank(QuestionDtos.PracticeRequest request, boolean random, int limit) {
        List<Question> questions = questionRepository.findAll((root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("subject").get("id"), request.subjectId()));
            if (request.chapterId() != null) {
                predicates.add(cb.equal(root.get("chapter").get("id"), request.chapterId()));
            }
            if (request.type() != null) {
                predicates.add(cb.equal(root.get("type"), request.type()));
            }
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        });
        return limitAndShuffle(questions, random, limit);
    }

    private List<Question> fetchFromWrongBook(QuestionDtos.PracticeRequest request, boolean random, int limit) {
        List<WrongQuestion> wrongQuestions = wrongQuestionRepository.findAllWithQuestion(request.subjectId());
        List<Question> questions = wrongQuestions.stream()
                .map(WrongQuestion::getQuestion)
                .filter(q -> request.chapterId() == null
                        || (q.getChapter() != null && q.getChapter().getId().equals(request.chapterId())))
                .filter(q -> request.type() == null || q.getType() == request.type())
                .toList();
        return limitAndShuffle(questions, random, limit);
    }

    private List<Question> fetchFromFavorite(QuestionDtos.PracticeRequest request, boolean random, int limit) {
        List<Favorite> favorites = favoriteRepository.findAllWithQuestion(request.subjectId());
        List<Question> questions = favorites.stream()
                .map(Favorite::getQuestion)
                .filter(q -> request.chapterId() == null
                        || (q.getChapter() != null && q.getChapter().getId().equals(request.chapterId())))
                .filter(q -> request.type() == null || q.getType() == request.type())
                .toList();
        return limitAndShuffle(questions, random, limit);
    }

    private List<Question> limitAndShuffle(List<Question> questions, boolean random, int limit) {
        List<Question> copy = new ArrayList<>(questions);
        if (random) {
            java.util.Collections.shuffle(copy);
        } else {
            copy.sort((a, b) -> Long.compare(a.getId(), b.getId()));
        }
        return copy.stream().limit(limit).collect(Collectors.toList());
    }

    // ==================== 判分 ====================

    /**
     * 判分：单选精确匹配；多选排序后集合全等；判断映射 TRUE/FALSE；问答按自评。
     */
    @Transactional
    public boolean judge(Question question, String userAnswer) {
        String standard = normalizeAnswer(question.getAnswer());
        String submitted = normalizeAnswer(userAnswer);
        return switch (question.getType()) {
            case SINGLE -> standard.equalsIgnoreCase(submitted);
            case MULTIPLE -> isSameOptionSet(standard, submitted);
            case JUDGE -> standard.equalsIgnoreCase(submitted);
            case ESSAY -> false; // 主观题由自评得分决定，此处不判对错
        };
    }

    private boolean isSameOptionSet(String standard, String submitted) {
        Set<String> standardSet = standard.chars()
                .mapToObj(c -> String.valueOf((char) Character.toUpperCase(c)))
                .collect(Collectors.toSet());
        Set<String> submittedSet = submitted.chars()
                .mapToObj(c -> String.valueOf((char) Character.toUpperCase(c)))
                .collect(Collectors.toSet());
        return standardSet.equals(submittedSet) && !standardSet.isEmpty();
    }

    /** 去除空白、分隔符，仅保留字母数字（用于客观题比对） */
    private String normalizeAnswer(String answer) {
        if (answer == null) {
            return "";
        }
        return answer.replaceAll("[^0-9A-Za-z\\u4e00-\\u9fa5]", "");
    }

    // ==================== 私有辅助 ====================

    private void validateSaveRequest(QuestionDtos.SaveRequest request) {
        if (request.type() == QuestionType.SINGLE || request.type() == QuestionType.MULTIPLE) {
            if (request.options() == null || request.options().size() < 2) {
                throw new BusinessException("选择题至少需要两个选项");
            }
        }
        if (request.type() == QuestionType.JUDGE) {
            String answer = request.answer() == null ? "" : request.answer().trim().toUpperCase();
            if (!answer.equals("TRUE") && !answer.equals("FALSE") && !answer.equals("对") && !answer.equals("错")) {
                throw new BusinessException("判断题答案必须为 TRUE/FALSE（或 对/错）");
            }
        }
        if (request.type() == QuestionType.SINGLE) {
            String answer = request.answer() == null ? "" : request.answer().trim().toUpperCase();
            if (!answer.matches("^[A-Z]$")) {
                throw new BusinessException(ANSWER_HINT.get(QuestionType.SINGLE));
            }
            if (!request.options().containsKey(answer)) {
                throw new BusinessException("答案 " + answer + " 不在选项中");
            }
        }
        if (request.type() == QuestionType.MULTIPLE) {
            String answer = request.answer() == null ? "" : request.answer().trim().toUpperCase();
            if (!answer.matches("^[A-Z]{2,}$")) {
                throw new BusinessException(ANSWER_HINT.get(QuestionType.MULTIPLE));
            }
            for (char c : answer.toCharArray()) {
                if (!request.options().containsKey(String.valueOf(c))) {
                    throw new BusinessException("答案 " + c + " 不在选项中");
                }
            }
        }
    }

    private void applySaveRequest(Question question, QuestionDtos.SaveRequest request, Subject subject) {
        question.setSubject(subject);
        if (request.chapterId() != null) {
            Chapter chapter = chapterRepository.findById(request.chapterId())
                    .orElseThrow(() -> new BusinessException("章节不存在: id=" + request.chapterId()));
            if (!chapter.getSubject().getId().equals(subject.getId())) {
                throw new BusinessException("章节不属于所选科目");
            }
            question.setChapter(chapter);
        } else {
            question.setChapter(null);
        }
        question.setType(request.type());
        question.setStem(request.stem().trim());
        question.setOptions(orderedOptions(request.options()));
        question.setAnswer(normalizeStoredAnswer(request.type(), request.answer()));
        question.setAnalysis(request.analysis());
        question.setDifficulty(request.difficulty() == null ? 3 : request.difficulty());
        question.setSource(request.source());
    }

    /** 存储前统一答案格式 */
    private String normalizeStoredAnswer(QuestionType type, String answer) {
        String trimmed = answer.trim();
        return switch (type) {
            case JUDGE -> {
                String upper = trimmed.toUpperCase();
                if (upper.equals("对") || upper.equals("TRUE") || upper.equals("T") || upper.equals("Y")) {
                    yield "TRUE";
                }
                yield "FALSE";
            }
            case SINGLE, MULTIPLE -> trimmed.toUpperCase().replaceAll("[^A-Z]", "");
            case ESSAY -> trimmed;
        };
    }

    private Question requireQuestion(Long id) {
        return questionRepository.findById(id)
                .orElseThrow(() -> new BusinessException("题目不存在: id=" + id));
    }

    private Map<String, String> orderedOptions(Map<String, String> options) {
        if (options == null || options.isEmpty()) {
            return Map.of();
        }
        Map<String, String> ordered = new LinkedHashMap<>();
        options.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> ordered.put(entry.getKey(), entry.getValue()));
        return ordered;
    }

    private QuestionDtos.ListItem toListItem(Question question) {
        return new QuestionDtos.ListItem(
                question.getId(),
                question.getSubject().getId(),
                question.getSubject().getName(),
                question.getChapter() == null ? null : question.getChapter().getId(),
                question.getChapter() == null ? null : question.getChapter().getName(),
                question.getType(),
                question.getStem(),
                question.getOptions(),
                question.getDifficulty(),
                question.getSource(),
                wrongQuestionRepository.existsByQuestionId(question.getId()),
                favoriteRepository.existsByQuestionId(question.getId())
        );
    }

    private QuestionDtos.Detail toDetail(Question question) {
        return new QuestionDtos.Detail(
                question.getId(),
                question.getSubject().getId(),
                question.getSubject().getName(),
                question.getChapter() == null ? null : question.getChapter().getId(),
                question.getChapter() == null ? null : question.getChapter().getName(),
                question.getType(),
                question.getStem(),
                question.getOptions(),
                question.getAnswer(),
                question.getAnalysis(),
                question.getDifficulty(),
                question.getSource(),
                wrongQuestionRepository.existsByQuestionId(question.getId()),
                favoriteRepository.existsByQuestionId(question.getId())
        );
    }

    private Specification<Question> buildSpec(QuestionDtos.Filter filter) {
        return (root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            if (filter != null) {
                if (filter.subjectId() != null) {
                    predicates.add(cb.equal(root.get("subject").get("id"), filter.subjectId()));
                }
                if (filter.chapterId() != null) {
                    predicates.add(cb.equal(root.get("chapter").get("id"), filter.chapterId()));
                }
                if (filter.type() != null) {
                    predicates.add(cb.equal(root.get("type"), filter.type()));
                }
                if (StringUtils.hasText(filter.keyword())) {
                    predicates.add(cb.like(cb.lower(root.get("stem")),
                            "%" + filter.keyword().toLowerCase() + "%"));
                }
            }
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
    }
}
