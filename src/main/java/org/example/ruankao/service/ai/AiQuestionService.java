package org.example.ruankao.service.ai;

import com.fasterxml.jackson.core.type.TypeReference;
import org.example.ruankao.common.BusinessException;
import org.example.ruankao.common.QuestionType;
import org.example.ruankao.dto.AiDtos;
import org.example.ruankao.dto.ImportDtos;
import org.example.ruankao.entity.Chapter;
import org.example.ruankao.entity.Subject;
import org.example.ruankao.service.SubjectService;
import org.example.ruankao.service.importer.ImportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * AI 题目服务：联网生成新题（预览后入库）、解析文档文本为题目。
 */
@Service
public class AiQuestionService {

    private static final Logger log = LoggerFactory.getLogger(AiQuestionService.class);

    private static final Function<QuestionType, String> TYPE_NAME = Map.of(
            QuestionType.SINGLE, "单选题", QuestionType.MULTIPLE, "多选题",
            QuestionType.JUDGE, "判断题", QuestionType.ESSAY, "问答题")::get;

    private static final String GENERATE_SYSTEM_PROMPT = """
            你是软考（计算机技术与软件专业技术资格考试）命题专家，精通软件设计师、系统集成项目管理工程师、网络工程师等科目的考纲与真题风格。
            请严格按用户要求出题，并只输出一个 JSON 数组，不要输出任何其他文字。
            数组元素结构：
            {"type":"SINGLE|MULTIPLE|JUDGE|ESSAY","stem":"题干","options":[{"key":"A","content":"..."},{"key":"B","content":"..."}],"answer":"答案","analysis":"解析","difficulty":1到5的整数}
            规则：
            1. SINGLE（单选）：4 个选项 A-D，answer 为单个字母如 "B"。
            2. MULTIPLE（多选）：4-5 个选项，answer 为多个字母如 "ABD"。
            3. JUDGE（判断）：options 为空数组，answer 为 "TRUE"（正确）或 "FALSE"（错误）。
            4. ESSAY（问答）：options 为空数组，answer 为详细参考答案文本。
            5. 题目须符合软考真题难度与表述风格，解析须讲清考点。
            """;

    private static final String PARSE_SYSTEM_PROMPT = """
            你是题库结构化专家。用户会提供从 PDF/Word 提取的题目文本，请将其解析为结构化题目，只输出一个 JSON 数组，不要输出任何其他文字。
            数组元素结构：
            {"type":"SINGLE|MULTIPLE|JUDGE|ESSAY","stem":"题干","options":[{"key":"A","content":"..."}],"answer":"答案","analysis":"解析","difficulty":3}
            规则：
            1. 尽力识别题号、题干、选项（A. B. C. D. 或 A、B、C、D 等）、答案标记（如"答案：B"、"【答案】B"）、解析（如"解析："）。
            2. 无法确定答案的题目，根据专业知识给出最可能的答案并在解析中说明。
            3. 判断题 answer 用 "TRUE"/"FALSE"；问答题 answer 为参考答案文本，options 为空数组。
            4. 损坏严重无法还原的题目直接跳过，不要编造题干。
            """;

    private final AiClientService aiClientService;
    private final AiConfigService aiConfigService;
    private final SubjectService subjectService;
    private final ImportService importService;

    public AiQuestionService(AiClientService aiClientService, AiConfigService aiConfigService,
                             SubjectService subjectService, ImportService importService) {
        this.aiClientService = aiClientService;
        this.aiConfigService = aiConfigService;
        this.subjectService = subjectService;
        this.importService = importService;
    }

    public AiDtos.StatusResponse status() {
        return aiConfigService.status();
    }

    public AiDtos.StatusResponse updateConfig(AiDtos.ConfigRequest request) {
        return aiConfigService.save(request);
    }

    public AiDtos.TestResponse testConnection() {
        String reply = aiClientService.chat(
                "你是 AI 连接测试助手，请严格只回复一个单词：ok。",
                "请回复 ok");
        return new AiDtos.TestResponse(reply, aiConfigService.status().model());
    }

    /** 生成题目（返回预览，不入库） */
    public AiDtos.GenerateResponse generate(AiDtos.GenerateRequest request) {
        Subject subject = subjectService.ensureSubjectExists(request.subjectId());
        Chapter chapter = request.chapterId() == null ? null : subjectService.chapterOf(request.chapterId());
        int count = request.count() == null ? 5 : request.count();
        String subjectName = subject.getName();
        String chapterName = chapter == null ? "综合" : chapter.getName();

        String userPrompt = """
                请出 %d 道%s，科目：%s，章节/知识点：%s，具体考点：%s。%s
                """.formatted(count, TYPE_NAME.apply(request.type()), subjectName, chapterName,
                request.topic(),
                request.extraRequirement() == null || request.extraRequirement().isBlank()
                        ? "" : "补充要求：" + request.extraRequirement());

        String content = aiClientService.chat(GENERATE_SYSTEM_PROMPT, userPrompt);
        List<AiDtos.GeneratedQuestion> questions = aiClientService.parseJson(
                content, new TypeReference<List<AiDtos.GeneratedQuestion>>() {
                });
        log.info("AI 生成题目: subject={}, count={}", subjectName, questions.size());
        return new AiDtos.GenerateResponse(request.subjectId(), request.chapterId(), questions);
    }

    /** 确认入库：将预览题目保存到题库 */
    public ImportDtos.ImportResult confirm(AiDtos.ConfirmRequest request) {
        Subject subject = subjectService.ensureSubjectExists(request.subjectId());
        Chapter chapter = request.chapterId() == null ? null : subjectService.chapterOf(request.chapterId());
        List<ImportDtos.JsonQuestion> items = new ArrayList<>();
        for (AiDtos.GeneratedQuestion q : request.questions()) {
            items.add(new ImportDtos.JsonQuestion(subject.getName(),
                    chapter == null ? null : chapter.getName(),
                    q.type(), q.stem(), q.options(), q.answer(), q.analysis(),
                    q.difficulty(), request.source() == null ? "AI 生成" : request.source(), null));
        }
        return importService.saveAll("AI 生成", items);
    }

    /** 解析文档文本为题目（供文档导入编排调用） */
    public List<ImportDtos.JsonQuestion> parseDocumentText(String text, String subject, String chapter) {
        String userPrompt = "目标科目：" + (subject == null ? "自动判断" : subject)
                + "；目标章节：" + (chapter == null || chapter.isBlank() ? "自动判断" : chapter)
                + "\n\n以下是需要解析的文本：\n" + text;
        String content = aiClientService.chat(PARSE_SYSTEM_PROMPT, userPrompt);
        List<ImportDtos.JsonQuestion> questions = aiClientService.parseJson(
                content, new TypeReference<List<ImportDtos.JsonQuestion>>() {
                });
        // 补充科目/章节信息
        List<ImportDtos.JsonQuestion> normalized = new ArrayList<>();
        for (ImportDtos.JsonQuestion q : questions) {
            normalized.add(new ImportDtos.JsonQuestion(
                    q.subject() == null || q.subject().isBlank() ? subject : q.subject(),
                    q.chapter() == null || q.chapter().isBlank() ? chapter : q.chapter(),
                    q.type(), q.stem(), q.options(), q.answer(), q.analysis(),
                    q.difficulty(), q.source() == null ? "文档导入" : q.source(), q.wrong()));
        }
        log.info("AI 解析文档: 解析出 {} 道题", normalized.size());
        return normalized;
    }

    /** 校验 AI 是否可用，不可用抛出带指引的异常 */
    public void requireConfigured() {
        if (!aiClientService.isConfigured()) {
            throw new BusinessException("AI 功能未启用：请在“AI 智能出题 - AI 设置”中完成配置");
        }
    }
}
