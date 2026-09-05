package org.example.ruankao.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI 服务配置（OpenAI 兼容协议，如 DeepSeek）。
 *
 */
@ConfigurationProperties(prefix = "ai")
@Getter
@Setter
public class AiProperties {

    private String baseUrl = "https://api.deepseek.com";
    private String apiKey = "";
    private String model = "deepseek-chat";
    private Double temperature = 1.0;
    private int timeoutSeconds = 180;

    public boolean isConfigured() {
        if (baseUrl == null || baseUrl.isBlank() || model == null || model.isBlank()) {
            return false;
        }
        if (apiKey != null && !apiKey.isBlank()) {
            return true;
        }
        String url = baseUrl.toLowerCase();
        return url.contains("localhost") || url.contains("127.0.0.1");
    }
}
