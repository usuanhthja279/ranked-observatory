# Technical findings

Paths below are relative to `leaderboard-service/src/main/`. Java files are under `java/com/valorant/leaderboard/`. Findings distinguish a source-level defect from a risk that still requires runtime measurement. P1 means address in the next reliability phase; it does not imply an emergency outage.

## P1: historical trend requests can overwrite newer selections

Evidence: `resources/static/act-trends.js`, `loadSeries` around lines 102–110. There is no request-generation guard or cancellation before applying asynchronous results. `state.acts` is also reassigned from the selected response. The error path can repaint retained rows.

Impact: quickly changing ranges, countries, or estimates can display a response for a superseded selection. A failed new request can leave the previous chart visible beside an error or newer controls.

Fix: keep catalog state separate from response state; capture an immutable query; increment a request ID; apply only the latest response. Render chart and table from one derived result. Show explicit stale/error status or clear both together.

Acceptance: intentionally reverse two response completion orders; only the newer query appears. Chart endpoints equal table endpoints for every tier and estimate mode. Failure never silently relabels retained data.

## P1: full historical range boundary excludes 35 previous acts plus current

Evidence: `HistoricalTrendsQueryService.series` rejects `to-from>=35`. An inclusive 36-point selection has index difference 35.

Fix: validate inclusive count against the supported catalog size or an explicitly documented maximum that includes current. Do not remove all request bounds.

Acceptance: earliest of 35 archives through current is accepted; invalid/reversed/unknown ranges remain rejected.

## P1: archive cache revision does not represent all changes

Evidence: `DashboardCache.java` lines 19–36. Archive revision is a sum of page counts for the first recognized archive UUID argument. Current reads track the latest COMPLETE run ID.

Risk: a same-page-count archive correction, country enrichment, or another act within a cached range can change data without changing that revision. The cache has no ordinary expiry to repair this promptly.

Fix: introduce a monotonic publication revision covering all relevant archived facts, model policy, and country enrichment. Use that revision in keys. Keep status uncached and profile success TTL at 15 minutes.

Acceptance: repairing data with unchanged page count invalidates the affected response; a newly published act refreshes a cached range; a failed collection does not evict valid published data unnecessarily.

## P1: historical worker ownership is process-local

Evidence: `HistoricalActArchiveService` has a local running flag and startup RUNNING-to-PENDING reset; the inspected flow lacks the database advisory ownership used by `GlobalLeaderboardService`.

Risk: a second service instance against the same database can reset or compete for work owned by the first. This is a deployment risk, not a claim it is happening now.

Fix: database lease/advisory ownership per historical worker or source, with owner identity and expiry. Reclaim only abandoned work.

Acceptance: two instances cannot fetch the same owned source concurrently; a dead owner can be resumed without losing committed pages.

## P1: analytics provenance is inconsistent across surfaces

Evidence: live browser weighting in `country-model.js`, historical proportional allocation in `HistoricalTrendsQueryService`/trend presentation, RR-based reconstruction for combined tiers, and different current-versus-archive collection bases.

Fix: make source basis, coverage, estimate method/version, threshold profile, and selected snapshot explicit response fields. A shared estimate label alone is insufficient. See [data review](DATA_QUALITY_AND_MODELS.md).

Acceptance: a displayed value can be traced to observed count, reconstructed tier count, allocated unknown count, and publication revision.

## P2: rank thresholds depend on catalog ordering

Evidence: repeated `act_order>=32` expressions in archive/trend SQL; current catalog insertion uses order zero. A current combined-tier record would therefore choose the older backcast branch. Explicitly reported current tiers avoid this branch.

Fix: use a named, versioned rules table keyed by act/region or effective period. Separate reported tier from reconstructed tier and keep unsupported periods labeled as backcast.

Acceptance: inserting/reordering catalog entries cannot change tier rules; every boundary RR has one deterministic tier; current combined records use the intended profile.

## P2: resource and collector resilience

- The live collector holds a database advisory-lock connection across remote collection. With configured pool size five this consumes a scarce slot. Measure pool wait time before altering concurrency; use a dedicated lock connection or lease design if necessary.
- Historical ingestion recomputes source row counts per page. Measure query cost as archives grow; use transactionally maintained counters plus periodic reconciliation if scans become material.
- The historical loop relies on a short terminal page and a safety cap. Add repeat-page fingerprints, nonprogress detection, and explicit capped/partial status instead of treating every terminal response as equivalent coverage.
- Transport uses decompression/read-all patterns; the profile size check occurs after reading. Bound compressed and decompressed bytes before parsing to contain malformed upstream responses.
- A process-local pacer does not coordinate multiple deployments. Any multi-instance rollout needs shared upstream rate control.

## P2: public enrichment needs abuse controls

Evidence: read routes are public; profile requests can cause upstream work; profile concurrency is bounded but that is not a per-client budget. Collection POST requires ADMIN; CSRF and BCrypt are useful existing protections.

Fix: bounded per-client requests, per-player single-flight profile loading, negative-cache handling, payload limits, and safe error output. Review registration throttling, session settings under HTTPS, and authorization of future management routes. Do not log cookies or credentials.

Acceptance: repeated identical profile misses produce one upstream fetch; excessive callers receive a controlled response; public users cannot start administrative work. This review does not establish an exploitable vulnerability or validate production security headers.

## P2: maintainability and verification

Large, densely formatted services and frontend files make ownership and side effects hard to inspect. Extract transport, publication, metrics, and UI state incrementally; avoid a framework rewrite before fixing correctness. Move duplicated rules into shared server-side contracts or versioned fixtures. Existing tests cover several parsing, observation, pacing, and persistence paths, but the inventory does not establish historical trend or browser race coverage.

## P3: documentation and bundled assets

Update scheduling descriptions to match startup-relative fixed-rate behavior. Document what source COMPLETE means. Audit bundled diagnostic JSON and screenshots before public packaging; their existence does not prove they are requested on page load. Establish an asset budget and image sizing policy based on actual network measurements.

