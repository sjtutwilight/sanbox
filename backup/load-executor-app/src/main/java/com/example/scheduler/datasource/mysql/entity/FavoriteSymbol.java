package com.example.scheduler.datasource.mysql.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "favorite_symbol", indexes = {
        @Index(name = "idx_uid", columnList = "user_id"),
        @Index(name = "idx_uid_symbol", columnList = "user_id,symbol")
})
@Getter
@Setter
public class FavoriteSymbol {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "symbol", nullable = false, length = 32)
    private String symbol;

    @Column(name = "tags", length = 256)
    private String tags;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
