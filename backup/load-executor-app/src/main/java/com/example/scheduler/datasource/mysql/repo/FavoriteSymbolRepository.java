package com.example.scheduler.datasource.mysql.repo;

import com.example.scheduler.datasource.mysql.entity.FavoriteSymbol;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FavoriteSymbolRepository extends JpaRepository<FavoriteSymbol, Long> {
    List<FavoriteSymbol> findByUserId(Long userId);
    boolean existsByUserIdAndSymbol(Long userId, String symbol);
}
