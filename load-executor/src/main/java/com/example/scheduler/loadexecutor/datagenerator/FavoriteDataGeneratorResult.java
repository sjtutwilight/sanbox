package com.example.scheduler.loadexecutor.datagenerator;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class FavoriteDataGeneratorResult {
    long startUserId;
    int userCount;
    int favoritesPerUser;
    long totalFavorites;
    boolean warmCache;
}
