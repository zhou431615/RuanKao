package org.example.ruankao.service.importer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.ruankao.dto.ImportDtos;
import org.example.ruankao.entity.Chapter;
import org.example.ruankao.entity.Question;
import org.example.ruankao.repository.QuestionRepository;
import org.example.ruankao.repository.WrongQuestionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 题库导出：将题目导出为标准 JSON 格式（与导入格式一致，可再导入）。
 */
@Service
public class ExportService {

    private final QuestionRepository questionRepository;
    private final ObjectMapper objectMapper;
    private final WrongQuestionRepository wrongQuestionRepository;

    public ExportService(QuestionRepository questionRepository,
                         ObjectMapper objectMapper,
                         WrongQuestionRepository wrongQuestionRepository) {
        this.questionRepository = questionRepository;
        this.objectMapper = objectMapper;
        this.wrongQuestionRepository = wrongQuestionRepository;
    }

    /** 导出指定科目题目为 JSON 字节数组；subjectId 为空导出全部 */
    @Transactional(readOnly = true)
    public byte[] exportJson(Long subjectId) {
        List<Question> questions = subjectId == null
                ? questionRepository.findAll()
                : questionRepository.findAll().stream()
                        .filter(q -> q.getSubject().getId().equals(subjectId))
                        .toList();

        List<ImportDtos.JsonQuestion> items = questions.stream().map(this::toJsonQuestion).toList();
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(items);
        } catch (Exception e) {
            throw new IllegalStateException("导出 JSON 失败", e);
        }
    }

    private ImportDtos.JsonQuestion toJsonQuestion(Question q) {
        Chapter chapter = q.getChapter();
        List<ImportDtos.OptionItem> options = q.getOptions().entrySet().stream()
                .map(e -> new ImportDtos.OptionItem(e.getKey(), e.getValue()))
                .toList();
        return new ImportDtos.JsonQuestion(
                q.getSubject().getName(),
                chapter == null ? null : chapter.getName(),
                q.getType().name(),
                q.getStem(),
                options,
                q.getAnswer(),
                q.getAnalysis(),
                q.getDifficulty(),
                q.getSource(),
                wrongQuestionRepository.existsByQuestionId(q.getId()));
    }
}
