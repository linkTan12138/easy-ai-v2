package com.link.easyai.starter.domain.exception;

import org.springframework.http.HttpStatus;

/**
 * @Author link
 * @Description 业务异常处理类
 * @Date 18:09 2024/12/6
 * @Param 
 * @return 
 **/
public class UserNotFoundException extends BaseUncheckedException{

    private static final long serialVersionUID = 1L;

    public UserNotFoundException(String message) {
        super(HttpStatus.INTERNAL_SERVER_ERROR.value(),message);
    }


    public UserNotFoundException(int code, String message) {
        super(code, message);
    }



    @Override
    public String getMessage() {
        return message;
    }

}
