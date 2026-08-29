package com.link.easyai.starter.engine.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares a premise (pre-condition) expression for a field.
 * <p>
 * 该字段只有在表达式求值为 true 时才参与收集。表达式语法支持：
 * <ul>
 *   <li>存在性：{@code field != null} / {@code field == null}</li>
 *   <li>比较：{@code ==} / {@code !=} / {@code >} / {@code <} / {@code >=} / {@code <=}</li>
 *   <li>逻辑：{@code AND} / {@code OR} / {@code !}（同时支持 {@code &&} / {@code ||}）</li>
 *   <li>包含：{@code field in ("v1","v2")}</li>
 *   <li>分组：{@code ()}</li>
 * </ul>
 * <p>
 * 示例：
 * <pre>
 * // 只要 customerName 或 channelName 其中一个存在即可
 * &#64;AiPremise("customerName != null || channelName != null")
 *
 * // 组合逻辑
 * &#64;AiPremise("customerName != null && (phone != null || email != null)")
 *
 * // 值比较
 * &#64;AiPremise("ticketType == 'COMPLAINT'")
 *
 * // 枚举包含
 * &#64;AiPremise("priority in ('高','中')")
 *
 * // 否定
 * &#64;AiPremise("!(country == 'CN')")
 * </pre>
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AiPremise {

    /**
     * 前提条件表达式，求值为 true 时该字段才参与收集。
     */
    String value();
}
