# Tracker-first match analysis — feasibility and implementation design

Research date: 27 October 2026. Scope: analysis for the next Observatory module; no match collector or analyzer has been implemented or enabled by this investigation.

## Decision

Use Tracker's normally accessible match pages as the primary source. Prefer the structured response that the browser receives when a match is opened, if inspection confirms that it contains the needed data. Fall back to extraction from the rendered page. Riot API approval is not a prerequisite for this proposed prototype. Tracker page access is not a guaranteed or documented developer API contract; collection and retention permissions still need to be settled before a public rollout.

## What was actually inspected

The existing TrackerLeaderboardClient and TrackerProfiles import leaderboard snapshots, current/peak ratings and reported profile country. They do not persist match histories, round events or economy. Existing RR snapshot history cannot reconstruct individual matches.

Opened the public profile for the chosen one#mØØda in the ChatGPT in-app browser, opened Matches, then opened match 96d890bc-2361-4c12-a3e3-d0ab48795641. The match is competitive Corrode, 13–5, 18 rounds, with a displayed duration of 25m 01s. Inspected Scoreboard, Rounds and Economy. The page exposes all ten participants, match K/D/A, score metrics, round outcomes, per-player round K/D/A, economy, event timestamps and event-position views. The selected player's overall 19/12/4 contrasted with 0/1/0 in round 1; this is observed data, not a coaching judgment.

The browser tools exposed the rendered page but did not expose a network-response recorder. Therefore the exact Tracker match endpoint, response schema, pagination cursor and whether round switching issues further requests have NOT been verified. Do not implement a guessed endpoint or treat Riot field names as Tracker field names.

Match evidence: https://tracker.gg/valorant/match/96d890bc-2361-4c12-a3e3-d0ab48795641

## Extraction spike: first acceptance gate

1. In an instrumented, ordinary browser session, open one public profile and start recording only relevant Tracker match requests before opening a match. In manual Developer Tools, use Network > Fetch/XHR, clear the log, and preserve it while navigating. Inspect the request associated with the match ID; ads and analytics are irrelevant.
2. Record method, URL/path parameters, non-secret pagination parameters, HTTP status, content type, payload size and response JSON. Never commit cookies, authorization headers or an unsanitized HAR. Save a sanitized response body as the parser fixture.
3. Open rounds 1, 2 and 13 and the Economy tab. Determine whether one match response contains every round or whether a separate detail request is required. If it contains everything, fetch each completed match once and derive all views locally. A click reveals access to data; it does not necessarily create new data.
4. Verify a second match, an overtime match and an older match. Check history pagination, completion/correction behavior and private/unavailable states. Historical coverage begins where Tracker's accessible history begins, not at the start of an Act by assumption.
5. If structured responses are not accessible through supported browser tooling, use rendered match links, tabs and round selectors. Extract semantic text/tables and inspect accessible event/map labels. Missing kill identities or positional coordinates stay unavailable. Do not OCR a map into supposedly precise coordinates.
6. Reconcile score against round results and match totals against round totals. Account for revives and multiple deaths; never assume five deaths always ends every round. Preserve unknown separately from zero.

A page-driven collector should use one bounded session, a small queue, match-ID deduplication and resumable checkpoints. Finish and close each page rather than leaving many profiles polling. Respect Retry-After and stop on private pages, authentication failures or challenges. Do not rotate identities, spoof browser fingerprints or copy clearance cookies to defeat access controls. Direct HTTP retrieval is an optional later optimization only if the observed request is suitable for that use; do not simply extend the existing profile client's browser-header impersonation.

## Source-neutral storage

Keep the new module separate from the live leaderboard publisher. Reuse request pacing, bounded retries, duplicate-request coalescing, PostgreSQL migrations and job-status patterns, without allowing match jobs to starve live collection.

Proposed records:

- match_imports: provider, match ID, observed URL, fetched time, content hash, parser version, completion state, field-availability flags and private raw payload reference.
- matches: provider identity, actual game region, platform, queue, map, patch, Act, start time, duration and score.
- participants: stable provider player ID where exposed, observed Riot ID, team, agent and rank observed at match time if available. A display-name change must not silently create a new person.
- rounds and player_rounds: actual round number/side/outcome; kills/deaths/assists/damage/score; starting equipment value, weapon, armour, spend and remaining credits when supported.
- events: typed kill, plant, defuse and other explicitly supplied events, with timestamp, participants and any position snapshots. Preserve source ordering and missing fields.
- benchmark_memberships: cohort, regional leaderboard basis, selected player IDs and selection timestamp. Do not reinterpret present rank as historical rank.
- derived_metrics: analysis version, qualifying round IDs, numerator, denominator, exclusions and benchmark provenance.

One match belongs to multiple player histories but its raw payload and team outcome must not be counted repeatedly. Retain corrected payload versions rather than silently overwriting evidence. Implement deletion/visibility controls for retained player data before broader publication.

## Scenario analysis

Pistol: identify regulation half-openers from the mode and round sequence (normally rounds 1 and 13 for standard competitive); overtime is separate. Measure damage per round, opening involvement, first deaths, survival and team conversion.

Economy: classify each player's and both teams' starting equipment separately. Use loadout and remaining bank together; spending alone misses saved or gifted weapons. Distinguish low-buy, partial-buy, full-buy, mixed-team buys and equipment advantage/disadvantage. Force-buy and bonus labels require previous-round and equipment evidence and should be explicitly inferred. Patch/version the thresholds; retain an unknown category.

Opening fights: report first-kill/first-death involvement, success among opening engagements and team win rate after an opening advantage. A high first-death rate on an entry agent needs role context.

Trades: use a declared time window and explicit killer/victim relationships. Report time-based trade indicators as estimates unless geometry and event completeness support stronger claims. Changing the time window must not silently change historical scores.

Post-plant, retake and clutch: use objective events and trustworthy alive-player state. Death-only reconstruction is unsafe when resurrection or temporary-life mechanics are not represented. Suppress exact 1vX labels when state is ambiguous.

Crucial rounds: start with explicit pre-round tags such as overtime, match point, close late score and economy disparity. These are context labels, not proof of mental pressure or a mathematically measured win-probability swing.

Teamplay: measure damage/assist contribution, opening-advantage conversion, trade indicators, economy coordination and objective outcomes. Do not infer voice communication, intentions or blame from statistics.

## Meaningful Radiant comparisons

Offer a Top 10 Radiant reference group selected from a named region and dated completed leaderboard snapshot. Record missing/private profiles and never silently replace unavailable players. Distinguish 'current top 10 selected today' from 'ranked top 10 when this match was played.'

Match comparable rounds by mode, map, attack/defence, patch, agent/role, own/opponent loadout and lobby strength where available. Show the user's overall and scenario results beside the median and spread across benchmark players. Avoid allowing one prolific player to dominate the reference; show player-balanced summaries as well as pooled opportunity counts.

Ten players can yield very few comparable pistol or clutch opportunities. Report unique players, unique matches and eligible rounds for every comparison. Use match/player-clustered uncertainty when estimating intervals. Broaden the reference only with an explicit UI label, or display insufficient evidence. Exclude the subject from their own benchmark and deduplicate shared matches. Team round outcomes are not ten independent observations.

Interpret differences descriptively. A Diamond player and Radiant reference face different opposition; a damage gap alone does not establish a mistake. Do not invent a 'true rank', hidden MMR or probability of reaching Radiant.

## Analyzer experience

Add a Match analysis area to the player profile: recent matches, import coverage and a selectable period. Opening a match should show the score/agent/context, a selectable round strip, individual/team/opponent economy, event timeline, and relevant benchmark comparisons. Positions must be labelled event snapshots rather than continuous replay.

The most useful output is a small set of evidence-linked review patterns. Every finding should open its contributing rounds and show the metric definition, sample size and uncertainty. Suggested coaching questions should be labelled hypotheses. Do not generate claims about bad crosshair placement, rotation speed, a missed trade opportunity or wasted utility unless the required evidence exists.

For deeper technique analysis, optionally attach a user-provided VOD and let the player/coach inspect the flagged rounds. Structured match data alone does not expose continuous movement, every peek, full utility timing/effectiveness, audio or team communication. An LLM may explain verified metrics later; deterministic code should compute the metrics and supply the evidence first.

## Incremental implementation and acceptance

1. Extraction proof: one match plus overtime and unavailable-state fixtures; establish the real Tracker response contract and DOM fallback coverage.
2. Single-match explorer: accurate round/economy/event views with provenance and unknown states. Validate against the actual Tracker page.
3. Player trends: on-demand import of a bounded recent-match window; idempotency, rename handling, pagination, correction and retry tests.
4. Radiant reference: a dated, accessible cohort; deduplication, matched scenarios, player-balanced summaries and coverage disclosure.
5. Review guidance: a few deterministic, evidence-linked patterns; inspect false positives against VODs or human review before adding generative explanations.

Start with approximately 20 recent matches for one subject and a bounded reference sample, subject to access and request budget. Expand sample sizes when the actual scenario coverage warrants it; do not promise a fixed match count will be statistically sufficient. No broad all-player crawler is needed to validate this feature.

## Access findings and references

Tracker's current developer page lists no VALORANT developer API. Tracker staff reiterated in July 2024 that its VALORANT API is not public and directs API applicants to Riot. This is why the proposed source is normal page access, with collection permissions and changing page structure treated explicitly rather than assuming an official Tracker contract.

- https://tracker.gg/developers/docs/getting-started
- https://feedback.tracker.gg/t/development-of-a-bot-discord-stats-valorant/43663

Riot's documented match schema contains match participants, round results, timed kill events, damage, economy and positions at certain events. This confirms the class of analysis is feasible, but it does not establish which fields Tracker returns. Riot also distinguishes leaderboard/anonymous aggregate use from named player analysis and requires opt-in in the product's ecosystem. Access through Tracker should not be assumed to transfer consent to this application; resolve the intended product's permissions before public release.

- https://developer.riotgames.com/apis#val-match-v1/GET_getMatch
- https://developer.riotgames.com/docs/valorant

Verification limit: this was a real-page feasibility inspection and local source audit, not a successful JSON extraction or a completed analyzer. No extraction job, schema migration, production change or live-collector restart was performed.
