package org.example.ruankao.service;

import org.example.ruankao.common.BusinessException;
import org.example.ruankao.dto.QuestionDtos;
import org.example.ruankao.entity.Favorite;
import org.example.ruankao.entity.Question;
import org.example.ruankao.repository.FavoriteRepository;
import org.example.ruankao.repository.QuestionRepository;
import org.example.ruankao.repository.WrongQuestionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 收藏夹：收藏、取消、列表。
 */
@Service
public class FavoriteService {

    private static final Logger log = LoggerFactory.getLogger(FavoriteService.class);

    private final FavoriteRepository favoriteRepository;
    private final QuestionRepository questionRepository;
    private final WrongQuestionRepository wrongQuestionRepository;

    public FavoriteService(FavoriteRepository favoriteRepository,
                           QuestionRepository questionRepository,
                           WrongQuestionRepository wrongQuestionRepository) {
        this.favoriteRepository = favoriteRepository;
        this.questionRepository = questionRepository;
        this.wrongQuestionRepository = wrongQuestionRepository;
    }

    @Transactional
    public void add(Long questionId) {
        Question question = questionRepository.findById(questionId)
                .orElseThrow(() -> new BusinessException("题目不存在: id=" + questionId));
        if (favoriteRepository.existsByQuestionId(questionId)) {
            return; // 幂等
        }
        Favorite favorite = new Favorite();
        favorite.setQuestion(question);
        favoriteRepository.save(favorite);
        log.info("收藏题目: questionId={}", questionId);
    }

    @Transactional
    public void remove(Long questionId) {
        Favorite favorite = favoriteRepository.findByQuestionId(questionId)
                .orElseThrow(() -> new BusinessException("该题目未收藏"));
        favoriteRepository.delete(favorite);
        log.info("取消收藏: questionId={}", questionId);
    }

    @Transactional(readOnly = true)
    public List<QuestionDtos.ListItem> list(Long subjectId) {
        return favoriteRepository.findAllWithQuestion(subjectId).stream()
                .map(Favorite::getQuestion)
                .map(q -> new QuestionDtos.ListItem(
                        q.getId(), q.getSubject().getId(), q.getSubject().getName(),
                        q.getChapter() == null ? null : q.getChapter().getId(),
                        q.getChapter() == null ? null : q.getChapter().getName(),
                        q.getType(), q.getStem(), q.getOptions(),
                        q.getDifficulty(), q.getSource(),
                        wrongQuestionRepository.existsByQuestionId(q.getId()), true))
                .toList();
    }
}
