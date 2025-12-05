package com.example.scheduler.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * 将实验运行ID写入日志上下文，方便统一过滤
 */
@Component
public class ExperimentLoggingFilter extends OncePerRequestFilter {

    private static final String HEADER_NAME = "X-Experiment-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String experimentRunId = request.getHeader(HEADER_NAME);
        if (!StringUtils.hasText(experimentRunId)) {
            experimentRunId = request.getParameter("experimentId");
        }
        boolean added = false;
        if (StringUtils.hasText(experimentRunId)) {
            MDC.put("experimentId", experimentRunId);
            added = true;
        }
        try {
            if (!added) {
                // 保证日志上下文中始终有请求trace，便于排查
                MDC.put("requestId", UUID.randomUUID().toString());
            }
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove("experimentId");
            MDC.remove("requestId");
        }
    }
}
