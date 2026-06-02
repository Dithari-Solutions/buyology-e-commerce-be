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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/game")
@PreAuthorize("hasRole('ADMIN')")
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
        return ApiResponse.success(config, "Daily game configured successfully");
    }

    @Operation(summary = "Create a new quiz question")
    @PostMapping("/quiz")
    public ResponseEntity<ApiResponse<QuizQuestionResponse>> createQuizQuestion(@Valid @RequestBody QuizQuestionRequest request) {
        QuizQuestionResponse response = gameService.createQuizQuestion(request);
        return ApiResponse.success(response, "Quiz question created successfully");
    }

    @Operation(summary = "Get all quiz questions")
    @GetMapping("/quiz")
    public ResponseEntity<ApiResponse<List<QuizQuestionResponse>>> getAllQuizQuestions() {
        List<QuizQuestionResponse> questions = gameService.getAllQuizQuestions();
        return ApiResponse.success(questions, "Quiz questions retrieved successfully");
    }

    @Operation(summary = "Update a quiz question")
    @PutMapping("/quiz/{id}")
    public ResponseEntity<ApiResponse<QuizQuestionResponse>> updateQuizQuestion(
            @PathVariable UUID id,
            @Valid @RequestBody QuizQuestionRequest request) {
        QuizQuestionResponse response = gameService.updateQuizQuestion(id, request);
        return ApiResponse.success(response, "Quiz question updated successfully");
    }

    @Operation(summary = "Delete a quiz question")
    @DeleteMapping("/quiz/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteQuizQuestion(@PathVariable UUID id) {
        gameService.deleteQuizQuestion(id);
        return ApiResponse.success(null, "Quiz question deleted successfully");
    }

    @Operation(summary = "Get the daily game configuration for a given date (defaults to today)")
    @GetMapping("/config")
    public ResponseEntity<ApiResponse<DailyGameConfig>> getDailyGameConfig(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        LocalDate target = date != null ? date : LocalDate.now();
        DailyGameConfig config = gameService.getDailyGameConfig(target);
        return ApiResponse.success(config, "Daily game configuration retrieved");
    }
}
