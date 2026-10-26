"""Compare the unfiltered AP ladder to the service's latest country snapshots.

No country is inferred from a Riot tag. Source observations span a crawl window.
"""
import concurrent.futures
import csv
import datetime
import json
import re
import subprocess
import time
import urllib.request
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
ACT = 'd816f426-48ea-f052-117f-9697a155b319'
BASE = 'http://127.0.0.1:8090/api/leaderboard/'
OUT = ROOT / 'docs/leaderboard-coverage-audit.json'
COUNTRIES = json.loads((ROOT / 'leaderboard-service/src/main/resources/leaderboard-countries.json').read_text())


def api(path):
    with urllib.request.urlopen(BASE + path, timeout=60) as response:
        return json.load(response)


def fetch(page):
    url = f'https://tracker.gg/valorant/leaderboards/ranked/all/default?platform=pc&region=ap&act={ACT}&page={page}'
    command = ['curl.exe', '-sS', '--fail', '--max-time', '30', url,
        '-H', 'Upgrade-Insecure-Requests: 1',
        '-H', 'User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/155.0.0.0 Safari/537.36',
        '-H', 'sec-ch-ua: "Brave";v="155", "Chromium";v="155", "Not(A:Brand";v="24"',
        '-H', 'sec-ch-ua-mobile: ?0', '-H', 'sec-ch-ua-platform: "Windows"']
    raw = subprocess.run(command, capture_output=True, check=True).stdout.decode('utf-8')
    marker = re.search(r'window\.__INITIAL_STATE__\s*=\s*', raw)
    if not marker:
        raise ValueError(f'Page {page}: missing initial state')
    state, _ = json.JSONDecoder().raw_decode(raw[marker.end():])
    board = state['stats']['standardLeaderboards'][0]
    key = board['_key']
    start, end = key.index('{'), key.rindex('}')
    filters = json.loads(key[start:end+1])
    if filters.get('act') != ACT or filters.get('region') != 'ap' or filters.get('platform') != 'pc' or filters.get('country') or key[end+1:] != f'|{(page-1)*100}|100':
        raise ValueError(f'Page {page}: unexpected filters or offset')
    rows = []
    for item in board['items']:
        metadata = item['owner']['metadata']
        stats = {stat['metadata']['key']: stat['value'] for stat in item['owner']['stats']}
        identity = metadata.get('platformUserIdentifier') or item.get('id')
        if not identity or not isinstance(item['rank'], int) or not isinstance(item['value'], int):
            raise ValueError(f'Page {page}: invalid player')
        rows.append(dict(player=identity, key=identity.lower(), country=(metadata.get('countryCode') or '').lower(),
            rank=item['rank'], rr=item['value'], tier=stats['Tier'], wins=stats['Wins'], sourceUpdated=board['lastUpdated']))
    time.sleep(0.5)
    return rows, state


if __name__ == '__main__':
    started = datetime.datetime.now(datetime.timezone.utc).isoformat()
    players, duplicate_rows, conflicting_rows, pages, raw_rows = {}, 0, [], 0, 0
    terminal = False
    catalogue = {}
    with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
        for first in range(1, 1001, 2):
            results = list(pool.map(fetch, [first, first+1]))
            for rows, state in results:
                if terminal:
                    break
                catalogue = {k: v for c in state['region']['countriesByContinents']['continents'] for k,v in c['countries'].items()}
                if rows:
                    pages += 1
                added = 0
                raw_rows += len(rows)
                for row in rows:
                    old = players.get(row['key'])
                    if old:
                        duplicate_rows += 1
                        if any(old[k] != row[k] for k in ['country', 'rr', 'tier', 'wins']):
                            conflicting_rows.append(dict(first=old, later=row))
                    else:
                        players[row['key']] = row
                        added += 1
                if len(rows) == 100 and not added:
                    raise ValueError('Unfiltered pagination made no progress')
                if len(rows) < 100:
                    terminal = True
            if first % 10 == 1:
                print(json.dumps(dict(pages=pages, rawRows=raw_rows, uniquePlayers=len(players))), flush=True)
            if terminal:
                break
    if not terminal:
        raise ValueError('Unfiltered page safety limit reached; audit incomplete')
    for attempt in range(180):
        status = api('status')
        if not status['running']:
            break
        time.sleep(2)
    else:
        raise ValueError('Country collection still running; comparison postponed')
    comparison = api('countries')
    by_country = {}
    with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
        for code, rows in zip(COUNTRIES, pool.map(lambda code: api('players?country='+code), COUNTRIES)):
            by_country[code] = rows
    local = {p['player_key'].lower(): p for rows in by_country.values() for p in rows}
    missing, matched, source_changes = [], [], []
    for key, player in players.items():
        if key in local:
            matched.append(player)
            if player['rr'] != local[key]['rr'] or player['tier'] != local[key]['tier']:
                source_changes.append(dict(player=player['player'], regionalRR=player['rr'], countryRR=local[key]['rr'], regionalTier=player['tier'], countryTier=local[key]['tier']))
        else:
            reason = 'country_unset' if not player['country'] else 'country_outside_configured_scope' if player['country'] not in COUNTRIES else 'in_scope_absent_from_country_snapshot'
            missing.append(dict(**player, reason=reason))
    extras = [dict(player=p['player_key'], country=p['country'], tier=p['tier'], rank=p['regional_rank'], rr=p['rr']) for key,p in local.items() if key not in players]
    categories = []
    for reason in ['matched_country_snapshot', 'country_unset', 'country_outside_configured_scope', 'in_scope_absent_from_country_snapshot']:
        rows = matched if reason == 'matched_country_snapshot' else [p for p in missing if p['reason'] == reason]
        categories.append(dict(reason=reason, players=len(rows), radiant=sum(p['tier']=='Radiant' for p in rows), immortal=sum(p['tier'].startswith('Immortal') for p in rows)))
    country_summary = []
    for code, count in Counter(p['country'] or 'unknown' for p in players.values()).most_common():
        rows = [p for p in players.values() if (p['country'] or 'unknown')==code]
        country_summary.append(dict(country=code, name=catalogue.get(code, code), players=count, radiant=sum(p['tier']=='Radiant' for p in rows), configured=code in COUNTRIES))
    report = dict(act=ACT, region='ap', startedAt=started, completedAt=datetime.datetime.now(datetime.timezone.utc).isoformat(),
        regionalPages=pages, regionalRawRows=raw_rows, regionalUniquePlayers=len(players), regionalRadiants=sum(p['tier']=='Radiant' for p in players.values()),
        duplicateRegionalRows=duplicate_rows, conflictingRegionalRows=conflicting_rows,
        countryUniquePlayers=len(local), countryRadiants=sum(p['tier']=='Radiant' for p in local.values()),
        latestCountryRuns=status['runs'], comparison=comparison, categories=categories, countrySummary=country_summary,
        missingPlayers=missing, countryPlayersAbsentFromRegional=extras, changedStats=source_changes,
        regionalPlayers=list(players.values()), notes=['Independent source snapshots span a crawl window; differences are observations, not an atomic census.', 'Duplicate regional identities retain the first observation; conflicting rows are listed for review.', 'Country unset is reported as unknown; country is never inferred from Riot tags.'])
    OUT.write_text(json.dumps(report, indent=2, ensure_ascii=False), encoding='utf-8')
    with (ROOT/'docs/leaderboard-missing-players.csv').open('w', newline='', encoding='utf-8-sig') as output:
        fields = ['player','country','rank','rr','tier','wins','reason','sourceUpdated']
        writer = csv.DictWriter(output, fieldnames=fields, extrasaction='ignore'); writer.writeheader();writer.writerows(missing)
    print(json.dumps({k:v for k,v in report.items() if k in ['regionalPages','regionalUniquePlayers','regionalRadiants','countryUniquePlayers','countryRadiants','categories','duplicateRegionalRows']}, indent=2), flush=True)


