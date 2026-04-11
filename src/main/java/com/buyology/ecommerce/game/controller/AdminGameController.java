package com.buyology.ecommerce.game.controller;

import com.buyology.ecommerce.common.response.ApiResponse;
import com.buyology.ecommerce.game.domain.DailyGameConfig;
import com.buyology.ecommerce.game.dto.DailyGameConfigDto;
import com.buyology.ecommerce.game.dto.QuizQuestionRequest;
import com.buyology.ecommerce.game.dto.QuizQuestionResponse;
import com.buyology.ecommerce.game.service.GameService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/game")
@Tag(name = "Admin – Daily Games", description = "Manage daily games and quizzes")
public class AdminGameController {

    private final GameService gameService;

    public AdminGameController(GameService gameService) {
        this.gameService = gameService;
    }

    @Operation(summary = "Configure daily game type")
    @PostMapping("/config")
    public ResponseEntity<ApiResponse<DailyGameConfig>> configureDailyGame(@Valid @RequestBody DailyGameConfigDto dto) {
        DailyGameConfig config = gameService.configureDailyGame(dto);
        return ResponseEntity.ok(ApiResponse.success("Daily game configured successfully", config));
    }

    @Operation(summary = "Create a new quiz question")
    @PostMapping("/quiz")
    public ResponseEntity<ApiResponse<QuizQuestionResponse>> createQuizQuestion(@Valid @RequestBody QuizQuestionRequest request) {
        QuizQuestionResponse response = gameService.createQuizQuestion(request);
        return ResponseEntity.ok(ApiResponse.success("Quiz question created successfully", response));
    }

    @Operation(summary = "Get all quiz questions")
    @GetMapping("/quiz")
    public ResponseEntity<ApiResponse<List<QuizQuestionResponse>>> getAllQuizQuestions() {
        List<QuizQuestionResponse> questions = gameService.getAllQuizQuestions();
        return ResponseEntity.ok(ApiResponse.success("Quiz questions retrieved successfully", questions));
    }
}
