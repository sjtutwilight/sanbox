package com.example.scheduler.loadexecutor.experiment.favorite;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class FavoriteReadResult {
    long userId;
    List<String> symbols;
    Source source;

    public enum Source {
        CACHE,
        DATABASE
    }
}
