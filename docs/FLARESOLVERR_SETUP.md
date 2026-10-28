# Local FlareSolverr transport

The user stated that permission for this access is in place. The transport is opt-in and operates only against the existing Tracker leaderboard URL. It sends the rendered HTML through the same filter, offset, player and snapshot validation as the direct transport.

The application supports `LEADERBOARD_SOURCE_TRANSPORT=direct` (default) or `flaresolverr`, and `LEADERBOARD_FLARESOLVERR_URL` (default `http://127.0.0.1:8191`). The checked-in Windows launcher can also read `sourceTransport` and `flareSolverrUrl` from the ignored local settings file. The solver endpoint is restricted to loopback HTTP. Never expose it through the public Observatory tunnel.

Install the official Windows x64 release into `build/tools/flaresolverr-v3.5.2` after verifying its release checksum, then run `scripts/Start-FlareSolverr.ps1`. The script binds the service to `127.0.0.1`, waits for it to listen, disables HTML logging and leaves an existing listener untouched. When the local settings select `flaresolverr`, `Start-LeaderboardService.ps1` starts or reuses that local service before launching the application. The application uses one serialized browser session and destroys that session at shutdown. Cookies returned by FlareSolverr are not saved or logged by the application.

The source may still refuse access or the verification may time out. Such a response stops the snapshot, retains the last complete snapshot and pauses automatic retries. The existing administrator collection control can make a fresh attempt after access is restored.

## Verification on 28 October 2026

- The official Windows x64 v3.5.2 release was downloaded and its SHA-256 matched the published digest `309abd7c821546325ae6c509b40d969c5e39034be2314d21b3f5e0517feda7a7`. FlareSolverr started on `127.0.0.1:8191` and its browser self-check passed.
- One real global leaderboard request through the local service returned source HTTP 200 and contained `window.__INITIAL_STATE__`. Cookies and the page response were not saved by the diagnostic.
- The Observatory was restarted only after the earlier collector was idle. Its local settings select `flaresolverr`. Run 464 was still active after 96 global pages had been fetched with no reported error; snapshot 458 remained the latest completed snapshot at that point. The historical archive pause marker was preserved. This is not a claim that run 464 completed.
- The focused Gradle run reported 15 tests passed and one skipped (the opt-in direct live Tracker test). The tests cover session reuse, loopback restrictions, denial handling, page validation, and suppression of a scheduled run after a page-fetch denial. `bootJar` succeeded in an isolated staging directory; `git diff --check` passed. The startup script also successfully reused the existing local solver process.
- A follow-up correction to the scheduled denial guard was built and tested after run 464 started. Once snapshot 464 had published, the queued run 465 was stopped shortly after starting and the Observatory was restarted using the staged JAR. A hash comparison confirmed that the active JAR matches the staged build; run 466 resumed collection without a reported error. FlareSolverr remained on loopback port 8191 and the archive stayed paused.
