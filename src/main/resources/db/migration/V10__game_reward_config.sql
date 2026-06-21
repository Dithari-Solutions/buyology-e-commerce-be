-- Single-row, admin-tunable token rewards for the daily game + quiz.
-- Replaces the hardcoded TOKENS_PER_WIN constant in GameService.
CREATE TABLE IF NOT EXISTS game_reward_config (
    id                UUID        PRIMARY KEY,
    quiz_reward       INTEGER     NOT NULL DEFAULT 10,
    mini_game_reward  INTEGER     NOT NULL DEFAULT 10,
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Seed the single row so the config always exists (matches the V9 pattern).
INSERT INTO game_reward_config (id, quiz_reward, mini_game_reward, updated_at)
SELECT gen_random_uuid(), 10, 10, now()
WHERE NOT EXISTS (SELECT 1 FROM game_reward_config);
