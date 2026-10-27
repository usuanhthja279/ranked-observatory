# One-player Tracker match analyzer — Luna handoff

## Objective
Implement the analyzer for one player and prove workability with real Tracker page data. Tracker is the primary source; Riot API approval is not a prerequisite. Give live updates, use native Gradle/no Docker, run focused checks, and commit/push related changes. The original investigation was paused for a model handoff. The one-player prototype has since been implemented; see the implementation results at the end of this document.

## Repository
- Actual project: E:\Projects\ranked-observatory. The default PrintFlow cwd is unrelated.
- Branch: codex/ranked-observatory; remote: usuanhthja279/ranked-observatory.
- Last prior commit: d49d97c, the design report. Read docs/MATCH_ANALYZER_DESIGN.md.
- Untracked ui-sample/ is user content; leave untouched.
- No root AGENTS.md was found. Check applicable instructions before edits.
- Some shell operations require elevation because this repo is outside the current workspace roots. Non-elevated Git misleadingly reported 'must be run in a work tree'; elevated commands with exact workdir worked.
- JDK: C:\Users\usuan\.jdks\dragonwell-21.0.12. Use the existing Gradle wrapper.
- Local app: http://localhost:8090. Live extraction is intentionally enabled; archive extraction is paused. Preserve those settings. Check collection status before any necessary restart and avoid disrupting an active run.

## Verified source
Player: the chosen one#mØØda.
Match: https://tracker.gg/valorant/match/96d890bc-2361-4c12-a3e3-d0ab48795641
Competitive Corrode, Team A 13–5 Team B, 18 rounds, displayed duration 25m 01s. Selected player: Team A, Jett, overall K/D/A 19/12/4; ACS 279, ADR 187.8, HS 44%, displayed KAST 89%, first kills 1, first deaths 3. These values were read from the actual page.

One ordinary unauthenticated Invoke-WebRequest to the match page returned HTTP 403. Do not retry with copied cookies, clearance tokens, fingerprint spoofing or proxies. The normal in-app browser loads the public match without an authentication/challenge screen. Use the planned rendered-page fallback. No exact match JSON endpoint or payload has been captured; the available browser tools did not expose network recording. Do not invent an endpoint or call DOM extraction network capture.

In-app browser tab 65 was marked for handoff and shows this match's Rounds view. Rebind it if available, otherwise open the observed URL. Call cua.rewriteDocumentation() after compaction. Use the in-app browser for browser actions and read-only tab.playwright.evaluate for DOM extraction. Navigate with documented locators rather than clicks inside evaluate. Do not read hidden app stores.

## Observed DOM — verify before reuse
- Tabs: .trn-match-drawer__tabs-tab with text Scoreboard, Performance, Economy, Rounds, Duels.
- Heading: .vmr-header-left contains 'ROUND 1' and 'TEAM B ELIMINATION WIN'.
- The previous sibling of .vmr-header is the round strip. Its direct child divs have numeric round labels 1..18, loadout mini-bars and result images. Ground a locator in a fresh DOM read, click a round, then verify its heading before extraction.
- Round tables: .st-valorant-team-Red and .st-valorant-team-Blue. Their .st-header labels are Team A / Team B, Score, K, D, A, Econ. Derive team names from headers; do not equate team color to attack/defence.
- Rows: .st-content__category .st-content__item. Direct .st-content__item-value children are cells. First cell has a.info (player profile href) and agent img alt. Remaining cells have .value text. Econ additionally has .label text.
- Decode Riot ID from the observed profile href after /valorant/profile/riot/. Round display text omits tags, so text alone is not a stable identity.
- Rows are sorted by score; never identify the player by row index.
- Econ meanings need tooltip/label verification before naming them loadout/bank. The selected player's round 1 shows Score 0, K 0, D 1, A 0, Econ .value 600 and .label 200. Team B won by elimination. Unknown fields must remain unavailable, not zero.
- Event Log: the parent of the rendered leaf 'Event Log' contains event rows. They contain acting/victim agent images, team-color classes, displayed time, damage-source image and distance. Round 1 times: 0:05, 0:10, 0:12, 0:13, 0:15, 0:16, 0:29. Do not infer exact identity/type or complete alive state from icons alone; inspect accessible tooltips/labels.
- .vmr-map and .vmr-map-image show event positions, not continuous movement. No numeric map coordinates have been extracted.
- Economy tab visibly has per-round team bank/loadout/total charts. Numeric chart payload has not been extracted.

## Relevant code
TrackerLeaderboardClient.java: existing pacing and bounded requests. fetchProfile uses browser-like headers and an optional cookie; do not extend that approach to bypass the match 403.
TrackerProfiles.java: cached current/peak ratings, not match data.
LeaderboardController.java: player-journey means Act snapshots, not match history.
LeaderboardSecurity.java: POST /api/leaderboard/collect is ADMIN; other filter routes permitAll, with CSRF retained. New writes need explicit authorization and validation; never accidentally add a public import endpoint.
leaderboard.js and player-profile.css: current player workspace. Prefer a modular match view linked from there.
.gitignore already excludes data/ and build/ for local captures. Never commit credentials or unsanitized HAR files.

## Smallest functional implementation
1. Build a reusable rendered-round extraction adapter with bounded round count and schema-change detection. Capture all 18 rounds for this one match via normal browser UI. Save source URL, capture time, parser version and coverage. Browser-assisted import is acceptable for this prototype; label it accurately.
2. Normalize identity, team, round, result, K/D/A and verified economy fields. Reconcile the player's round totals to 19/12/4 and team outcomes to 13–5 before accepting the capture. Revives can cause multiple deaths; do not assume one death per round.
3. Add a one-player analysis view: match header, round selector/filter, round table, pistol split (1 and 13 for this normal competitive match), per-round K/D/A/score, team result and verified economy. Display coverage/source and unknown fields. Do not fabricate a top-ten Radiant benchmark; this initial request is a one-player feasibility test.
4. Prefer explicit local-file import/export to prove the browser-assisted pipeline without adding public server writes. A documented file-backed local pilot is acceptable. If adding API persistence, require authorization, size/schema checks and safe paths.
5. Focused regressions: href identity, missing versus zero, duplicate/missing rounds, reconciliation failure, schema changes, zero-death ratio, unknown economy, pistol/overtime boundaries. Verify another match or overtime if feasible; report limits honestly if not performed.
6. Run relevant focused Node/Java tests and build. Verify actual desktop/mobile UI through the in-app browser and inspect screenshots. Avoid repeated restarts and preserve live collection settings.
7. Document exact real-data and synthetic results separately; commit/push related changes on codex/ranked-observatory. Leave the user sample folder untouched.

## Done means
The user can inspect an actually extracted match for this player and its round analysis, with validation, coverage and evidence-linked observations. State clearly whether importing is browser-assisted, server-driven or manual. Do not call a data viewer complete Radiant coaching. The implementation below completes the one-player feasibility prototype while leaving multi-match ingestion and coaching claims out of scope.

## Implementation results — 27 October 2026

### Delivered prototype

- Added a profile-only, browser-assisted match panel to `/players?player=...`. It accepts a local JSON file in the current tab; no match data is posted to the Observatory and the import clears on reload.
- Added `tracker-match-capture.js`. The helper is meant to be copied into the Tracker page's DevTools Console. It reads the rendered scoreboard, clicks the page's visible round selectors, checks that each result renders, identifies the chosen player by the rendered Riot profile href, and downloads one selected-player capture. It makes no match API/network request.
- Added a strict versioned schema validator. It checks exact fields, supported Tracker source URL and matching match ID, sequential/unique rounds, field bounds, K/D/A reconciliation, and round-winner/final-score reconciliation. A mismatch rejects the import.
- Added match context, overall K/D/A and ratios, ACS/ADR, HS/KAST, Team A/B score from the player's perspective, regulation pistol rounds, round-level player K/D/A/score, team/opponent kill totals, win method, all/pistol/win/loss filters, capture coverage and source link.
- Economy values are omitted. The two rendered Econ values have not been semantically verified. Round sides, opening-duel identities, event timeline, buy types, clutch/utility/movement detail and Top 10 Radiant comparisons are explicitly unavailable.
- No backend endpoint, database schema, cookie flow, public import, or persistent player-match history was added.

### Real match evidence

The local ignored fixture `data/match-analyzer/96d890bc-2361-4c12-a3e3-d0ab48795641.json` contains data read from the normally rendered Tracker page for `the chosen one#mØØda`, Competitive Corrode. It is intentionally ignored by Git. The browser view showed Team A 13–5 Team B, 18 rounds, duration 25m01s; Jett; overall K/D/A 19/12/4, ACS 279, ADR 187.8, HS 44%, KAST 89%, FK 1, FD 3. Each of the 18 normal UI round selectors had been opened and inspected. Round K/D/A sums reconcile exactly to 19/12/4 and winners reconcile to 13–5. The prototype labels rounds 1 and 13 as regulation pistols; it does not infer overtime pistol rounds.

### Actual checks

- `node --test src/test/js/match-analyzer.test.cjs` — **PASS**, 8 tests. Covers valid/zero values, unsupported or extra schema fields, duplicate/missing/out-of-range rounds, K/D/A and score mismatch, zero-death handling, pistol/filter behavior, source ID mismatch, and non-Tracker/lookalike URLs.
- `node --check src/main/resources/static/match-analyzer.js` — **PASS**.
- `node --check src/main/resources/static/tracker-match-capture.js` — **PASS**.
- `git diff --check` — **PASS**.
- `JAVA_HOME=C:\Users\usuan\.jdks\dragonwell-21.0.12 .\gradlew.bat bootJar --no-daemon` — **PASS**, packaged with the native Java 21 JDK; no Docker used. No Java source changed, so no Java test suite was run.
- Browser verification on the source-served local preview at 1280×720 — **PASS** for the real fixture import flow. The desktop document width stayed within the viewport (1270 CSS px) and the 915 px round table fit without horizontal scrolling. UI displayed 18/18 reconciled, 19/12/4, 13–5, 1.58 K/D, 279/187.8, 44%/89%; pistol filter returned 2 rounds, wins 13, losses 5, all 18. Desktop screenshots were inspected in the browser.
- Mobile visual verification — **NOT RUN**. The available in-app browser session did not expose viewport emulation or a file-input upload API. Mobile CSS rules were reviewed, including two-column metrics and horizontally scrollable round table; this is not a substitute for a real phone-width screenshot.

### Runtime state and remaining limits

The native service at 8090 and its Cloudflare tunnel were left running. During final checks the service reported live extraction enabled and unpaused, and a live run was actively progressing; restarting Spring would have interrupted it. Consequently the newly built jar is not loaded on port 8090 yet. The tested source-tree preview was served read-only on 127.0.0.1:8091 and proxied GET API calls to 8090; it is only a local review harness, not a deployment. The existing Cloudflare URL still serves the currently running jar until a safe between-runs reload.

This proves a one-match rendered-page workflow, not an automatic Tracker importer or a coaching system. Tracker DOM changes can break the helper. The user must run the helper on a normally accessible match page and import its downloaded file. Economy, side, event/utility and benchmark semantics need further page evidence before they can be analyzed honestly.
