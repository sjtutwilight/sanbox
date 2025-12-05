package com.example.scheduler.loadexecutor.datagenerator;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/datagenerator")
@RequiredArgsConstructor
public class DataGeneratorController {

    private final FavoriteDataGeneratorService favoriteDataGeneratorService;

    @PostMapping("/favorite")
    public FavoriteDataGeneratorResult generateFavorite(@Valid @RequestBody FavoriteDataGeneratorRequest request) {
        return favoriteDataGeneratorService.generate(request);
    }
}
