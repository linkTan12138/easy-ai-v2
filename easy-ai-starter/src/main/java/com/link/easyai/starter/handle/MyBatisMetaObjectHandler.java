package com.link.easyai.starter.handle;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.link.easyai.starter.domain.entity.UserDetails;
import com.link.easyai.starter.domain.exception.UserNotFoundException;
import com.link.easyai.starter.service.UserDetailsService;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Date;

// java example
@Slf4j
@Component
public class MyBatisMetaObjectHandler implements MetaObjectHandler {

    @Autowired
    private UserDetailsService userDetailsService;

    @Override
    public void insertFill(MetaObject metaObject) {
        UserDetails user = userDetailsService.getUser();
        if(user == null) throw new UserNotFoundException("未找到用户");
        this.strictInsertFill(metaObject, "createBy", Long.class, user.getId());
        this.strictInsertFill(metaObject, "updateBy", Long.class, user.getId());
        this.setFieldValByName("tenantId", user.getTenantId() == null ? 0 : user.getTenantId(), metaObject);
        Date now = new Date();
        this.strictInsertFill(metaObject, "createTime", Date.class, now);
        this.strictInsertFill(metaObject, "updateTime", Date.class, now);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        UserDetails user = userDetailsService.getUser();
        if(user == null) throw new UserNotFoundException("未找到用户");
        this.strictInsertFill(metaObject, "updateBy", Long.class, user.getId());
        Date now = new Date();
        this.strictUpdateFill(metaObject, "updateTime", Date.class, now);
    }
}