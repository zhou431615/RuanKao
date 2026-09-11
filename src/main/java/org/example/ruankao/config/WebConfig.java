package org.example.ruankao.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置：CORS 由 SecurityConfig.corsConfigurationSource() 统一管理。
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {
}