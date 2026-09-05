package org.example.ruankao.service.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.example.ruankao.config.AiProperties;

import java.util.List;

/**
 * OpenAI 兼容 Chat Completions 协议的最小 DTO 集合。
 */
public final class ChatDtos {

    private ChatDtos() {
    }

    public record ChatRequest(String model, List<Message> messages, Double temperature,
                              @com.fasterxml.jackson.annotation.JsonProperty("max_tokens") Integer maxTokens) {
    }

    public record Message(String role, String content) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ChatResponse(List<Choice> choices, Usage usage) {
        public String firstContent() {
            if (choices == null || choices.isEmpty() || choices.get(0).message() == null) {
                return "";
            }
            return choices.get(0).message().content();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Choice(Integer index, Message message) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Usage(Integer promptTokens, Integer completionTokens, Integer totalTokens) {
    }

    /** 构造请求的便捷方法 */
    public static ChatRequest of(AiProperties props, String systemPrompt, String userPrompt) {
        return new ChatRequest(props.getModel(),
                List.of(new Message("system", systemPrompt), new Message("user", userPrompt)),
                props.getTemperature(), 4096);
    }
}
