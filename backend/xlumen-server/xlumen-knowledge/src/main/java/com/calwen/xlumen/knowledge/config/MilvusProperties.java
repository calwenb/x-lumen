package com.calwen.xlumen.knowledge.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Milvus 配置（F-0402）：绑定 .env 的 XLUMEN_MILVUS_* 变量（决策 D8 唯一配置载体）。
 * 装配层探测 /healthz 可达性决定启用 Milvus 或降级 Noop。
 *
 * @author calwen
 * @date 2026/8/13
 */
@Data
@ConfigurationProperties(prefix = "xlumen")
public class MilvusProperties {

    /** Milvus 主机地址。 */
    private String milvusHost = "127.0.0.1";

    /** Milvus 端口。 */
    private int milvusPort = 19530;

    /** Milvus 数据库名。 */
    private String milvusDatabase = "default";
}
