package com.example.scheduler.loadexecutor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan(basePackages = "com.example.scheduler.loadexecutor")
public class LoadExecutorApplication {

    public static void main(String[] args) {
        SpringApplication.run(LoadExecutorApplication.class, args);
    }
}
