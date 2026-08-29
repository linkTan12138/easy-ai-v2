package com.link.easyai.starter.engine.extraction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Locale;
import java.util.Map;

/**
 * 框架内置的当前日期上下文提供者。
 * <p>
 * 提供以下变量，可通过 {@code @AiExtract(contextVars = {...})} 声明使用：
 * <ul>
 *   <li>{@code currentDate}：当前日期，格式 yyyy-MM-dd（如 2026-08-29）</li>
 *   <li>{@code currentWeekday}：当前星期几（如 星期六）</li>
 *   <li>{@code currentMonth}：当前月份（如 八月）</li>
 * </ul>
 * <p>
 * 帮助 LLM 理解"今天"、"明天"、"下周"等相对日期表达。
 * 每次抽取时调用，返回实时日期。
 * <p>
 * 示例：
 * <pre>
 * &#64;AiExtract(
 *     description = "预约日期",
 *     contextVars = {"currentDate"}
 * )
 * private String appointmentDate;
 * </pre>
 */
@Component
public class CurrentDateContextProvider implements ExtractionContextProvider {

    private static final Logger log = LoggerFactory.getLogger(CurrentDateContextProvider.class);

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Override
    public Map<String, String> getContextVariables() {
        LocalDate today = LocalDate.now();
        Map<String, String> vars = Map.of(
                "currentDate", today.format(DATE_FORMATTER),
                "currentWeekday", today.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.CHINA),
                "currentMonth", today.getMonth().getDisplayName(TextStyle.FULL, Locale.CHINA)
        );
        log.debug("[CurrentDateContextProvider] 提供上下文变量: {}", vars);
        return vars;
    }
}
