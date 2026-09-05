package org.example.ruankao.repository;

import org.example.ruankao.common.QuestionType;
import org.example.ruankao.entity.Question;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface QuestionRepository extends JpaRepository<Question, Long>, JpaSpecificationExecutor<Question> {

    long countBySubjectId(Long subjectId);

    long countByChapterId(Long chapterId);

    long countBySubjectIdAndType(Long subjectId, QuestionType type);

    /** 刷题取题：按条件取 ID 列表（配合 Specification 统计总数） */
    @Query("select q.id from Question q where q.subject.id = :subjectId " +
            "and (:chapterId is null or q.chapter.id = :chapterId) " +
            "and (:type is null or q.type = :type)")
    List<Long> findIdsByFilter(@Param("subjectId") Long subjectId,
                               @Param("chapterId") Long chapterId,
                               @Param("type") QuestionType type);

    List<Question> findByIdIn(Collection<Long> ids);

    @Query("select q.stem from Question q where q.source = :source")
    List<String> findStemsBySource(@Param("source") String source);

    @Query("select distinct q.subject.id from Question q")
    List<Long> findDistinctSubjectIds();

    @Query("select count(q) from Question q where q.subject.id = :subjectId and q.chapter.id = :chapterId")
    long countBySubjectIdAndChapterId(@Param("subjectId") Long subjectId, @Param("chapterId") Long chapterId);

    Page<Question> findByStemContainingIgnoreCase(String keyword, Pageable pageable);
}
