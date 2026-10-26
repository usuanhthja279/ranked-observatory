# Current update: full-tier Immortal 2

The active model now uses S = 3R + 2I3 + all I2. All Radiant, Immortal 3 and Immortal 2 unknowns use known country tier count times S times the network-access factor. Only Immortal 1 uses the population/median rule. The 150 RR cap on the Immortal 1 median multiplier remains; there is no Immortal 2 allocation split. Backend RR bands are retained as evidence and merged before allocation.

See [full-tier comparison](FULL_IMMORTAL2_COMPARISON.md). The earlier split-policy description and results below are historical.

---

# Country allocation policy v3

This is the user-requested allocation policy. It replaces v2 as the dashboard default. It is not selected by optimizing the earlier known-label accuracy diagnostic, act wins, nationality assumptions, or a target India count.

## Reported data first

Snapshot country reports remain authoritative. Source Unknown accounts can be recovered from consistent fresh leaderboard country reports or recent identity-checked profile reports, following the existing seven-day/conflict rules. Recoveries update only the model's working inventory. No original snapshot country, displayed source count, individual flag or official tier is overwritten.

The policy then operates on the remaining Unknown accounts independently for every shard and allocation group. Country median RR is calculated from unambiguous reported statistics after recovery.

## Upper groups

Let R, I3 and I2hi be the country's reported-and-recovered counts for Radiant, Immortal 3 and Immortal 2 at **RR ≥ 150**.

    S(country) = 3 × R + 2 × I3 + I2hi
    upper weight(country, group) = reported country count in group × S(country)

The upper rule applies to all Radiants, all Immortal 3 accounts, and only the 150+ RR portion of Immortal 2. More accounts in the target group increase the weight; more upper-ranked accounts and a stronger tier mix increase it again. The effect is intentionally compounded. The 3/2/1 tier points are explicit policy coefficients, not empirically fitted country skill values. Within-tier RR and maximum player rank receive no additional coefficient.

## Lower groups

Let K be the country's full reported-and-recovered leaderboard population in that shard and M its country median RR.

    median multiplier = 1 + clamp(M, 0, 150) / 150
    lower weight(country) = K × median multiplier

The lower rule applies to Immortal 1 and Immortal 2 below 150 RR. The multiplier ranges from 1 to 2; a missing median uses 1 (population-only). This prevents an extreme median among a tiny upper-only sample from dominating the lower allocation. Population means the collected leaderboard accounts, not national population or all Valorant users.

The 150 RR boundary is an allocation-policy boundary. It does not change the game's regional promotion thresholds or rename source tiers. Immortal 2's displayed estimate is the sum of its two allocation portions. Immortal 1 follows the lower rule by tier even if an uncertain source observation has an unusual RR.

## Allocation and totals

    additional(country, group) = Unknown(group) × weight(country, group) / Σcountry weight
    estimated(country, tier) = source observed + recovered + rounded additional groups

Largest-remainder rounding preserves each group's integer total; country-code ordering breaks ties. Estimated Players sums estimated tiers plus any unclassified inventory. Groups without positive country support remain explicitly unallocated. World totals are sums of independently allocated shards, never one pooled cross-shard calculation.

High-rank counts can therefore decrease sharply for countries with small upper-rank populations and increase sharply for countries with large upper-rank populations. Lower-rank counts can change too, because their former within-tier distribution is replaced by population plus median weighting. This policy does not freeze Immortal 1 at its previous estimate.

## Implementation and comparison

- `country-model.js`: `estimatePolicy` implements v3; `estimate` dispatches to v3 when versioned RR-band and median evidence is available. `estimatePrevious` retains v2 for comparisons and archived analysis.
- `/api/leaderboard/country-model`: version 3 includes inventory/recoveries split at 150 RR and post-recovery country medians. Aggregate evidence contains no player IDs.
- Main dashboard estimate columns and `/country-model-comparison.html` use v3. The comparison displays original alpha allocation, previous v2 allocation with the same recoveries, and the new policy.
- Method text names the formulas, 150 RR allocation split, and policy interpretation. Script asset versions force updated pages to load the new model.

Fixed snapshot 27 example, **previous v2 → v3**: India Radiant 7 → 4, Immortal 3 55 → 29, Immortal 2 182 → 158, Immortal 1 631 → 519, Players 875 → 710. India has strength 66 and country median RR 69 in that inventory. These numbers follow the fixed formula; the coefficients were specified before inspecting this result.

Other AP Radiants on the same fixed input: Japan 81 → 119; Singapore 15 → 9; Philippines 32 → 27; Australia 25 → 20; Korea-labelled AP accounts 142 → 197. Korea-labelled AP accounts are distinct from the separate KR shard. The policy prioritizes rank representation and volume rather than proximity to servers.

The deployed live comparison was subsequently captured on **snapshot 29**: India Radiant 7 → 4, Immortal 3 55 → 29, Immortal 2 181 → 157, Immortal 1 623 → 514, Players 866 → 704. Source counts remain 311 / 3 / 22 / 67 / 219. Its strength is 65 and median RR 70. The small differences from snapshot 27 reflect new source observations, not different policy coefficients. Live snapshot 29 AP Radiants: Japan 81 → 120, Singapore 15 → 9, Korea-labelled AP accounts 137 → 192, Philippines 34 → 30 and Australia 25 → 20. Full same-snapshot numbers and RR-group allocations are saved in `country-policy-comparison.json` and `.csv`.

## Verification

`node scripts/leaderboard/check_country_policy.cjs` checks compounded upper strength, equal target-tier counts with different upper mixes, population/median lower allocation, capped adjustment, recovery priority, immutable source inputs, per-shard independence, and conservation. Its `--live` option captures current comparisons and checks all five totals for all six shards, writing `country-policy-comparison.json`, `.csv` and aggregate evidence to `docs/`.

The PostgreSQL evidence integration test verifies exact 149/150 RR separation and a median computed after reported recovery, while checking that source Unknown rows remain untouched. Existing Java and dashboard regression checks also run.

Historical act-grinding reports remain fixed experiments with their own snapshots and former v2 baseline. They are not the active policy and do not establish v3 accuracy.

