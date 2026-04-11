package com.buyology.ecommerce.game.service;

import com.buyology.ecommerce.game.domain.*;
import com.buyology.ecommerce.game.dto.*;
import com.buyology.ecommerce.game.enums.GameType;
import com.buyology.ecommerce.game.repository.*;
import com.buyology.ecommerce.user.domain.Users;
import com.buyology.ecommerce.user.repository.UserRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class GameService {

    private final DailyGameConfigRepository dailyGameConfigRepository;
    private final QuizQuestionRepository quizQuestionRepository;
    private final GameResultRepository gameResultRepository;
    private final UserStreakRepository userStreakRepository;
    private final UserRepository userRepository;

    public GameService(DailyGameConfigRepository dailyGameConfigRepository,
                       QuizQuestionRepository quizQuestionRepository,
                       GameResultRepository gameResultRepository,
                       UserStreakRepository userStreakRepository,
                       UserRepository userRepository) {
        this.dailyGameConfigRepository = dailyGameConfigRepository;
        this.quizQuestionRepository = quizQuestionRepository;
        this.gameResultRepository = gameResultRepository;
        this.userStreakRepository = userStreakRepository;
        this.userRepository = userRepository;
    }

    // Admin Methods

    @Transactional
    public DailyGameConfig configureDailyGame(DailyGameConfigDto dto) {
        Optional<DailyGameConfig> existing = dailyGameConfigRepository.findByGameDate(dto.getGameDate());
        DailyGameConfig config = existing.orElse(new DailyGameConfig());
        config.setGameDate(dto.getGameDate());
        config.setGameType(dto.getGameType());
        return dailyGameConfigRepository.save(config);
    }

    @Transactional
    public QuizQuestionResponse createQuizQuestion(QuizQuestionRequest request) {
        QuizQuestion question = new QuizQuestion();
        question.setCorrectOptionIndex(request.getCorrectOptionIndex());
        question.setPoints(request.getPoints());
        question.setActive(request.isActive());

        List<QuizQuestionTranslation> translations = request.getTranslations().stream()
                .map(t -> new QuizQuestionTranslation(question, t.getLanguage(), t.getQuestionText(), 
                        t.getOptionA(), t.getOptionB(), t.getOptionC(), t.getOptionD()))
                .collect(Collectors.toList());
        
        question.setTranslations(translations);
        QuizQuestion saved = quizQuestionRepository.save(question);
        return mapToResponse(saved);
    }

    public List<QuizQuestionResponse> getAllQuizQuestions() {
        return quizQuestionRepository.findAll().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    // User Methods

    public GameType getDailyGameType() {
        return dailyGameConfigRepository.findByGameDate(LocalDate.now())
                .map(DailyGameConfig::getGameType)
                .orElse(GameType.MINI_GAME); // Default
    }

    public List<QuizQuestionResponse> getActiveQuizQuestions() {
        return quizQuestionRepository.findByIsActiveTrue().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public void submitResult(GameSubmissionRequest request) {
        Users user = getCurrentUser();
        LocalDateTime now = LocalDateTime.now();
        LocalDate today = now.toLocalDate();

        // Check if already played today
        LocalDateTime startOfDay = today.atStartOfDay();
        LocalDateTime endOfDay = today.atTime(LocalTime.MAX);
        
        if (gameResultRepository.findByUserAndPlayedDate(user, startOfDay, endOfDay).isPresent()) {
            throw new RuntimeException("Already played today");
        }

        GameResult result = new GameResult(user, request.getGameType(), request.getScore(), request.isSuccess(), now);
        gameResultRepository.save(result);

        updateStreak(user, today);
    }

    private void updateStreak(Users user, LocalDate today) {
        UserStreak streak = userStreakRepository.findByUser(user)
                .orElse(new UserStreak(user));

        if (streak.getLastPlayedDate() == null) {
            streak.setCurrentStreak(1);
        } else if (streak.getLastPlayedDate().equals(today.minusDays(1))) {
            streak.setCurrentStreak(streak.getCurrentStreak() + 1);
        } else if (!streak.getLastPlayedDate().equals(today)) {
            streak.setCurrentStreak(1);
        }

        if (streak.getCurrentStreak() > streak.getLongestStreak()) {
            streak.setLongestStreak(streak.getCurrentStreak());
        }

        streak.setLastPlayedDate(today);
        userStreakRepository.save(streak);
    }

    public List<LeaderboardResponse> getDailyLeaderboard() {
        LocalDate today = LocalDate.now();
        List<GameResult> results = gameResultRepository.findDailyLeaderboard(today.atStartOfDay(), today.atTime(LocalTime.MAX));
        
        return results.stream().map(r -> {
            UserStreak streak = userStreakRepository.findByUser(r.getUser()).orElse(null);
            return new LeaderboardResponse(
                    r.getUser().getId(),
                    r.getUser().getFirstName() + " " + r.getUser().getLastName(),
                    r.getScore(),
                    streak != null ? streak.getCurrentStreak() : 0
            );
        }).collect(Collectors.toList());
    }

    public List<LeaderboardResponse> getStreakLeaderboard() {
        return userStreakRepository.findTop10ByOrderByCurrentStreakDesc().stream()
                .map(s -> new LeaderboardResponse(
                        s.getUser().getId(),
                        s.getUser().getFirstName() + " " + s.getUser().getLastName(),
                        0, // Score not relevant for streak leaderboard
                        s.getCurrentStreak()
                )).collect(Collectors.toList());
    }

    private Users getCurrentUser() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        UUID userId;
        if (principal instanceof UUID) {
            userId = (UUID) principal;
        } else {
            try {
                userId = UUID.fromString(principal.toString());
            } catch (Exception e) {
                throw new RuntimeException("User not found: " + principal);
            }
        }
        return userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));
    }


    private QuizQuestionResponse mapToResponse(QuizQuestion q) {
        QuizQuestionResponse resp = new QuizQuestionResponse();
        resp.setId(q.getId());
        resp.setCorrectOptionIndex(q.getCorrectOptionIndex());
        resp.setPoints(q.getPoints());
        resp.setActive(q.isActive());
        resp.setTranslations(q.getTranslations().stream().map(t -> {
            QuizTranslationResponse tr = new QuizTranslationResponse();
            tr.setLanguage(t.getLanguage());
            tr.setQuestionText(t.getQuestionText());
            tr.setOptionA(t.getOptionA());
            tr.setOptionB(t.getOptionB());
            tr.setOptionC(t.getOptionC());
            tr.setOptionD(t.getOptionD());
            return tr;
        }).collect(Collectors.toList()));
        return resp;
    }
}
