package com.example.scheduler.loadexecutor.experiment.favorite;

import com.example.scheduler.loadexecutor.datasource.mysql.MySqlDataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Repository;

import java.util.List;

@Slf4j
@Repository
@RequiredArgsConstructor
public class FavoriteSymbolRepository {

    private final MySqlDataSource mySqlDataSource;

    public List<String> findSymbolsByUser(long userId) {
        return mySqlDataSource.query(jdbc -> jdbc.queryForList(
                """
                        SELECT symbol 
                        FROM favorite_symbol 
                        WHERE user_id = :userId 
                        ORDER BY created_at DESC
                        """,
                new MapSqlParameterSource("userId", userId),
                String.class));
    }

    public void upsert(long userId, String symbol, String tags) {
        mySqlDataSource.execute(jdbc -> jdbc.update(
                """
                        INSERT INTO favorite_symbol(user_id, symbol, tags, created_at)
                        VALUES (:userId, :symbol, :tags, NOW(6))
                        ON DUPLICATE KEY UPDATE tags = VALUES(tags)
                        """,
                new MapSqlParameterSource()
                        .addValue("userId", userId)
                        .addValue("symbol", symbol)
                        .addValue("tags", tags)));
    }

    public void delete(long userId, String symbol) {
        mySqlDataSource.execute(jdbc -> jdbc.update(
                "DELETE FROM favorite_symbol WHERE user_id = :userId AND symbol = :symbol",
                new MapSqlParameterSource()
                        .addValue("userId", userId)
                        .addValue("symbol", symbol)));
    }
}
