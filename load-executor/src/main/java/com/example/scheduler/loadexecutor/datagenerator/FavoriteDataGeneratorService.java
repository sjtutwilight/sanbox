package com.example.scheduler.loadexecutor.datagenerator;

import com.example.scheduler.loadexecutor.experiment.favorite.FavoriteExperimentService;
import com.example.scheduler.loadexecutor.experiment.favorite.FavoriteSymbolRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
public class FavoriteDataGeneratorService {

    private static final List<String> DEFAULT_SYMBOLS = List.of(
            "BTCUSDT", "ETHUSDT", "SOLUSDT", "DOGEUSDT", "XRPUSDT", "OPUSDT", "ARBUSDT"
    );

    private final FavoriteSymbolRepository repository;
    private final FavoriteExperimentService experimentService;

    public FavoriteDataGeneratorResult generate(FavoriteDataGeneratorRequest request) {
        List<String> symbolPool = resolveSymbols(request.getSymbols());
        long userId = request.getStartUserId();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = 0; i < request.getUserCount(); i++) {
            long currentUserId = userId + i;
            List<String> picked = pickFavorites(symbolPool, request.getFavoritesPerUser(), random);
            for (String symbol : picked) {
                repository.upsert(currentUserId, symbol, request.getTags());
            }
            if (request.isWarmCache()) {
                experimentService.warmUpCache(currentUserId, Duration.ofSeconds(30));
            } else {
                experimentService.evictCache(currentUserId);
            }
        }
        return FavoriteDataGeneratorResult.builder()
                .startUserId(request.getStartUserId())
                .userCount(request.getUserCount())
                .favoritesPerUser(request.getFavoritesPerUser())
                .totalFavorites((long) request.getUserCount() * request.getFavoritesPerUser())
                .warmCache(request.isWarmCache())
                .build();
    }

    private List<String> resolveSymbols(List<String> symbols) {
        if (symbols == null || symbols.isEmpty()) {
            return DEFAULT_SYMBOLS;
        }
        return symbols;
    }

    private List<String> pickFavorites(List<String> pool, int count, ThreadLocalRandom random) {
        List<String> result = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            result.add(pool.get(random.nextInt(pool.size())));
        }
        return result;
    }
}
