package com.link.easyai.starter.domain.vo;

import lombok.Data;

/**
 * @author :link
 * @date :2023/1/31
 * @description :
 */
@Data
public class Response<T> {

    public static final int CODE_SUCCESS = 200;
    public static final String MSG_SUCCESS = "请求成功";
    public static final int CODE_FAIL = 400;

    private T data;

    private Integer code;

    private String message;

    public Response(T data, Integer code, String message) {
        this.data = data;
        this.code = code;
        this.message = message;
    }

    public Response(T data, Integer code) {
        this(data, code, MSG_SUCCESS);
    }

    public Response(T data) {
        this(data, CODE_SUCCESS, MSG_SUCCESS);
    }

    public Response() {
        this(null, CODE_SUCCESS, MSG_SUCCESS);
    }

    public static <T> Response<T> success() {
        return new Response<>();
    }

    public static <T> Response<T> success(T t) {
        return new Response<>(t);
    }

    public static <T> Response<T> success(T t, int code) {
        return new Response<>(t, code);
    }

    public static <T> Response<T> success(T t, int code, String message) {
        return new Response<>(t, code, message);
    }

    public static <T> Response<T> fail(String message) {
        return new Response<>(null, CODE_FAIL, message);
    }

    public static <T> Response<T> fail(T t, String message) {
        return new Response<>(t, CODE_FAIL, message);
    }

    public static <T> Response<T> fail(T t, Integer code, String message) {
        return new Response<>(t, code, message);
    }
}
