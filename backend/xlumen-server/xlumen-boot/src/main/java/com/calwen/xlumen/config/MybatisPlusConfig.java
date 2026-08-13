package com.calwen.xlumen.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 装配：分页插件 + 乐观锁插件（BACKEND.md §2/§11/§18）。
 * 乐观锁：实体 version 字段标注 @Version，updateById 自动带 version 条件并自增，影响行数 0 即冲突（HTTP 409）。
 *
 * @author calwen
 * @date 2026/8/12
 */
@Configuration
public class MybatisPlusConfig {

    /**
     * 分页拦截器：MySQL 方言，溢出页数时返回空结果而非报错；乐观锁拦截器支持版本校验（F-0905/PRODUCT §6）。
     *
     * @return MyBatis-Plus 拦截器
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        PaginationInnerInterceptor pagination = new PaginationInnerInterceptor(DbType.MYSQL);
        pagination.setOverflow(false);
        interceptor.addInnerInterceptor(pagination);
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        return interceptor;
    }
}
