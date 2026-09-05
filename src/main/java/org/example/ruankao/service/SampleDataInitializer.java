package org.example.ruankao.service;

import org.example.ruankao.dto.ImportDtos;
import org.example.ruankao.entity.SampleSeedRecord;
import org.example.ruankao.repository.QuestionRepository;
import org.example.ruankao.repository.SampleSeedRecordRepository;
import org.example.ruankao.service.importer.ImportService;
import org.example.ruankao.service.importer.JsonQuestionParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 启动初始化：首次加载内置示例题库，旧版本升级时补充缺失的内置题（幂等）。
 */
@Component
public class SampleDataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SampleDataInitializer.class);
    private static final String SAMPLE_PATH = "sample/sample-questions.json";
    private static final String SAMPLE_SOURCE = "内置示例";

    private final QuestionRepository questionRepository;
    private final JsonQuestionParser jsonQuestionParser;
    private final ImportService importService;
    private final SampleSeedRecordRepository sampleSeedRecordRepository;

    public SampleDataInitializer(QuestionRepository questionRepository,
                                 JsonQuestionParser jsonQuestionParser,
                                 ImportService importService,
                                 SampleSeedRecordRepository sampleSeedRecordRepository) {
        this.questionRepository = questionRepository;
        this.jsonQuestionParser = jsonQuestionParser;
        this.importService = importService;
        this.sampleSeedRecordRepository = sampleSeedRecordRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        try (InputStream in = new ClassPathResource(SAMPLE_PATH).getInputStream()) {
            List<ImportDtos.JsonQuestion> questions = jsonQuestionParser.parse(in);
            if (questionRepository.count() == 0) {
                ImportDtos.ImportResult result = importService.saveAll("内置示例题库", questions);
                log.info("示例题库加载完成: 成功 {} 题 / 共 {} 题", result.successCount(), result.total());
                saveSeedRecords(questions);
                return;
            }

            // 已有数据时仅补充新版本内置题：不恢复用户主动删除的题目，也不覆盖用户自行维护的题库
            Set<String> existingStems = questionRepository.findStemsBySource(SAMPLE_SOURCE).stream()
                    .map(String::trim)
                    .collect(Collectors.toSet());
            Set<String> seededStems = sampleSeedRecordRepository.findAll().stream()
                    .map(SampleSeedRecord::getStem)
                    .collect(Collectors.toSet());
            List<ImportDtos.JsonQuestion> missing = questions.stream()
                    .filter(q -> !existingStems.contains(q.stem().trim()))
                    .filter(q -> !seededStems.contains(q.stem().trim()))
                    .toList();
            if (missing.isEmpty()) {
                log.info("内置示例题库已完整，跳过示例补充");
                saveSeedRecords(questions);
                return;
            }
            ImportDtos.ImportResult result = importService.saveAll("内置示例题库补充", missing);
            log.info("示例题库补充完成: 成功 {} 题 / 补充 {} 题", result.successCount(), missing.size());
            saveSeedRecords(questions);
        } catch (Exception e) {
            log.error("示例题库加载失败", e);
        }
    }

    private void saveSeedRecords(List<ImportDtos.JsonQuestion> questions) {
        for (ImportDtos.JsonQuestion question : questions) {
            String stem = question.stem().trim();
            if (sampleSeedRecordRepository.findByStem(stem).isEmpty()) {
                SampleSeedRecord record = new SampleSeedRecord();
                record.setStem(stem);
                sampleSeedRecordRepository.save(record);
            }
        }
    }
}
