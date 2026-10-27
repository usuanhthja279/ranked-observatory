# Selected-player Tracker browser collector

## What it does

The player workspace has a **Collect latest five matches** button. With the local extension installed, a deliberate click opens the selected Riot ID's Tracker profile in a browser tab, clicks the first five visible recent-match cards, and reads the visible match and round tables. It validates the player identity, schema, round K/D/A and final score using the same validator as the manual import. Duplicate match IDs are skipped; individual failures are reported and previously validated matches remain selectable in the page.

Tracker's default profile filters determine the visible cards. The observed profile opened in its current-Act Competitive view, so this version does not guarantee the latest five matches across every mode or Act. Economy labels, round sides, detailed events and Radiant comparisons remain unavailable. Captures live in browser memory and are cleared on reload; no new backend endpoint or persistent match database is introduced.

The collector permits a single active job, checks the requested player against the profile and capture, and accepts app requests only from an explicit button click on local Observatory player pages on ports 8090 or 8091. It monitors Tracker page request status and stops on HTTP 403 or 429. It also stops on private profiles or visible challenges. It does not read or copy cookies, use proxies or spoof browser identity. It reads rendered UI rather than making a guessed match API call.

## Install and use locally

1. From the repository root run:

       .\scripts\Build-TrackerCollectorExtension.ps1

2. In Chrome, Edge or Brave, open the browser's extensions page, enable developer mode, choose **Load unpacked**, and select E:\Projects\ranked-observatory\build\tracker-collector-extension.
3. Open the local Observatory player page on port 8090 or the local source preview on port 8091 in that same browser. Pick a player, then choose **Collect latest five matches**.
4. Keep the collector tab open while it works. The player page shows progress, a match selector and any individual failures. Closing the collector tab stops the job.

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
