package com.example.scheduler.loadexecutor.datasource.mysql;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

@Slf4j
@Component
@RequiredArgsConstructor
public class JdbcMySqlDataSource implements MySqlDataSource {

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final MeterRegistry meterRegistry;

    private Timer sqlTimer() {
        return Timer.builder("mysql_query_latency")
                .description("Latency of MySQL operations executed via datasource")
                .register(meterRegistry);
    }

    private Counter errorCounter() {
        return Counter.builder("mysql_query_errors")
                .description("Errors thrown when executing MySQL operations")
                .register(meterRegistry);
    }

    @Override
    public <T> T query(Function<NamedParameterJdbcTemplate, T> callback) {
        Objects.requireNonNull(callback, "callback");
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            return callback.apply(jdbcTemplate);
        } catch (RuntimeException e) {
            errorCounter().increment();
            throw e;
        } catch (Exception e) {
            errorCounter().increment();
            throw new MySqlOperationException("MySQL query failed", e);
        } finally {
            sample.stop(sqlTimer());
        }
    }

    @Override
    public void execute(Consumer<NamedParameterJdbcTemplate> callback) {
        Objects.requireNonNull(callback, "callback");
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            callback.accept(jdbcTemplate);
        } catch (RuntimeException e) {
            errorCounter().increment();
            throw e;
        } catch (Exception e) {
            errorCounter().increment();
            throw new MySqlOperationException("MySQL command failed", e);
        } finally {
            sample.stop(sqlTimer());
        }
    }
}
