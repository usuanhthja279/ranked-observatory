"""Audit Tracker's Asia/Oceania country filters within AP; never treat blocks as zero."""
import concurrent.futures
import datetime
import json
import re
import subprocess
import time
import sys
import csv
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
ACT = 'd816f426-48ea-f052-117f-9697a155b319'
COUNTRIES = json.loads((ROOT / 'leaderboard-service/src/main/resources/leaderboard-countries.json').read_text())
OUT = ROOT / 'docs/leaderboard-country-audit.json'


def check(pair):
    country, name = pair
    result = dict(country=country, name=name, pages=0, players=0, status='checking')
    seen = set()
    try:
        for page in range(1, 201):
            url = f'https://tracker.gg/valorant/leaderboards/ranked/all/default?platform=pc&region=ap&act={ACT}&country={country}&page={page}'
            response = subprocess.run(['curl.exe', '-sS', '--fail', '--max-time', '25', url,
                '-H', 'Upgrade-Insecure-Requests: 1', '-H', 'sec-ch-ua: "Brave";v="155", "Chromium";v="155", "Not(A:Brand";v="24"', '-H', 'sec-ch-ua-mobile: ?0', '-H', 'sec-ch-ua-platform: "Windows"', '-H', 'User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/155.0.0.0 Safari/537.36'],
                capture_output=True, check=True).stdout.decode('utf-8')
            match = re.search(r'window\.__INITIAL_STATE__\s*=\s*', response)
            if not match:
                raise ValueError('No initial state; challenge or changed HTML')
            state, _ = json.JSONDecoder().raw_decode(response[match.end():])
            boards = state['stats']['standardLeaderboards']
            if not boards:
                raise ValueError('No leaderboard object; cannot verify empty country')
            board = boards[0]
            key = board['_key']
            if f'"country":"{country}"' not in key or ACT not in key or f'|{(page-1)*100}|100' not in key:
                raise ValueError('Response does not match requested filters/offset')
            rows = board['items']
            ids = {r['id'] for r in rows}
            if ids & seen:
                result['overlappingPlayers'] = result.get('overlappingPlayers', 0) + len(ids & seen)
            seen |= ids
            result['players'] += len(rows)
            result['uniquePlayers'] = len(seen)
            result['pages'] += bool(rows)
            if len(rows) < 100:
                result['status'] = 'complete'
                break
            time.sleep(0.4)
        else:
            raise ValueError('Page safety limit reached')
    except Exception as error:
        result['status'] = 'failed'
        result['error'] = ((error.stderr.decode('utf-8', errors='replace') if isinstance(error, subprocess.CalledProcessError) else str(error)))[:200]
        result['pagesObserved'] = result.pop('pages')
        result['playersObserved'] = result.pop('players')
    print(json.dumps(result), flush=True)
    return result


if __name__ == '__main__':
    previous = json.loads(OUT.read_text())['countries'] if '--retry-failed' in sys.argv and OUT.exists() else []
    results = [r for r in previous if r['status'] == 'complete']
    pending = {r['country'] for r in previous if r['status'] != 'complete'} if previous else set(COUNTRIES)
    with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
        for result in pool.map(check, [(k,v) for k,v in COUNTRIES.items() if k in pending]):
            results.append(result)
            OUT.write_text(json.dumps(dict(act=ACT, region='ap', scope='Tracker Asia and Oceania country catalogue, filtered to AP',
                checkedAt=datetime.datetime.now(datetime.timezone.utc).isoformat(), countries=results), indent=2), encoding='utf-8')

    if len(results) == len(COUNTRIES):
        (ROOT / "leaderboard-service/src/main/resources/static/leaderboard-country-audit.json").write_text(OUT.read_text(encoding="utf-8"), encoding="utf-8")
        with (ROOT / "docs/leaderboard-country-pages.csv").open("w", newline="", encoding="utf-8") as output:
            writer = csv.writer(output)
            writer.writerow(["country", "name", "pages", "rows_observed", "unique_players_if_recorded", "overlapping_players", "status"])
            writer.writerows([r["country"], r["name"], r.get("pages"), r.get("players"), r.get("uniquePlayers", ""), r.get("overlappingPlayers", 0), r["status"]] for r in sorted(results, key=lambda r:r["name"]))


