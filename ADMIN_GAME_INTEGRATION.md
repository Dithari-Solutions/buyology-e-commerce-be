# Admin Dashboard Game Integration Guide

## Overview
Administrative control for the daily games and quiz content.

## Admin Features
1. **Daily Game Config**:
   - Provide a date-picker and a radio button for `QUIZ` or `MINI_GAME`.
   - Post configuration to `POST /api/admin/game/config`.

2. **Quiz Question Management**:
   - List all quiz questions: `GET /api/admin/game/quiz`.
   - Create new quiz questions: `POST /api/admin/game/quiz`.
   - Multi-language support: provide forms for `az`, `en`, and `ar` versions of each question and its 4 options.
   - Set the `correctOptionIndex` (0-3).

3. **Leaderboard Monitoring**:
   - View global daily and streak leaderboards.

## API References
- See `docs/api_handoff/DAILY_GAME_API_HANDOFF.md` for full details.
