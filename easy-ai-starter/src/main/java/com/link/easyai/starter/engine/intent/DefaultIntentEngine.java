package com.link.easyai.starter.engine.intent;

import com.fasterxml.jackson.databind.JsonNode;
import com.link.easyai.starter.config.LargeLanguageModelHolder;
import com.link.easyai.starter.engine.AnnotationAiTaskConfigService;
import com.link.easyai.starter.engine.config.AiTaskConfig;
import com.link.easyai.starter.engine.llm.LlmCallException;
import com.link.easyai.starter.engine.llm.LlmClient;
import com.link.easyai.starter.engine.llm.RobustJsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 默认意图识别引擎实现。
 * <p>
 * <b>LLM 优先，关键词降级：</b>
 * <ol>
 *   <li>先调用 LLM 进行意图分类（few-shot prompt，传入所有任务的 name/description/examples）</li>
 *   <li>LLM 返回 taskType + confidence + reason + action</li>
 *   <li>高置信度直接返回；低置信度返回候选列表供澄清</li>
 *   <li>LLM 调用失败/超时时，降级到关键词快速匹配</li>
 * </ol>
 * <p>
 * <b>带上下文识别：</b>有活跃任务时，LLM 同时判断 continue/switch/cancel。
 */
@Component
public class DefaultIntentEngine implements IntentEngine {

    private static final Logger log = LoggerFactory.getLogger(DefaultIntentEngine.class);

    private final AnnotationAiTaskConfigService configService;
    private final LlmClient llmClient;
    private final LargeLanguageModelHolder llmHolder;

    @Autowired
    public DefaultIntentEngine(AnnotationAiTaskConfigService configService,
                                LlmClient llmClient,
                                LargeLanguageModelHolder llmHolder) {
        this.configService = configService;
        this.llmClient = llmClient;
        this.llmHolder = llmHolder;
    }

    @Override
    public IntentResult recognize(String userMessage) {
        if (userMessage == null || userMessage.isBlank()) {
            return IntentResult.fallback("Empty message");
        }

        Map<String, AiTaskConfig> allConfigs = configService.getAnnotationConfigs();
        if (allConfigs.isEmpty()) {
            log.warn("[IntentEngine] no task configs available, returning fallback");
            return IntentResult.fallback("No task configs available");
        }

        // LLM 优先
        try {
            IntentResult result = classifyByLlm(userMessage, allConfigs);
            if (result != null) {
                log.info("[IntentEngine] LLM recognized: taskType={}, confidence={}, source={}",
                        result.getTaskType(), result.getConfidence(), result.getSource());
                return result;
            }
        } catch (Exception e) {
            log.warn("[IntentEngine] LLM classification failed, falling back to keyword: {}", e.getMessage());
        }

        // 降级：关键词匹配
        IntentResult keywordResult = matchByKeyword(userMessage, allConfigs);
        if (keywordResult != null) {
            log.info("[IntentEngine] keyword fallback match: taskType={}", keywordResult.getTaskType());
            return keywordResult;
        }

        // 无匹配
        return IntentResult.builder()
                .taskType(null)
                .confidence(0.0)
                .reason("No intent matched by LLM or keyword")
                .candidates(new ArrayList<>(allConfigs.keySet()))
                .source(MatchSource.FALLBACK)
                .build();
    }

    @Override
    public IntentResult recognizeWithContext(String userMessage,
                                               String currentTaskType,
                                               String currentTaskName,
                                               String collectedFields) {
        if (userMessage == null || userMessage.isBlank()) {
            return IntentResult.continueTask(currentTaskType);
        }

        Map<String, AiTaskConfig> allConfigs = configService.getAnnotationConfigs();
        if (allConfigs.isEmpty()) {
            return IntentResult.continueTask(currentTaskType);
        }

        // LLM 判断 continue/switch/cancel
        try {
            IntentResult result = classifyWithContextByLlm(
                    userMessage, currentTaskType, currentTaskName, collectedFields, allConfigs);
            if (result != null) {
                log.info("[IntentEngine] LLM context result: action={}, taskType={}, confidence={}",
                        result.getAction(), result.getTaskType(), result.getConfidence());
                return result;
            }
        } catch (Exception e) {
            log.warn("[IntentEngine] LLM context classification failed, defaulting to continue: {}", e.getMessage());
        }

        // LLM 失败时默认继续当前任务（保守策略，不轻易切换）
        return IntentResult.continueTask(currentTaskType);
    }

    @Override
    public List<String> listAllTaskTypes() {
        return new ArrayList<>(configService.getAnnotationConfigs().keySet());
    }

    @Override
    public boolean judgeContinuity(String userMessage,
                                     String lastTaskType,
                                     String lastTaskName,
                                     String lastCollectedFields,
                                     String lastAiReply) {
        if (userMessage == null || userMessage.isBlank() || lastTaskType == null) {
            return false;
        }

        try {
            String prompt = buildContinuityPrompt(userMessage, lastTaskType, lastTaskName,
                    lastCollectedFields, lastAiReply);
            String primaryModel = llmHolder != null ? llmHolder.getActiveModelName() : "kimi";
            String response = llmClient.chatCompletion(primaryModel, prompt, userMessage);

            if (response == null || response.isBlank()) {
                return false;
            }

            JsonNode root = RobustJsonParser.parse(response);
            boolean continuous = root.has("continuous") && root.get("continuous").asBoolean(false);
            String reason = root.has("reason") && !root.get("reason").isNull()
                    ? root.get("reason").asText() : null;
            log.info("[IntentEngine] continuity judgment: continuous={}, reason={}", continuous, reason);
            return continuous;
        } catch (Exception e) {
            log.warn("[IntentEngine] continuity judgment failed, defaulting to false (new task): {}", e.getMessage());
            return false;
        }
    }

    // ---- LLM 分类（全新对话） ----

    private IntentResult classifyByLlm(String userMessage, Map<String, AiTaskConfig> configs) {
        String prompt = buildClassificationPrompt(userMessage, configs);
        String primaryModel = llmHolder != null ? llmHolder.getActiveModelName() : "kimi";

        String response;
        try {
            response = llmClient.chatCompletion(primaryModel, prompt, userMessage);
        } catch (LlmCallException e) {
            log.warn("[IntentEngine] LLM call failed for intent classification: {}", e.getMessage());
            return null;
        }

        if (response == null || response.isBlank()) {
            return null;
        }

        try {
            JsonNode root = RobustJsonParser.parse(response);
            String intent = root.has("intent") && !root.get("intent").isNull()
                    ? root.get("intent").asText() : null;
            double confidence = root.has("confidence") && root.get("confidence").isNumber()
                    ? root.get("confidence").asDouble() : 0.0;
            String reason = root.has("reason") && !root.get("reason").isNull()
                    ? root.get("reason").asText() : null;

            if (intent != null && !intent.isBlank() && !"null".equalsIgnoreCase(intent)
                    && configs.containsKey(intent)) {
                return IntentResult.builder()
                        .taskType(intent)
                        .confidence(confidence)
                        .reason(reason)
                        .source(MatchSource.LLM)
                        .build();
            }

            return IntentResult.builder()
                    .taskType(null)
                    .confidence(confidence)
                    .reason(reason != null ? reason : "LLM returned no valid intent")
                    .candidates(new ArrayList<>(configs.keySet()))
                    .source(MatchSource.LLM)
                    .build();

        } catch (Exception e) {
            log.warn("[IntentEngine] failed to parse LLM intent response: {} | raw={}",
                    e.getMessage(), response);
            return null;
        }
    }

    // ---- LLM 分类（带上下文，判断 continue/switch/cancel） ----

    private IntentResult classifyWithContextByLlm(String userMessage,
                                                    String currentTaskType,
                                                    String currentTaskName,
                                                    String collectedFields,
                                                    Map<String, AiTaskConfig> configs) {
        String prompt = buildContextPrompt(userMessage, currentTaskType, currentTaskName,
                collectedFields, configs);
        String primaryModel = llmHolder != null ? llmHolder.getActiveModelName() : "kimi";

        String response;
        try {
            response = llmClient.chatCompletion(primaryModel, prompt, userMessage);
        } catch (LlmCallException e) {
            log.warn("[IntentEngine] LLM call failed for context classification: {}", e.getMessage());
            return null;
        }

        if (response == null || response.isBlank()) {
            return null;
        }

        try {
            JsonNode root = RobustJsonParser.parse(response);
            String action = root.has("action") && !root.get("action").isNull()
                    ? root.get("action").asText().toLowerCase() : IntentResult.ACTION_CONTINUE;
            String newIntent = root.has("intent") && !root.get("intent").isNull()
                    ? root.get("intent").asText() : null;
            double confidence = root.has("confidence") && root.get("confidence").isNumber()
                    ? root.get("confidence").asDouble() : 0.0;
            String reason = root.has("reason") && !root.get("reason").isNull()
                    ? root.get("reason").asText() : null;

            IntentResult.IntentResultBuilder builder = IntentResult.builder()
                    .action(action)
                    .confidence(confidence)
                    .reason(reason)
                    .source(MatchSource.LLM);

            if (IntentResult.ACTION_SWITCH.equals(action) && newIntent != null
                    && configs.containsKey(newIntent)) {
                builder.taskType(newIntent);
            } else if (IntentResult.ACTION_CONTINUE.equals(action)) {
                builder.taskType(currentTaskType);
            }

            return builder.build();

        } catch (Exception e) {
            log.warn("[IntentEngine] failed to parse LLM context response: {} | raw={}",
                    e.getMessage(), response);
            return null;
        }
    }

    // ---- 关键词降级匹配 ----

    /** 常见虚词/语气词，归一化匹配时移除，提升自然语言变体的命中率 */
    private static final java.util.Set<String> STOP_WORDS = java.util.Set.of(
            "你", "我", "他", "她", "它", "的", "了", "吗", "呢", "啊", "吧", "呀", "哦",
            "为", "给", "把", "被", "让", "使", "向", "往", "从", "到", "在", "于",
            "一下", "一个", "一些", "这", "那", "这个", "那个", "什么", "怎么", "如何",
            "请", "请问", "帮忙", "帮我", "能否", "可不可以", "能不能"
    );

    private IntentResult matchByKeyword(String userMessage, Map<String, AiTaskConfig> configs) {
        String lowerMessage = userMessage.toLowerCase();
        String normalizedMessage = normalize(lowerMessage);

        for (Map.Entry<String, AiTaskConfig> entry : configs.entrySet()) {
            AiTaskConfig config = entry.getValue();
            List<String> keywords = config.getKeywords();
            if (keywords == null || keywords.isEmpty()) {
                continue;
            }
            for (String keyword : keywords) {
                if (keyword == null || keyword.isBlank()) {
                    continue;
                }
                String lowerKeyword = keyword.toLowerCase();
                // 1. 精确包含匹配
                if (lowerMessage.contains(lowerKeyword)) {
                    return IntentResult.keywordMatch(entry.getKey(), "Matched keyword: " + keyword);
                }
                // 2. 归一化匹配（移除虚词后再匹配，处理"你能为我做什么" vs "你能做什么"）
                String normalizedKeyword = normalize(lowerKeyword);
                if (!normalizedKeyword.isBlank() && normalizedKeyword.length() >= 2
                        && normalizedMessage.contains(normalizedKeyword)) {
                    log.debug("[IntentEngine] normalized keyword match: '{}' ~= '{}'", keyword, lowerMessage);
                    return IntentResult.keywordMatch(entry.getKey(),
                            "Matched keyword (normalized): " + keyword);
                }
            }
        }
        return null;
    }

    /**
     * 归一化文本：移除常见虚词、标点、多余空格，用于模糊关键词匹配。
     */
    private String normalize(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String result = text;
        // 移除标点
        result = result.replaceAll("[\\p{Punct}\\p{S}]", "");
        // 移除虚词（按长度降序替换，避免"一下"被"一"先替换）
        for (String word : STOP_WORDS) {
            result = result.replace(word, "");
        }
        // 移除多余空格
        result = result.replaceAll("\\s+", "");
        return result;
    }

    // ---- Prompt 构建 ----

    private String buildClassificationPrompt(String userMessage, Map<String, AiTaskConfig> configs) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是一个客服意图分类器。请根据用户消息判断用户意图。\n\n");
        sb.append("可用意图列表：\n");

        for (Map.Entry<String, AiTaskConfig> entry : configs.entrySet()) {
            AiTaskConfig config = entry.getValue();
            sb.append("- ").append(config.getTaskType()).append(": ").append(config.getName()).append("\n");
            if (config.getDescription() != null && !config.getDescription().isBlank()) {
                sb.append("  描述: ").append(config.getDescription()).append("\n");
            }
            if (config.getExamples() != null && !config.getExamples().isEmpty()) {
                sb.append("  用户表达方式示例: ").append(String.join("; ", config.getExamples())).append("\n");
            }
        }

        sb.append("\n请以 JSON 格式返回:\n");
        sb.append("{\n");
        sb.append("  \"intent\": \"最匹配的意图 type，无匹配则为 null\",\n");
        sb.append("  \"confidence\": 0.0-1.0,\n");
        sb.append("  \"reason\": \"简短判断理由\"\n");
        sb.append("}\n");

        return sb.toString();
    }

    private String buildContextPrompt(String userMessage,
                                        String currentTaskType,
                                        String currentTaskName,
                                        String collectedFields,
                                        Map<String, AiTaskConfig> configs) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是一个客服对话意图判断器。当前正在进行一个任务，请判断用户的下一条消息是：\n");
        sb.append("1. continue - 继续当前任务（提供参数、修正参数、确认等）\n");
        sb.append("2. switch - 切换到一个新任务（用户明确表示要做别的事情）\n");
        sb.append("3. cancel - 取消当前任务（用户说算了/不弄了/取消等）\n\n");

        sb.append("当前任务:\n");
        sb.append("  类型: ").append(currentTaskType).append("\n");
        sb.append("  名称: ").append(currentTaskName != null ? currentTaskName : currentTaskType).append("\n");
        if (collectedFields != null && !collectedFields.isBlank()) {
            sb.append("  已收集: ").append(collectedFields).append("\n");
        }

        sb.append("\n可用的其他任务类型（如果用户要切换）:\n");
        for (Map.Entry<String, AiTaskConfig> entry : configs.entrySet()) {
            if (!entry.getKey().equals(currentTaskType)) {
                sb.append("- ").append(entry.getKey()).append(": ").append(entry.getValue().getName()).append("\n");
            }
        }

        sb.append("\n请以 JSON 格式返回:\n");
        sb.append("{\n");
        sb.append("  \"action\": \"continue | switch | cancel\",\n");
        sb.append("  \"intent\": \"如果是 switch，填写新任务的 type；否则为 null\",\n");
        sb.append("  \"confidence\": 0.0-1.0,\n");
        sb.append("  \"reason\": \"简短判断理由\"\n");
        sb.append("}\n");

        return sb.toString();
    }

    // ---- 连续性判断 Prompt ----

    private String buildContinuityPrompt(String userMessage,
                                           String lastTaskType,
                                           String lastTaskName,
                                           String lastCollectedFields,
                                           String lastAiReply) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是一个对话连续性判断器。用户之前正在进行一个任务，但会话连接中断了。\n");
        sb.append("现在用户发来了一条新消息，请判断这条消息是在【继续上一轮任务】，还是在【开启一个全新的话题】。\n\n");

        sb.append("上一轮任务上下文:\n");
        sb.append("  任务类型: ").append(lastTaskType).append("\n");
        sb.append("  任务名称: ").append(lastTaskName != null ? lastTaskName : lastTaskType).append("\n");
        if (lastCollectedFields != null && !lastCollectedFields.isBlank()) {
            sb.append("  已收集字段: ").append(lastCollectedFields).append("\n");
        }
        if (lastAiReply != null && !lastAiReply.isBlank()) {
            sb.append("  上一轮AI回复: ").append(lastAiReply).append("\n");
        }

        sb.append("\n当前用户消息: ").append(userMessage).append("\n\n");

        sb.append("判断规则:\n");
        sb.append("- 如果用户在补充上一轮任务所需的参数、修正已收集的信息、或回应上一轮AI的提问 → continuous=true\n");
        sb.append("- 如果用户在询问完全不同的事情、想做另一个操作、或明确表示要重新开始 → continuous=false\n");
        sb.append("- 拿不准时，倾向于 continuous=false（开启新任务更安全）\n\n");

        sb.append("请以 JSON 格式返回:\n");
        sb.append("{\n");
        sb.append("  \"continuous\": true 或 false,\n");
        sb.append("  \"reason\": \"简短判断理由\"\n");
        sb.append("}\n");

        return sb.toString();
    }
}
