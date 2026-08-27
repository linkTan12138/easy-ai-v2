package com.link.easyai.starter.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CollectParamVo<T> {
    private String message;
    private T t;
    // 1：成功 0：失败
    private Integer status;

    public boolean isSuccess() {
        return status == 1;
    }

    public static <T> CollectParamVo<T> success(T t) {
        return new CollectParamVo<>(null, t,1);
    }

    public static <T> CollectParamVo<T> fail(String message) {
        return new CollectParamVo<>(message, null,0);
    }

    public CollectParamVo<T> data(T t) {
        this.t = t;
        return this;
    }
}
