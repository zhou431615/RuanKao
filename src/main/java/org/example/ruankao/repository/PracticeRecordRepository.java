package org.example.ruankao.repository;

import org.example.ruankao.entity.PracticeRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface PracticeRecordRepository extends JpaRepository<PracticeRecord, Long> {

    Page<PracticeRecord> findByQuestionIdOrderByIdDesc(Long questionId, Pageable pageable);

    boolean existsByQuestionId(Long questionId);

    @Modifying
    @Query("delete from PracticeRecord r where r.question.id = :questionId")
    void deleteByQuestionId(@Param("questionId") Long questionId);

    /** 近 N 天每日答题数与正确数 */
    @Query("select function('date', r.answeredAt), count(r), sum(case when r.correct = true then 1 else 0 end) " +
            "from PracticeRecord r where r.answeredAt >= :since " +
            "group by function('date', r.answeredAt) order by function('date', r.answeredAt)")
    List<Object[]> countDailyStats(@Param("since") LocalDateTime since);

    long countByAnsweredAtAfter(LocalDateTime since);

    long countByCorrectTrueAndAnsweredAtAfter(LocalDateTime since);

    /** 各科目已刷题目数（去重）、累计答题数与正确数 */
    @Query("select q.subject.id, count(distinct r.question.id), count(r), " +
            "sum(case when r.correct = true then 1 else 0 end) " +
            "from PracticeRecord r join Question q on r.question.id = q.id " +
            "group by q.subject.id")
    List<Object[]> countStatsBySubject();
}
