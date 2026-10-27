# Match Intelligence Implementation Progress

## Phase M1 — Canonical model and validation

**Status: complete.** Added a bounded schema-v1 export parser, provider-scoped identity resolution, response validation, canonical in-memory records, source provenance, and reconciliation with machine-readable quality statuses. Unknown source segments and duplicate-looking events are preserved and disclosed. No raw export was copied into fixtures or logs.

### Verification

- `JAVA_HOME=C:\Users\usuan\.jdks\dragonwell-21.0.12 .\gradlew.bat test --tests com.valorant.leaderboard.matchintelligence.MatchExportParserTest --no-daemon` — passed, 9 focused tests.
- Local-only validation of the already committed sample export — passed: 5 accepted, 0 partial, 0 rejected. The temporary probe was removed and is not part of the repository.
- See [VERIFICATION.md](VERIFICATION.md) and [REAL_DATA_VALIDATION.md](REAL_DATA_VALIDATION.md) for actual details and limitations.

### Safety and phase boundary

The Observatory process and collection controls were not changed; no database, API, collector, or UI work was started. The existing sample dataset was read-only. Phase M2 and later phases have not started.

### Phase boundary

M2 was not started during the original M1 task. The user has now authorized continuation through M8; M2 implementation and verification status follows below.

## Phase M2 — Persistence and APIs

**Complete.** Added Flyway V11 storage for shared canonical match versions and normalized team, participant, round, player-round, damage, event, and position records. Imports are account-owned, CSRF-protected, bounded to 25 MiB/20 matches, serialized one at a time per app process, and committed transactionally. Raw exports are gzip-compressed, private, never returned by read APIs, expire after 90 days by default, and can be explicitly deleted. Owner-scoped import, match, round, and deletion APIs are implemented. Duplicate inserts now recover to a transaction savepoint so PostgreSQL can safely continue idempotent imports.

### Verification

- `JAVA_HOME=C:\Users\usuan\.jdks\dragonwell-21.0.12 .\gradlew.bat test --rerun-tasks --tests com.valorant.leaderboard.StandaloneApplicationTest --tests com.valorant.leaderboard.matchintelligence.MatchExportParserTest --tests com.valorant.leaderboard.matchintelligence.PostgresMatchIntelligenceIntegrationTest --no-daemon` — **BUILD SUCCESSFUL** against H2 and isolated native PostgreSQL: 28 focused tests passed. The opt-in real-export test separately passed against the same isolated PostgreSQL database and confirmed five complete imports.
- The H2-backed Spring flow verifies authentication, CSRF, user isolation, duplicate import deduplication, relational round/event/position persistence, round APIs, private raw deletion, owner deletion, and canonical cleanup after the last import link is removed.
- Native PostgreSQL 17 ran on `127.0.0.1:5435` using the ignored local cluster under `build/match-intelligence-test-postgres`; test schemas were uniquely named and dropped after each run. The local five-match export was imported and verified in a temporary schema, then deleted. The dedicated test password is held only in an ignored local file with a restricted Windows ACL. The existing database on port 5434 and the running application were not touched.

### M2 boundary and remaining acceptance

The dedicated PostgreSQL test checks V1–V11 migration, five-match sample ingestion, duplicate import/event deduplication, and owner deletion cleanup. Explicit forced SQL failure rollback and concurrent import behavior remain for M8. The application on port 8090 and its extraction state were not restarted or changed. No Tracker collection or admin API was called. See [ARCHITECTURE.md](ARCHITECTURE.md), [API.md](API.md), and [VERIFICATION.md](VERIFICATION.md).

## Phases M3–M8

Not started. M3 requires the M2 schema/API to pass the dedicated PostgreSQL acceptance test before collector integration. Subsequent phases remain gated on completing and verifying each preceding phase.
