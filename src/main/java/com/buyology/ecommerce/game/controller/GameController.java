package com.buyology.ecommerce.game.controller;

import com.buyology.ecommerce.common.response.ApiResponse;
import com.buyology.ecommerce.game.dto.GameSubmissionRequest;
import com.buyology.ecommerce.game.dto.LeaderboardResponse;
import com.buyology.ecommerce.game.dto.QuizQuestionResponse;
import com.buyology.ecommerce.game.enums.GameType;
import com.buyology.ecommerce.game.service.GameService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/game")
@Tag(name = "Daily Games", description = "Endpoints for playing daily games and quizzes")
public class GameController {

    private final GameService gameService;

    public GameController(GameService gameService) {
        this.gameService = gameService;
    }

    @Operation(summary = "Get current daily game type")
    @GetMapping("/daily")
    public ResponseEntity<ApiResponse<GameType>> getDailyGameType() {
        GameType gameType = gameService.getDailyGameType();
        return ResponseEntity.ok(ApiResponse.success("Daily game type retrieved", gameType));
    }

    @Operation(summary = "Get active quiz questions")
    @GetMapping("/quiz")
    public ResponseEntity<ApiResponse<List<QuizQuestionResponse>>> getActiveQuizQuestions() {
        List<QuizQuestionResponse> questions = gameService.getActiveQuizQuestions();
        return ResponseEntity.ok(ApiResponse.success("Quiz questions retrieved", questions));
    }

    @Operation(summary = "Submit game/quiz result")
    @PostMapping("/submit")
    public ResponseEntity<ApiResponse<String>> submitResult(@Valid @RequestBody GameSubmissionRequest request) {
        try {
            gameService.submitResult(request);
            return ResponseEntity.ok(ApiResponse.success("Result submitted successfully", "OK"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @Operation(summary = "Get daily score leaderboard")
    @GetMapping("/leaderboard/daily")
    public ResponseEntity<ApiResponse<List<LeaderboardResponse>>> getDailyLeaderboard() {
        List<LeaderboardResponse> leaderboard = gameService.getDailyLeaderboard();
        return ResponseEntity.ok(ApiResponse.success("Daily leaderboard retrieved", leaderboard));
    }

    @Operation(summary = "Get streak leaderboard")
    @GetMapping("/leaderboard/streak")
    public ResponseEntity<ApiResponse<List<LeaderboardResponse>>> getStreakLeaderboard() {
        List<LeaderboardResponse> leaderboard = gameService.getStreakLeaderboard();
        return ResponseEntity.ok(ApiResponse.success("Streak leaderboard retrieved", leaderboard));
    }
}
