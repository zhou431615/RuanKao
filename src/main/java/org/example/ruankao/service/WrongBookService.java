package org.example.ruankao.service;

import org.example.ruankao.common.BusinessException;
import org.example.ruankao.dto.QuestionDtos;
import org.example.ruankao.entity.WrongQuestion;
import org.example.ruankao.repository.FavoriteRepository;
import org.example.ruankao.repository.WrongQuestionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 错题本：列表、重练取题、移除、清空。
 */
@Service
public class WrongBookService {

    private static final Logger log = LoggerFactory.getLogger(WrongBookService.class);

    private final WrongQuestionRepository wrongQuestionRepository;
    private final FavoriteRepository favoriteRepository;

    public WrongBookService(WrongQuestionRepository wrongQuestionRepository,
                            FavoriteRepository favoriteRepository) {
        this.wrongQuestionRepository = wrongQuestionRepository;
        this.favoriteRepository = favoriteRepository;
    }

    @Transactional(readOnly = true)
    public List<QuestionDtos.ListItem> list(Long subjectId) {
        return wrongQuestionRepository.findAllWithQuestion(subjectId).stream()
                .map(WrongQuestion::getQuestion)
                .map(q -> new QuestionDtos.ListItem(
                        q.getId(), q.getSubject().getId(), q.getSubject().getName(),
                        q.getChapter() == null ? null : q.getChapter().getId(),
                        q.getChapter() == null ? null : q.getChapter().getName(),
                        q.getType(), q.getStem(), q.getOptions(),
                        q.getDifficulty(), q.getSource(), true,
                        favoriteRepository.existsByQuestionId(q.getId())))
                .toList();
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
