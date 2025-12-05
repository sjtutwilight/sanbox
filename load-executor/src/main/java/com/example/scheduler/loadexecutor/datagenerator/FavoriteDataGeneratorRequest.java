package com.example.scheduler.loadexecutor.datagenerator;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class FavoriteDataGeneratorRequest {

    @NotNull
    private Long startUserId;

    @Min(1)
    @Max(10000)
    private int userCount = 1;

    @Min(1)
    @Max(100)
    private int favoritesPerUser = 5;

    private List<String> symbols;

    private String tags = "datagen";

    private boolean warmCache = false;
}
