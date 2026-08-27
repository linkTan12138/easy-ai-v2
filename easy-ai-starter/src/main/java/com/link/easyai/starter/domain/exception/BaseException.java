package com.link.easyai.starter.domain.exception;

/**
 * 异常接口类
 */
public interface BaseException {

    /**
     * 返回异常信息
     *
     * @return
     */
    String getMessage();

    /**
     * 返回异常编码
     *
     * @return
     */
    int getCode();

}
