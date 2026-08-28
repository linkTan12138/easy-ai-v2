package com.link.easyai.starter.engine.config.builtin;

import com.link.easyai.starter.engine.action.builtin.FeatureIntroAction;
import com.link.easyai.starter.engine.annotation.AiExtract;
import com.link.easyai.starter.engine.annotation.AiField;
import com.link.easyai.starter.engine.annotation.AiTask;

/**
 * 框架内置：功能介绍任务配置（type = FEATURE_INTRO）。
 * <p>
 * 当用户询问系统功能、使用方式、支持哪些操作时触发，
 * 无需多轮收集，直接返回功能清单。可选字段 {@code interestTopic}
 * 用于捕获用户指定的功能方向，命中时聚焦介绍该项。
 * <p>
 * 约定优于配置 —— code/type/order/options 全部由 Java 字段推导。
 * 该类仅作为配置声明，不会被实例化；修改字段即修改任务配置（version 恒为 1）。
 */
@AiTask(
        type = "FEATURE_INTRO",
        name = "功能介绍",
        description = "当用户询问系统功能、使用方式、支持哪些操作时，返回功能清单与简要说明",
        action = FeatureIntroAction.class,
        postActions = {"LOG"},
        keywords = {"功能介绍", "你能做什么", "你能为我做什么", "能做什么", "可以做什么", "会做什么",
                "有什么功能", "有哪些功能", "支持什么", "能帮我", "可以帮我",
                "怎么用", "如何使用", "使用说明", "使用方法", "操作指南",
                "帮助", "帮我", "介绍一下", "介绍下", "介绍系统",
                "help", "what can you do", "what do you do", "features", "capabilities"},
        examples = {
                "你能做什么",
                "你能为我做什么",
                "有什么功能",
                "介绍一下系统",
                "怎么下单",
                "帮我看看有哪些操作",
                "可以帮我做什么"
        }
)
public class FeatureIntroTask {

    @AiField(name = "想了解的功能方向")
    @AiExtract(
            description = "用户想了解的具体功能或方向。如果用户只是泛泛地问功能介绍、你能做什么，则不提取此字段；如果用户指定了具体方向（如“下单怎么用”“修改订单有什么用”），则提取该方向关键词",
            examples = {"下单", "修改订单", "渠道管理"},
            rules = {
                    "可选字段，用户未指定具体方向时不提取",
                    "提取简短的功能名称关键词，不要提取完整句子"
            }
    )
    private String interestTopic;
}
