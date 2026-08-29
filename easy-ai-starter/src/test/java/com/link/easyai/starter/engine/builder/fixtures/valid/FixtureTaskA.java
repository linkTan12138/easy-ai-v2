package com.link.easyai.starter.engine.builder.fixtures.valid;

import com.link.easyai.starter.engine.annotation.AiExtract;
import com.link.easyai.starter.engine.annotation.AiField;
import com.link.easyai.starter.engine.annotation.AiMapping;
import com.link.easyai.starter.engine.annotation.AiPremise;
import com.link.easyai.starter.engine.annotation.AiTask;
import com.link.easyai.starter.engine.annotation.AiValid;
import com.link.easyai.starter.engine.annotation.Mapping;
import com.link.easyai.starter.engine.builder.fixtures.FixtureAction;
import com.link.easyai.starter.engine.validation.builtin.NotEmptyValidator;

import java.util.List;

/**
 * Rich fixture exercising every v1 annotation feature:
 * required/sensitive, extraction with allowEmpty derivation, explicit
 * validators, enum auto-validation, normalization, multi-dependency premise
 * (AND) and explicit mapping rules.
 */
@AiTask(
        type = "FIXTURE_TASK_A",
        name = "任务甲",
        description = "丰富特性 fixture",
        action = FixtureAction.class,
        postActions = {"LOG", "ECHO"}
)
public class FixtureTaskA {

    @AiField(name = "客户名", required = true, sensitive = true)
    @AiExtract(
            description = "客户名描述",
            examples = {"张三", "李四"},
            rules = {"规则一", "规则二"}
    )
    @AiValid(by = NotEmptyValidator.class)
    private String customerName;

    @AiField(name = "优先级")
    @AiExtract(description = "优先级描述")
    private FixturePriority priority;

    @AiField(name = "备注", normalize = "TEST_NORMALIZE")
    private List<String> remarks;

    @AiField(name = "状态")
    private FixtureStatus status;

    @AiPremise("customerName != null AND priority != null")
    @AiMapping({
            @Mapping(target = "finalRemark", source = "$value"),
            @Mapping(target = "rawRemark", source = "$rawValue"),
            @Mapping(target = "tag", source = "LITERAL_TAG")
    })
    private String finalRemark;
}
