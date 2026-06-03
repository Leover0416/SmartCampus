package com.simon.campus;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * 【导读】SmartCampus 后端启动入口（单体 Spring Boot 应用）。
 * <ul>
 *   <li>排除默认 UserDetailsService：本项目用 JWT，不用 Spring Security 内置表单登录</li>
 *   <li>@MapperScan：扫描 com.simon.campus.mapper 下所有 MyBatis 接口</li>
 *   <li>@EnableAsync：知识库文档异步入库（IngestAsyncService）需要异步线程池</li>
 * </ul>
 */
@SpringBootApplication(exclude = {UserDetailsServiceAutoConfiguration.class})
@MapperScan("com.simon.campus.mapper")
@EnableAsync
public class CampusApplication {
    public static void main(String[] args) {
        SpringApplication.run(CampusApplication.class, args);
    }
}
