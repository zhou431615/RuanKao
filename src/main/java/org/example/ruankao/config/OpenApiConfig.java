package org.example.ruankao.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 文档配置，访问 /swagger-ui.html 查看。
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI ruanKaoOpenAPI() {
        return new OpenAPI().info(new Info()
                .title("软考刷题 API")
                .description("软考刷题网页软件后端接口：题库管理、多格式导入、AI 生成、刷题练习、错题本、收藏与统计")
                .version("1.0.0"));
    }
}
