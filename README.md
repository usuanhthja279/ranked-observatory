# Ranked Observatory

A standalone VALORANT ranked data observatory: Spring Boot dashboard and API, PostgreSQL persistence, historical Act archive, player and country analytics, extraction workers, operational scripts, and observatory documentation.

## Run locally (Windows / PowerShell)

Requirements: Java 21, Docker Desktop (for the included PostgreSQL service), and network access for the first Gradle build and public leaderboard collection.

1. Copy `config/leaderboard.local.example.json` to `config/leaderboard.local.json`; create the two local password files named there. These files are ignored by Git.
2. Set `LEADERBOARD_DB_PASSWORD` in the shell (Compose reads `.env` automatically) and start the isolated database with `docker compose up -d`.
3. Build with `./gradlew.bat bootJar`.
4. Start with `./scripts/Start-LeaderboardService.ps1 -Port 8090` and open <http://localhost:8090/leaderboard.html>.

To stop only this app, run `./scripts/Stop-LeaderboardService.ps1 -Port 8090`. The start script refuses to take over an occupied port. The application binds to loopback by default.

For an existing PostgreSQL installation, set `LEADERBOARD_DB_URL`, `LEADERBOARD_DB_USERNAME`, and `LEADERBOARD_DB_PASSWORD` instead of starting Compose. Flyway manages the separate `leaderboard` schema. Do not point this app at the stream tracker's database/schema.

## Repository contents

- `src/`, Gradle wrapper/build files, and Flyway migrations: independently buildable Spring Boot application.
- `scripts/`: lifecycle scripts, extraction monitor, fixed-hostname availability worker, and leaderboard-specific analysis/verification tools.
- `docs/`: observatory architecture, data quality, UX, operations, historical Act work, UI screenshots, and selected evidence.
- `compose.yml`: optional isolated PostgreSQL service with its own `leaderboard_data` volume.

The live database, accumulated observations, extraction checkpoints, logs, credentials, generated build files, and local Cloudflare executable are intentionally excluded. The source migrations create an empty schema; they do not contain the accumulated historical data. See `docs/REPOSITORY_SEPARATION.md` before moving existing data.

## Historical archive

Historical Act extraction is resumable and stored in PostgreSQL. `scripts/Watch-LeaderboardExtraction.ps1` displays live and historical progress. Avoid starting duplicate collectors against a running instance. The submitted Riot review URL remains tied to its existing Cloudflare hostname; the copied tunnel helper refuses to create a replacement quick tunnel.

## Project documentation

- `docs/ranked-observatory-review/README.md`
- `docs/HISTORICAL_ACT_BACKFILL_ANALYSIS.md`
- `docs/RANKED_OBSERVATORY_UI_GUIDE.md`
- `docs/REPOSITORY_SEPARATION.md`
