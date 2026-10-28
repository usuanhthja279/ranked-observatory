# Match analysis and review rules

## Deterministic reports

The M5 analyzer is a rules-based, source-linked description of captured records. It reports uniquely timed opening engagements, selected-team round outcomes after an opening advantage, reciprocal timed elimination sequences, recorded loadout values, explicit objective events, multi-kill rounds, no-death rounds, and reported damage. Each finding carries event IDs/source references, an analysis version, confidence and an explicit reason. Descriptive outcomes are not claims about intent, optimal decisions, or mistakes.

Unknown source fields stay unknown. Tracker's raw event time unit is unverified. Recorded loadout values are not source economy classifications. Missing visibility, utility, movement, voice, alive-state and VOD evidence is not reconstructed. Round wins, damage, and K/D are never expert-label substitutes.

## M7 pre-round baseline

`pre-round-score-side-v1` contains only round index, the selected team's score differential before the round, and the source-reported side. The label is the recorded round winner. The current and later round outcomes do not enter features. A deterministic SHA-256 assignment groups all rows by provider match ID before an 80/20 train/held-out split. Duplicate imports of a match are collapsed before splitting.

The baseline is CPU-only logistic regression and is available only as an opt-in evaluation of the signed-in owner's imported matches. It reports accuracy, class precision/recall, Brier score, five-bin confidence calibration error and false-positive rate. It never claims causal correctness or tactical expertise. Below 10 distinct matches or 200 supported rounds it returns `INSUFFICIENT_EVIDENCE`, with no predictive metrics and no model activation.

## Human-reviewed annotations

Expert annotations are private to the account that created them and can only reference a match that account owns. A round must exist; an optional event must belong to the exact selected round. VOD references must be HTTPS URLs and are stored only; the Observatory never fetches them. Annotation corrections and withdrawals append a new revision under the same annotation ID. Stale revisions return conflict. Each record stores annotator, guideline version, confidence, insufficient-evidence state, and an optional opaque disagreement group. Separate annotator records are not exposed cross-account; the service does not calculate reviewer agreement or train a tactical decision model.

## Evidence-grounded explanation

The Coaching view continues to render the deterministic M5 facts. It can copy a structured brief containing the match facts, availability, report and source references, plus strict explanation instructions. This is a manual handoff; no external LLM is called and no data leaves the app. An external explanation must preserve uncertainty and may not invent unseen movement, utility use, voice communication, intent or VOD content. Any unverified judgment must be labeled a hypothesis.
