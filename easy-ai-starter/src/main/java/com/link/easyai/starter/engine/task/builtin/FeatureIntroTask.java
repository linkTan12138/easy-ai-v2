package com.link.easyai.starter.engine.task.builtin;

import com.link.easyai.starter.engine.context.ExecuteContext;
import com.link.easyai.starter.engine.task.AiTask;
import com.link.easyai.starter.engine.task.TaskExecutor;
import com.link.easyai.starter.engine.task.TaskRegistry;
import com.link.easyai.starter.engine.task.TaskResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 框架内置：功能介绍任务（type = FEATURE_INTRO）。
 * <p>
 * 当用户询问系统能力、使用方式、支持哪些操作时触发。
 * 动态扫描 {@link TaskRegistry} 中所有已注册的 {@link TaskExecutor}，
 * 读取其 {@link AiTask} 注解上的 name / description / triggers 元信息，
 * 实时生成功能列表。
 * <p>
 * 新增业务任务时只需在 {@code @AiTask} 上填写元信息，无需修改本类。
 * 标注了 {@code hidden = true} 的任务不会出现在列表中。
 */
@AiTask(value = "FEATURE_INTRO", hidden = true)
public class FeatureIntroTask implements TaskExecutor {

    private static final Logger log = LoggerFactory.getLogger(FeatureIntroTask.class);

    private final TaskRegistry taskRegistry;

    @Autowired
    public FeatureIntroTask(TaskRegistry taskRegistry) {
        this.taskRegistry = taskRegistry;
    }

    @Override
    public String type() {
        return "FEATURE_INTRO";
    }

    @Override
    public TaskResult execute(ExecuteContext context) {
        Map<String, Object> params = context.getParameters();
        log.info("[FeatureIntroTask] executing, taskId={}, params={}", context.getTaskId(), params);

        String interestTopic = params.get("interestTopic") != null
                ? String.valueOf(params.get("interestTopic")).trim()
                : null;

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

        return TaskResult.success(message, data);
    }

    /**
     * 从 TaskRegistry 动态加载所有非 hidden 任务的元信息。
     */
    private List<FeatureMeta> loadVisibleFeatures() {
        List<FeatureMeta> result = new ArrayList<>();
        for (TaskExecutor executor : taskRegistry.getAllTasks()) {
            AiTask annotation = resolveAnnotation(executor);
            if (annotation == null) {
                log.debug("[FeatureIntroTask] executor {} has no @AiTask annotation, skipped",
                        executor.getClass().getName());
                continue;
            }
            if (annotation.hidden()) {
                continue;
            }
            String type = annotation.value();
            String name = annotation.name().isBlank() ? type : annotation.name();
            String description = annotation.description().isBlank()
                    ? "（该任务暂未填写功能描述）" : annotation.description();
            List<String> triggers = Arrays.asList(annotation.triggers());
            result.add(new FeatureMeta(name, type, description, triggers));
        }
        log.info("[FeatureIntroTask] loaded {} visible features from registry", result.size());
        return result;
    }

    /**
     * 解析 TaskExecutor 实例上的 @AiTask 注解，兼容 CGLIB 代理子类。
     */
    private AiTask resolveAnnotation(TaskExecutor executor) {
        Class<?> clazz = executor.getClass();
        AiTask annotation = clazz.getAnnotation(AiTask.class);
        if (annotation == null && clazz.getSuperclass() != null) {
            annotation = clazz.getSuperclass().getAnnotation(AiTask.class);
        }
        return annotation;
    }

    /**
     * 根据用户指定的方向构建介绍文本；未指定时返回全量功能清单。
     */
    private String buildIntroMessage(String interestTopic, List<FeatureMeta> features) {
        if (features.isEmpty()) {
            return "当前系统暂未注册任何业务功能。请联系管理员配置 @AiTask 任务。";
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

        return "暂未找到与「" + interestTopic + "」直接相关的功能。\n\n"
                + buildIntroMessage(null, features);
    }

    /** 功能项内部描述结构（从 @AiTask 注解动态提取）。 */
    private record FeatureMeta(String name, String type, String description, List<String> triggers) {}
}
