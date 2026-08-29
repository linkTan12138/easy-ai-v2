package com.link.easyai.starter.engine.extraction;

import java.util.Map;

/**
 * 抽取上下文变量提供者（SPI 扩展点）。
 * <p>
 * 实现此接口的 Bean 会在字段抽取时被调用，提供动态上下文变量
 * （如当前日期、当前用户、业务专有名词等），注入到 LLM 抽取 prompt 中。
 * <p>
 * 变量不会自动注入所有字段，需要在 {@code @AiExtract(contextVars = {...})}
 * 中显式声明该字段需要哪些变量，避免 prompt 臃肿。
 * <p>
 * 示例：
 * <pre>
 * &#64;Component
 * public class CurrentDateContextProvider implements ExtractionContextProvider {
 *     &#64;Override
 *     public Map&lt;String, String&gt; getContextVariables() {
 *         return Map.of("currentDate", LocalDate.now().toString());
 *     }
 * }
 * </pre>
 * <p>
 * 多个 Provider 可共存，框架自动合并变量。同名变量后注册的覆盖先注册的。
 */
public interface ExtractionContextProvider {

    /**
     * 提供上下文变量。
     * 每次抽取时调用，支持动态值（如当前日期、当前登录用户）。
     *
     * @return key 为变量名（全局唯一），value 为变量值
     */
    Map<String, String> getContextVariables();
}
