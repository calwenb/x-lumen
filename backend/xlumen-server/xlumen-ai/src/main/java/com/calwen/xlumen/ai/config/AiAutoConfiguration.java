package com.calwen.xlumen.ai.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * AI 模块自动装配（F-0501）：启用 AiProperties 绑定（config/.env 的 XLUMEN_ 变量）。
 * AiProperties 为 @ConfigurationProperties 纯 POJO，无 @Component，需在此显式装配。
 *
 * @author calwen
 * @date 2026/8/13
 */
@Configuration
@EnableConfigurationProperties(AiProperties.class)
public class AiAutoConfiguration {
}
