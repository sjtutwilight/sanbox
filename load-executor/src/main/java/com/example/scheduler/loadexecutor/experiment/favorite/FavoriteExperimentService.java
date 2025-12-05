package com.example.scheduler.loadexecutor.experiment.favorite;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

@Service
@RequiredArgsConstructor
public class FavoriteExperimentService {

    private final FavoriteSymbolRepository repository;
    private final FavoriteCacheService cacheService;

    public FavoriteReadResult readWithCache(long userId, Duration ttlOverride) {
        return cacheService.getCached(userId)
                .map(symbols -> FavoriteReadResult.builder()
                        .userId(userId)
                        .symbols(symbols)
                        .source(FavoriteReadResult.Source.CACHE)
                        .build())
                .orElseGet(() -> {
                    List<String> symbols = repository.findSymbolsByUser(userId);
                    cacheService.cache(userId, symbols, ttlOverride != null ? ttlOverride : cacheService.defaultTtl());
                    return FavoriteReadResult.builder()
                            .userId(userId)
                            .symbols(symbols)
                            .source(FavoriteReadResult.Source.DATABASE)
                            .build();
                });
    }

    public void addFavorite(long userId, String symbol, String tags) {
        repository.upsert(userId, symbol, tags);
        cacheService.evict(userId);
    }

    public void removeFavorite(long userId, String symbol) {
        repository.delete(userId, symbol);
        cacheService.evict(userId);
    }

    public void warmUpCache(long userId, Duration ttl) {
        List<String> symbols = repository.findSymbolsByUser(userId);
        cacheService.cache(userId, symbols, ttl != null ? ttl : cacheService.defaultTtl());
    }

    public void evictCache(long userId) {
        cacheService.evict(userId);
    }
}
