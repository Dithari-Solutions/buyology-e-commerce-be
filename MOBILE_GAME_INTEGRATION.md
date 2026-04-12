# Mobile App Daily Game Integration Guide

## Overview
Implement the Daily Game and Quiz feature in the mobile e-commerce application.

## Mobile Features
1. **Daily Game Access**:
   - Check `GET /api/game/daily` when the user visits the Game section.
   - Show either the `QUIZ` screen or the `MINI_GAME` screen.

2. **Quiz Logic**:
   - Fetch active questions: `GET /api/game/quiz`.
   - Provide a native mobile UI for the quiz (multi-choice).
   - Filter translations by user language setting (`az`, `en`, `ar`).
   - Post results to `POST /api/game/submit`.

3. **Mini-game (Matching/Zip)**:
   - Develop these mini-games natively (or use WebView if needed).
   - Post success/fail status and user score to `POST /api/game/submit`.
   - **Reward**: Successful games award **10 tokens** to the user's account.

4. **Leaderboard & Streaks**:
   - Native UI for daily leaderboard: `GET /api/game/leaderboard/daily`.
   - Native UI for streak leaderboard: `GET /api/game/leaderboard/streak`.
   - Show current user streak and total tokens (tokens can be retrieved from user profile endpoint).

## API References
- See `docs/api_handoff/DAILY_GAME_API_HANDOFF.md` for full details.
