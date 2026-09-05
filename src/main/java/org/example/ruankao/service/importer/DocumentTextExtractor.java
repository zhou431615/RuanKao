package org.example.ruankao.service.importer;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.example.ruankao.common.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;

/**
 * 文档文本提取器：PDF（PDFBox）与 Word .doc/.docx（POI）提取纯文本，供 AI 结构化解析。
 */
@Component
public class DocumentTextExtractor {

    private static final Logger log = LoggerFactory.getLogger(DocumentTextExtractor.class);

    public String extract(String fileName, byte[] bytes) {
        String lower = fileName == null ? "" : fileName.toLowerCase();
        if (lower.endsWith(".pdf")) {
            return extractPdf(bytes);
        }
        if (lower.endsWith(".docx")) {
            return extractDocx(bytes);
        }
        if (lower.endsWith(".doc")) {
            return extractLegacyDoc(bytes);
        }
        throw new BusinessException("不支持的文件类型: " + fileName + "（支持 PDF / DOC / DOCX / XLSX / JSON）");
    }

    private String extractPdf(byte[] bytes) {
        try (PDDocument document = Loader.loadPDF(bytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            String text = stripper.getText(document);
            log.info("PDF 文本提取完成: {} 字符", text.length());
            return text;
        } catch (IOException e) {
            throw new BusinessException("PDF 解析失败: " + e.getMessage());
        }
    }

    private String extractDocx(byte[] bytes) {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(bytes));
             XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            String text = extractor.getText();
            log.info("Word 文本提取完成: {} 字符", text.length());
            return text;
        } catch (Exception e) {
            throw new BusinessException("Word 解析失败: " + e.getMessage());
        }
    }

    private String extractLegacyDoc(byte[] bytes) {
        try (WordExtractor extractor = new WordExtractor(new ByteArrayInputStream(bytes))) {
            String text = extractor.getText();
            log.info("旧版 Word 文本提取完成: {} 字符", text.length());
            return text;
        } catch (Exception e) {
            throw new BusinessException("旧版 Word(.doc) 解析失败: " + e.getMessage());
        }
    }
}
