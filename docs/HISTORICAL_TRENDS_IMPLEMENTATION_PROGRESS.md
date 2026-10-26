# Historical Act Trends implementation progress

Last updated: 26 October 2026, Asia/Kolkata.

## Phase 1 — analytical foundation

Status: source prepared; database changes and runtime activation deferred.

### Read-only runtime observation before editing

- `build/service.log` showed PID 25232's historical archive worker actively advancing pages at 1:01 PM local time.
- `build/live-extraction-paused.flag` exists and says live extraction should resume after all historical ladders complete.
- Local process enumeration through CIM was denied by the host access policy. No attempt was made to stop or restart a process.
- No database connection, query, migration, service restart, or build was performed.

### Prepared changes

- Added `leaderboard-service/src/deferred-migrations/V8__historical_trend_foundation.sql`. It defines act/rank/subregion metadata, source audits, immutable per-act fact revisions, regional canonical entries, aggregates/cohorts, and a publication manifest/current pointer.
- The migration is intentionally under `src/deferred-migrations`, outside Flyway's configured `classpath:db/migration` location. It cannot be applied by an ordinary service startup. Move it to the Flyway folder only after the historical extraction is complete and migration execution has been scheduled.
- Added `HistoricalTrendFoundationService.java`, guarded by `@ConditionalOnProperty(... havingValue="true")`; the condition is false when absent. It refuses to audit or publish while any historical source is incomplete. When enabled later, it records capability and Global-vs-regional conflicts, canonicalizes only regional source rows, treats Global as supplementary evidence, builds aggregates, checks row reconciliation, and switches the publication pointer in one transaction.
- The staging path maps combined Immortal to `IMMORTAL_COMBINED`; missing subdivisions remain absent. It labels country as reported source metadata and account identity as a Riot-name key, not stable identity.

### Deliberately not done

- No application code outside the new foundation service was changed.
- No Java build, automated test, SQL execution, database migration, backfill, endpoint, deployment, service restart, or live collector change was made.
- The current extractor, concurrency/pacing, checkpoints, process, port 8080 app, match collector, tunnel, and live pause marker were left alone.

### Remaining before Phase 1 can be considered runtime-ready

1. After extraction completes, review this migration against the then-current Flyway version and database schema.
2. Validate the deferred SQL and aggregation plans against a copy or controlled environment; do not begin on the active database.
3. Resolve whether page-captured country is historical or only reported at retrieval time; current canonical provenance intentionally says only that it was reported in the archive source.
4. Decide whether source fingerprints should reuse unchanged immutable act revisions. The current preparatory publisher creates a new act revision for each explicit publication request.
5. Add operational failure reporting around failed staging and ensure the last published pointer remains visible on rollback.
6. Only after the archive is complete and separately authorized, move/apply the migration, compile, verify, and expose an administrative or scheduled publication path. The feature service currently has no controller or automatic job.

## Phase 2 — APIs and core Act Trends UI

Status: built and active on the local leaderboard service at port 8090. Historical extraction resumed from its saved checkpoint after deployment.

### Read-only API and UI changes

- Added `HistoricalTrendsQueryService` over the existing V7 archive tables. Its catalog includes an act only after all six regional sources are `COMPLETE` and each has a recorded terminal page with fewer than 100 rows. It derives this from the existing extraction checkpoints, so later acts appear without a new migration or collector change.
- Added `/api/leaderboard/trends/catalog`, `/api/leaderboard/trends/series`, and `/api/leaderboard/trends/players`. The catalog endpoint stays uncached; series/player reads use the dashboard cache and its archive-page version key.
- Added `/api/leaderboard/trends/capabilities`, a read-only rollup over completed regional archive rows. Its cache key follows the completed-act catalog so a newly completed act becomes visible even while live extraction is paused. It classifies each act/shard as `SPLIT`, `COMBINED_ONLY`, `MIXED`, or `NO_IMMORTAL_ROWS`, and provides separate observed counts without inventing subdivisions.
- The trend query reads the regional ladders only, because those rows are the canonical shard capture. The Tracker Global ladder is excluded from totals to avoid double-counting and mismatched coverage. It reports archived standings, not longitudinal within-act populations or matches.
- Added an Act Trends panel to the existing leaderboard page with from/to completed-act selectors, region and country scope, full-ladder / Top 100 / Top 500 / Top 1,000 cohorts per shard, tier selection, observed count / regional share / internal tier mix / first-act index measures, a trend chart, a scrollable comparison table, unknown-country coverage, and ten-player archived pages. Its range is separate from the page's current-act selector.
- Exact Immortal 1/2/3 values are rendered unavailable for an act/shard with combined-only Immortal capture. The UI explains unknown-country coverage and the archived standings semantics instead of presenting unavailable data as zero.
- Added a scrollable “Immortal tier availability by act” inventory covering every completed act and the selected region. It names combined-only and mixed act/shards and shows their observed combined/subtier counts; unknown detail is represented as unavailable rather than zero.
- Added RR-based estimated Immortal 1/2/3 distributions for combined-only archive rows, preserving each original source tier and count. Mixed/split rows retain their source tier counts and the estimated distribution combines exact source subtiers with only the inferred combined rows. The act coverage inventory and archived-player list expose the selected profile, RR boundaries, and per-player source-versus-estimated tier.
- Threshold profiles: pre-V26 Act II archive rows use the user-supplied Episode 3-era regional backcast (APAC I2/I3 80/200; NA 90/200; EU 100/200; Brazil 100/230; LATAM and South Korea 90/150), including Episodes 1–2 as explicitly requested. V26 Act II onward uses APAC/NA/EU/Brazil 100/200 and LATAM/South Korea 90/150. Immortal 3 is not converted to Radiant from RR because Radiant additionally depends on leaderboard placement. Riot's official 12.05 notes confirm that RR thresholds changed in V26 Act II to align regional requirements, but do not publish the numeric regional values in the notes; the displayed values come from the supplied regional table.

### Phase boundary and operational status

- No database schema changes, migration, backfill, or writes were introduced for Phase 2. Queries are read-only against V7.
- No request was made to pause, stop, or reconfigure the live or historical extractors. Their processes, pause marker, checkpoints, port 8080 app, match-study collector, and Cloudflare tunnel were not touched.
- Built the `bootJar` artifact with tests skipped, restarted only the leaderboard service on port 8090, and confirmed the page returns HTTP 200 plus the new capabilities endpoint's 156 act/region rows. The paused live collector remained paused; no port 8080 or match-study process was changed.
- The latest inventory query found 73 combined-only act/region rows across 13 acts: Episode 4 Act III; Episode 5 Acts I and II; Episode 6 Acts I, II and III; Episode 7 Acts I, II and III; Episode 8 Acts I, II and III have all six regions each; Episode 5 Act III has Latin America only. No mixed rows were found. The UI's region filter narrows this inventory while retaining all completed acts.
- The archive worker resumed from saved checkpoints after the 8090 service reload. It completed Episode 4 Act II's seven regional/global sources after restart and continues across the remaining acts. The latest status endpoint reports 28 of 35 acts complete; the exact rolling ETA varies as the worker switches source/act. Live current-act extraction remains paused.
- The submitted quick Cloudflare hostname remains unresolvable (`No such host is known`) for both the dashboard and API. The public deployment therefore remains unavailable until a permanent Cloudflare hostname/domain can be configured; the existing hostname and tunnel were not replaced.
- Source quality limitation: the archive stores end-of-act ladder captures only. “Trend” means act-to-act change in captured ladder counts, not the same individual players improving over time. Cross-act identity/cohort analysis, confidence classification, emerging-market explanations, verified dates/rules eras, and publication manifests remain Phase 1/3/4 work and are not inferred here.
- The optional subregion filter is deferred for historical trends until the act-effective grouping source is reviewed. Reusing a current tournament grouping for past acts could imply historical membership that the archive does not establish.
- Phase 1's deferred V8 migration and disabled service remain untouched. Phase 3 is not started.

## Phase 3 — explanations and emerging-scene indicators

Status: not started.

## Phase 4 — optional estimates and identity features

Status: not started.

