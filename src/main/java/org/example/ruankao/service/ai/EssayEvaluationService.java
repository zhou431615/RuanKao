package org.example.ruankao.service.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.example.ruankao.common.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 主观题/综述题 AI 阅卷：按参考答案给出 0-100 分与中文评语。
 */
@Service
public class EssayEvaluationService {

    private static final Logger log = LoggerFactory.getLogger(EssayEvaluationService.class);

    private static final String SYSTEM_PROMPT = """
            你是软考（计算机技术与软件专业技术资格考试）主观题阅卷专家。
            请根据题干、参考答案和评分要点评估考生作答，只输出一个 JSON 对象，不要输出其他文字：
            {"score":0到100的整数,"feedback":"简洁的中文评语"}
            评分要求：
            1. 从准确性、完整性、逻辑清晰度和专业术语使用四个方面综合评分。
            2. 60 分及以上表示基本掌握，90 分及以上表示优秀。
            3. 未作答或严重跑题给 0 分。
            4. feedback 要指出得分点、明显遗漏和下一步复习建议。
            """;

    private final AiClientService aiClientService;

    public EssayEvaluationService(AiClientService aiClientService) {
        this.aiClientService = aiClientService;
    }

    public boolean isAvailable() {
        return aiClientService.isConfigured();
    }

    public Evaluation evaluate(String stem, String referenceAnswer, String userAnswer) {
        String userPrompt = """
                题干：
                %s

                参考答案：
                %s

                考生作答：
                %s
                """.formatted(stem, referenceAnswer, userAnswer);
        String content = aiClientService.chat(SYSTEM_PROMPT, userPrompt);
        Evaluation evaluation = aiClientService.parseJson(content, Evaluation.class);
        int score = evaluation.score() == null ? 0 : evaluation.score();
        score = Math.max(0, Math.min(100, score));
        if (evaluation.feedback() == null || evaluation.feedback().isBlank()) {
            throw new BusinessException("AI 未返回有效评语");
        }
        log.info("主观题 AI 评分: score={}", score);
        return new Evaluation(score, evaluation.feedback().trim());
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Evaluation(Integer score, String feedback) {
    }
}
