package com.example.monolith.shared.persistence;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.BlockAttackInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 插件。插件本质是 MyBatis 的拦截器（责任链），在 SQL 执行前改写它。
 *
 * <ul>
 *   <li>分页插件：用 JSqlParser 解析原始 SQL，自动生成 count 语句并追加 LIMIT；</li>
 *   <li>防全表更新/删除插件：UPDATE / DELETE 没有 WHERE 条件时直接拒绝执行，防止手滑把整张表改掉。</li>
 * </ul>
 * 多个插件有顺序要求：分页插件要放在最后，见官方文档。
 */
@Configuration(proxyBeanMethods = false)
public class MybatisPlusConfiguration {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new BlockAttackInnerInterceptor());
        PaginationInnerInterceptor pagination = new PaginationInnerInterceptor(DbType.MYSQL);
        // 单页最多 100 条：即使上层忘了限制，这里也兜住
        pagination.setMaxLimit(100L);
        interceptor.addInnerInterceptor(pagination);
        return interceptor;
    }
}
