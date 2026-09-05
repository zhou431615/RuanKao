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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.example.ruankao.common.QuestionType;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * 题目。选项以 JSON 存储（key -> content），答案统一存文本：
 * 单选如 "A"；多选如 "ABD"；判断为 "TRUE"/"FALSE"；问答为参考答案全文。
 */
@Entity
@Table(name = "questions", indexes = {
        @Index(name = "idx_question_subject", columnList = "subject_id"),
        @Index(name = "idx_question_chapter", columnList = "chapter_id"),
        @Index(name = "idx_question_type", columnList = "type")
})
@Getter
@Setter
public class Question {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "subject_id", nullable = false)
    private Subject subject;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chapter_id")
    private Chapter chapter;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private QuestionType type;

    @Column(name = "stem", nullable = false, length = 4000)
    private String stem;

    /** 选项 JSON：A/B/C/D -> 选项内容；判断/问答为空 */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "options_json", columnDefinition = "json")
    private Map<String, String> options = new LinkedHashMap<>();

    /** 标准答案：单选 "A"；多选 "ABD"；判断 "TRUE"/"FALSE"；问答为参考答案文本 */
    @Lob
    @Column(nullable = false)
    private String answer;

    @Lob
    @Column
    private String analysis;

    /** 难度 1-5，默认 3 */
    @Column(nullable = false)
    private Integer difficulty = 3;

    @Column(length = 200)
    private String source;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * H2 的 JSON 类型反序列化可能返回无序 HashMap，这里统一按选项键排序，
     * 保证页面展示、导出和键盘快捷键都使用 A/B/C/D 顺序。
     */
    public Map<String, String> getOptions() {
        if (options == null) {
            return new LinkedHashMap<>();
        }
        return new LinkedHashMap<>(new TreeMap<>(options));
    }

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
