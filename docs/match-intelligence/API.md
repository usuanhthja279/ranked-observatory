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

## Benchmarks — M6

### `GET /benchmarks`

Lists the authenticated owner's reference cohorts, including member-level import counts, unique match counts, identity availability and source-coverage counts. Cohort selection ranks and access-status statements are owner-entered, not independently verified.

### `POST /benchmarks`

Creates a private ten-member cohort. Requires a CSRF token. Body fields: `name`, supported `region` (`ap`, `eu`, `na`, `br`, `kr`, `latam`), `actId`, `leaderboardSnapshotDate`, `dataAccessStatus` (`UNCONFIRMED` or `OWNER_REPORTED_PERMISSION`), and exactly ten `members`. Each member has a unique `selectionRank` 1–10, `riotId`, optional `displayName`, and `matchesRequested` 1–20 (default 20). The latter status is only an owner statement; the API does not verify external permission or leaderboard rank.

### `GET /benchmarks/{cohortId}`

Returns one owner-private cohort and derived import/source-coverage metadata. Another account receives 404.

### `GET /players/{playerId}/benchmark-comparison?cohortId={id}&map={map}&mode={mode}&agent={agent}`

Returns comparison of the authenticated owner's imported player records against the specified owner-owned cohort. The cohort Act and ranked Competitive mode are required context; optional filters narrow by map, exact mode name and agent. The subject is omitted from the cohort distribution when their Riot ID matches a member. The response reports per-player reference median and quartiles, subject values, metric-specific player/match counts, and limitations. Reference distribution values are withheld until at least ten eligible reference players and twenty unique reference matches remain in the scenario. Quartiles are between-player spread, not confidence intervals. Unsupported context is not guessed.

## Expert annotations and M7 baseline

### `GET /matches/{matchId}/annotations`

Returns the authenticated owner's latest revision for each private annotation on an owned match. Other accounts receive 404.

### `POST /matches/{matchId}/annotations`

Requires CSRF. Creates a private annotation for an existing round. An optional event ID must belong to that same round. Required fields: `roundNumber`, `judgment`, `reason`, `insufficientEvidence`, and `confidence` (0–100). Optional fields: `eventId`, `eventTimeRaw`, `betterAlternative`, HTTPS-only `vodUrl`, and an opaque `disagreementGroup`. VOD links are stored only and never fetched. Annotator and guideline version are server-recorded.

### `PUT /annotations/{annotationId}`

Requires CSRF and `{ "expectedRevision": n, "annotation": { ... } }`. Appends a correction revision; stale revision returns 409. A reviewer can revise only their own annotation and must still own the referenced match.

### `DELETE /annotations/{annotationId}?expectedRevision={n}`

Requires CSRF. Appends a `WITHDRAWN` revision and retains previous revisions. A stale revision returns 409.

### `GET /players/{playerId}/baseline-evaluation`

Runs an opt-in CPU-only logistic-regression evaluation on this account's imported selected-player matches. It uses pre-round score state, round index and reported side; rows from one match remain in one deterministic holdout group. The response includes model/feature/split versions, train/test match and row counts, accuracy, class precision/recall, Brier score, calibration error and false-positive rate. Below 10 distinct matches or 200 supported round rows it returns `INSUFFICIENT_EVIDENCE` and no predictive metrics. The fitted model is not persisted or activated for inference.

The Coaching tab's copy action creates a local structured evidence brief only. It does not send data to an AI service.
