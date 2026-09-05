package org.example.ruankao.service;

import org.example.ruankao.common.BusinessException;
import org.example.ruankao.dto.SubjectDtos;
import org.example.ruankao.entity.Chapter;
import org.example.ruankao.entity.Subject;
import org.example.ruankao.repository.ChapterRepository;
import org.example.ruankao.repository.QuestionRepository;
import org.example.ruankao.repository.SubjectRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 科目与章节管理。
 */
@Service
public class SubjectService {

    private static final Logger log = LoggerFactory.getLogger(SubjectService.class);

    private final SubjectRepository subjectRepository;
    private final ChapterRepository chapterRepository;
    private final QuestionRepository questionRepository;

    public SubjectService(SubjectRepository subjectRepository,
                          ChapterRepository chapterRepository,
                          QuestionRepository questionRepository) {
        this.subjectRepository = subjectRepository;
        this.chapterRepository = chapterRepository;
        this.questionRepository = questionRepository;
    }

    @Transactional(readOnly = true)
    public List<SubjectDtos.Response> listAll() {
        return subjectRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public SubjectDtos.Response getById(Long id) {
        return subjectRepository.findById(id).map(this::toResponse)
                .orElseThrow(() -> new BusinessException("科目不存在: id=" + id));
    }

    @Transactional
    public SubjectDtos.Response create(SubjectDtos.CreateRequest request) {
        String name = request.name().trim();
        if (subjectRepository.existsByName(name)) {
            throw new BusinessException("科目已存在: " + name);
        }
        Subject subject = new Subject();
        subject.setName(name);
        subject.setDescription(request.description());
        Subject saved = subjectRepository.save(subject);
        log.info("创建科目: id={}, name={}", saved.getId(), saved.getName());
        return toResponse(saved);
    }

    @Transactional
    public SubjectDtos.Response update(Long id, SubjectDtos.UpdateRequest request) {
        Subject subject = requireSubject(id);
        if (request.name() != null && !request.name().isBlank()) {
            String name = request.name().trim();
            subjectRepository.findByName(name)
                    .filter(other -> !other.getId().equals(id))
                    .ifPresent(other -> {
                        throw new BusinessException("科目已存在: " + name);
                    });
            subject.setName(name);
        }
        if (request.description() != null) {
            subject.setDescription(request.description());
        }
        return toResponse(subject);
    }

    @Transactional
    public void delete(Long id) {
        Subject subject = requireSubject(id);
        long questionCount = questionRepository.countBySubjectId(id);
        if (questionCount > 0) {
            throw new BusinessException("科目下仍有 " + questionCount + " 道题目，请先删除题目");
        }
        List<Chapter> chapters = chapterRepository.findBySubjectIdOrderBySortOrderAscIdAsc(id);
        if (!chapters.isEmpty()) {
            chapterRepository.deleteAll(chapters);
        }
        subjectRepository.delete(subject);
        log.info("删除科目: id={}, name={}", id, subject.getName());
    }

    @Transactional
    public SubjectDtos.ChapterResponse createChapter(Long subjectId, SubjectDtos.ChapterCreateRequest request) {
        Subject subject = requireSubject(subjectId);
        String name = request.name().trim();
        chapterRepository.findBySubjectIdAndName(subjectId, name).ifPresent(c -> {
            throw new BusinessException("该科目下章节已存在: " + name);
        });
        Chapter chapter = new Chapter();
        chapter.setSubject(subject);
        chapter.setName(name);
        chapter.setSortOrder(request.sortOrder());
        Chapter saved = chapterRepository.save(chapter);
        return toChapterResponse(saved);
    }

    @Transactional
    public SubjectDtos.ChapterResponse updateChapter(Long chapterId, SubjectDtos.ChapterUpdateRequest request) {
        Chapter chapter = chapterRepository.findById(chapterId)
                .orElseThrow(() -> new BusinessException("章节不存在: id=" + chapterId));
        if (request.name() != null && !request.name().isBlank()) {
            String name = request.name().trim();
            chapterRepository.findBySubjectIdAndName(chapter.getSubject().getId(), name)
                    .filter(other -> !other.getId().equals(chapterId))
                    .ifPresent(other -> {
                        throw new BusinessException("该科目下章节已存在: " + name);
                    });
            chapter.setName(name);
        }
        if (request.sortOrder() != null) {
            chapter.setSortOrder(request.sortOrder());
        }
        return toChapterResponse(chapter);
    }

    @Transactional
    public void deleteChapter(Long chapterId) {
        Chapter chapter = chapterRepository.findById(chapterId)
                .orElseThrow(() -> new BusinessException("章节不存在: id=" + chapterId));
        long questionCount = questionRepository.countByChapterId(chapterId);
        if (questionCount > 0) {
            throw new BusinessException("章节下仍有 " + questionCount + " 道题目，无法删除");
        }
        chapterRepository.delete(chapter);
    }

    /** 按 ID 取科目，不存在抛业务异常 */
    @Transactional(readOnly = true)
    public Subject ensureSubjectExists(Long id) {
        return subjectRepository.findById(id)
                .orElseThrow(() -> new BusinessException("科目不存在: id=" + id));
    }

    /** 按 ID 取章节，不存在抛业务异常 */
    @Transactional(readOnly = true)
    public Chapter chapterOf(Long chapterId) {
        return chapterRepository.findById(chapterId)
                .orElseThrow(() -> new BusinessException("章节不存在: id=" + chapterId));
    }

    /** 按名称取科目，不存在则创建（导入时使用） */
    @Transactional
    public Subject ensureSubject(String name) {
        return subjectRepository.findByName(name.trim()).orElseGet(() -> {
            Subject subject = new Subject();
            subject.setName(name.trim());
            return subjectRepository.save(subject);
        });
    }

    /** 按名称取章节，不存在则创建；名称为空返回 null（导入时使用） */
    @Transactional
    public Chapter ensureChapter(Subject subject, String chapterName) {
        if (chapterName == null || chapterName.isBlank()) {
            return null;
        }
        String name = chapterName.trim();
        return chapterRepository.findBySubjectIdAndName(subject.getId(), name).orElseGet(() -> {
            Chapter chapter = new Chapter();
            chapter.setSubject(subject);
            chapter.setName(name);
            chapter.setSortOrder(0);
            return chapterRepository.save(chapter);
        });
    }

    private Subject requireSubject(Long id) {
        return subjectRepository.findById(id)
                .orElseThrow(() -> new BusinessException("科目不存在: id=" + id));
    }

    private SubjectDtos.Response toResponse(Subject subject) {
        List<SubjectDtos.ChapterResponse> chapters =
                chapterRepository.findBySubjectIdOrderBySortOrderAscIdAsc(subject.getId()).stream()
                        .map(this::toChapterResponse)
                        .toList();
        return new SubjectDtos.Response(subject.getId(), subject.getName(), subject.getDescription(),
                questionRepository.countBySubjectId(subject.getId()), chapters);
    }

    private SubjectDtos.ChapterResponse toChapterResponse(Chapter chapter) {
        return new SubjectDtos.ChapterResponse(chapter.getId(), chapter.getName(), chapter.getSortOrder(),
                questionRepository.countByChapterId(chapter.getId()));
    }
}
