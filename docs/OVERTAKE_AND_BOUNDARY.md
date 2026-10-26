# Overtake targets and Immortal boundary watch

Player cards use `/api/leaderboard/overtake-targets` with region, player and the card's
time window. The next five strictly higher RR values among current usable observations
are targets. Equal-RR players are omitted; this is not an official leaderboard tie-break.
Carried, conflicting statistics and ambiguous ranks are excluded. Each target's current
gap is target RR minus selected-player RR. Its baseline is the earliest earlier completed
snapshot inside the selected window where both players have usable observations in the
same region. Gap change = current gap minus baseline gap: negative means closing, positive
means widening. Missing baselines display "More history needed", never zero change.

The boundary watch uses the last two COMPLETE scans in the same act, not the graph time
filter. Collection windows must not overlap. Each candidate region must retain at least
80% of its prior fresh population. New usable Immortal 1 records at 0–25 RR absent from
every region in the prior snapshot are possible promotions. Prior usable Immortal 1
records at 0–25 RR absent from every region in the latest snapshot are possible demotions
only if that latest snapshot completed full regional reconciliation. A carried record is
not an absence, and a move to another region is not a crossing. Both signals are heuristic:
renames, delayed provider data or scan omissions remain alternative explanations.

The watch shows counts plus up to 20 examples in the current region/country scope. It
does not modify historical records, tier totals, confirmed movement counts, wins or losses.
Demotion counts say "await regional scan" when the latest run was global-only. Overlapping
scans or no baseline display a pending state, rather than claiming no activity.

Riot's current Immortal guidance says Immortal 1 starts at 10 RR, and demotion requires
0 RR followed by another loss. This supports the low-RR signal but does not prove a
specific disappearance is a demotion:
https://support.riotgames.com/en-us/valorant/gameplay/rank-rating-rr-for-immortal-and-radiant-ranks

Both endpoints use the existing bounded cache keyed by successful snapshot ID and filter
arguments. The build excludes automated tests at the user's request.

