package com.calwen.xlumen.knowledge.config;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Milvus 配置：绑定各环境 profile 的 xlumen.milvus.* 键（决策 D29）。
 * 装配层探测 REST v2 可达性决定启用 Milvus 或降级 Noop。
 * 注：@Value 键名与 profile YAML 的小写点号键一致（如 xlumen.milvus.host）；键缺失即启动失败。
 *
 * @author calwen
 * @date 2026/8/13
 */
@Data
@ConfigurationProperties(prefix = "xlumen")
public class MilvusProperties {

    /** Milvus 主机地址。 */
    @Value("${xlumen.milvus.host}")
    private String milvusHost;

    /** Milvus 端口。 */
    @Value("${xlumen.milvus.port}")
    private int milvusPort;

    /** Milvus 数据库名。 */
    @Value("${xlumen.milvus.database}")
    private String milvusDatabase;
}
