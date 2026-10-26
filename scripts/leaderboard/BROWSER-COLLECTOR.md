# Brave match collector

This is a research collector, separate from the application's scheduled
leaderboard extraction. It opens normal Tracker match pages in installed Brave
and captures their match-detail network response using Python Playwright.
It requires Python 3.9+ and the `playwright` Python package. No separate Chromium
download is required because installed Brave supplies the executable.
The dependency version used here is recorded in `requirements-browser.txt`.

The dedicated browser profile is stored under
`data/browser-profiles/tracker-study`. It never copies the user's browser profile
or cookies. Study data and the browser profile are excluded by `/data/` in
`.gitignore`.

From the repository root, capture one missing match first:

```powershell
python scripts/leaderboard/browser_match_collector.py --probe
```

After that succeeds, resume the whole frozen plan and generate the report:

```powershell
python scripts/leaderboard/browser_match_collector.py --analyze
```

Optional arguments: `--study PATH`, `--brave PATH`, `--delay SECONDS` (default
five, minimum three). The analysis currently targets the frozen India study;
`--analyze` is for its default study directory.

Files already present in `matches/*.json.gz` are skipped. Each new response is
validated against its requested match ID, then saved atomically in the same
compressed format as the Java collector. A file lock prevents two Python
collectors using the same study simultaneously. Stop the old Java research
collector before running this one; the app's leaderboard collector is unrelated.

`browser-capture-status.json` and `capture-status.json` report current match,
download count, HTTP access failures and the next allowed retry time. A persisted
future retry deadline prevents an early restart. HTTP 429/5xx responses respect
Retry-After; absent a usable header they wait two minutes. Access-denied,
challenge and missing-response cases pause collection and save a screenshot
instead of attempting to bypass browser/site protections. No stealth patches,
proxy rotation or challenge solving are included.
One transient missing-response failure is retried after thirty seconds. Browser
network diagnostics also record HTTP status and CORS failure details when the
page cannot expose its response, without storing response cookies or headers
other than the retry delay.

The collector closes only its own dedicated browser context on completion or
pause. A successful probe saves `browser-probe-success.png`; an access blocker
saves `browser-capture-blocker.png`. Status and source responses remain on disk
so interruptions do not lose downloaded matches.

## Integration path

Keep browser extraction out of web request handlers and out of the leaderboard
snapshot transaction. A separate worker can accept deduplicated match IDs,
reuse immutable match captures, and publish a new versioned player summary only
after analysis completes. Serve cached summaries with selection rank, historical
rank/date coverage, analysed match counts and explicit missing-data fields.

Player-card summaries should distinguish recorded behaviour from interpretation:
economy-category performance, pistol conversion, opening involvement, map/side
results and context-matched response after loss streaks. Country/rank comparisons
should show cohort size and completion before displaying any population claim.
The current research outputs are `report.html`, `analysis.json` and
`player-rounds.csv`; this collector does not change production endpoints.


