# Live extraction: Cloudflare 403 diagnosis

Date: 2026-10-28

## Finding

The failure occurs before HTML parsing. The collector issues a direct Java `HttpClient` GET for the leaderboard HTML and reads `window.__INITIAL_STATE__` from a successful response. A one-request diagnostic using the collector's URL and headers received HTTP 403, `Content-Type: text/html; charset=UTF-8`, `Server: cloudflare`, no redirect, and an HTML verification page beginning “Just a moment…” and asking to enable JavaScript and cookies. Cloudflare request ID: `a518872b3e000942-CCU`.

The failed application runs were for the global source (`region=global`), while the user's example link is Asia-Pacific (`region=ap`). This difference does not explain the denial: the same diagnostic global request was refused before application parsing. The app's direct request has no browser JavaScript runtime or browser-established session state. The response is a Cloudflare browser verification challenge, not an empty leaderboard or malformed embedded JSON.

Snapshot 458 remains the latest complete snapshot: 1,084 pages and 54,237 players. Runs 459–461 failed at page 1 with HTTP 403 and did not publish partial results. Snapshot data was not deleted or modified. The completed run used 1,116 requests at a configured 500 ms minimum start interval with concurrency 5. That volume may have contributed to Cloudflare challenging subsequent requests, but the specific Cloudflare rule is not exposed by the response and cannot be confirmed from this evidence.

## Initial correction

- Classify HTTP 403 separately and identify a Cloudflare JavaScript challenge using bounded, in-memory response inspection. Do not store or log the challenge HTML.
- Report the content type and Cloudflare request ID for support diagnostics.
- After an upstream access denial, suppress scheduled retries and any already-coalesced retry. Preserve the last complete snapshot. An administrator can use the existing manual collection control after source access is restored.
- No cookie reuse, proxy, challenge solver, header impersonation change, or other access-control workaround is included.

## Verification

- `TrackerLeaderboardClientTest` passed, including gzip-compressed challenge recognition and exactly one HTTP request on 403.
- `GlobalPersistenceTest.scheduledCollectionStopsRetryingAfterAnUpstreamAccessDenial` passed, verifying scheduled work is suppressed and wrapped access-denial causes are recognized.
- Command: `.\gradlew.bat test --tests com.valorant.leaderboard.TrackerLeaderboardClientTest --tests com.valorant.leaderboard.GlobalPersistenceTest.scheduledCollectionStopsRetryingAfterAnUpstreamAccessDenial`
- `git diff --check` passed before documentation was added; rerun before commit.

## Runtime limitation at the initial correction

The running service was not restarted to activate this code because startup immediately makes another source request, and the upstream challenge has not been cleared. The current process therefore still has the earlier retry behavior. Fresh automated server-side data collection cannot resume until Tracker permits the request or an approved access method is configured. The existing service and tunnel were left running.

## Follow-up

The user subsequently stated that permission was in place and requested the opt-in FlareSolverr transport described in [FLARESOLVERR_SETUP.md](FLARESOLVERR_SETUP.md). During that work, the original retry guard was found to miss errors already caught inside `collectSnapshot`; that claim in the initial correction was incomplete. The guard now runs in that catch path as well, and a focused test exercises a failed page and confirms the next scheduled trigger does not fetch again. After snapshot 464 published, the service was restarted with the tested correction and collection resumed.
