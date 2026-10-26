# One-time historical leaderboard archive

Assessed 26 October 2026, Asia/Kolkata. This records the source audit and running implementation. The full historical crawl is asynchronous; its per-act/source progress is shown in the dashboard's Data confidence panel.

## What was verified

Tracker's actual leaderboard filter catalogue advertises 36 PC acts: Episode 2 Act I through V26 Act VI. Of these, 35 have ended, through V26 Act V. The current V26 Act VI remains the responsibility of the existing scheduled collector.

The public season metadata catalogue includes Episode 1, but Tracker's ranked leaderboard catalogue does not offer its three acts. Episodes/year seasons are parents of acts, not additional ladders to download. Riot introduced the comparable regional RR leaderboards in Episode 2, January 2021.

The bounded live audit examined 43 pages:

- First global page of every one of the 35 completed acts: all returned 100 player rows with matching act, platform, region and offset metadata.
- First page of each of the six regions for Episode 2 Act I: all returned 100 rows.
- Episode 2 Act I global page 100: returned 100 rows with the historical `Immortal` tier.
- Episode 2 Act I Asia-Pacific page 100: returned a valid empty items array, demonstrating an explicit pagination end at that sampled offset. It does not establish the precise earlier final page.

These observations confirm archive availability at the sampled pages. They do not establish completeness of every page or every region for every act. Availability checks must continue during the full crawl.

Worldwide in this proposal means Tracker's PC Global view plus Asia-Pacific, Europe, North America, South Korea, Brazil and Latin America. China is absent from these region options. Console is a separate platform and is not included. The population is players present on the archived high-rank ladders, rather than all VALORANT accounts or all competitive ranks.

## Data we can archive

Observed rows provide Riot ID, regional shard, reported country when present, displayed tier, RR, act wins and leaderboard position. Fetching both global and regional sources preserves both provider global position and regional position. Optional avatar/agent metadata exists, but its historical meaning and completeness have not been established; it should not be treated as full act gameplay evidence.

Country totals, tier mixes, RR distributions and an observed end-of-act Radiant cutoff can be calculated after each act's regional ladder has been completely retrieved and checked. A cutoff is one archived observation per act and region, not a reconstructed daily cutoff history.

Tracker Global is a provider ranking across regional ladders. Store its provenance and preserve real regional ranks. RR thresholds and ranked rules have changed, so equal RR across regions/years is not proof of equal competitive difficulty.

## History that this download cannot reconstruct

- Daily or hourly RR trajectories within an old act.
- Exact promotions/demotions, country active players, wins during an arbitrary time window, losses, total matches or loss streaks from a single standings snapshot.
- Every player who participated during an act but is absent from its archived board.
- Full historical match and round data. Match-history collection is a separate, much larger project with its own source retention and access constraints.
- Historical country residence or nationality. A country label obtained today may be mutable profile metadata; the archive response does not establish when that label applied.

An RR decrease between two act finishes cannot be counted as a loss. Act resets, placements, eligibility and inactive leaderboard removal make that inference invalid. The existing within-act adjacent-snapshot loss assumption must not be applied across acts.

The old archive's `lastUpdated` value was a 2026 provider refresh timestamp even for Episode 2 Act I. Store season end, retrieval time and source refresh time separately. A successful retrieval today is not evidence that the ranking was observed today or captured precisely at the original act ending.

## Implemented archive

The service keeps the existing live collector fixed to its configured act and runs an independent low-priority archive for the 35 completed Tracker PC acts. It fetches the Global ladder and each of the six Tracker regions. A shared request pacer gives queued live collection requests priority over archive requests.

Flyway migration V7 creates a separate act catalogue, per-source progress, per-page checkpoints and normalized player rows. Each response and its checkpoint commit together. A process restart retries an incomplete page from its saved cursor; transient failures pause a source with a retry time and do not mark it complete. The archive has its own worker and tables, so its rows never enter or get marked failed by live snapshot publication.

The shared header act selector works on overview, global, regional and country pages, and carries the selected act through in-app links. The existing live act remains the default. Archived pages show a single end-of-act snapshot, keep the old unified Immortal rank separate, and suppress current-act estimates, movement, hourly trends, match counts and live profile details. Cache keys include archive page progress so results refresh while the crawl advances.

Fetch Global and the six regional ladders once per act, then merge overlapping identities into one canonical player-act record with both ranks. Country data already accompanies the rows; crawling each country separately would multiply work without creating missing country evidence. Preserve source-specific evidence and flag conflicts instead of silently combining incompatible RR/tier observations.

Verify explicit source pagination completion for all seven scopes. A missing payload, HTTP error, repeated full page or hard page cap must not be interpreted as a successful end. The live collector's 2,000-page setting must not silently truncate a larger archived ladder.

Use a stable player identifier where the source supplies one. The sampled board uses Riot IDs as identifiers; names and tags can change. A case-folded name alone cannot establish that a player in 2021 and one in 2026 are the same account. Historical identity continuity needs stable-ID/alias evidence, otherwise label it uncertain.

The act selector uses request-scoped API parameters. Archived response caches are keyed by act and page-progress version; the active act retains its existing snapshot cache rules. Historical views show archived standings and act totals; time-window movement and activity controls are unavailable because a single archived leaderboard does not contain those histories.

## Historical rank and country handling

Episode 2 initially had one Immortal rank. Riot restored Immortal 1/2/3 in patch 3.05. The source's old page actually returns `Immortal`, while current country totals primarily count exact `Immortal 1`, `Immortal 2` and `Immortal 3` strings. Without historical handling, old players would disappear from those three columns.

Preserve `Immortal (unified)` as its own historical category. Do not invent modern subdivisions from today's RR thresholds. Version rank rules and cutoff definitions by act; patch 5.0 also changed the upper-rank requirements.

Use observed reported countries first, then calculate optional unknown allocations within each historical act and its valid rank categories. Store estimates separately with model version and coverage. Do not copy current-act country shares, current server access assumptions or current profile countries into old acts as if they were historical observations.

Unknown-country coverage can be substantial: the oldest act's sampled top 100 regional rows contained 65 unknown countries in AP and 99 in KR. These are first-page samples, not whole-ladder percentages. Every act/region needs its own coverage measure before country growth is interpreted. Changes in country reporting must not be presented as changes in national competitive participation.

## Conditional capacity estimate

The live database currently contains 264 completed snapshots and 14,325,799 player observations. Its player table uses 2,031,124,480 bytes and its indexes 2,027,339,776 bytes: approximately 283 bytes per stored observation at the current layout and occupancy. These measurements exclude other tables, WAL and backups.

If every past act were the same size as the latest observed current-act snapshot (54,347 merged players), 35 archives would contain approximately 1,902,145 merged records. At the measured existing table-plus-index ratio, that is about 539 MB of additional database storage. Archived ladder sizes differ, so this is a benchmark rather than a forecast or a measured archive count.

Using the observed 1,084-page global-plus-regional live run as the same conditional benchmark gives approximately 37,940 requests. The audit's median HTTP duration was 1.34 seconds. At the audit's sequential one-second gap, that request volume would take roughly 25 hours before long backoffs and live-work priority; parallelism can shorten it only within a shared request budget. Determine the actual ETA from discovered page counts and sustained throughput during the crawl.

The audit's compact seven-field normalized records compressed to a median of roughly 23 bytes per player in 100-player pages. The benchmark population would therefore be roughly 43 MB of compressed normalized checkpoints. This excludes provenance, extra fields and full source evidence. Keep compact page payloads, request metadata and checksums rather than complete HTML for every page. Provider HTML contains large unrelated application state.

Process pages incrementally and batch database writes. There is no need to hold all acts or all player records in memory. Materialize compact act/region/country aggregates after publication for fast archive page loads.

## Current rollout and limits

The service seeds all 35 completed acts, newest first, and starts fetching after application readiness so the live scan can establish its queue first. An act is complete only when all seven source ladders reach a validated pagination end. The archive stores observed RR, tier, source rank, act wins, source refresh time, regional shard and reported country. It does not infer historical country, split legacy unified Immortal, or fabricate old movement or match history.

Total duration depends on actual page counts, Tracker availability and how much of the shared request budget the live 20-minute collector uses. The archive intentionally has lower priority, so completion may take many hours. A blocked or unavailable source remains visibly partial and retries from its checkpoint; it is never treated as an empty ladder.

## Evidence

- Local live-source audit: `build/historical-act-audit/availability-audit.json` (43 sampled pages, including normalized rows and exact source filters).
- Tracker-provided act and region options: `act-options.json` and `region-options.json` in the same directory.
- Public season catalogue: https://valorant-api.com/v1/seasons (community metadata; reconciled against Tracker's own offered act IDs).
- Calculated capacity measurements: `build/historical-act-audit/analysis-summary.json`.
- Tracker sample: https://tracker.gg/valorant/leaderboards/ranked/all/default?platform=pc&region=global&act=97b6e739-44cc-ffa7-49ad-398ba502ceb0&page=1
- Riot's introduction of regional leaderboards and unified Immortal: https://playvalorant.com/en-us/news/game-updates/valorant-episode-2-act-i-competitive-changes/
- Restoration of Immortal tiers: https://playvalorant.com/en-us/news/game-updates/valorant-patch-notes-3-05/
- Later rank requirement changes: https://playvalorant.com/en-us/news/game-updates/valorant-patch-notes-5-0/
- Official per-act regional leaderboard endpoint: https://developer.riotgames.com/docs/valorant

