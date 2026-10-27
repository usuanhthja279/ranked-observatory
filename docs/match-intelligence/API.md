# Match Intelligence API — M2

Base path: `/api/match-intelligence`. These routes use the existing Observatory session or HTTP Basic authentication. All POST/DELETE requests require the standard CSRF token. Every read is scoped to imports owned by the authenticated account.

## Import and lifecycle

### `POST /imports`

Content type: `application/json`. Body is the existing Tracker response-export schema v1. Synchronous response `201 Created` includes `jobId`, status, returned/accepted/partial/rejected counts, and export-level validation issues. The request is capped at 25 MiB and 20 match entries. One import is processed per application instance at a time; concurrent attempts receive `429 Too Many Requests`.

### `GET /imports/{jobId}`

Returns an owner-scoped job summary and one validation result per entry. It never returns the private compressed payload.

### `DELETE /imports/{jobId}/raw`

Deletes only that owner's retained compressed source export. It is safe to repeat.

### `DELETE /imports/{jobId}`

Deletes the owner's job and links. Canonical versions are pruned only after their last import link is gone; versions still used by another account remain available to that account.

## Query

### `GET /matches?page=0&size=20`

Returns a bounded page (`size` 1–100) of distinct matches available through the owner's imports. Match summaries omit event and raw source payloads.

### `GET /players/{playerId}/matches?page=0&size=20`

Returns the owner's matches whose imported selected Riot ID matches the decoded path value, case-insensitively. The path value is a filter only; it grants no access.

### `GET /matches/{matchId}`

Returns the shared canonical match version plus an owner-specific selected-player perspective, import quality/issues, and that import's capture provenance.

### `GET /matches/{matchId}/rounds`

Returns round summaries for an authorized match.

### `GET /matches/{matchId}/rounds/{roundNumber}`

Returns the round, participant round records, damage interactions, events, and discrete position snapshots. Times and coordinates retain their M1 raw-unit/coordinate-space labels.

## Errors and availability

Controller validation, missing/unauthorized match links, bad identifiers, oversized requests and busy imports return an HTTP error with a concise JSON `message`. Unauthorized reads return 401; CSRF failures return 403; a resource owned by another account is indistinguishable from a missing resource (404). Error responses never include raw exports.

These APIs have H2-backed Spring acceptance coverage. The native PostgreSQL migration/API acceptance test is opt-in and requires `LEADERBOARD_TEST_DB_URL`, `LEADERBOARD_TEST_DB_USERNAME`, and `LEADERBOARD_TEST_DB_PASSWORD` configured for the dedicated `ranked_observatory_redesign_test` database; it creates and drops only a unique schema there.
