package org.example.ruankao.service.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import org.example.ruankao.common.BusinessException;
import org.example.ruankao.config.AiProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * OpenAI 兼容 Chat Completions 客户端（适配 DeepSeek 等服务）。
 */
@Service
public class AiClientService {

    private static final Logger log = LoggerFactory.getLogger(AiClientService.class);

    private final AiProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient.Builder restClientBuilder;

    public AiClientService(AiProperties properties, ObjectMapper objectMapper,
                           RestClient.Builder restClientBuilder) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.restClientBuilder = restClientBuilder;
    }

    public boolean isConfigured() {
        return properties.isConfigured();
    }

    /**
     * 发送 Chat 请求并返回首条回复文本。
     */
    public String chat(String systemPrompt, String userPrompt) {
        if (!isConfigured()) {
            throw new BusinessException(
                    "AI 功能未启用：请在“AI 智能出题 - AI 设置”中填写 API Key，或配置本地 OpenAI 兼容服务");
        }
        String url = normalizeBaseUrl(properties.getBaseUrl()) + "/chat/completions";
        ChatDtos.ChatRequest request = ChatDtos.of(properties, systemPrompt, userPrompt);

        RestClient.Builder clientBuilder = restClientBuilder.clone()
                .baseUrl(url)
                .requestFactory(clientFactory());
        if (StringUtils.hasText(properties.getApiKey())) {
            clientBuilder.defaultHeader("Authorization", "Bearer " + properties.getApiKey());
        }
        RestClient client = clientBuilder.build();

        try {
            log.info("调用 AI: model={}, url={}, promptLength={}", properties.getModel(), url, userPrompt.length());
            ChatDtos.ChatResponse response = client.post()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(ChatDtos.ChatResponse.class);
            String content = response == null ? "" : response.firstContent();
            if (content.isBlank()) {
                throw new BusinessException("AI 返回内容为空，请稍后重试");
            }
            log.info("AI 调用成功: contentLength={}", content.length());
            return content;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("AI 调用失败: {}", e.getMessage(), e);
            throw new BusinessException("AI 调用失败: " + rootMessage(e), e);
        }
    }

    /** 解析 AI 返回的 JSON（容忍 markdown 代码块包裹） */
    public <T> T parseJson(String content, Class<T> type) {
        String json = stripCodeFence(content);
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            throw new BusinessException("AI 返回内容解析失败，请重试: " + e.getMessage());
        }
    }

    /** 解析 AI 返回的 JSON 集合（容忍 markdown 代码块包裹） */
    public <T> T parseJson(String content, TypeReference<T> type) {
        String json = stripCodeFence(content);
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            throw new BusinessException("AI 返回内容解析失败，请重试: " + e.getMessage());
        }
    }

    /** 去掉 markdown 代码块包裹与首尾杂文本 */
    private String stripCodeFence(String content) {
        String trimmed = content.trim();
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            int lastFence = trimmed.lastIndexOf("```");
            if (firstNewline > 0 && lastFence > firstNewline) {
                trimmed = trimmed.substring(firstNewline + 1, lastFence);
            }
        }
        int start = trimmed.indexOf('{');
        int startArray = trimmed.indexOf('[');
        if (startArray >= 0 && (start < 0 || startArray < start)) {
            int end = trimmed.lastIndexOf(']');
            if (end > startArray) {
                return trimmed.substring(startArray, end + 1);
            }
        }
        if (start >= 0) {
            int end = trimmed.lastIndexOf('}');
            if (end > start) {
                return trimmed.substring(start, end + 1);
            }
        }
        return trimmed;
    }

    private String normalizeBaseUrl(String baseUrl) {
        String url = baseUrl == null ? "" : baseUrl.trim();
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        if (url.endsWith("/v1")) {
            return url;
        }
        return url + "/v1";
    }

    private org.springframework.http.client.ClientHttpRequestFactory clientFactory() {
        var factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(15));
        factory.setReadTimeout(Duration.ofSeconds(Math.max(properties.getTimeoutSeconds(), 30)));
        return factory;
    }

    private String rootMessage(Throwable e) {
        Throwable cause = e;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
    }
}
