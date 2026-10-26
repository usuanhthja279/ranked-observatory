"""Offline, aggregate-only activity diagnostic. Does not change country estimates."""
import json, statistics, collections, datetime
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
BASE = ROOT / 'build/live-collection'
CAPTURES = ['e83aaacb-d36b-4b54-ade1-59ceae5f9ad4', '0b843c6e-42bf-45c1-b280-d383d5e9f6dd']

def read_capture(name):
    groups = collections.defaultdict(list)
    files = sorted((BASE / name).glob('global-*.json'), key=lambda p: int(p.stem.split('-')[1]))
    if not files or json.loads(files[-1].read_text(encoding='utf-8'))['response']['players']:
        raise ValueError('Global capture has no terminal empty page')
    for path in files:
        response = json.loads(path.read_text(encoding='utf-8'))['response']
        for p in response['players']:
            if p['region'] == 'ap':
                groups[p['key'].casefold()].append((p, response['updatedAt']))
    result = {}
    for key, records in groups.items():
        stats = {(r['rr'], r['tier'], r['wins']) for r, _ in records}
        countries = {r['country'] for r, _ in records if r.get('country')}
        if len(stats) != 1 or len(countries) > 1:
            continue
        player, observed = max(records, key=lambda r: r[1])
        result[key] = dict(player, country=next(iter(countries), 'unknown'), observed=observed)
    return result

def timestamp(s):
    return datetime.datetime.fromisoformat(s.replace('Z', '+00:00'))

old, new = map(read_capture, CAPTURES)
groups = collections.defaultdict(list)
rejected = collections.Counter()
for key, after in new.items():
    before = old.get(key)
    if not before:
        rejected['not_comparable_or_ambiguous'] += 1
        continue
    if before['country'] != after['country']:
        rejected['country_report_changed_or_missing'] += 1
        continue
    hours = (timestamp(after['observed']) - timestamp(before['observed'])).total_seconds() / 3600
    dw, dr = after['wins'] - before['wins'], after['rr'] - before['rr']
    if hours <= 0 or dw < 0:
        rejected['nonpositive_window_or_wins_reset'] += 1
        continue
    groups[after['country'], after['tier']].append((dw, dr, hours))

summary = []
for (country, tier), rows in sorted(groups.items()):
    n = len(rows)
    summary.append(dict(country=country, tier=tier, comparable_accounts=n,
        observed_activity_accounts=sum(w > 0 or r != 0 for w, r, h in rows),
        observed_activity_fraction=sum(w > 0 or r != 0 for w, r, h in rows) / n,
        accounts_with_added_wins=sum(w > 0 for w, r, h in rows),
        added_wins=sum(w for w, r, h in rows),
        mean_added_wins_per_account_hour=statistics.mean(w / h for w, r, h in rows),
        median_net_rr_change=statistics.median(r for w, r, h in rows),
        median_window_minutes=60 * statistics.median(h for w, r, h in rows)))

report = dict(captures=CAPTURES, shard='ap', source='saved global page captures only; not reconciled snapshot inventories',
    method='Same reported country in both captures, same account, distinct increasing source times. Exclude within-capture conflicting statistics/countries and wins resets. Activity means added wins or changed RR; zero does not prove inactivity. Group by ending tier.',
    limitations=['Roughly one-hour window with varying page observation times; unsuitable for national grinding conclusions.',
                'Wins are not matches played. Losses, unchanged RR, playtime and hidden MMR are unavailable.',
                'Only accounts retained in both captures; activity affects leaderboard eligibility and selection.',
                'Country labels remain systematically missing. This diagnostic cannot identify missing-country odds.'],
    rejected=dict(rejected), countries=summary)
(ROOT / 'docs/country-grinding-diagnostic.json').write_text(json.dumps(report, indent=2), encoding='utf-8')
for country in ['in', 'sg', 'jp', 'kr', 'ph', 'au', 'unknown']:
    for r in summary:
        if r['country'] == country and r['tier'] in ['Radiant', 'Immortal 3']:
            print(json.dumps(r))


