package org.example.ruankao.common;

/**
 * 题型枚举。
 */
public enum QuestionType {
    /** 单选题 */
    SINGLE,
    /** 多选题（全部选对方得分） */
    MULTIPLE,
    /** 判断题 */
    JUDGE,
    /** 问答题（主观，自评后查看参考答案） */
    ESSAY
}
