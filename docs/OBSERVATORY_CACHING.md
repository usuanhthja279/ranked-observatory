# Observatory caching

The standalone service follows the tracker's bounded Caffeine / synchronized-load approach.

Public read endpoints for regions, countries, insights, player lists and search, history,
player summaries, trends, country movement and activity overview share `dashboardReads`.
Keys contain the act, latest COMPLETE snapshot ID, controller method and all arguments.
A cheap indexed snapshot lookup still runs per request. Publishing a new snapshot selects
a new namespace immediately; old entries are cleared on the first read that observes the new completed snapshot. There is no time expiry; entries remain subject to bounded-memory eviction. Failed or in-progress
extractions do not change that namespace. Exceptions are not cached. Concurrent identical
requests share one load (`sync=true`). Different filters, countries and pages have distinct keys.

The shared cache has a 16 MiB accounting budget. Weight is three times serialized response
size plus a key/entry allowance. This is an estimate, not a hard JVM heap limit. Large
responses can be evicted immediately. No new unbounded player/search cache is introduced.

Status, CSRF, accounts, collection and the cache-status endpoint are uncached. Tracker profiles
retain the existing 256-entry cache (15 minutes success, 5 minutes unavailable). Country-model
evidence is retained until the completed snapshot or reported-profile-country revision changes.
This also invalidates recovered countries independently of snapshot publication. Bundled
scenery, flags and rank icons use a one-day public browser cache; APIs and application
scripts retain their existing freshness headers. JSON and text compression is enabled.

`GET /api/leaderboard/cache-status` reports hits, misses, loads, failures and evictions.
Benefits should be measured from these counters under normal usage, not assumed. This
reduces repeated database reads and computation, not extraction requests, historical disk
storage or collector peak memory. Raw capture checkpoints remain scoped to one collection;
they must never supply a later collection as if they were fresh observations.

Profile successes remain cached for 15 minutes; unavailable profiles retain the existing 5-minute negative cache so recovery is not delayed. Status stays uncached.

Build validation uses bootJar with tests excluded, as requested. No historical records are
deleted or downsampled by this change.

