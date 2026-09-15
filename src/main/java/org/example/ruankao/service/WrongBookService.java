package org.example.ruankao.service;

import org.example.ruankao.common.BusinessException;
import org.example.ruankao.dto.QuestionDtos;
import org.example.ruankao.entity.WrongQuestion;
import org.example.ruankao.repository.FavoriteRepository;
import org.example.ruankao.repository.PracticeRecordRepository;
import org.example.ruankao.repository.WrongQuestionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 错题本：列表、重练取题、移除、清空。
 */
@Service
public class WrongBookService {

    private static final Logger log = LoggerFactory.getLogger(WrongBookService.class);

    private final WrongQuestionRepository wrongQuestionRepository;
    private final FavoriteRepository favoriteRepository;
    private final PracticeRecordRepository practiceRecordRepository;

    public WrongBookService(WrongQuestionRepository wrongQuestionRepository,
                            FavoriteRepository favoriteRepository,
                            PracticeRecordRepository practiceRecordRepository) {
        this.wrongQuestionRepository = wrongQuestionRepository;
        this.favoriteRepository = favoriteRepository;
        this.practiceRecordRepository = practiceRecordRepository;
    }

    @Transactional(readOnly = true)
    public List<QuestionDtos.ListItem> list(Long subjectId) {
        List<QuestionDtos.ListItem> items = new ArrayList<>(wrongQuestionRepository.findAllWithQuestion(subjectId).stream()
                .map(WrongQuestion::getQuestion)
                .map(q -> new QuestionDtos.ListItem(
                        q.getId(), q.getSubject().getId(), q.getSubject().getName(),
                        q.getChapter() == null ? null : q.getChapter().getId(),
                        q.getChapter() == null ? null : q.getChapter().getName(),
                        q.getType(), q.getStem(), q.getOptions(),
                        q.getDifficulty(), q.getSource(), true,
                        favoriteRepository.existsByQuestionId(q.getId()),
                        false, 0))
                .toList());
        fillPracticeCounts(items);
        return items;
    }

    private void fillPracticeCounts(List<QuestionDtos.ListItem> items) {
        if (items.isEmpty()) {
            return;
        }
        List<Long> ids = items.stream().map(QuestionDtos.ListItem::id).toList();
        Map<Long, Long> counts = new HashMap<>();
        for (Object[] row : practiceRecordRepository.countByQuestionIdIn(ids)) {
            counts.put(((Number) row[0]).longValue(), ((Number) row[1]).longValue());
        }
        for (int i = 0; i < items.size(); i++) {
            QuestionDtos.ListItem item = items.get(i);
            long count = counts.getOrDefault(item.id(), 0L);
            if (count > 0) {
                items.set(i, new QuestionDtos.ListItem(
                        item.id(), item.subjectId(), item.subjectName(), item.chapterId(), item.chapterName(),
                        item.type(), item.stem(), item.options(), item.difficulty(), item.source(),
                        item.wrong(), item.favorite(), true, count));
            }
        }
    }

    @Transactional
    public void remove(Long questionId) {
        WrongQuestion wrong = wrongQuestionRepository.findByQuestionId(questionId)
                .orElseThrow(() -> new BusinessException("该题目不在错题本中"));
        wrongQuestionRepository.delete(wrong);
        log.info("错题移除: questionId={}", questionId);
    }

    @Transactional
    public int clear(Long subjectId) {
        List<WrongQuestion> all = wrongQuestionRepository.findAllWithQuestion(subjectId);
        wrongQuestionRepository.deleteAll(all);
        log.info("清空错题本: subjectId={}, count={}", subjectId, all.size());
        return all.size();
    }
}
