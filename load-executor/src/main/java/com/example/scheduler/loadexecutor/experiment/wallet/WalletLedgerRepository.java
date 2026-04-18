package com.example.scheduler.loadexecutor.experiment.wallet;

import com.example.scheduler.loadexecutor.datasource.postgres.PostgresDataSource;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class WalletLedgerRepository {

    private final PostgresDataSource postgresDataSource;

    public void saveLedgerRecords(Collection<WalletLedgerRecord> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        String sql = """
                INSERT INTO wallet_snapshot_ledger(user_id, day_offset, record_count, checksum, processed_at)
                VALUES (:userId, :dayOffset, :recordCount, :checksum, :processedAt)
                """;
        SqlParameterSource[] batch = records.stream()
                .map(record -> new MapSqlParameterSource()
                        .addValue("userId", record.getUserId())
                        .addValue("dayOffset", record.getDayOffset())
                        .addValue("recordCount", record.getRecordCount())
                        .addValue("checksum", record.getChecksum())
                        .addValue("processedAt", timestamp(record.getProcessedAt())))
                .toArray(SqlParameterSource[]::new);
        postgresDataSource.execute(jdbc -> jdbc.batchUpdate(sql, batch));
    }

    public void upsertSummary(long userId, long totalRecords, int daysBack) {
        postgresDataSource.execute(jdbc -> jdbc.update("""
                        INSERT INTO wallet_snapshot_summary(user_id, total_records, days_back, last_rebuild_at)
                        VALUES (:userId, :totalRecords, :daysBack, NOW())
                        ON CONFLICT (user_id) DO UPDATE SET
                            total_records = EXCLUDED.total_records,
                            days_back = EXCLUDED.days_back,
                            last_rebuild_at = EXCLUDED.last_rebuild_at
                        """,
                new MapSqlParameterSource()
                        .addValue("userId", userId)
                        .addValue("totalRecords", totalRecords)
                        .addValue("daysBack", daysBack)));
    }

    private Timestamp timestamp(Instant instant) {
        return instant != null ? Timestamp.from(instant) : Timestamp.from(Instant.now());
    }
}
