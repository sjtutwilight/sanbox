package com.example.scheduler.datasource.mysql;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@Configuration
@EnableTransactionManagement
@EnableJpaRepositories(basePackages = "com.example.scheduler.datasource.mysql")
public class MySqlConfig {
    // 采用 application.yml 的 datasource + JPA 默认配置
}
