# Operations, performance, and validation

## Operational boundaries

The observatory uses port 8090 and its own database/schema configuration. Use the existing `scripts/Start-LeaderboardService.ps1` for approved startup work. Do not touch the independent port 8080 service, match-study collector, live pause markers, archive cursors, or Cloudflare tunnel during a documentation/UI-only task.

Relevant artifacts include `build/service.log`, `service-error.log`, `service.pid`, live checkpoints, and `riot-review-availability.json`. Their existence is not proof of current health. Check process identity, HTTP response, database connectivity, and newest completed publication independently. Never print local credential files.

## Health model

Track application availability, database availability, collector progress, and published-data age separately. A responsive dashboard with a one-hour-old snapshot can be healthy as a web service but delayed as a data product. A running process with no advancing page/cursor/source timestamp may be stuck or receiving stale upstream content.

Status should include run ID, source/act, pages completed, last successful page time, retries, next retry, pending trigger, next scheduled trigger, latest publication, and reason for pause. ETA should be a rolling estimate with completed/remaining pages and known source-size uncertainty, not an exact promise.

The live scheduler coalesces overlapping runs rather than queueing unlimited missed intervals. If a run exceeds its interval, show that clearly. Historical completion can re-enable live collection; this behavior should be documented and surfaced in status.

## Cache contract

- Current dashboard data: completed-publication generation; failed extraction leaves prior data available.
- Historical data: revision covering all published corrections and acts used by a response.
- Profiles: successful result 15 minutes; shorter unavailable-result caching is separate.
- Status: uncached.
- Browser assets: versioned assets or reliable cache validators, so old JS does not coexist with a new response contract.

The configured Caffeine weight budget is approximately 16 MiB according to a custom serialized-size estimate. It is not an actual JVM heap ceiling. Measure retained memory before changing it.

## Measurement plan, not measurements already taken

Capture cold and warm overview, region, country, historical comparison, search, and player-card journeys. Record API duration, database time, rows read, response bytes, browser useful-content time, and request count. Repeat with collection idle and active. Use a fixed dataset revision and realistic country/act selections. Report sample count, median and p95; do not claim p95 from a handful of requests.

Measure heap, GC, pool utilization/waits, database/table/index size, and rows added per completed run. Estimate monthly storage using measured daily growth, not source JSON size alone. Avoid expensive full-history scans on the production database just to produce a report.

## Storage reduction without losing required history

Separate player identity/dimensions from observations; compress cold exports; retain source timestamps and provenance. An unchanged-state interval representation can reduce repeated player rows, but must preserve snapshot membership, wins/RR changes, absence evidence, act boundaries, and source freshness. Keep published aggregates rebuildable from preserved facts. Downsampling graphs alone is not sufficient if movement calculations need the original sequence.

Start with a duplicate-storage inventory and a backup. Prototype compaction on a copy; compare all supported historical queries before production migration. Do not prune observations until both restore and semantic equivalence have been demonstrated.

## Focused validation backlog

Existing inventory: 13 Java test classes, 37 `@Test` occurrences; no tests executed for this review. Prioritize meaningful invariants:

- Atomic publication: failed/partial run never replaces completed public data.
- Resume: a crash after a page commit neither loses nor double-counts players.
- Archive correction: same-page-count changes invalidate cached reads.
- Inclusive range: all previous acts plus current; unknown/reversed ranges rejected.
- Identity and movement: rename, counter reset, stale source, missing regional scan, and low-RR absence remain distinct.
- Allocation: nonnegative integers, exact tier conservation, deterministic ties, neutral missing access evidence.
- UI: out-of-order responses, error after success, single/multiple countries, all three chart shapes, and small-screen picker positioning.

## Recovery and public availability

Document database backup cadence, retention, restore location, and owner. Verify recovery in an isolated environment. Store credentials outside source and redact logs. A local tunnel depends on the workstation, network, service, and tunnel process; a review endpoint needs a durable hosting/hostname plan. Preserve a submitted hostname and verification response while it remains in use; do not silently replace it with a new URL.

Update the root README when behavior changes. Keep a short operational checklist tied to real scripts, rather than adding independent restart scripts with overlapping ownership.

