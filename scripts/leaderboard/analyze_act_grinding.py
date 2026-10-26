"""Same-snapshot comparison using act wins and ALL retained act rank observations.
No source country labels or live estimation settings are changed.
"""
import bisect, collections, datetime, hashlib, json, math, statistics, csv, subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
TIERS = ['Radiant', 'Immortal 3', 'Immortal 2', 'Immortal 1']
KEYS = dict(zip(TIERS, ['radiant', 'immortal3', 'immortal2', 'immortal1']))
RANK = {'Immortal 1': 1, 'Immortal 2': 2, 'Immortal 3': 3, 'Radiant': 4}
WIN_EDGES = [10, 20, 30, 40, 60]
STRENGTH = 10  # Fixed before inspecting country estimate results; sensitivity 5/20/50 is also reported.

def valid(p):
    return not p.get('stats_ambiguous') and not p.get('carried_forward') and isinstance(p.get('wins'), int) and p['wins'] >= 0

def trajectory(p, global_only=False):
    first = p.get('global_first_observed' if global_only else 'first_observed')
    count = p.get('global_observations' if global_only else 'observations') or 0
    last = p.get('last_observed')
    if count < 3 or not first or not last or (datetime.datetime.fromisoformat(last) - datetime.datetime.fromisoformat(first)).total_seconds() < 3600:
        return 'insufficient history'
    peak = p.get('global_peak_observed_tier' if global_only else 'peak_observed_tier') or 0
    initial = RANK.get(p.get('global_first_observed_tier' if global_only else 'first_observed_tier'), 0)
    current = RANK[p['tier']]
    if peak > current:
        return 'previously higher tier'
    if initial and initial < current:
        return 'promoted in retained history'
    if (p.get('best_observed_rr') or p['rr']) - p['rr'] >= 25:
        return '25+ RR below observed peak'
    if p['rr'] - (p.get('first_observed_rr') if p.get('first_observed_rr') is not None else p['rr']) >= 25:
        return '25+ RR above first observation'
    return 'within 25 RR of first/peak'

def feature(p, mode):
    if mode == 'current' or mode == 'original' or not valid(p):
        return None
    wins = bisect.bisect_right(WIN_EDGES, p['wins'])
    return (wins,) if mode == 'wins' else (wins, trajectory(p, mode == 'global_history'))

def distribute(total, probabilities):
    if not probabilities or sum(probabilities.values()) == 0:
        return {}, total
    denominator = sum(probabilities.values())
    exact = {c: total * p / denominator for c, p in probabilities.items()}
    out = {c: math.floor(x) for c, x in exact.items()}
    for c in sorted(out, key=lambda c: (-(exact[c] - out[c]), c))[:total - sum(out.values())]:
        out[c] += 1
    assert sum(out.values()) == total
    return out, 0

def prediction(known, targets, mode, strength=STRENGTH, region='ap'):
    country_tiers = collections.defaultdict(collections.Counter)
    country_total = collections.Counter(p['resolved_country'] for p in known)
    bins = collections.defaultdict(collections.Counter)
    for p in known:
        c, t = p['resolved_country'], p['tier']
        country_tiers[t][c] += 1
        f = feature(p, mode)
        if f is not None:
            bins[t, f][c] += 1
    target_bins = collections.Counter((p['tier'], feature(p, mode)) for p in targets)
    result = collections.defaultdict(collections.Counter)
    unsupported = collections.Counter()
    for (t, f), n in target_bins.items():
        base = country_tiers[t]
        total = sum(base.values())
        if not total:
            unsupported[t] += n
            continue
        prior = {c: base.get(c, 0) / total for c in country_total}
        if mode == 'original':
            total_all = sum(country_total.values())
            prior = {c: ((base.get(c, 0) + 250 * country_total[c] / total_all) / (total + 250)
                         if region in ['ap', 'br'] else country_total[c] / total_all) for c in country_total}
        observed = bins[t, f]
        count = sum(observed.values())
        probabilities = {c: (observed.get(c, 0) + strength * prior[c]) / (count + strength)
                         for c in country_total} if f is not None and count else prior
        for c, p in probabilities.items():
            result[t][c] += n * p
    return result, unsupported

def calculate(players, mode, strength=STRENGTH, region='ap'):
    # Original means the old production formula before current reported recoveries.
    if mode == 'original':
        players = [dict(p, resolved_country=p['country']) for p in players]
    known = [p for p in players if p['resolved_country']]
    unknown = [p for p in players if not p['resolved_country']]
    expected, unsupported = prediction(known, unknown, mode, strength, region)
    countries = sorted({p['resolved_country'] for p in known})
    out = {c: {k: 0 for k in ['players', *KEYS.values()]} for c in countries}
    for t, key in KEYS.items():
        target = sum(p['tier'] == t for p in unknown)
        allocated, unallocated = distribute(target, expected.get(t, {}))
        assert unallocated == unsupported.get(t, 0)
        for c in countries:
            out[c][key] = sum(p['resolved_country'] == c and p['tier'] == t for p in known) + allocated.get(c, 0)
        assert sum(r[key] for r in out.values()) + unallocated == sum(p['tier'] == t for p in players)
    for r in out.values():
        r['players'] = sum(r[k] for k in KEYS.values())
    assert sum(r['players'] for r in out.values()) + sum(unsupported.values()) == len(players)
    return out, dict(unsupported)

def fold(p):
    # Same Riot ID receives the same fold across shards; each current account appears once per shard.
    return int.from_bytes(hashlib.sha256(p['player_key'].casefold().encode()).digest()[:8], 'big') % 5

def validate(players, mode, strength=STRENGTH, region='ap'):
    known = [p for p in players if p['resolved_country']]
    absolute = held = 0
    for i in range(5):
        train, test = [p for p in known if fold(p) != i], [p for p in known if fold(p) == i]
        predicted, _ = prediction(train, test, mode, strength, region)
        actual = collections.defaultdict(collections.Counter)
        for p in test:
            actual[p['tier']][p['resolved_country']] += 1
        for tier in TIERS:
            for c in set(actual[tier]) | set(predicted[tier]):
                absolute += abs(actual[tier][c] - predicted[tier][c])
        held += len(test)
    return absolute / max(1, held)

def run(input_file=None):
    path = input_file or ROOT / 'build/act-grinding-inputs.json'
    export = json.loads(Path(path).read_text(encoding='utf-8'))
    subprocess.run(['node', str(ROOT / 'scripts/leaderboard/check_act_comparison.cjs'), '--export-baselines'], cwd=ROOT, check=True, capture_output=True, text=True)
    canonical=json.loads((ROOT / 'build/act-grinding-baselines.json').read_text())
    assert canonical['snapshotId']==export['snapshotId']
    names = json.loads((ROOT / 'leaderboard-service/src/main/resources/global-countries.json').read_text(encoding='utf-8'))
    report = dict(snapshotId=export['snapshotId'], act=export['act'], completedAt=export['completedAt'], coverage=export['coverage'],
        generatedAt=datetime.datetime.now(datetime.timezone.utc).isoformat(),
        parameters=dict(winEdges=WIN_EDGES, smoothingStrength=STRENGTH, rankHistoryMinimumObservations=3, rankHistoryMinimumMinutes=60, rrTrajectoryBoundary=25),
        method='Known reported countries first. Unknown accounts are matched within current shard/tier by act-win band and optionally all retained act rank history. Country probabilities are shrunk toward the current country/tier share with ten pseudo-accounts per feature cell. Counts are rounded only after summing expected allocations across feature cells.',
        limitations=['Act wins are cumulative competitive wins, not games played, playtime, win rate or hidden MMR.',
            'All retained current-act rank observations are used, but full-act rank-history coverage is not established. See coverage timestamps.',
            'Historical peak and promotion labels refer to retained observations, not full-act or all-time peaks.',
            'Uniform country-label odds remain an assumption. Known-label validation cannot establish accuracy on actual Unknown accounts.',
            'Parameters were fixed before reading country estimate outcomes; sensitivity analysis is not a selection procedure.'], regions={})
    csv_rows = []
    for region in ['ap', 'eu', 'na', 'br', 'kr', 'latam']:
        players = [p for p in export['players'] if p['region'] == region and p['tier'] in TIERS]
        models = {mode: calculate(players, mode, region=region)[0] for mode in ['original', 'current', 'wins', 'history', 'global_history']}
        # Use the deployed JS calculation for baseline rounding, including its floating-point tie behavior.
        for mode in ['original','current']:
            models[mode]={c:{key:values['estimated_'+key] for key in ['players',*KEYS.values()]} for c,values in canonical['regions'][region][mode].items()}
        validation = {mode: validate(players, mode, region=region) for mode in ['current', 'wins', 'history']}
        sensitivity = {str(a): calculate(players, 'history', a, region)[0] for a in [5, 20, 50]}
        countries = []
        for c in sorted(models['current'], key=lambda c: (-models['current'][c]['players'], c)):
            raw = [p for p in players if p['country'] == c]
            resolved = [p for p in players if p['resolved_country'] == c]
            recovered = [p for p in resolved if not p['country']]
            stats = {}
            for tier, key in KEYS.items():
                sample = [p for p in resolved if p['tier'] == tier and valid(p)]
                wins = [p['wins'] for p in sample]
                stats[key] = dict(usableAccounts=len(sample), medianActWins=statistics.median(wins) if wins else None,
                    meanActWins=statistics.mean(wins) if wins else None,
                    medianBestObservedRR=statistics.median(p['best_observed_rr'] or p['rr'] for p in sample) if sample else None,
                    trajectoryCounts=dict(collections.Counter(trajectory(p) for p in sample)))
            result = dict(country=c, name=names.get(c, c), observed={key: sum(p['tier'] == tier for p in raw) for tier, key in KEYS.items()},
                recovered={key: sum(p['tier'] == tier for p in recovered) for tier, key in KEYS.items()}, stats=stats,
                estimates={mode: model.get(c, dict.fromkeys(['players', *KEYS.values()], 0)) for mode, model in models.items()},
                historySensitivity={a: model[c] for a, model in sensitivity.items()})
            result['observed']['players'] = len(raw)
            result['recovered']['players'] = len(recovered)
            countries.append(result)
            for metric in ['players', *KEYS.values()]:
                csv_rows.append(dict(snapshot=report['snapshotId'], region=region, country=c, metric=metric, observed=result['observed'][metric],
                    reported_recovered=result['recovered'][metric], **{mode: result['estimates'][mode][metric] for mode in models},
                    change_from_current=result['estimates']['history'][metric] - result['estimates']['current'][metric]))
        report['regions'][region] = dict(accounts=len(players), recovered=sum(not p['country'] and bool(p['resolved_country']) for p in players),
            knownLabelFiveFoldAllocationError=validation, countries=countries)
        print(region, 'known-label diagnostic error', json.dumps({k: round(v, 4) for k, v in validation.items()}))
    destination = ROOT / 'docs/act-grinding-comparison.json'
    destination.write_text(json.dumps(report, indent=2), encoding='utf-8')
    (ROOT / 'leaderboard-service/src/main/resources/static/act-grinding-comparison.json').write_text(json.dumps(report, separators=(',', ':')), encoding='utf-8')
    with (ROOT / 'docs/act-grinding-comparison.csv').open('w', newline='', encoding='utf-8') as file:
        writer = csv.DictWriter(file, fieldnames=list(csv_rows[0]));writer.writeheader();writer.writerows(csv_rows)
    for c in ['in', 'sg', 'jp', 'kr', 'ph', 'au']:
        r = next(r for r in report['regions']['ap']['countries'] if r['country'] == c)
        print(c, json.dumps(dict(observed=r['observed'], estimates=r['estimates'], historySensitivity=r['historySensitivity'])))
    return report

if __name__ == '__main__':
    run()


