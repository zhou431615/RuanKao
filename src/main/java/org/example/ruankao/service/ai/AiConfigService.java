package org.example.ruankao.service.ai;

import org.example.ruankao.config.AiProperties;
import org.example.ruankao.dto.AiDtos;
import org.example.ruankao.entity.AiSetting;
import org.example.ruankao.repository.AiSettingRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * AI 配置：默认读取 application.yml，用户保存后持久化到 H2 并立即生效。
 */
@Service
public class AiConfigService implements ApplicationRunner {

    private final AiProperties properties;
    private final AiSettingRepository settingRepository;

    public AiConfigService(AiProperties properties, AiSettingRepository settingRepository) {
        this.properties = properties;
        this.settingRepository = settingRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        loadPersisted();
    }

    @Transactional
    public void loadPersisted() {
        settingRepository.findTopByOrderByIdAsc().ifPresent(this::applyToProperties);
    }

    public AiDtos.StatusResponse status() {
        return new AiDtos.StatusResponse(properties.isConfigured(), properties.getBaseUrl(),
                properties.getModel(), properties.getTemperature(), properties.getTimeoutSeconds());
    }

    @Transactional
    public AiDtos.StatusResponse save(AiDtos.ConfigRequest request) {
        AiSetting setting = settingRepository.findTopByOrderByIdAsc().orElseGet(AiSetting::new);
        setting.setBaseUrl(request.baseUrl().trim());
        setting.setModel(request.model().trim());
        setting.setTemperature(request.temperature() == null
                ? properties.getTemperature() : request.temperature());
        setting.setTimeoutSeconds(request.timeoutSeconds() == null
                ? properties.getTimeoutSeconds() : request.timeoutSeconds());

        if (request.apiKey() != null && !request.apiKey().isBlank()) {
            setting.setApiKey(request.apiKey().trim());
        } else if (Boolean.TRUE.equals(request.clearApiKey())) {
            setting.setApiKey("");
        } else if (setting.getApiKey() == null) {
            setting.setApiKey(properties.getApiKey());
        }
        settingRepository.save(setting);
        applyToProperties(setting);
        return status();
    }

    private void applyToProperties(AiSetting setting) {
        properties.setBaseUrl(setting.getBaseUrl());
        properties.setApiKey(setting.getApiKey());
        properties.setModel(setting.getModel());
        properties.setTemperature(setting.getTemperature());
        properties.setTimeoutSeconds(setting.getTimeoutSeconds());
    }
}
