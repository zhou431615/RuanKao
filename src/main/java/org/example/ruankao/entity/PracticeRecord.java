package org.example.ruankao.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.example.ruankao.common.QuestionType;

import java.time.LocalDateTime;

/**
 * 一次答题记录：刷题时每提交一题写一条。
 */
@Entity
@Table(name = "practice_records", indexes = {
        @Index(name = "idx_record_question", columnList = "question_id"),
        @Index(name = "idx_record_answered_at", columnList = "answeredAt")
})
@Getter
@Setter
public class PracticeRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_id", nullable = false)
    private Question question;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private QuestionType type;

    /** 用户本次提交的作答内容（与 Question.answer 同格式） */
    @Lob
    @Column(name = "user_answer", nullable = false)
    private String userAnswer;

    @Column(nullable = false)
    private boolean correct;

    /** 主观题自评得分（0-100），客观题为空 */
    @Column(name = "self_score")
    private Integer selfScore;

    @Column(name = "answered_at", nullable = false, updatable = false)
    private LocalDateTime answeredAt;

    @PrePersist
    void onCreate() {
        this.answeredAt = LocalDateTime.now();
    }
}
