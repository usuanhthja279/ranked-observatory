# Repository separation and data handoff

This folder is the source-code home for Ranked Observatory. It contains the standalone Spring service, schema migrations, operational scripts, and observatory-only docs. It does not depend on the parent VALORANT tracker at runtime.

## Runtime boundaries

- The app owns a PostgreSQL `leaderboard` schema and Flyway history.
- The streamer's tracker database and credentials are not needed.
- The live database and historical extraction progress remain at their existing location to avoid interrupting current collection.
- Build output, local secrets, the cloudflared binary, and resumable filesystem checkpoints are excluded from version control.

## Moving accumulated data later

When ready to migrate runtime data, schedule a quiet window: stop only the leaderboard service, create a PostgreSQL custom-format backup of the current leaderboard database, restore it into a new PostgreSQL database, compare Flyway version and row counts, point the new app at that database, confirm the latest live snapshot and Act archive status, then resume the existing collection schedule. Keep the source database untouched until the restored app is verified. Do not copy a live PostgreSQL data directory or start two writers against the same archive.

## Hosting

The submitted Riot review URL is tied to its existing Cloudflare hostname. This repository copy must not start a replacement quick tunnel. Keep the existing tunnel process and hostname; for stable hosting, configure a named Cloudflare Tunnel/custom hostname before changing any registered product URL.

