package org.example.ruankao.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.example.ruankao.common.ApiResponse;
import org.example.ruankao.common.BusinessException;
import org.example.ruankao.dto.ImportDtos;
import org.example.ruankao.service.importer.DocumentImportService;
import org.example.ruankao.service.importer.ExcelQuestionParser;
import org.example.ruankao.service.importer.ExportService;
import org.example.ruankao.service.importer.ImportService;
import org.example.ruankao.service.importer.JsonQuestionParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Tag(name = "题库导入导出", description = "Excel/JSON/PDF/Word 导入、模板下载、JSON 导出")
@RestController
@RequestMapping("/api/import")
public class ImportController {

    private static final Logger log = LoggerFactory.getLogger(ImportController.class);

    private final ImportService importService;
    private final ExcelQuestionParser excelQuestionParser;
    private final JsonQuestionParser jsonQuestionParser;
    private final DocumentImportService documentImportService;
    private final ExportService exportService;

    public ImportController(ImportService importService,
                            ExcelQuestionParser excelQuestionParser,
                            JsonQuestionParser jsonQuestionParser,
                            DocumentImportService documentImportService,
                            ExportService exportService) {
        this.importService = importService;
        this.excelQuestionParser = excelQuestionParser;
        this.jsonQuestionParser = jsonQuestionParser;
        this.documentImportService = documentImportService;
        this.exportService = exportService;
    }

    @Operation(summary = "上传导入（支持 .xlsx / .json / .pdf / .doc / .docx；PDF 与 Word 需 AI）")
    @PostMapping("/upload")
    public ApiResponse<ImportDtos.ImportResult> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String subject,
            @RequestParam(required = false) String chapter) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("请选择要导入的文件");
        }
        String fileName = file.getOriginalFilename();
        log.info("导入文件: name={}, size={}", fileName, file.getSize());
        try {
            ImportDtos.ImportResult result = doImport(fileName, file.getInputStream(), subject, chapter);
            return ApiResponse.ok("导入完成", result);
        } catch (IOException e) {
            throw new BusinessException("读取上传文件失败: " + e.getMessage());
        }
    }

    private ImportDtos.ImportResult doImport(String fileName, InputStream inputStream,
                                             String subject, String chapter) throws IOException {
        String lower = fileName == null ? "" : fileName.toLowerCase();
        if (lower.endsWith(".xlsx")) {
            return importService.saveAll(fileName, excelQuestionParser.parse(inputStream));
        }
        if (lower.endsWith(".json")) {
            return importService.saveAll(fileName, jsonQuestionParser.parse(inputStream));
        }
        if (lower.endsWith(".pdf") || lower.endsWith(".doc") || lower.endsWith(".docx")) {
            byte[] bytes = inputStream.readAllBytes();
            return documentImportService.importDocument(fileName, bytes, subject, chapter);
        }
        throw new BusinessException("不支持的文件格式: " + fileName + "（支持 .xlsx / .json / .pdf / .doc / .docx）");
    }

    @Operation(summary = "下载 Excel 导入模板")
    @GetMapping("/template")
    public ResponseEntity<byte[]> template() {
        byte[] bytes = excelQuestionParser.buildTemplate();
        String encodedName = URLEncoder.encode("题库导入模板.xlsx", StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encodedName)
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(bytes);
    }

    @Operation(summary = "导出题库为 JSON（subjectId 为空导出全部）")
    @GetMapping("/export")
    public ResponseEntity<byte[]> export(@RequestParam(required = false) Long subjectId) {
        byte[] bytes = exportService.exportJson(subjectId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=ruankao-questions.json")
                .contentType(MediaType.APPLICATION_JSON)
                .body(bytes);
    }
}
