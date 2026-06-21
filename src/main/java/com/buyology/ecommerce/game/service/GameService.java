package com.buyology.ecommerce.game.service;

import com.buyology.ecommerce.common.utils.SecurityUtils;
import com.buyology.ecommerce.game.domain.*;
import com.buyology.ecommerce.game.dto.*;
import com.buyology.ecommerce.game.enums.GameType;
import com.buyology.ecommerce.game.repository.*;
import com.buyology.ecommerce.user.domain.UserProfiles;
import com.buyology.ecommerce.user.domain.Users;
import com.buyology.ecommerce.user.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class GameService {

    private final DailyGameConfigRepository dailyGameConfigRepository;
    private final QuizQuestionRepository quizQuestionRepository;
    private final GameResultRepository gameResultRepository;
    private final UserStreakRepository userStreakRepository;
    private final UserRepository userRepository;
    private final com.buyology.ecommerce.user.repository.UserProfilesRepository userProfilesRepository;
    private final com.buyology.ecommerce.game.repository.GameRewardConfigRepository gameRewardConfigRepository;

    public GameService(DailyGameConfigRepository dailyGameConfigRepository,
                       QuizQuestionRepository quizQuestionRepository,
                       GameResultRepository gameResultRepository,
                       UserStreakRepository userStreakRepository,
                       UserRepository userRepository,
                       com.buyology.ecommerce.user.repository.UserProfilesRepository userProfilesRepository,
                       com.buyology.ecommerce.game.repository.GameRewardConfigRepository gameRewardConfigRepository) {
        this.dailyGameConfigRepository = dailyGameConfigRepository;
        this.quizQuestionRepository = quizQuestionRepository;
        this.gameResultRepository = gameResultRepository;
        this.userStreakRepository = userStreakRepository;
        this.userRepository = userRepository;
        this.userProfilesRepository = userProfilesRepository;
        this.gameRewardConfigRepository = gameRewardConfigRepository;
    }

    // ── Admin-tunable token reward config ────────────────────────────────────
    public com.buyology.ecommerce.game.domain.GameRewardConfig getOrCreateRewardConfig() {
        return gameRewardConfigRepository.findTopByOrderByUpdatedAtAsc()
                .orElseGet(() -> gameRewardConfigRepository.save(
                        new com.buyology.ecommerce.game.domain.GameRewardConfig()));
    }

    public com.buyology.ecommerce.game.dto.GameRewardConfigDto updateRewardConfig(
            com.buyology.ecommerce.game.dto.GameRewardConfigDto dto) {
        com.buyology.ecommerce.game.domain.GameRewardConfig cfg = getOrCreateRewardConfig();
        cfg.setQuizReward(Math.max(0, dto.getQuizReward()));
        cfg.setMiniGameReward(Math.max(0, dto.getMiniGameReward()));
        return com.buyology.ecommerce.game.dto.GameRewardConfigDto.from(
                gameRewardConfigRepository.save(cfg));
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

    @Transactional
    public QuizQuestionResponse updateQuizQuestion(UUID id, QuizQuestionRequest request) {
        QuizQuestion question = quizQuestionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Quiz question not found: " + id));

        question.setCorrectOptionIndex(request.getCorrectOptionIndex());
        question.setPoints(request.getPoints());
        question.setActive(request.isActive());

        question.getTranslations().clear();
        request.getTranslations().forEach(t -> question.getTranslations().add(
                new QuizQuestionTranslation(question, t.getLanguage(), t.getQuestionText(),
                        t.getOptionA(), t.getOptionB(), t.getOptionC(), t.getOptionD())));

        return mapToResponse(quizQuestionRepository.save(question));
    }

    @Transactional
    public void deleteQuizQuestion(UUID id) {
        if (!quizQuestionRepository.existsById(id)) {
            throw new IllegalArgumentException("Quiz question not found: " + id);
        }
        quizQuestionRepository.deleteById(id);
    }

    public DailyGameConfig getDailyGameConfig(LocalDate date) {
        return dailyGameConfigRepository.findByGameDate(date).orElse(null);
    }

    // User Methods

    public GameType getDailyGameType() {
        return dailyGameConfigRepository.findByGameDate(LocalDate.now())
                .map(DailyGameConfig::getGameType)
                .orElse(GameType.MINI_GAME); // Default
    }

    /**
     * Rich daily-game status used by the mobile app's CHOICE screen:
     * which game today, whether the current user already played it, their
     * total tokens, and current streak.
     */
    public DailyGameStatusResponse getDailyGameStatus() {
        Users user = getCurrentUser();
        LocalDate today = LocalDate.now();
        GameType gameType = getDailyGameType();

        boolean played = hasPlayedToday(user, today);

        int tokens = userProfilesRepository.findByUser(user)
                .map(UserProfiles::getTokens)
                .orElse(0);

        int streak = userStreakRepository.findByUser(user)
                .map(UserStreak::getCurrentStreak)
                .orElse(0);

        return new DailyGameStatusResponse(gameType, played, tokens, streak);
    }

    public List<QuizQuestionResponse> getActiveQuizQuestions() {
        return quizQuestionRepository.findByIsActiveTrue().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    // Token rewards are now admin-configurable via GameRewardConfig (see
    // getOrCreateRewardConfig); the old flat TOKENS_PER_WIN constant was removed.

    /**
     * Server-side outcome of a submission. Success and score are derived here
     * from authoritative data (stored quiz answers), never from the client.
     */
    private record GameOutcome(boolean success, int score) {}

    /**
     * Recomputes success/score on the server. For QUIZ games every submitted
     * answer is checked against the stored {@link QuizQuestion#getCorrectOptionIndex()};
     * the client's {@code isSuccess}/{@code score} are ignored. A quiz is a "win"
     * only if at least one question was answered and ALL answered questions are correct.
     */
    private GameOutcome evaluate(GameSubmissionRequest request) {
        if (request.getGameType() != GameType.QUIZ) {
            // MINI_GAME has no server-side answer key; trust the reported score but
            // not the client success flag — success is derived from a positive score.
            int score = request.getScore();
            return new GameOutcome(score > 0, Math.max(score, 0));
        }

        List<GameSubmissionRequest.QuizAnswer> answers =
                request.getAnswers() == null ? Collections.emptyList() : request.getAnswers();
        if (answers.isEmpty()) {
            return new GameOutcome(false, 0);
        }

        List<UUID> questionIds = answers.stream()
                .map(GameSubmissionRequest.QuizAnswer::getQuestionId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toList());

        Map<UUID, QuizQuestion> questions = quizQuestionRepository.findAllById(questionIds).stream()
                .collect(Collectors.toMap(QuizQuestion::getId, Function.identity()));

        int score = 0;
        for (GameSubmissionRequest.QuizAnswer answer : answers) {
            QuizQuestion question = answer.getQuestionId() == null ? null : questions.get(answer.getQuestionId());
            // Unknown/inactive questions can never count as correct.
            if (question != null && question.isActive()
                    && question.getCorrectOptionIndex() == answer.getSelectedOptionIndex()) {
                score += question.getPoints();
            }
        }

        // Reward participation: any correct answer earns the daily tokens (matches the mini-game,
        // which also treats score > 0 as a win). Avoids the old all-or-nothing 0-token behaviour.
        return new GameOutcome(score > 0, score);
    }

    @Transactional
    public GameSubmissionResponse submitResult(GameSubmissionRequest request) {
        Users user = getCurrentUser();
        LocalDateTime now = LocalDateTime.now();
        LocalDate today = now.toLocalDate();

        // Daily-limit guard — backend is the source of truth, regardless of what
        // the client sends. Mobile/web should also gate their UI but we don't trust them.
        // This is best-effort; the (user, play_date) unique constraint below is the
        // real race-proof guard against concurrent double-submits.
        if (hasPlayedToday(user, today)) {
            throw new AlreadyPlayedException("Already played today");
        }

        // Recompute the outcome server-side; the client's success/score are not trusted.
        GameOutcome outcome = evaluate(request);

        GameResult result = new GameResult(user, request.getGameType(), outcome.score(), outcome.success(), now);
        try {
            gameResultRepository.saveAndFlush(result);
        } catch (DataIntegrityViolationException e) {
            // A concurrent submit won the race and already inserted today's row.
            throw new AlreadyPlayedException("Already played today");
        }

        com.buyology.ecommerce.game.domain.GameRewardConfig rewardConfig = getOrCreateRewardConfig();
        int reward = request.getGameType() == GameType.QUIZ
                ? rewardConfig.getQuizReward()
                : rewardConfig.getMiniGameReward();
        int tokensEarned = outcome.success() ? reward : 0;
        int newTotalTokens = userProfilesRepository.findByUser(user)
                .map(profile -> {
                    int updated = profile.getTokens() + tokensEarned;
                    profile.setTokens(updated);
                    userProfilesRepository.save(profile);
                    return updated;
                })
                .orElse(tokensEarned);

        UserStreak streak = updateStreak(user, today);

        return new GameSubmissionResponse(tokensEarned, streak.getCurrentStreak(), newTotalTokens);
    }

    private boolean hasPlayedToday(Users user, LocalDate today) {
        LocalDateTime startOfDay = today.atStartOfDay();
        LocalDateTime endOfDay = today.atTime(LocalTime.MAX);
        return gameResultRepository.findByUserAndPlayedDate(user, startOfDay, endOfDay).isPresent();
    }

    private UserStreak updateStreak(Users user, LocalDate today) {
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
        return userStreakRepository.save(streak);
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
        // Identify the player from the authenticated principal only — never from any
        // client-supplied id. Throws 401 via SecurityUtils if unauthenticated.
        UUID userId = SecurityUtils.currentUserId();
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
