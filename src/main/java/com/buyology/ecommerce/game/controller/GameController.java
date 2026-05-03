package com.buyology.ecommerce.game.controller;

import com.buyology.ecommerce.common.response.ApiResponse;
import com.buyology.ecommerce.game.dto.AlreadyPlayedException;
import com.buyology.ecommerce.game.dto.DailyGameStatusResponse;
import com.buyology.ecommerce.game.dto.GameSubmissionRequest;
import com.buyology.ecommerce.game.dto.GameSubmissionResponse;
import com.buyology.ecommerce.game.dto.LeaderboardResponse;
import com.buyology.ecommerce.game.dto.QuizQuestionResponse;
import com.buyology.ecommerce.game.service.GameService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
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

    @Operation(summary = "Get current daily game status (type + played + tokens + streak)")
    @GetMapping("/daily")
    public ResponseEntity<ApiResponse<DailyGameStatusResponse>> getDailyGameStatus() {
        DailyGameStatusResponse status = gameService.getDailyGameStatus();
        return ApiResponse.success(status, "Daily game status retrieved");
    }

    @Operation(summary = "Get current daily game type only")
    @GetMapping("/daily-type")
    public ResponseEntity<ApiResponse<com.buyology.ecommerce.game.enums.GameType>> getDailyGameType() {
        return ApiResponse.success(gameService.getDailyGameType(), "Daily game type retrieved");
    }

    @Operation(summary = "Get active quiz questions")
    @GetMapping("/quiz")
    public ResponseEntity<ApiResponse<List<QuizQuestionResponse>>> getActiveQuizQuestions() {
        List<QuizQuestionResponse> questions = gameService.getActiveQuizQuestions();
        return ApiResponse.success(questions, "Quiz questions retrieved");
    }

    @Operation(summary = "Submit game/quiz result")
    @PostMapping("/submit")
    public ResponseEntity<ApiResponse<GameSubmissionResponse>> submitResult(@Valid @RequestBody GameSubmissionRequest request) {
        try {
            GameSubmissionResponse result = gameService.submitResult(request);
            return ApiResponse.success(result, "Result submitted successfully");
        } catch (AlreadyPlayedException e) {
            // 409 — distinguishes "you've used your daily allowance" from generic 400s
            // so clients can show a friendly message instead of a generic error.
            return ApiResponse.failure(HttpStatus.CONFLICT, e.getMessage());
        } catch (RuntimeException e) {
            return ApiResponse.failure(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @Operation(summary = "Get daily score leaderboard")
    @GetMapping("/leaderboard/daily")
    public ResponseEntity<ApiResponse<List<LeaderboardResponse>>> getDailyLeaderboard() {
        List<LeaderboardResponse> leaderboard = gameService.getDailyLeaderboard();
        return ApiResponse.success(leaderboard, "Daily leaderboard retrieved");
    }

    @Operation(summary = "Get streak leaderboard")
    @GetMapping("/leaderboard/streak")
    public ResponseEntity<ApiResponse<List<LeaderboardResponse>>> getStreakLeaderboard() {
        List<LeaderboardResponse> leaderboard = gameService.getStreakLeaderboard();
        return ApiResponse.success(leaderboard, "Streak leaderboard retrieved");
    }
}
