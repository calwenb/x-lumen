package com.calwen.xlumen.knowledge.config;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Milvus 配置（F-0402）：绑定 .env 的 XLUMEN_MILVUS_* 变量（决策 D8 唯一配置载体）。
 * 装配层探测 REST v2 可达性决定启用 Milvus 或降级 Noop。
 * 注：Boot 4 的 Binder 对 .env 导入的大写属性不做 relaxed binding，改用 @Value 显式占位符绑定（同 AiProperties）。
 *
 * @author calwen
 * @date 2026/8/13
 */
@Data
@ConfigurationProperties(prefix = "xlumen")
public class MilvusProperties {

    /** Milvus 主机地址。 */
    @Value("${XLUMEN_MILVUS_HOST}")
    private String milvusHost;

    /** Milvus 端口。 */
    @Value("${XLUMEN_MILVUS_PORT}")
    private int milvusPort;

    /** Milvus 数据库名。 */
    @Value("${XLUMEN_MILVUS_DATABASE}")
    private String milvusDatabase;
}
