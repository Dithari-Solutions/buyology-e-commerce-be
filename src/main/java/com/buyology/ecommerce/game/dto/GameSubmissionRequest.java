package com.buyology.ecommerce.game.dto;

import com.buyology.ecommerce.game.enums.GameType;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public class GameSubmissionRequest {

    @NotNull
    private GameType gameType;

    /**
     * @deprecated success is recomputed server-side and the client value is ignored
     * for token awards. Kept only for backwards-compatible deserialization.
     */
    @Deprecated
    private boolean isSuccess;

    /**
     * @deprecated for QUIZ submissions the score is recomputed server-side from
     * {@link #answers}. Honoured only for MINI_GAME where there is no server-side
     * answer to validate against.
     */
    @Deprecated
    private int score;

    /** Time taken in milliseconds (mini-games). Null for quizzes. */
    private Integer time;

    /**
     * Submitted quiz answers (one per question the user attempted). Used to
     * recompute success/score on the server. Ignored for MINI_GAME.
     */
    private List<QuizAnswer> answers;

    /** A single submitted quiz answer: which option the user picked for a question. */
    public static class QuizAnswer {
        private UUID questionId;
        private int selectedOptionIndex;

        public UUID getQuestionId() {
            return questionId;
        }

        public void setQuestionId(UUID questionId) {
            this.questionId = questionId;
        }

        public int getSelectedOptionIndex() {
            return selectedOptionIndex;
        }

        public void setSelectedOptionIndex(int selectedOptionIndex) {
            this.selectedOptionIndex = selectedOptionIndex;
        }
    }

    public GameType getGameType() {
        return gameType;
    }

    public void setGameType(GameType gameType) {
        this.gameType = gameType;
    }

    public boolean isSuccess() {
        return isSuccess;
    }

    public void setSuccess(boolean success) {
        isSuccess = success;
    }

    public int getScore() {
        return score;
    }

    public void setScore(int score) {
        this.score = score;
    }

    public Integer getTime() {
        return time;
    }

    public void setTime(Integer time) {
        this.time = time;
    }

    public List<QuizAnswer> getAnswers() {
        return answers;
    }

    public void setAnswers(List<QuizAnswer> answers) {
        this.answers = answers;
    }
}
