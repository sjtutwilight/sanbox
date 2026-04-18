package com.example.scheduler.loadexecutor.datagenerator;

import com.example.scheduler.loadexecutor.datagenerator.onchain.OnchainMockDataGeneratorRequest;
import com.example.scheduler.loadexecutor.datagenerator.onchain.OnchainMockDataGeneratorResponse;
import com.example.scheduler.loadexecutor.datagenerator.onchain.OnchainMockDataGeneratorService;
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
    private final OnchainMockDataGeneratorService onchainMockDataGeneratorService;

    @PostMapping("/favorite")
    public FavoriteDataGeneratorResult generateFavorite(@Valid @RequestBody FavoriteDataGeneratorRequest request) {
        return favoriteDataGeneratorService.generate(request);
    }

    @PostMapping("/onchain/mock")
    public OnchainMockDataGeneratorResponse generateOnchain(@Valid @RequestBody OnchainMockDataGeneratorRequest request) {
        return onchainMockDataGeneratorService.generate(request);
    }
}
