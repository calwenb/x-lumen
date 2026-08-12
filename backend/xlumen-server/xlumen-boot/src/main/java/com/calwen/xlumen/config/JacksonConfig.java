package com.calwen.xlumen.config;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.core.Version;
import tools.jackson.databind.JacksonModule;
import tools.jackson.databind.module.SimpleSerializers;
import tools.jackson.databind.ser.std.ToStringSerializer;

/**
 * Jackson 序列化配置：Long 统一序列化为 String。
 * 雪花 ID（1.9e18）超出 JS Number 安全整数（2^53），数字传输会丢精度（M03 详情路由踩坑）；
 * 统计类数值（viewCount 等）由前端 API 层 Number() 还原。
 * Spring Boot 4 使用 Jackson 3（tools.jackson 包），定制入口为 JsonMapperBuilderCustomizer + JacksonModule。
 *
 * @author calwen
 * @date 2026/8/12
 */
@Configuration
public class JacksonConfig {

    @Bean
    public JsonMapperBuilderCustomizer longToStringCustomizer() {
        SimpleSerializers serializers = new SimpleSerializers()
                .addSerializer(Long.class, ToStringSerializer.instance)
                .addSerializer(Long.TYPE, ToStringSerializer.instance);
        JacksonModule module = new JacksonModule() {
            @Override
            public String getModuleName() {
                return "xlumen-long-to-string";
            }

            @Override
            public Version version() {
                return Version.unknownVersion();
            }

            @Override
            public void setupModule(SetupContext context) {
                context.addSerializers(serializers);
            }
        };
        return builder -> builder.addModule(module);
    }
}
