# Match Intelligence Architecture

The module is being added inside the existing Spring Boot/JDBC application and shares its authentication, CSRF policy, datasource, and Flyway history. It remains separate from leaderboard collection and publication code.

## Data path

`authenticated browser import → bounded UTF-8 request → M1 export parser → reconciliation → one database transaction → owner-scoped query APIs`

M1 owns validation and in-memory canonical records. M2 stores shared provider/match versions in `mi_matches` and `mi_match_versions`, structured team/participant/round/economy/damage/event/position rows under each immutable version, and per-account import jobs and selected-player perspectives in `mi_import_jobs` and `mi_import_job_matches`.

The full source export is gzip-compressed in `mi_raw_imports`, scoped through its owner import job, excluded from all read APIs, and expires after a configurable number of days (default 90). Owners can delete the raw payload or the entire import. Expired raw payloads are pruned every six hours and when a new import starts. Deleting an import removes a canonical version only when no other owner's import refers to it.

Each import has a 25 MiB request bound and the existing M1 limit of 20 matches. M2 processes one request at a time per application instance and returns HTTP 429 when its import slot is occupied. Parsing happens before the database transaction; accepted canonical records, raw payload, per-match outcomes, and job summary commit together.

## Ownership and deduplication

All match-intelligence routes require an authenticated account; writes and deletes also use Spring's existing CSRF protection. Authorization is by authenticated account name and import-job ownership, never by a player ID supplied in the request path. A match or round is returned only if that account has a linked valid/partial import. Unknown and invalid entries remain visible in the owner's import report but do not create shared canonical records.

The provider match key is unique globally. Canonical version hashes exclude per-view selected-player fields and capture-specific timestamp/hash fields, so importing the same match for a second player links another owner perspective without duplicating canonical versions or normalized event rows. Actual source-response hashes and capture timestamps remain on the private import-to-match link.

The selected Riot ID in an export is source metadata. M2 validates it against the selected participant in each response, but does not claim cryptographic proof that the signed-in Observatory account owns that Riot account. A later consent/account-link feature is required for that claim.

## Phase boundaries

- M1: parser, identity, reconciliation, provenance and quality.
- M2: additive persistence, private raw retention, authenticated APIs and account ownership.
- M3: collector-to-API transfer and bounded repeat batches.
- M4: browser workspace and accessible match/round exploration.
- M5: deterministic tactical metrics with evidence references.
- M6: contextual, sample-aware benchmark cohorts.
- M7: evaluated ML baseline and evidence-grounded explanation interface.
- M8: cross-module release verification and operational readiness.

No match-intelligence import can start or pause a leaderboard collection job. M2 does not call Tracker, the collector, or any collection/admin endpoint.

M4 adds the account-wide `/match-analysis` workspace and the selected-player profile section. Both use owner-scoped APIs only. Navigation route: `/match-analysis` (shared `leaderboard.html` shell), or open a profile from `/players?player=...&region=...`. Match rows are followed by summary/detail GETs; no raw match payload is returned. The view surfaces API auth/loading/error/empty states and source-quality metadata. Dense tables scroll within their own container. Unknown values remain “Not reported”; events retain unverified source time units. No spatial map is rendered unless both source transform and a matching map asset can be validated.
