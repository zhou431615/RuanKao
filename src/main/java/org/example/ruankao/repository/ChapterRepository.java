package org.example.ruankao.repository;

import org.example.ruankao.entity.Chapter;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ChapterRepository extends JpaRepository<Chapter, Long> {

    List<Chapter> findBySubjectIdOrderBySortOrderAscIdAsc(Long subjectId);

    Optional<Chapter> findBySubjectIdAndName(Long subjectId, String name);

    long countBySubjectId(Long subjectId);
}
