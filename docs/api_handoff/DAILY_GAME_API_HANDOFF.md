# Daily Game & Quiz API Handoff

This document outlines the API endpoints and integration steps for the Daily Game and Quiz feature.

## Feature Overview
- **Daily Game Type**: Admin can choose between `QUIZ` or `MINI_GAME` for each day.
- **Quizzes**: Multi-language support (AZ, EN, AR). Admin creates questions with points.
- **Mini-Games**: Handled primarily on the frontend. Success/Fail and Score are sent back to the API.
- **Streaks**: Users maintain a daily streak by playing either game type.
- **Leaderboards**: 
  - Daily Leaderboard (based on today's scores).
  - Streak Leaderboard (based on current user streaks).

---

## 1. Endpoints for Users (Website & Mobile)

### Get Current Daily Game Type
Returns whether today's game is a Quiz or a Mini-game.
- **URL**: `GET /api/game/daily`
- **Response**: `ApiResponse<GameType>` (QUIZ or MINI_GAME)

### Get Active Quiz Questions
Fetch the list of active quiz questions for today.
- **URL**: `GET /api/game/quiz`
- **Response**: `ApiResponse<List<QuizQuestionResponse>>`
- **Structure**:
```json
{
  "id": "uuid",
  "correctOptionIndex": 0,
  "points": 10,
  "translations": [
    {
      "language": "en",
      "questionText": "What is the capital of Azerbaijan?",
      "optionA": "Baku",
      "optionB": "London",
      "optionC": "Paris",
      "optionD": "Tokyo"
    }
  ]
}
```

### Submit Game/Quiz Result
Send the result of the play session. Only one submission per day is allowed.
- **URL**: `POST /api/game/submit`
- **Request Body**:
```json
{
  "gameType": "QUIZ",
  "isSuccess": true,
  "score": 50
}
```

### Get Daily Leaderboard
- **URL**: `GET /api/game/leaderboard/daily`
- **Response**: `ApiResponse<List<LeaderboardResponse>>`

### Get Streak Leaderboard
- **URL**: `GET /api/game/leaderboard/streak`
- **Response**: `ApiResponse<List<LeaderboardResponse>>`

---

## 2. Endpoints for Admin

### Configure Daily Game
Set the game type for a specific date.
- **URL**: `POST /api/admin/game/config`
- **Request Body**:
```json
{
  "gameDate": "2026-04-12",
  "gameType": "QUIZ"
}
```

### Create Quiz Question
- **URL**: `POST /api/admin/game/quiz`
- **Request Body**:
```json
{
  "correctOptionIndex": 0,
  "points": 10,
  "isActive": true,
  "translations": [
    {
      "language": "az",
      "questionText": "Azerbaycanin paytaxti haradir?",
      "optionA": "Baki",
      "optionB": "Gence",
      "optionC": "Sumqayit",
      "optionD": "Naxcivan"
    },
    {
      "language": "en",
      "questionText": "What is the capital of Azerbaijan?",
      "optionA": "Baku",
      "optionB": "Ganja",
      "optionC": "Sumqayit",
      "optionD": "Nakhchivan"
    }
  ]
}
```

---

## 3. Integration Logic

### Frontend (Website/Mobile)
1. Call `GET /api/game/daily` to see what to show.
2. If `QUIZ`, call `GET /api/game/quiz`, show questions one by one, calculate total score based on correct answers.
3. If `MINI_GAME`, show the matching/zip game.
4. On finish, call `POST /api/game/submit` with the results.
5. Display current streak and leaderboards.

### Admin Panel
1. Provide a calendar/date picker to set `GameType` for future dates.
2. Provide a form to create/edit Quiz questions with multiple translation fields.
