package com.link.easyai.starter.handle;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.util.Date;

/**
 * MyBatis-Plus 自动填充处理器。
 * <p>
 * 自动填充审计字段：createBy / updateBy / createTime / updateTime。
 * createBy / updateBy 默认填充为 0（系统用户），业务层可在保存前显式设置当前用户 ID 覆盖。
 */
@Slf4j
@Component
public class MyBatisMetaObjectHandler implements MetaObjectHandler {

    /** 默认系统用户 ID */
    private static final Long SYSTEM_USER_ID = 0L;

    @Override
    public void insertFill(MetaObject metaObject) {
        Date now = new Date();
        this.strictInsertFill(metaObject, "createTime", Date.class, now);
        this.strictInsertFill(metaObject, "updateTime", Date.class, now);
        this.strictInsertFill(metaObject, "createBy", Long.class, SYSTEM_USER_ID);
        this.strictInsertFill(metaObject, "updateBy", Long.class, SYSTEM_USER_ID);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        Date now = new Date();
        this.strictUpdateFill(metaObject, "updateTime", Date.class, now);
        this.strictUpdateFill(metaObject, "updateBy", Long.class, SYSTEM_USER_ID);
    }
}
