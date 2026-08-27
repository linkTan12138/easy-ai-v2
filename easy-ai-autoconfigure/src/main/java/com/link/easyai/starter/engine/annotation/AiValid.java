package com.link.easyai.starter.engine.annotation;

import com.link.easyai.starter.engine.validation.FieldValidator;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares a business validator for a field, referenced by class.
 * <p>
 * The class is resolved as a Spring bean at startup; the bean's {@code type()}
 * becomes the validator type in the generated config. Business differences are
 * expressed through different validator classes, never through params:
 * <pre>
 * &#64;AiValid(by = ReceiveChannelExistValidator.class)
 * private String receiveChannelName;
 *
 * &#64;AiValid(by = SendChannelExistValidator.class)
 * private String sendChannelName;
 * </pre>
 * <p>
 * Repeatable — validators run in declaration order as a pipeline.
 * A Java enum field with no explicit @AiValid automatically gets the built-in
 * ENUM validator (label → value conversion against the generated options);
 * declaring any @AiValid overrides that default.
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Repeatable(AiValids.class)
public @interface AiValid {

    /**
     * The validator class, resolved as a Spring bean at startup.
     */
    Class<? extends FieldValidator> by();
}
