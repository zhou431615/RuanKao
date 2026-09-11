package org.example.ruankao.repository;

import org.example.ruankao.entity.Favorite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface FavoriteRepository extends JpaRepository<Favorite, Long> {

    Optional<Favorite> findByQuestionId(Long questionId);

    @Query("select f from Favorite f join fetch f.question q " +
            "where (:subjectId is null or q.subject.id = :subjectId) " +
            "order by f.createdAt desc")
    List<Favorite> findAllWithQuestion(@Param("subjectId") Long subjectId);

    boolean existsByQuestionId(Long questionId);

    @Query("select f.question.id from Favorite f where f.question.id in :ids")
    Set<Long> findQuestionIdsByIdIn(@Param("ids") Collection<Long> ids);
}