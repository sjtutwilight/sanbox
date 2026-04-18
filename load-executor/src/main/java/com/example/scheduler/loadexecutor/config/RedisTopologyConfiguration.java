package com.example.scheduler.loadexecutor.config;

import com.example.scheduler.loadexecutor.datasource.redis.RedisStrategy;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisClusterConfiguration;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisSentinelConfiguration;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;

/**
 * Redis 连接拓扑配置，按策略构造单机、哨兵与集群模板。
 */
@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(LoadExecutorRedisProperties.class)
public class RedisTopologyConfiguration {

    private final LoadExecutorRedisProperties properties;

    @Bean(name = "standaloneStringRedisTemplate")
    public StringRedisTemplate standaloneStringRedisTemplate() {
        return new StringRedisTemplate(standaloneConnectionFactory());
    }

    @Bean(name = "sentinelStringRedisTemplate")
    public StringRedisTemplate sentinelStringRedisTemplate() {
        return new StringRedisTemplate(sentinelConnectionFactory());
    }

    @Bean(name = "clusterStringRedisTemplate")
    public StringRedisTemplate clusterStringRedisTemplate() {
        return new StringRedisTemplate(clusterConnectionFactory());
    }

    /**
     * 仅供数据源路由层使用，不作为应用默认连接工厂。
     */
    @Bean(name = "standaloneConnectionFactory")
    public LettuceConnectionFactory standaloneConnectionFactory() {
        LoadExecutorRedisProperties.Standalone standalone = properties.getStandalone();
        RedisStandaloneConfiguration configuration = new RedisStandaloneConfiguration(
                standalone.getHost(),
                standalone.getPort());
        configuration.setDatabase(standalone.getDatabase());
        if (standalone.getUsername() != null && !standalone.getUsername().isBlank()) {
            configuration.setUsername(standalone.getUsername());
        }
        if (standalone.getPassword() != null && !standalone.getPassword().isBlank()) {
            configuration.setPassword(RedisPassword.of(standalone.getPassword()));
        }
        return new LettuceConnectionFactory(configuration);
    }

    @Bean(name = "sentinelConnectionFactory")
    public LettuceConnectionFactory sentinelConnectionFactory() {
        LoadExecutorRedisProperties.Sentinel sentinel = properties.getSentinel();
        RedisSentinelConfiguration configuration = new RedisSentinelConfiguration();
        if (sentinel.getMaster() != null && !sentinel.getMaster().isBlank()) {
            configuration.master(sentinel.getMaster());
        }
        for (String node : sentinel.getNodes()) {
            if (node != null && !node.isBlank()) {
                configuration.sentinel(node.trim());
            }
        }
        configuration.setDatabase(sentinel.getDatabase());
        if (sentinel.getUsername() != null && !sentinel.getUsername().isBlank()) {
            configuration.setUsername(sentinel.getUsername());
        }
        if (sentinel.getPassword() != null && !sentinel.getPassword().isBlank()) {
            configuration.setPassword(RedisPassword.of(sentinel.getPassword()));
        }
        return new LettuceConnectionFactory(configuration);
    }

    @Bean(name = "clusterConnectionFactory")
    public LettuceConnectionFactory clusterConnectionFactory() {
        LoadExecutorRedisProperties.Cluster cluster = properties.getCluster();
        RedisClusterConfiguration configuration = new RedisClusterConfiguration(cluster.getNodes());
        if (cluster.getMaxRedirects() != null) {
            configuration.setMaxRedirects(cluster.getMaxRedirects());
        }
        if (cluster.getUsername() != null && !cluster.getUsername().isBlank()) {
            configuration.setUsername(cluster.getUsername());
        }
        if (cluster.getPassword() != null && !cluster.getPassword().isBlank()) {
            configuration.setPassword(RedisPassword.of(cluster.getPassword()));
        }
        return new LettuceConnectionFactory(configuration);
    }
}
