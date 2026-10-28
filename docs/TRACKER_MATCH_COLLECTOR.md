# Selected-player Tracker browser collector

## What it does

The player workspace has a **Collect latest five matches** button. With the local extension installed, a deliberate click opens the selected Riot ID's Tracker profile in a separate collector window, clicks the first five visible recent-match cards, and reads the visible match and round tables. It validates the player identity, schema, round K/D/A and final score using the same validator as the manual import. Duplicate match IDs are skipped; individual failures are reported and previously validated matches remain selectable in the page.

Tracker's default profile filters determine the visible cards. The observed profile opened in its current-Act Competitive view, so this version does not guarantee the latest five matches across every mode or Act. Economy labels, round sides, detailed events and Radiant comparisons remain unavailable. Captures live in browser memory and are cleared on reload; no new backend endpoint or persistent match database is introduced.

The collector permits a single active job, checks the requested player against the profile and capture, and accepts app requests only from an explicit button click on local Observatory player pages on ports 8090 or 8091. It monitors Tracker page request status and stops on HTTP 403 or 429. It also stops on private profiles or visible challenges. It does not read or copy cookies, use proxies or spoof browser identity. It reads rendered UI rather than making a guessed match API call.

## Install and use locally

1. From the repository root run:

       .\scripts\Build-TrackerCollectorExtension.ps1

2. In Chrome, Edge or Brave, open the browser's extensions page, enable developer mode, choose **Load unpacked**, and select E:\Projects\ranked-observatory\build\tracker-collector-extension.
3. Open the local Observatory player page on port 8090 or the local source preview on port 8091 in that same browser. Pick a player, then choose **Collect latest five matches**.
4. Keep the Tracker collector window open and visible while it works. You can switch tabs in the main Observatory window. The player page shows progress, a match selector and any individual failures. Closing the collector or Tracker window stops the job.

The ChatGPT in-app browser automation interface exposed no extension installation or management control during this work, so installation there was not verified. Use a Chromium browser with unpacked-extension support for this version. The collector is intentionally restricted to the listed local origins; the public Cloudflare tunnel does not launch it.

## Verification on 27 October 2026

- node --test src/test/js/match-analyzer.test.cjs: PASS, all 8 validator tests.
- python scripts/leaderboard/test_tracker_collector_extension.py: PASS in isolated bundled Chromium with a synthetic rendered Tracker profile. It checked a trusted button click, rejection of a synthetic DOM click, five validated matches, match selection, Team B elimination perspective, a duplicate, a malformed match and simulated 403 and 429 stops. After an intermittent navigation failure, the collector was changed to wait for a fresh page instance and the suite passed twice consecutively. It uses the local source preview and intercepts the synthetic Tracker pages.
- python scripts/leaderboard/test_tracker_collector_extension.py --real: **BLOCKED**, not an acceptance pass. One normal selected-player attempt, before the final page-instance timing correction, returned HTTP 403; the collector stopped with zero validated matches. No retry or challenge bypass was attempted. The test script now exits with code 2 when a real run collects fewer than five matches.
- The Gradle command .\gradlew.bat bootJar --no-daemon passed with the installed Java 21 JDK. No Docker or broad Java test suite was used.
- Desktop synthetic and real-block screenshots were inspected. Local evidence is under ignored build/tracker-collector-test/: synthetic-five.png, synthetic-block.png, and real-result.png. The synthetic screenshot uses fabricated match data.

The running port 8090 service was not restarted during its active live extraction. Live extraction remained enabled and unpaused, the archive pause marker remained present, and the existing public tunnel continued to point to the previously running build. The source preview on port 8091 served the updated UI during verification; loading the new packaged UI on port 8090 requires a safe later restart.

## Remaining work

A successful real five-match capture has not been demonstrated because Tracker returned 403 in the isolated browser. Tracker page markup and default filters can change, and a private or unavailable match can yield fewer than five results. Full cross-Act or cross-mode history, persisted captures, economy semantics, detailed events and comparative coaching are future work.


## Result and background-window correction — 27 October 2026

Tracker's rendered round-three heading for match 30cff3a7-2b96-44a7-a538-7eb73f212aa0 was observed as TEAM A DETONATE WIN. DETONATE now maps to SPIKE_DETONATED. Unrecognized result labels are retained as UNKNOWN with their displayed Tracker label; round K/D/A, winner and score reconciliation still applies. Existing captures without the optional label remain supported.

The Tracker tab now runs as the active tab in a dedicated popup window. The main Observatory window can switch tabs without making the Tracker tab inactive in its own window. Keep the popup open and visible: browser or operating-system throttling when a window is minimized or completely obscured may still delay collection.

After updating this code, rebuild with scripts/Build-TrackerCollectorExtension.ps1, open the browser extensions page, and reload the unpacked collector. Refresh the Observatory player page to load the updated UI.

Focused checks: node --test src/test/js/match-analyzer.test.cjs passed 9 tests. The synthetic Chromium extension test passed all five-match, unknown-result, duplicate, partial-failure, 403 and 429 checks. It also switched the main window to another tab, verified Tracker remained the active tab in a separate window with document visibility reported as visible, and completed all five synthetic matches. The native Gradle bootJar --no-daemon build also passed. This was an isolated Chromium test, not a successful live Brave rerun. The user reported an earlier Brave run collected four matches and rejected one result; the corrected extension still needs a live Brave retry after reload.


## Single-profile reuse — 27 October 2026

The collector opens the profile once, attempts to close the match drawer with Escape between matches, and reloads the profile only if the drawer remains visible. It validates each result as before. The focused synthetic Chromium test verified five matches with one profile request, switching the main-window tab, duplicate and invalid-match handling, and 403/429 stops. The nine match-analyzer Node tests passed. This test uses a synthetic drawer; closing the real Tracker drawer with Escape has not been verified. If it does not close, the existing reload fallback is used.

Network response collection and richer match analysis have not been implemented: the response schema and its available fields were not verified in the current Brave session. The collector continues to use rendered data and does not infer economy or events.


## Network response prototype — 27 October 2026

Version 0.2.0 attaches the browser debugger to the dedicated collector tab and observes successful JSON match responses already requested by Tracker after a match card is opened. It does not fetch the match endpoint itself. The response body is held temporarily in extension memory and only the selected-player derived analysis is sent to the local player page. Request headers, cookies and raw response bodies are not saved. The extension declares the debugger permission, so Brave may present an additional access warning on reload. Keep stopping on access restrictions.

The parser requires matching match ID and player, complete numbered player and result rounds, valid teams, and round K/D/A totals matching the player summary. It displays round damage, reported side and recorded loadout, plus overall, pistol, attack and defense aggregates. It does not yet verify the round-win totals against the displayed final score on the network path. Unknown side and loadout remain unknown. Event counts are not coaching findings; economy classifications, validated event analysis, training and top-Radiant comparisons remain pending. Unavailable or rejected responses fall back to the rendered-page capture.

Verification: node --test src/test/js/tracker-network-analysis.test.cjs src/test/js/match-analyzer.test.cjs passed 11 tests. The synthetic browser test exercised five network responses in the player workspace and the existing visible-table fallback. Results are synthetic; a real Brave run of version 0.2.0 has not been verified. The earlier observed real response was HTTP 200 and showed player-round and player-round-damage fields, but the full response was not inspected by this test. The running application was not restarted; port 8090 may still serve the previous UI until a safe restart.


## Local response JSON export — 27 October 2026

Version 0.3.0 prompts to save one JSON file at the end of a collection when at least one match response has been received. Choose a location inside the ignored `build/` or `data/` directory. The file contains the selected player, export timestamp, requested and received counts, completeness flag, reported collection failures, and each matched response body with its match ID and analysis-validation result. It contains match data about other participants, so keep it private. It contains no request headers or cookies.

A response is included only when its match ID and selected-player summary match the requested match and player. A response that fails deeper analysis validation is retained and marked failed. If Tracker blocks collection or some matches use the rendered-page fallback, the export is partial and explicitly marked incomplete; fallback captures are not represented as raw response bodies. If no response was captured or the save is cancelled, the collector reports this in its status. The browser downloads permission is required.

Verification: `python scripts/leaderboard/test_tracker_collector_extension.py --network-only` passed against synthetic responses and inspected the downloaded JSON: five bodies, matching IDs, validation status and completeness. `node --test src/test/js/tracker-network-analysis.test.cjs` passed two tests. The real Brave save dialog and real response export were not tested in this pass.

## Version 0.5.0 — resumable local checkpoints

The bounded 0.4.0 workflow now checkpoints validated rendered captures, validated response bodies, completed match index, and partial failures in the extension's local IndexedDB. A deliberate collection click for the same player and target resumes after the last completed match, including after the runner tab or browser closes. Saved entries are revalidated for schema, match ID and selected-player identity before reuse. Checkpoints expire after 30 days and are cleared after successful finalization. A checkpoint-write failure is reported without discarding the current in-memory collection.

Checkpoint data is private to the local browser profile and can contain full match response bodies; do not share the browser profile. No cookies, headers, app credentials or auth material are stored. HTTP 403/429, challenge and private-profile stops clear the checkpoint, so resuming cannot automatically retry a restricted source. Resume requires a new explicit user click. This does not bypass a block or change the profile's normal collection behavior.

The bounded 0.4.0 workflow supports targets of 5, 10, or 20 recent matches and sequential authenticated account import in batches of no more than five. Network bodies and rendered-page fallback captures remain distinct.

The collector supports explicit targets of 5, 10, or 20 recent matches. It opens the selected player's profile once in its dedicated window, opens each match by normal page interaction, observes only Tracker's own successful JSON response, deduplicates IDs, and preserves response bodies for the requested JSON export. When the Observatory page is signed in, the extension sends network responses sequentially in batches of no more than five to the existing same-origin API. The page obtains the Observatory CSRF token and sends it with the authenticated request; the backend remains authoritative for full validation, reconciliation, persistence, and account ownership. This is not a generic anonymous upload route. The response JSON export remains available for private local storage under ignored `build/` or `data/`.

Each collection reports the requested target, actual captures, failures and account-import outcome. A target of 20 can produce fewer than 20 records if matches are unavailable or collection stops. A partial set is labeled partial. Network response exports preserve their source response metadata; visible-table fallback captures are represented only in the UI and are never labeled raw JSON. The service worker rejects requests except from an explicit Observatory action at the local Observatory origin and matching player route. The app browser must support a Chromium unpacked extension; ChatGPT's embedded browser extension support is not available.

The extension observes requests Tracker makes during normal UI navigation. It does not call guessed endpoints or copy cookies/headers. It stops on HTTP 403/429, private profiles and challenges. After a restriction, the checkpoint is removed and the user must follow the source's access flow before explicitly starting another collection. Successfully completed API batches are idempotent; an interrupted finalization can safely re-import the same match IDs and relies on server-side deduplication.

To test the local queue without contacting Tracker, use the synthetic Chromium test script with `--network-only --target-count 5`, `10`, or `20`. The 10/20 options verify multiple bounded imports and JSON export using synthetic response bodies. These do not establish real Tracker availability or permissions.
