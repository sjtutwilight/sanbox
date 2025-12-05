package com.example.scheduler.datasource.mysql;

import com.example.scheduler.datasource.mysql.entity.FavoriteSymbol;
import com.example.scheduler.datasource.mysql.repo.FavoriteSymbolRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 自选收藏持久化到 MySQL，用于缓存击穿/回源实验
 */
@Service
@RequiredArgsConstructor
public class FavoritePersistenceService {

    private final FavoriteSymbolRepository favoriteSymbolRepository;

    @Transactional(readOnly = true)
    public List<FavoriteSymbol> listByUser(Long userId) {
        return favoriteSymbolRepository.findByUserId(userId);
    }

    @Transactional
    public FavoriteSymbol addFavorite(Long userId, String symbol, String tags) {
        Optional<FavoriteSymbol> existing = favoriteSymbolRepository.findByUserId(userId).stream()
                .filter(f -> f.getSymbol().equals(symbol))
                .findFirst();
        if (existing.isPresent()) {
            return existing.get();
        }
        FavoriteSymbol fav = new FavoriteSymbol();
        fav.setUserId(userId);
        fav.setSymbol(symbol);
        fav.setTags(tags);
        fav.setCreatedAt(Instant.now());
        return favoriteSymbolRepository.save(fav);
    }

    @Transactional(readOnly = true)
    public boolean exists(Long userId, String symbol) {
        return favoriteSymbolRepository.existsByUserIdAndSymbol(userId, symbol);
    }
}
