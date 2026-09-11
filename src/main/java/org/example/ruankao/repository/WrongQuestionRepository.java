package org.example.ruankao.repository;

import org.example.ruankao.entity.WrongQuestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface WrongQuestionRepository extends JpaRepository<WrongQuestion, Long> {

    Optional<WrongQuestion> findByQuestionId(Long questionId);

    @Query("select w from WrongQuestion w join fetch w.question q " +
            "where (:subjectId is null or q.subject.id = :subjectId) " +
            "order by w.updatedAt desc")
    List<WrongQuestion> findAllWithQuestion(@Param("subjectId") Long subjectId);

    boolean existsByQuestionId(Long questionId);

    @Query("select w.question.id from WrongQuestion w where w.question.id in :ids")
    Set<Long> findQuestionIdsByIdIn(@Param("ids") Collection<Long> ids);
}