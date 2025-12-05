package com.example.scheduler.loadexecutor.datasource.mysql;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.function.Consumer;
import java.util.function.Function;

public interface MySqlDataSource {

    <T> T query(Function<NamedParameterJdbcTemplate, T> callback);

    void execute(Consumer<NamedParameterJdbcTemplate> callback);
}
