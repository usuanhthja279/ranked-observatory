# M7 — ML training and AI coach

## Current status

The implemented learning pipeline is a small, CPU-only pre-round round-outcome baseline. Feature extraction is versioned as `pre-round-score-side-v1`; the logistic model is `cpu-logistic-regression-v1`. It runs on demand for one authenticated owner's selected-player imports. Its evaluation result is not persisted or enabled as an inference model at application startup.

The baseline predicts the recorded round winner from three fields available before that round: normalized round index, the selected team's prior-round score differential, and the selected player's source-reported side. It does not use the round winner, later events, final score, or rows from another match group as training data. Duplicate provider match IDs are deduplicated. A fixed seed and SHA-256 match-group assignment make the 80/20 held-out split reproducible.

When support is adequate, the evaluation reports accuracy, precision and recall for both round classes, Brier score, five-bin calibration error and false-positive rate. It withholds these metrics and returns `INSUFFICIENT_EVIDENCE` below 10 distinct matches or 200 supported round examples. Training is in-memory, pure Java, CPU-only and bounded to at most 500 authorized matches per request. No large downloads, GPU or app-start training is required.

## Human-reviewed labels

`V13__match_intelligence_tactical_annotations.sql` stores private, versioned reviewer labels. Reviewers can select an existing round and an optional exact event, assign a tactical category, explain the evidence, optionally state a better alternative, mark insufficient evidence, set confidence, add an HTTPS VOD reference, and optionally group a disagreement. VOD URLs are not fetched. Corrections and withdrawals append revisions; stale writes conflict. Labels include annotator identity and guideline version. Each user can access only their own annotations for matches they own.

The UI's Expert review tab supports new reviews, reason corrections and withdrawal. Reviewer disagreement is retained only as an opaque group label; the current account model intentionally does not expose another annotator's private notes or calculate agreement. Labels are not yet numerous or quality-reviewed enough to train tactical judgments.

## Tactical model gate

No tactical decision classifier is trained or served. Outcome or damage labels cannot stand in for tactical ground truth. A future training phase must define label guidelines, gather sufficient expert-reviewed evidence, estimate disagreement, split by match and preferably player/time, and report class-level precision/recall, calibration, uncertainty and false-positive rates before activation. A model must abstain when evidence is insufficient.

## Explanation interface

The deterministic M5 report is the only coach-facing evidence generator. The Coaching tab can copy a structured brief containing reconstructed match facts, event references, deterministic metrics, selected benchmark results where available, source completeness and availability limitations. Its instructions prohibit inventing information. The copied brief is a manual, user-controlled handoff; there is no external LLM client, key, cookie access or outbound AI request in this phase. The existing report and the copied text are not AI-generated judgments.

## Evaluation evidence

The M7 regression suite evaluates the implementation on sanitized synthetic fixtures: 120 synthetic match groups and 240 round rows, with 96 groups/192 rows for training and 24 groups/48 rows held out. It also verifies feature invariance to a changed later-round outcome and insufficiency behavior. This validates pipeline mechanics only. It is not a real-match quality result and does not establish useful real-world predictive performance. The local real dataset was not used to report ML performance in M7.
