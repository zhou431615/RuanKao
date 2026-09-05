package org.example.ruankao.service.importer;

import org.example.ruankao.common.BusinessException;
import org.example.ruankao.dto.ImportDtos;
import org.example.ruankao.service.ai.AiQuestionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 文档导入编排：提取文本 -> AI 结构化解析 -> 落库。
 */
@Component
public class DocumentImportService {

    private static final Logger log = LoggerFactory.getLogger(DocumentImportService.class);

    /** 单个 AI 请求的文本上限（字符） */
    private static final int MAX_CHUNK_LENGTH = 12000;

    /** 防止超长文档触发过多 AI 请求 */
    private static final int MAX_CHUNKS = 20;

    private final DocumentTextExtractor textExtractor;
    private final AiQuestionService aiQuestionService;
    private final ImportService importService;

    public DocumentImportService(DocumentTextExtractor textExtractor,
                                 AiQuestionService aiQuestionService,
                                 ImportService importService) {
        this.textExtractor = textExtractor;
        this.aiQuestionService = aiQuestionService;
        this.importService = importService;
    }

    public ImportDtos.ImportResult importDocument(String fileName, byte[] bytes,
                                                  String subject, String chapter) {
        String text = textExtractor.extract(fileName, bytes);
        if (text.isBlank()) {
            throw new BusinessException("未能从文档中提取到文本内容");
        }
        aiQuestionService.requireConfigured();

        List<String> chunks = splitText(text);
        List<ImportDtos.JsonQuestion> parsed = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            String chunk = chunks.get(i);
            try {
                parsed.addAll(aiQuestionService.parseDocumentText(chunk, subject, chapter));
            } catch (Exception e) {
                log.warn("文档第 {}/{} 段解析失败: {}", i + 1, chunks.size(), e.getMessage());
            }
        }
        if (parsed.isEmpty()) {
            throw new BusinessException("AI 未能从文档中解析出题目，请确认 AI 已配置并检查文档内容");
        }
        log.info("文档分片解析完成: 文本 {} 字符，共 {} 段，解析出 {} 道题",
                text.length(), chunks.size(), parsed.size());
        return importService.saveAll(fileName, parsed);
    }

    /**
     * 按自然断点拆分长文本。优先在换行或句号附近拆分，避免把一道题裁成两半。
     */
    private List<String> splitText(String text) {
        if (text.length() <= MAX_CHUNK_LENGTH) {
            return List.of(text);
        }
        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < text.length() && chunks.size() < MAX_CHUNKS) {
            int end = Math.min(start + MAX_CHUNK_LENGTH, text.length());
            if (end < text.length()) {
                int newline = text.lastIndexOf('\n', end);
                int sentence = text.lastIndexOf('。', end);
                int breakAt = Math.max(newline, sentence);
                if (breakAt > start + MAX_CHUNK_LENGTH / 2) {
                    end = breakAt + 1;
                }
            }
            chunks.add(text.substring(start, end).trim());
            start = end;
        }
        return chunks;
    }
}
