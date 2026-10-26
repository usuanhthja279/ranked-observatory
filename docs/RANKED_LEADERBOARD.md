> Historical AP country-collector notes. The active service now uses world snapshots; see [current service documentation](../README.md). Existing AP observations are preserved as legacy history.

# AP ranked leaderboard

Dashboard: `http://localhost:8090/leaderboard.html`, served by the independent `leaderboard-service` application. See `README.md` for startup and database configuration. The original tracker does not collect leaderboard data.

## Collection

The app collects PC/AP data for act `d816f426-48ea-f052-117f-9697a155b319` at :00 and :30 UTC (also :00/:30 in IST), while running. The service's own administrator login and CSRF-protected Collect now action starts a collection immediately. A single worker keeps this separate from the stream collection scheduler; a PostgreSQL advisory lock prevents concurrent app instances collecting together.

Tracker HTML contains `window.__INITIAL_STATE__`. The Java HTTP client reads this JSON without executing scripts. It validates act, region, platform, country, offset, player fields and page size, then follows 100-row pages until a shorter page. An exactly-full last page requires one empty-page request. Country filters come from Tracker's Asia and Oceania catalogue (74 entries); they do not assert membership in the AP shard. Korea-labelled accounts on AP are included; this does not query the separate KR leaderboard.

Configuration environment variables: `LEADERBOARD_ENABLED` (true), `LEADERBOARD_ACT`, `LEADERBOARD_CRON` (`0 0,30 * * * *`), `LEADERBOARD_MAX_PAGES` (200), `LEADERBOARD_REQUEST_DELAY_MS` (1000; minimum effective delay 250ms).

HTTP errors/challenges, mismatched responses, conflicting duplicate statistics, repeated full pages without new players, or page-limit exhaustion fail that country run. No partial player snapshot is published. Other countries proceed. Compatible duplicates within or across pages are merged case-insensitively when country, RR, tier and wins match. Raw row counts still control pagination, so merging a full page never truncates the crawl. The first observed row and source time are retained. Disagreeing ranks are marked uncertain, excluded from top-100/top-500 aggregates, and rank gain is suppressed when either compared observation has an uncertain rank. Conflicting country/RR/tier/wins fail the snapshot; retry occurs at the next scheduled cycle. A repeated full page with no new identities also fails instead of silently accepting broken pagination. Never bypass challenges or substitute zero counts for blocked requests.

## History and comparisons

The service's Flyway V1 adds `leaderboard_runs` and `leaderboard_players`, independent of stream snapshots and their inactive-hours cleanup. Each successful country crawl persists atomically. Failed runs store diagnostics and partial observed counts only. The UI compares each country's latest complete snapshot and explicitly displays its timestamp. Cross-country totals combine these different observation windows, not an atomic regional snapshot.

Player RR and rank deltas compare the two latest complete country runs; a positive rank gain means a lower/better regional position. Baseline, entered and returning players are distinguished. History is keyed by displayed Riot identifier because this page does not expose a reliable stable account identifier; renames can split history. Missing players are not assumed demoted. History APIs return the latest 1,000 observations. Flyway V2 adds raw-row/duplicate/uncertain-rank diagnostics to new collection runs and an uncertainty flag to player observations. Earlier snapshots retain their original data; diagnostics are recorded from V2 onward. Raw history is retained; capacity planning and a retention policy are needed for long-running deployments.

Country labels are user-associated Tracker locations, not verified citizenship. Unknown/unlabelled players are excluded from country-filtered results. Country comparisons describe this observed population only. The source can be cached: source update time is stored separately from collection time. The configured act stays pinned until changed; the collector does not silently roll over to another act.

## Initial audit

See `leaderboard-country-audit.json` and `leaderboard-country-pages.csv` in this folder. All 74 filters were checked on 20 October 2026. 40 were nonempty, totalling 84 nonempty pages. Japan: 14; Australia, Korea, Philippines: 6 each; India, Thailand: 4 each; China, Singapore, Taiwan, Vietnam: 3 each; Indonesia, Malaysia: 2 each. Remaining nonempty filters had one page; empty filters had zero data pages (their first page was still requested). India and Japan included overlapping rows during pagination, recorded explicitly. These counts describe the audit window and will change. The dashboard includes the audit separately from live collection results.

Recheck with `python scripts/leaderboard/check_pages.py`; use `--retry-failed` to resume failed filters. It uses two paced workers and curl with the supplied browser headers. Scraping availability is network-dependent and can change.

## API

- GET `/api/leaderboard/status`: configuration and latest attempt per country.
- GET `/api/leaderboard/countries`: latest complete country aggregates.
- GET `/api/leaderboard/players?country=in`: latest players with deltas.
- GET `/api/leaderboard/history?player=venka%23111`: player observations.
- GET `/api/leaderboard/country-history?country=in`: historical country totals.
- GET `/api/leaderboard/csrf`: token for same-origin collection requests.
- POST `/api/leaderboard/collect`: authenticated collection (all countries by default; `?country=in&country=ph` limits a manual run to selected countries); 202 accepted, 409 disabled/already running.

Start `gradlew.bat :leaderboard-service:bootRun` with its database environment variables to apply the service's V1 migration in its own schema. The root tracker V4 migration is retained unchanged for compatibility only; its tables are no longer used by this service. Existing history is not automatically copied to the new service schema. Existing stream/esports data is unaffected. The first scheduled run occurs at the next half-hour; Collect now can start earlier after sign-in.

## Verification

Run `gradlew.bat :leaderboard-service:test`. Set `LEADERBOARD_LIVE_TEST=true` to also exercise the actual Java HTTP client against Tracker. Optional PostgreSQL checks use `LEADERBOARD_TEST_DB_URL` and `LEADERBOARD_TEST_DB_USERNAME`/`LEADERBOARD_TEST_DB_PASSWORD`; they create and remove a uniquely named test schema without touching existing tables.

