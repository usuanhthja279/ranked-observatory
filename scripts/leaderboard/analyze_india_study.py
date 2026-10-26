"""Analyze captured competitive matches; no fabricated events or psychological labels."""
import collections
import csv
import gzip
import html
import json
import statistics
from pathlib import Path
from datetime import datetime, timezone

ROOT = Path('data/studies/india-2026-10-23')


def read(path):
    with (gzip.open(path, 'rt', encoding='utf-8-sig') if str(path).endswith('.gz') else open(path, encoding='utf-8-sig')) as f:
        return json.load(f)


def val(segment, key, default=None):
    return segment.get('stats', {}).get(key, {}).get('value', default)


def fraction(a, b):
    return round(a / b, 5) if b else None


def summarize(rows):
    n = len(rows)
    event_rows = [r for r in rows if r['event_data_usable']]
    return dict(rounds=n, matches=len({r['match_id'] for r in rows}),
                win_rate=fraction(sum(r['won'] for r in rows), n),
                damage_per_round=round(statistics.mean(r['damage'] for r in rows), 2) if n else None,
                opening_rounds=len(event_rows),
                opening_involvement=fraction(sum(r['opening_involved'] for r in event_rows), len(event_rows)),
                first_kill_rate=fraction(sum(r['first_kill'] for r in event_rows), len(event_rows)),
                first_death_rate=fraction(sum(r['first_death'] for r in event_rows), len(event_rows)),
                opening_win_rate=fraction(sum(r['first_kill'] for r in event_rows), sum(r['opening_involved'] for r in event_rows)),
                kills=sum(r['kills'] for r in rows), deaths=sum(r['deaths'] for r in rows),
                assists=sum(r['assists'] for r in rows),
                trade_candidates=sum(r['trade_kills'] for r in event_rows),
                traded_death_candidates=sum(r['traded_deaths'] for r in event_rows),
                isolated_high_loadout_rounds=sum(r['isolated_high_loadout'] for r in rows))


def matched_response(rows):
    target = [r for r in rows if r['recovery']]
    baseline = collections.defaultdict(list)
    context = lambda r: (r['map'], r['side'], r['agent'], r['loadout_band'], r['equipment_balance'])
    for r in rows:
        if not r['recovery'] and r['pre_loss_streak'] < 2:
            baseline[context(r)].append(r)
    comparable = [r for r in target if len(baseline[context(r)]) >= 3]
    result = {'raw': summarize(target), 'matched_rounds': len(comparable), 'baseline_minimum_per_context': 3,
              'episodes': len({(r['match_id'], r['recovery_episode']) for r in target})}
    if len(comparable) >= 20:
        changes = {}
        for metric in ('opening_involved', 'first_death', 'damage', 'won'):
            eligible = [r for r in comparable if metric not in ('opening_involved', 'first_death') or
                        (r['event_data_usable'] and sum(b['event_data_usable'] for b in baseline[context(r)]) >= 3)]
            if len(eligible) < 20:
                continue
            observed = statistics.mean(r[metric] for r in eligible)
            expected = statistics.mean(statistics.mean(b[metric] for b in baseline[context(r)] if
                                       metric not in ('opening_involved', 'first_death') or b['event_data_usable']) for r in eligible)
            changes[metric] = {'rounds': len(eligible), 'observed': round(observed, 5), 'matched_baseline': round(expected, 5), 'difference': round(observed - expected, 5)}
        result['descriptive_differences'] = changes
    return result


def analyze():
    cohort = read(ROOT / 'cohort.json')
    histories = read(ROOT / 'histories-index.json')
    index = {h['key'].casefold(): h for h in histories}
    selected = {p['player_key'].casefold(): p for p in cohort['players']}
    wanted = collections.defaultdict(set)
    for h in histories:
        for match in h['matches']:
            wanted[match].add(h['key'].casefold())
    rows_by_player = collections.defaultdict(list)
    meta_by_player = collections.defaultdict(list)
    loadout_by_player = collections.defaultdict(lambda: collections.defaultdict(collections.Counter))
    kill_weapons_by_player = collections.defaultdict(collections.Counter)
    coverage = []
    server_paths = set()
    schema = collections.defaultdict(lambda: {'count': 0, 'metadata': set(), 'stats': set(), 'attributes': set()})
    def inspect_server_keys(value, path='data'):
        if isinstance(value, dict):
            for k, v in value.items():
                if any(x in k.lower() for x in ('server', 'gamepod', 'datacenter', 'ping')):
                    server_paths.add(path + '.' + k)
                inspect_server_keys(v, path + '.' + k)
        elif isinstance(value, list):
            for v in value:
                inspect_server_keys(v, path + '[]')
    for match_id, players in wanted.items():
        path = ROOT / 'matches' / (match_id + '.json.gz')
        if not path.exists():
            coverage.append({'match_id': match_id, 'state': 'not_captured', 'players': sorted(players)})
            continue
        data = read(path)['data']
        metadata = data.get('metadata', {})
        segments = data.get('segments', [])
        inspect_server_keys(data)
        types = collections.defaultdict(list)
        for s in segments:
            t = s['type']; types[t].append(s)
            schema[t]['count'] += 1
            for part in ('metadata', 'stats', 'attributes'):
                schema[t][part].update(s.get(part, {}))
        for k in metadata:
            if any(x in k.lower() for x in ('server', 'gamepod', 'datacenter', 'ping')):
                server_paths.add('metadata.' + k)
        rounds = {int(s['attributes']['round']): s for s in types['round-summary']}
        player_rounds = collections.defaultdict(dict)
        teams_by_round = collections.defaultdict(dict)
        for s in types['player-round']:
            rnd = int(s['attributes']['round']); key = s['attributes']['platformUserIdentifier'].casefold()
            player_rounds[key][rnd] = s; teams_by_round[rnd][key] = s.get('metadata', {}).get('teamId')
        keys = set(player_rounds)
        summaries = {s['attributes']['platformUserIdentifier'].casefold(): s for s in types['player-summary']}
        team_results = {s['attributes']['teamId']: s.get('metadata', {}).get('hasWon') for s in types['team-summary']}
        record = {'match_id': match_id, 'map': metadata.get('mapName'), 'date': metadata.get('dateStarted'),
                  'rounds_reported': metadata.get('rounds'), 'rounds_captured': len(rounds), 'player_rounds': len(types['player-round']),
                  'kill_events': len(types['player-round-kills']), 'extended': metadata.get('extendedDataIncluded', False),
                  'players': sorted(players), 'state': 'captured'}
        if metadata.get('queueId') != 'competitive':
            record['state'] = 'wrong_queue'; coverage.append(record); continue
        valid_rounds = {r for r, s in rounds.items() if val(s, 'winningTeam') in ('Blue', 'Red')}
        full_rounds = len(valid_rounds) == metadata.get('rounds') and set(valid_rounds) == set(range(1, len(valid_rounds) + 1))
        record['complete_round_results'] = full_rounds
        record['missing_selected_players'] = sorted(players - keys)
        coverage.append(record)
        if not full_rounds:
            continue
        for s in types['player-loadout']:
            key = s['attributes']['platformUserIdentifier'].casefold()
            if key not in players:
                continue
            bucket = loadout_by_player[key][s['attributes']['loadout']]
            bucket['match_records'] += 1
            for stat in ('roundsPlayed', 'roundsWon', 'roundsLost', 'kills', 'deaths', 'assists', 'damage', 'firstBloods', 'firstDeaths'):
                value = val(s, stat)
                if isinstance(value, (float, int)):
                    bucket[stat] += value
        # Canonical enemy kill events, excluding self/friendly kills and exact duplicate events.
        events = collections.defaultdict(list); seen = set()
        for s in types['player-round-kills']:
            a = s['attributes']; m = s.get('metadata', {}); rnd = int(a['round'])
            killer = a['platformUserIdentifier'].casefold(); victim = a['opponentPlatformUserIdentifier'].casefold()
            time = m.get('roundTime')
            if time is None or killer == victim or not teams_by_round[rnd].get(killer) or not teams_by_round[rnd].get(victim):
                continue
            if teams_by_round[rnd][killer] == teams_by_round[rnd][victim]:
                continue
            signature = (rnd, killer, victim, time, m.get('weaponName'))
            if signature in seen:
                continue
            seen.add(signature)
            events[rnd].append({'killer': killer, 'victim': victim, 'time': time, 'weapon': m.get('weaponName'), 'category': m.get('weaponCategory')})
            if killer in players:
                kill_weapons_by_player[killer][m.get('weaponName') or 'Unavailable'] += 1
        openings = {}; trade_kills = collections.Counter(); traded_deaths = collections.Counter()
        for rnd, kills in events.items():
            kills.sort(key=lambda e: (e['time'], e['killer'], e['victim']))
            openings[rnd] = kills[0]
            for i, kill in enumerate(kills):
                prior = [p for p in kills[:i] if 0 <= kill['time'] - p['time'] <= 5000 and p['killer'] == kill['victim'] and teams_by_round[rnd].get(p['victim']) == teams_by_round[rnd].get(kill['killer'])]
                if prior:
                    trade_kills[(rnd, kill['killer'])] += 1
                    traded_deaths[(rnd, prior[-1]['victim'])] += 1
        scores = {'Blue': 0, 'Red': 0}; streaks = {'Blue': 0, 'Red': 0}; recovery = {}; phase = None
        for rnd in sorted(valid_rounds):
            new_phase = 0 if rnd <= 12 else 1 if rnd <= 24 else 2
            if new_phase != phase:
                streaks = {'Blue': 0, 'Red': 0}; recovery = {}; phase = new_phase
            for team in scores:
                if streaks[team] == 3 and team not in recovery and phase < 2:
                    recovery[team] = (rnd, rnd + 2)
                if team in recovery and rnd > recovery[team][1]:
                    del recovery[team]
            winner = val(rounds[rnd], 'winningTeam')
            team_loadouts = collections.defaultdict(list)
            for key, prs in player_rounds.items():
                if rnd in prs:
                    v = val(prs[rnd], 'loadoutValue')
                    if isinstance(v, (int, float)):
                        team_loadouts[prs[rnd]['metadata']['teamId']].append(v)
            for key in players & keys:
                prs = player_rounds[key]
                if rnd not in prs:
                    continue
                s = prs[rnd]; m = s.get('metadata', {}); team = m.get('teamId'); other = 'Blue' if team == 'Red' else 'Red'
                if team not in scores or any(val(s, k) is None for k in ('kills', 'deaths', 'assists', 'damage')):
                    continue
                loadout = val(s, 'loadoutValue'); band = 'unavailable'
                if loadout is not None:
                    band = 'low' if loadout < 2000 else 'mid' if loadout < 3500 else 'high'
                if rnd in (1, 13): band = 'pistol'
                elif rnd >= 25: band = 'overtime'
                own = team_loadouts[team]; enemy = team_loadouts[other]
                equipment = 'unavailable'
                if len(own) == 5 and len(enemy) == 5 and sum(enemy):
                    ratio = sum(own) / sum(enemy)
                    equipment = 'advantage' if ratio > 1.2 else 'disadvantage' if ratio < 1 / 1.2 else 'similar'
                opener = openings.get(rnd, {})
                first_kill = opener.get('killer') == key; first_death = opener.get('victim') == key
                own_mp = scores[team] >= 12 and scores[team] > scores[other]
                opp_mp = scores[other] >= 12 and scores[other] > scores[team]
                late = max(scores.values()) >= 10 and abs(scores[team] - scores[other]) <= 2
                rec = recovery.get(team)
                recorded_kills = sum(val(prs[rnd], 'kills', 0) for prs in player_rounds.values() if rnd in prs)
                event_usable = bool(metadata.get('extendedDataIncluded') and len(events[rnd]) == recorded_kills)
                pistol_winner = val(rounds.get(rnd - 1, {}), 'winningTeam') if rnd in (2, 14) else None
                rows_by_player[key].append(dict(match_id=match_id, round=rnd, map=metadata.get('mapName'), date=metadata.get('dateStarted'),
                    act=metadata.get('seasonId'), agent=m.get('agentName'), side=m.get('teamSide'), team=team,
                    kills=val(s, 'kills'), deaths=val(s, 'deaths'), assists=val(s, 'assists'), damage=val(s, 'damage'),
                    won=winner == team, first_kill=first_kill, first_death=first_death, opening_involved=first_kill or first_death,
                    opening_available=bool(opener), event_data_usable=event_usable,
                    trade_kills=trade_kills[(rnd, key)], traded_deaths=traded_deaths[(rnd, key)],
                    loadout=loadout, loadout_band=band, equipment_balance=equipment, remaining_credits=val(s, 'remainingCredits'), spent_credits=val(s, 'spentCredits'),
                    pressure=own_mp or opp_mp or rnd >= 25 or late, own_match_point=own_mp, opponent_match_point=opp_mp,
                    post_pistol='after_win' if pistol_winner == team else 'after_loss' if pistol_winner else None,
                    pre_loss_streak=streaks[team], recovery=bool(rec), recovery_episode=rec[0] if rec else None,
                    isolated_high_loadout=bool(band == 'high' and len(own) == 5 and sum(x < 2000 for x in own) >= 3)))
            for team in scores:
                streaks[team] = 0 if team == winner else streaks[team] + 1
            scores[winner] += 1
        for key in players:
            s = summaries.get(key)
            if s:
                meta_by_player[key].append(dict(match_id=match_id, date=metadata.get('dateStarted'), map=metadata.get('mapName'),
                    country=s.get('metadata', {}).get('countryCode'), observed_tier=val(s, 'rank'), current_tier=s.get('stats', {}).get('currRank', {}).get('metadata', {}).get('tierName'),
                    party=s.get('metadata', {}).get('partyId'), agent=s.get('metadata', {}).get('agentName'),
                    team_won=team_results.get(s.get('metadata', {}).get('teamId')),
                    clutches=val(s, 'clutches'), clutches_lost=val(s, 'clutchesLost'),
                    provider_first_kills=val(s, 'firstKills'), provider_first_deaths=val(s, 'firstDeaths')))
    results = []
    for key, p in selected.items():
        rows = rows_by_player[key]; h = index.get(key, {})
        categories = {name: summarize([r for r in rows if r['loadout_band'] == name]) for name in ('pistol', 'low', 'mid', 'high', 'overtime')}
        maps = {}
        for map_name in sorted({r['map'] for r in rows if r['map']}):
            mr = [r for r in rows if r['map'] == map_name]
            maps[map_name] = dict(overall=summarize(mr), pistol=summarize([r for r in mr if r['loadout_band'] == 'pistol']),
                                 pressure=summarize([r for r in mr if r['pressure']]), loss_response=matched_response(mr),
                                 sides={side: summarize([r for r in mr if r['side'] == side]) for side in ('attacker', 'defender')})
        total = summarize(rows)
        provider_loadouts = {}
        for name, counters in loadout_by_player[key].items():
            provider_loadouts[name] = dict(counters,
                win_rate=fraction(counters['roundsWon'], counters['roundsPlayed']),
                damage_per_round=fraction(counters['damage'], counters['roundsPlayed']),
                first_kill_rate=fraction(counters['firstBloods'], counters['roundsPlayed']),
                first_death_rate=fraction(counters['firstDeaths'], counters['roundsPlayed']))
        results.append(dict(player=p['display_name'], key=key, selection_tier=p['tier'], selection_rr=p['rr'], selection_region=p['region'],
            requested_matches=30, history_matches=h.get('matchCount', 0), history_error=h.get('error'), round_analysis_matches=total['matches'],
            captured_history_matches=sum((ROOT / 'matches' / (m + '.json.gz')).is_file() for m in h.get('matches', [])),
            history_capture_complete=bool(h.get('matches')) and all((ROOT / 'matches' / (m + '.json.gz')).is_file() for m in h.get('matches', [])),
            overall=total, categories=categories, pressure=summarize([r for r in rows if r['pressure']]), loss_response=matched_response(rows),
            provider_loadouts=provider_loadouts, killing_weapons=dict(kill_weapons_by_player[key].most_common()),
            post_pistol={name: summarize([r for r in rows if r['post_pistol'] == name]) for name in ('after_win', 'after_loss')},
            equipment={name: summarize([r for r in rows if r['equipment_balance'] == name]) for name in ('advantage', 'similar', 'disadvantage')},
            match_metadata=meta_by_player[key], maps=maps,
            agents=dict(collections.Counter(m['agent'] for m in meta_by_player[key])),
            date_range=dict(start=min((m['date'] for m in meta_by_player[key] if m['date']), default=None),
                            end=max((m['date'] for m in meta_by_player[key] if m['date']), default=None)),
            coverage=dict(opening_event_rounds=sum(r['opening_available'] for r in rows),
                          complete_kill_event_rounds=sum(r['event_data_usable'] for r in rows), player_rounds=len(rows),
                          acts=dict(collections.Counter(r['act'] for r in rows)),
                          matches_with_reported_country=dict(collections.Counter(str(m['country']) for m in meta_by_player[key])))))
    tiers = []
    for tier in ('Radiant', 'Immortal 3', 'Immortal 2', 'Immortal 1'):
        group = [p for p in results if p['selection_tier'] == tier]
        summary = {'tier': tier, 'selected': len(group), 'with_round_data': sum(p['round_analysis_matches'] > 0 for p in group),
                   'fully_captured_players': sum(p['history_capture_complete'] for p in group),
                   'player_match_observations': sum(p['round_analysis_matches'] for p in group), 'player_round_observations': sum(p['overall']['rounds'] for p in group)}
        for category in ('overall', 'pistol', 'pressure'):
            part = lambda p: p['categories']['pistol'] if category == 'pistol' else p[category]
            values = [part(p) for p in group if p['history_capture_complete'] and part(p)['rounds'] >= 20]
            summary[category] = {'eligible_players': len(values)}
            for k in ('win_rate', 'damage_per_round', 'opening_involvement', 'first_kill_rate', 'first_death_rate'):
                v = [x[k] for x in values if x[k] is not None]
                summary[category][k] = round(statistics.mean(v), 5) if v else None
        tiers.append(summary)
    report = dict(generated_at=datetime.now(timezone.utc).isoformat(), cohort_snapshot=cohort['snapshotId'],
                  collection_status=read(ROOT / 'capture-status.json') if (ROOT / 'capture-status.json').exists() else {},
                  selection=cohort['selection'], selected_players=len(results), unique_requested_matches=len(wanted),
                  unique_captured_matches=sum(x['state'] == 'captured' for x in coverage),
                  matches_with_complete_round_results=sum(x.get('complete_round_results', False) for x in coverage),
                  server_fields_found=sorted(server_paths), schema={t: {k: sorted(v) if isinstance(v, set) else v for k, v in s.items()} for t, s in schema.items()},
                  tiers=tiers, players=results, match_coverage=coverage,
                  limitations=['Reported India at selection, not verified nationality or residency.',
                               'Cohort tier is frozen at selection; historical matches may have different ranks/acts.',
                               'Full histories and extended rounds may be unavailable; coverage is explicit.',
                               'Low/mid/high are descriptive recorded-loadout bands (<2000, 2000–3499, >=3500), not verified buy intentions.',
                               'Trade candidate uses a five-second revenge-kill window; it is not a causal opportunity measure.',
                               'Opening event tie at the same timestamp is deterministically ordered; damage before first kill is not an entry attempt measure.',
                               'Loss-response differences are descriptive, matched within player/map/side/agent/loadout/equipment context, not evidence of tilt.',
                               'Opening/trade rates exclude rounds whose enemy-kill event count differs from recorded kills. Missing events are not zero.',
                               'Provider Pistol/Semi/Eco/Full categories are match aggregates; their exact classification rule is not supplied in the response.',
                               'No exact purchase inventories, game server or continuous movement are claimed.',
                               'Ten sampled players per tier do not establish national play style; equal-player means are exploratory.'])
    (ROOT / 'analysis.json').write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding='utf-8')
    with open(ROOT / 'player-rounds.csv', 'w', newline='', encoding='utf-8-sig') as f:
        all_rows = [dict(player=p['display_name'], selection_tier=p['tier'], **r) for key, p in selected.items() for r in rows_by_player[key]]
        if all_rows:
            w = csv.DictWriter(f, fieldnames=list(all_rows[0])); w.writeheader(); w.writerows(all_rows)
    build_html(report)
    build_findings(report)
    print(json.dumps({k: report[k] for k in ('selected_players', 'unique_requested_matches', 'unique_captured_matches', 'matches_with_complete_round_results')}, indent=2))


def pct(v):
    return '—' if v is None else f'{100*v:.1f}%'


def build_findings(report):
    complete = report['unique_captured_matches'] == report['unique_requested_matches']
    lines = ['# India reported-country cohort: rank-wise observations', '',
             ('Capture complete.' if complete else '**Partial collection: the planned rank comparison is not complete.**'),
             f"Generated {report['generated_at']}; {report['unique_captured_matches']}/{report['unique_requested_matches']} unique matches captured.", '',
             'Tier rates below use equal-player means from fully downloaded histories only. '
             'Historical match rank can differ from selection rank. Reported country is not verified nationality; '
             'the Radiant sample includes two NA-shard accounts. Agent and opponent differences prevent a causal rank comparison.', '']
    for tier in report['tiers']:
        lines += [f"## {tier['tier']}", '',
                  f"Fully captured players: {tier['fully_captured_players']}/{tier['selected']} planned."]
        if tier['overall']['eligible_players']:
            lines += [f"Opening-fight involvement: {pct(tier['overall']['opening_involvement'])}; "
                      f"damage per round: {tier['overall']['damage_per_round']:.1f}; "
                      f"pistol team-win rate: {pct(tier['pistol']['win_rate'])}."]
        else:
            lines += ['No fully captured histories yet; no rank-level play-style conclusion.']
        for player in report['players']:
            if player['selection_tier'] != tier['tier'] or not player['history_capture_complete']:
                continue
            response = player['loss_response']
            lines += ['', f"**{player['player']}** — {player['round_analysis_matches']} usable matches; "
                      f"{response['episodes']} loss-streak episodes; {response['matched_rounds']} context-matched following rounds."]
            changes = response.get('descriptive_differences', {})
            for key, title, unit in [('opening_involved', 'Opening involvement', 'rate'),
                                      ('first_death', 'First-death rate', 'rate'),
                                      ('damage', 'Damage per round', 'damage')]:
                if key not in changes:
                    continue
                d = changes[key]
                if unit == 'rate':
                    lines += [f"- {title}: {pct(d['matched_baseline'])} baseline → {pct(d['observed'])} after losses "
                              f"({d['difference']*100:+.1f} percentage points; {d['rounds']} comparable rounds)."]
                else:
                    lines += [f"- {title}: {d['matched_baseline']:.1f} baseline → {d['observed']:.1f} after losses."]
            if not changes:
                lines += ['Insufficient comparable rounds for a loss-response comparison.']
        lines += ['']
    lines += ['## How to read loss response', '',
              'After losses means the next three rounds once a team reaches three consecutive round losses. '
              'The comparison matches player, map, side, agent, recorded loadout band and relative team equipment. '
              'It requires at least three baseline observations per context and twenty comparable response rounds. '
              'Remaining differences in opponents, teammates and game state are not controlled. '
              'These are descriptive changes, not proof of aggression, tilt, intention or mental resilience. '
              'Shared games and repeated rounds are not independent samples.', '',
              'See report.html for provider economy categories, map/side evidence, pistol conversion and coverage.']
    (ROOT / 'findings.md').write_text('\n'.join(lines) + '\n', encoding='utf-8')


def build_html(report):
    e = html.escape
    complete = report['unique_captured_matches'] == report['unique_requested_matches']
    collection_note = ('Capture complete. Round-analysis exclusions remain explicit below.' if complete else
                       'PARTIAL COLLECTION — only downloaded evidence is shown. Do not interpret this as the completed 30-match tier comparison.')
    tier_rows = ''.join(f"<tr><td>{e(t['tier'])}</td><td>{t['fully_captured_players']}/{t['selected']}</td><td>{t['player_match_observations']}</td><td>{pct(t['overall']['opening_involvement'])}</td><td>{pct(t['pistol']['win_rate'])}</td><td>{pct(t['pressure']['first_death_rate'])}</td></tr>" for t in report['tiers'])
    player_blocks = []
    for p in report['players']:
        o = p['overall']; pr = p['categories']['pistol']; pressure = p['pressure']; loss = p['loss_response']
        if not o['rounds']:
            continue
        map_rows = ''.join(f"<tr><td>{e(m)}</td><td>{v['overall']['matches']}</td><td>{v['overall']['rounds']}</td><td>{pct(v['overall']['opening_involvement'])}</td><td>{pct(v['pistol']['win_rate'])} ({v['pistol']['rounds']})</td><td>{pct(v['pressure']['first_death_rate'])} ({v['pressure']['rounds']})</td></tr>" for m, v in p['maps'].items())
        descriptions = []
        if loss.get('descriptive_differences'):
            for metric, title in [('opening_involved', 'Opening involvement'), ('first_death', 'First-death rate'), ('won', 'Team round win rate')]:
                if metric not in loss['descriptive_differences']:
                    continue
                d = loss['descriptive_differences'][metric]
                descriptions.append(f"{title}: {pct(d['observed'])} after streak vs {pct(d['matched_baseline'])} matched baseline ({d['difference']*100:+.1f} percentage points; {d['rounds']} matched rounds).")
        else:
            descriptions.append('Insufficient comparable recovery rounds for a behavioural comparison; no mindset label assigned.')
        loadout_rows = ''.join(f"<tr><td>{e(name.title())}</td><td>{v['match_records']}</td><td>{v['roundsPlayed']}</td><td>{pct(v['win_rate'])}</td><td>{v['damage_per_round'] or 0:.1f}</td><td>{pct(v['first_kill_rate'])}</td><td>{pct(v['first_death_rate'])}</td></tr>" for name, v in p['provider_loadouts'].items())
        weapon_text = ', '.join(f'{name}: {count}' for name, count in list(p['killing_weapons'].items())[:5])
        conversion = p['post_pistol']['after_win']; response = p['post_pistol']['after_loss']
        player_blocks.append(f"""<details class="player" data-name="{e(p['player'].lower())}" data-tier="{e(p['selection_tier'])}"><summary>{e(p['player'])}<span>{e(p['selection_tier'])} · {p['selection_rr']} RR · {p['round_analysis_matches']}/{p['history_matches']} matches with round data</span></summary>
<p>{o['rounds']} observed rounds · {pct(o['opening_involvement'])} opening involvement · {pct(o['first_death_rate'])} first deaths · {o['damage_per_round'] or '—'} damage/round.</p>
<p>Pistols: team won {pct(pr['win_rate'])} across {pr['rounds']} opportunities. Pressure rounds: {pressure['rounds']}, first deaths {pct(pressure['first_death_rate'])}. These are observations, not attribution of team wins to the player.</p>
<p>Round immediately after a pistol win: {pct(conversion['win_rate'])} team wins ({conversion['rounds']} opportunities). After a pistol loss: {pct(response['win_rate'])} ({response['rounds']}). Opening/trade rates have {o['opening_rounds']}/{o['rounds']} rounds with reconciled event logs.</p>
<p>After three consecutive losses: {loss['episodes']} episodes, {loss['raw']['rounds']} following rounds, {loss['matched_rounds']} context-matched rounds. {' '.join(descriptions)}</p>
<h3>Provider economy categories</h3><p class="muted">Tracker's match-level categories. Labels do not reveal exact purchases or buying intention.</p><div class="scroll"><table><thead><tr><th>Category</th><th>Match records</th><th>Rounds</th><th>Team win rate</th><th>Damage / round</th><th>First kills / round</th><th>First deaths / round</th></tr></thead><tbody>{loadout_rows}</tbody></table></div>
<p>Most recorded killing weapons: {e(weapon_text)}. Kill counts are not weapon use or purchase rates.</p>
<h3>Map evidence</h3>
<div class="scroll"><table><thead><tr><th>Map</th><th>Matches</th><th>Rounds</th><th>Opening involvement</th><th>Pistol team wins (n)</th><th>Pressure first deaths (n)</th></tr></thead><tbody>{map_rows}</tbody></table></div>
<p class="muted">Selection region: {e(p['selection_region'])}. Match range: {e(str(p['date_range']['start']))} to {e(str(p['date_range']['end']))}. Agents (matches): {e(str(p['agents']))}. Historical acts (rounds): {e(str(p['coverage']['acts']))}. Reported match-country evidence: {e(str(p['coverage']['matches_with_reported_country']))}.</p></details>""")
    warnings = ''.join('<li>' + e(x) + '</li>' for x in report['limitations'])
    doc = f"""<!doctype html><html lang="en"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>India round behaviour study</title><style>
:root{{color-scheme:dark;font:15px system-ui;background:#0d1420;color:#e2eaf1}}body{{max-width:1200px;margin:auto;padding:28px}}h1{{font-size:28px}}h2{{margin-top:32px}}p{{line-height:1.7}}.muted,small{{color:#a9bac8}}.stats{{display:flex;gap:32px;flex-wrap:wrap;padding:20px 0;border-block:1px solid #304252}}.stats strong{{display:block;font-size:25px}}table{{border-collapse:collapse;width:100%;font-size:13px}}th,td{{padding:13px;text-align:left;border-bottom:1px solid #304252}}th{{color:#9dd5d0}}.scroll{{overflow:auto}}.player{{border-top:1px solid #304252;padding:18px 0}}summary{{cursor:pointer;font-weight:650}}summary span{{display:block;color:#a9bac8;font-weight:400;font-size:13px;margin-top:7px}}input,select{{padding:12px;background:#162537;color:inherit;border:1px solid #445a6c;border-radius:7px;margin:5px}}.notice{{border-left:3px solid #9dd5d0;padding-left:18px}}li{{margin-bottom:8px;line-height:1.6}}a{{color:#9dd5d0}}
</style><h1>India · Round behaviour study</h1><p class="muted">Reported-country cohort · snapshot {report['cohort_snapshot']} · generated {e(report['generated_at'])}. Descriptive research, not a national mindset assessment.</p>
<div class="stats"><div><strong>{report['selected_players']}</strong>selected players</div><div><strong>{report['unique_captured_matches']}/{report['unique_requested_matches']}</strong>unique matches captured</div><div><strong>{report['matches_with_complete_round_results']}</strong>complete round-result timelines</div></div>
<p class="notice"><strong>{collection_note}</strong></p>
<p class="notice">Tier rates use only players whose complete requested histories are downloaded, equal-player means and at least 20 eligible rounds per category. Observation totals include partial histories. Map rows retain denominators; small samples do not support stable labels. No tests were run.</p>
<h2>Tier overview</h2><div class="scroll"><table><thead><tr><th>Selection tier</th><th>Players fully captured</th><th>All usable player-match observations</th><th>Opening involvement</th><th>Pistol team-win rate</th><th>Pressure first-death rate</th></tr></thead><tbody>{tier_rows}</tbody></table></div>
<h2>Player evidence</h2><input id="search" placeholder="Find player" aria-label="Find player"><select id="tier" aria-label="Selection tier"><option value="">All tiers</option><option>Radiant</option><option>Immortal 3</option><option>Immortal 2</option><option>Immortal 1</option></select>{''.join(player_blocks)}
<h2>Definitions and limits</h2><ul>{warnings}</ul><p>Pressure = overtime, either team's match point, or close late regulation (at least ten rounds won by a team, score difference at most two). Recovery = next three rounds after the first three-loss streak trigger; half boundaries reset the window. Context matching requires the same player, map, side, agent, recorded-loadout band and relative equipment band.</p><p><a href="analysis.json">Full analysis JSON</a> · <a href="player-rounds.csv">Player-round observations CSV</a> · <a href="cohort.json">Frozen cohort</a></p>
<script>function filter(){{const q=document.getElementById('search').value.toLowerCase(),tier=document.getElementById('tier').value;document.querySelectorAll('.player').forEach(p=>p.hidden=!p.dataset.name.includes(q)||(tier&&p.dataset.tier!==tier))}}document.getElementById('search').oninput=filter;document.getElementById('tier').onchange=filter;</script></html>"""
    (ROOT / 'report.html').write_text(doc, encoding='utf-8')


if __name__ == '__main__':
    analyze()


