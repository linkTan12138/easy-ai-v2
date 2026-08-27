package com.link.easyai.starter.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.link.easyai.starter.domain.entity.UserDetails;
import com.link.easyai.starter.domain.exception.UserNotFoundException;
import com.link.easyai.starter.service.UserDetailsService;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.schema.Column;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Configuration
public class MybatisPlusConfig {

    @Bean
    public TenantLineHandler tenantLineHandler(UserDetailsService userDetailsService) {
        return new TenantLineHandler() {
            @Override
            public Expression getTenantId() {
                UserDetails user = userDetailsService.getUser();
                if (user == null) {
                    throw new UserNotFoundException("未找到用户");
                }
                Long tenantId = user.getTenantId();
                return new LongValue(tenantId == null ? 0L : tenantId);
            }

            @Override
            public String getTenantIdColumn() {
                return "tenant_id";
            }

            @Override
            public boolean ignoreTable(String tableName) {
                return false;
            }

            @Override
            public boolean ignoreInsert(List<Column> columns, String tenantIdColumn) {
                return columns.stream().anyMatch(
                        col -> col.getColumnName().equalsIgnoreCase(tenantIdColumn)
                );
            }
        };
    }


    /**
     * 添加分页插件
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor(TenantLineHandler tenantLineHandler) {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        // 添加租户拦截器
        interceptor.addInnerInterceptor(new TenantLineInnerInterceptor(tenantLineHandler));

        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));

        return interceptor;
    }

}