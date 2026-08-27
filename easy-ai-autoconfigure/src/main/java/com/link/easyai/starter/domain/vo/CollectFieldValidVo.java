package com.link.easyai.starter.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CollectFieldValidVo {
    private String errMsg;
    private boolean passed;

    public static CollectFieldValidVo build(String errMsg, boolean passed) {
        return new CollectFieldValidVo(errMsg, passed);
    }
    public static CollectFieldValidVo build(boolean passed) {
        return new CollectFieldValidVo(null, passed);
    }
    public static CollectFieldValidVo build() {
        return new CollectFieldValidVo();
    }

    public CollectFieldValidVo passed(boolean passed) {
        this.passed = passed;
        return this;
    }

    public CollectFieldValidVo errMsg(String errMsg) {
        this.errMsg = errMsg;
        return this;
    }
}
