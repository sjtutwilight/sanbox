CREATE TABLE IF NOT EXISTS wallet_snapshot_ledger (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    day_offset INT NOT NULL,
    record_count BIGINT NOT NULL,
    checksum DOUBLE NOT NULL,
    processed_at DATETIME(6) NOT NULL,
    INDEX idx_wallet_ledger_user_day (user_id, day_offset)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS wallet_snapshot_summary (
    user_id BIGINT PRIMARY KEY,
    total_records BIGINT NOT NULL,
    days_back INT NOT NULL,
    last_rebuild_at DATETIME(6) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
