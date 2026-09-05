package org.example.ruankao.service.importer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.ruankao.common.BusinessException;
import org.example.ruankao.dto.ImportDtos;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * JSON 题库解析器（与导出格式一致，见 ImportDtos.JsonQuestion）。
 */
@Component
public class JsonQuestionParser {

    private final ObjectMapper objectMapper;

    public JsonQuestionParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<ImportDtos.JsonQuestion> parse(InputStream inputStream) {
        try {
            return objectMapper.readValue(inputStream, new TypeReference<>() {
            });
        } catch (IOException e) {
            throw new BusinessException("JSON 解析失败: " + e.getMessage());
        }
    }
}
