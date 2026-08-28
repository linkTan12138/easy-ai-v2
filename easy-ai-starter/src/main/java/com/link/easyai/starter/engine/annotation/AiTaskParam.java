package com.link.easyai.starter.engine.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记一个 DTO 类为 AI 任务的参数收集配置。
 * <p>
 * 仅当任务需要多轮对话收集参数时才需要此注解。通过 {@link #type()} 与
 * {@link com.link.easyai.starter.engine.task.AiTask} 一对一关联。
 * <p>
 * 类中的字段通过 {@link AiField}、{@link AiValid}、{@link AiDependsOn} 等注解
 * 定义参数收集规则。
 * <p>
 * 示例：
 * <pre>
 * &#64;AiTaskParam(type = "CREATE_TICKET")
 * public class CreateTicketDto {
 *     &#64;AiField(name = "工单类型")
 *     private TicketType ticketType;
 *     // ...
 * }
 * </pre>
 * <p>
 * 纯动作场景（无需参数收集）不需要此注解，只需 {@code @AiTask}。
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AiTaskParam {

    /**
     * 关联的任务类型标识，必须与某个 {@code @AiTask.value()} 一致。
     */
    String type();
}
