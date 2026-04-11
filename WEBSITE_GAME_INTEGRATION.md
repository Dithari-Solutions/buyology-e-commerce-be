# Website Daily Game Integration Guide

## Overview
Integrate the Daily Game and Quiz feature into the e-commerce website to increase user engagement.

## Steps
1. **Fetch Today's Game**:
   - Call `GET /api/game/daily` on page load.
   - If response is `QUIZ`, display the Quiz component.
   - If response is `MINI_GAME`, display the Mini-game component (matching or zip game).

2. **Quiz Implementation**:
   - Fetch questions from `GET /api/game/quiz`.
   - Filter translations by the current website language (`az`, `en`, `ar`).
   - Track user answers and calculate final score.
   - Submit result using `POST /api/game/submit`.

3. **Mini-game Implementation**:
   - The mini-game (matching/zip) logic is handled locally in JS.
   - On completion, submit success/fail status and final score to `POST /api/game/submit`.

4. **Leaderboard & Streaks**:
   - Show daily leaderboards using `GET /api/game/leaderboard/daily`.
   - Display the user's current streak and streak leaderboard.

## API References
- See `docs/api_handoff/DAILY_GAME_API_HANDOFF.md` for full details.
