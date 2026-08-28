package com.link.easyai.starter.engine.action.builtin;

import com.link.easyai.starter.engine.AnnotationAiTaskConfigService;
import com.link.easyai.starter.engine.action.ActionExecutor;
import com.link.easyai.starter.engine.action.ActionResult;
import com.link.easyai.starter.engine.action.AiAction;
import com.link.easyai.starter.engine.config.AiTaskConfig;
import com.link.easyai.starter.engine.context.ActionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 框架内置：功能介绍业务动作（type = FEATURE_INTRO）。
 * <p>
 * 当用户询问系统能力、使用方式、支持哪些操作时触发。
 * <strong>不硬编码功能清单</strong>，而是动态扫描所有 {@link AiTaskConfig}
 * （来自 {@code @AiTask} 注解），读取其 name / description / keywords 元信息，
 * 实时生成功能列表。
 * <p>
 * 新增业务场景时只需在 {@code @AiTask} 上填写元信息，无需修改本类。
 * 内置任务（如本类自身）不会出现在列表中。
 * <p>
 * 可选字段 {@code interestTopic}（想了解的功能方向）由映射引擎传入，
 * 若用户指定了具体方向则聚焦介绍，否则返回全量功能清单。
 */
@AiAction(value = "FEATURE_INTRO", hidden = true)
public class FeatureIntroAction implements ActionExecutor {

    private static final Logger log = LoggerFactory.getLogger(FeatureIntroAction.class);

    /** 内置任务类型，不出现在功能列表中 */
    private static final List<String> BUILTIN_TASK_TYPES = List.of("FEATURE_INTRO");

    private final AnnotationAiTaskConfigService configService;

    @Autowired
    public FeatureIntroAction(AnnotationAiTaskConfigService configService) {
        this.configService = configService;
    }

    @Override
    public String type() {
        return "FEATURE_INTRO";
    }

    @Override
    public ActionResult execute(ActionContext context) {
        Map<String, Object> params = context.getParameters();
        log.info("[FeatureIntroAction] executing, taskId={}, params={}", context.getTaskId(), params);

        String interestTopic = params.get("interestTopic") != null
                ? String.valueOf(params.get("interestTopic")).trim()
                : null;

        // 动态加载所有可见任务的元信息
        List<FeatureMeta> features = loadVisibleFeatures();

        String message = buildIntroMessage(interestTopic, features);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("interestTopic", interestTopic);
        data.put("featureCount", features.size());
        data.put("features", features.stream().map(f -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", f.name);
            m.put("type", f.type);
            m.put("description", f.description);
            m.put("triggers", f.triggers);
            return m;
        }).toList());

        return ActionResult.success(message, data);
    }

    /**
     * 从所有 @AiTask 注解配置中动态加载非内置任务的元信息。
     */
    private List<FeatureMeta> loadVisibleFeatures() {
        List<FeatureMeta> result = new ArrayList<>();
        Map<String, AiTaskConfig> configs = configService.getAllAnnotationConfigs();

        for (Map.Entry<String, AiTaskConfig> entry : configs.entrySet()) {
            String taskType = entry.getKey();
            AiTaskConfig config = entry.getValue();

            // 跳过内置任务
            if (BUILTIN_TASK_TYPES.contains(taskType)) {
                continue;
            }

            String name = config.getName() != null && !config.getName().isBlank()
                    ? config.getName() : taskType;
            String description = config.getDescription() != null && !config.getDescription().isBlank()
                    ? config.getDescription() : "（该任务暂未填写功能描述）";
            List<String> triggers = config.getKeywords() != null ? config.getKeywords() : List.of();

            result.add(new FeatureMeta(name, taskType, description, triggers));
        }

        log.info("[FeatureIntroAction] loaded {} visible features from task configs", result.size());
        return result;
    }

    /**
     * 根据用户指定的方向构建介绍文本；未指定时返回全量功能清单。
     */
    private String buildIntroMessage(String interestTopic, List<FeatureMeta> features) {
        if (features.isEmpty()) {
            return "当前系统暂未配置任何业务功能。请联系管理员添加 @AiTask 任务。";
        }

        if (interestTopic == null || interestTopic.isBlank()) {
            StringBuilder sb = new StringBuilder();
            sb.append("本系统是一个 AI 驱动的业务助手，目前支持以下功能：\n");
            int idx = 1;
            for (FeatureMeta f : features) {
                sb.append(idx++).append(". ").append(f.name)
                        .append("：").append(f.description).append("\n");
            }
            sb.append("\n您可以直接告诉我要做什么，我会通过对话收集所需信息并自动执行。");
            return sb.toString();
        }

        // 按关键词模糊匹配功能项
        String lower = interestTopic.toLowerCase();
        FeatureMeta matched = features.stream()
                .filter(f -> f.name.contains(interestTopic)
                        || f.type.toLowerCase().contains(lower)
                        || f.description.contains(interestTopic)
                        || f.triggers.stream().anyMatch(t -> t.contains(interestTopic)))
                .findFirst()
                .orElse(null);

        if (matched != null) {
            StringBuilder sb = new StringBuilder();
            sb.append("关于「").append(matched.name).append("」功能：\n");
            sb.append(matched.description).append("\n");
            if (!matched.triggers.isEmpty()) {
                sb.append("触发方式：").append(String.join("、", matched.triggers)).append("\n");
            }
            sb.append("\n您可以直接说上述触发语开始使用，或继续问我其他功能。");
            return sb.toString();
        }

        // 未匹配到具体功能，返回全量清单并提示
        return "暂未找到与「" + interestTopic + "」直接相关的功能。\n\n"
                + buildIntroMessage(null, features);
    }

    /** 功能项内部描述结构（从 @AiTask 注解动态提取）。 */
    private record FeatureMeta(String name, String type, String description, List<String> triggers) {}
}
