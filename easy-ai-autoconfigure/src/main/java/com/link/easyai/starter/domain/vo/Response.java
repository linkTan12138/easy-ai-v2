package com.link.easyai.starter.domain.vo;

import com.link.easyai.starter.domain.enums.StatusCodeEnum;
import lombok.Data;

/**
 * @author :link
 * @date :2023/1/31
 * @description :
 */
@Data
public class Response<T> {
    
    private T data;
    
    private Integer code;
    
    private String message;
    
    public Response(T data, Integer code, String message) {
        this.data = data;
        this.code = code;
        this.message =message;
    }
    
    public Response(T data, Integer code) {
        this(data,code, StatusCodeEnum.SUCCESS.getMessage());
    }
    
    public Response(T data) {
        this(data, StatusCodeEnum.SUCCESS.getCode(), StatusCodeEnum.SUCCESS.getMessage());
    }

    public Response() {
        this(null, StatusCodeEnum.SUCCESS.getCode(), StatusCodeEnum.SUCCESS.getMessage());
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
        return new Response<>(null, StatusCodeEnum.FAIL.getCode(),message);
    }

    public static <T> Response<T> fail(T t, String message) {
        return new Response<>(t,StatusCodeEnum.FAIL.getCode(),message);
    }

    public static <T> Response<T> fail(T t, Integer code, String message) {
        return new Response<>(t,code,message);
    }
}
