package org.example.ruankao.service.importer;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.example.ruankao.dto.ImportDtos;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Excel(.xlsx) 题库解析器。
 *
 * <p>模板列（首行为表头）：科目 | 章节 | 题型 | 题干 | 选项A | 选项B | 选项C | 选项D | 选项E | 答案 | 解析 | 难度 | 来源 | 错题标记
 * <p>题型支持：单选/多选/判断/问答；判断题答案填 对/错 或 TRUE/FALSE；问答题选项列留空。
 */
@Component
public class ExcelQuestionParser {

    private static final String[] HEADERS = {"科目", "章节", "题型", "题干", "选项A", "选项B",
            "选项C", "选项D", "选项E", "答案", "解析", "难度", "来源", "错题标记"};
    private static final int COLUMN_COUNT = HEADERS.length;

    private final DataFormatter formatter = new DataFormatter();

    public List<ImportDtos.JsonQuestion> parse(InputStream inputStream) throws IOException {
        List<ImportDtos.JsonQuestion> result = new ArrayList<>();
        try (Workbook workbook = new XSSFWorkbook(inputStream)) {
            Sheet sheet = workbook.getSheetAt(0);
            int firstRow = sheet.getFirstRowNum();
            for (int i = firstRow + 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null || isBlankRow(row)) {
                    continue;
                }
                result.add(toQuestion(row));
            }
        }
        return result;
    }

    private ImportDtos.JsonQuestion toQuestion(Row row) {
        String[] cells = new String[COLUMN_COUNT];
        for (int c = 0; c < COLUMN_COUNT; c++) {
            cells[c] = cellValue(row, c);
        }
        List<ImportDtos.OptionItem> options = new ArrayList<>();
        char key = 'A';
        for (int c = 4; c <= 8; c++) {
            if (!cells[c].isBlank()) {
                options.add(new ImportDtos.OptionItem(String.valueOf(key), cells[c]));
            }
            key++;
        }
        Integer difficulty = null;
        if (!cells[11].isBlank()) {
            try {
                difficulty = (int) Double.parseDouble(cells[11].trim());
            } catch (NumberFormatException ignored) {
                // 难度列非法时使用默认值
            }
        }
        return new ImportDtos.JsonQuestion(cells[0], cells[1], cells[2], cells[3],
                options, cells[9], cells[10], difficulty, cells[12], parseWrongFlag(cells[13]));
    }

    private Boolean parseWrongFlag(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase();
        if (normalized.isBlank()) {
            return null;
        }
        return switch (normalized) {
            case "TRUE", "T", "Y", "YES", "1", "是", "对", "错题" -> true;
            case "FALSE", "F", "N", "NO", "0", "否", "正常" -> false;
            default -> null;
        };
    }

    private String cellValue(Row row, int index) {
        Cell cell = row.getCell(index);
        if (cell == null) {
            return "";
        }
        if (cell.getCellType() == CellType.FORMULA) {
            try {
                return formatter.formatCellValue(cell).trim();
            } catch (Exception e) {
                return "";
            }
        }
        return formatter.formatCellValue(cell).trim();
    }

    private boolean isBlankRow(Row row) {
        for (int c = 0; c < COLUMN_COUNT; c++) {
            if (!cellValue(row, c).isBlank()) {
                return false;
            }
        }
        return true;
    }

    /** 生成 Excel 模板（含表头与示例行） */
    public byte[] buildTemplate() {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("题库模板");
            Row header = sheet.createRow(0);
            for (int i = 0; i < HEADERS.length; i++) {
                header.createCell(i).setCellValue(HEADERS[i]);
            }
            Row sample1 = sheet.createRow(1);
            sample1.createCell(0).setCellValue("软件设计师");
            sample1.createCell(1).setCellValue("计算机组成与体系结构");
            sample1.createCell(2).setCellValue("单选");
            sample1.createCell(3).setCellValue("（示例）CPU 中负责算术与逻辑运算的部件是？");
            sample1.createCell(4).setCellValue("控制器");
            sample1.createCell(5).setCellValue("运算器");
            sample1.createCell(6).setCellValue("寄存器");
            sample1.createCell(7).setCellValue("存储器");
            sample1.createCell(9).setCellValue("B");
            sample1.createCell(10).setCellValue("ALU（算术逻辑单元）属于运算器的一部分，负责算术与逻辑运算。");
            sample1.createCell(11).setCellValue(2);
            sample1.createCell(12).setCellValue("示例");
            sample1.createCell(13).setCellValue("否");

            Row sample2 = sheet.createRow(2);
            sample2.createCell(0).setCellValue("软件设计师");
            sample2.createCell(1).setCellValue("计算机组成与体系结构");
            sample2.createCell(2).setCellValue("判断");
            sample2.createCell(3).setCellValue("（示例）Cache 的存在对程序员是透明的。");
            sample2.createCell(9).setCellValue("对");
            sample2.createCell(10).setCellValue("Cache 由硬件自动管理，对程序员透明。");
            sample2.createCell(11).setCellValue(1);
            sample2.createCell(13).setCellValue("否");

            Row sample3 = sheet.createRow(3);
            sample3.createCell(0).setCellValue("软件设计师");
            sample3.createCell(1).setCellValue("程序设计语言");
            sample3.createCell(2).setCellValue("问答");
            sample3.createCell(3).setCellValue("（示例）简述编译型语言与解释型语言的区别。");
            sample3.createCell(9).setCellValue("编译型语言在执行前将源代码整体编译为目标代码；解释型语言由解释器逐句解释执行，不生成独立目标程序。");
            sample3.createCell(11).setCellValue(3);
            sample3.createCell(13).setCellValue("否");

            for (int i = 0; i < HEADERS.length; i++) {
                sheet.setColumnWidth(i, i == 3 || i == 10 ? 9000 : 3600);
            }
            try (var out = new java.io.ByteArrayOutputStream()) {
                workbook.write(out);
                return out.toByteArray();
            }
        } catch (IOException e) {
            throw new IllegalStateException("生成 Excel 模板失败", e);
        }
    }
}
