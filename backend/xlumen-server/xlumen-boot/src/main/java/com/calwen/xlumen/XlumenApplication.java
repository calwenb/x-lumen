package com.calwen.xlumen;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * xLumen 应用启动类：模块化单体唯一装配入口（决策 D1）。
 * 扫描 com.calwen.xlumen 下全部模块的组件；业务模块不得依赖 boot。
 *
 * @author calwen
 * @date 2026/8/12
 */
@SpringBootApplication(scanBasePackages = "com.calwen.xlumen")
public class XlumenApplication {

    public static void main(String[] args) {
        SpringApplication.run(XlumenApplication.class, args);
    }
}
