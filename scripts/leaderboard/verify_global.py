"""Checkpointed, paced global-vs-regional identity coverage verification."""
import concurrent.futures
import datetime
import json
import re
import subprocess
import time
from collections import Counter
from pathlib import Path

ROOT=Path(__file__).resolve().parents[2]
ACT='d816f426-48ea-f052-117f-9697a155b319'
REGIONS=['global','ap','kr','eu','na','latam','br']
CACHE=ROOT/'build/global-verification'
CACHE.mkdir(parents=True,exist_ok=True)

def fetch(region,page,refresh=False):
    cache=CACHE/f'{region}-{page}.json'
    if cache.exists():
        try:return json.loads(cache.read_text(encoding='utf-8'))
        except json.JSONDecodeError:pass
    url=f'https://tracker.gg/valorant/leaderboards/ranked/all/default?platform=pc&region={region}&act={ACT}&page={page}'
    if refresh:url+='&_refresh='+str(time.time_ns())
    result=subprocess.run(['curl.exe','-sS','--fail','--max-time','35',url,
        '-H','Upgrade-Insecure-Requests: 1',
        '-H','User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/155.0.0.0 Safari/537.36',
        '-H','sec-ch-ua: "Brave";v="155", "Chromium";v="155", "Not(A:Brand";v="24"',
        '-H','sec-ch-ua-mobile: ?0','-H','sec-ch-ua-platform: "Windows"'],capture_output=True,check=True)
    html=result.stdout.decode('utf-8');match=re.search(r'window\.__INITIAL_STATE__\s*=\s*',html)
    if not match:raise ValueError(f'{region}/{page}: missing initial state')
    state,_=json.JSONDecoder().raw_decode(html[match.end():]);board=state['stats']['standardLeaderboards'][0]
    key=board['_key'];a,b=key.index('{'),key.rindex('}');filters=json.loads(key[a:b+1])
    if filters.get('region')!=region or filters.get('act')!=ACT or filters.get('country') or key[b+1:]!=f'|{(page-1)*100}|100':raise ValueError('Wrong source filters/offset')
    if not isinstance(board.get('items'),list):
        (CACHE/f'{region}-{page}-error.html').write_text(html,encoding='utf-8')
        raise ValueError(f"{region}/{page}: source unavailable: {board.get('errors')}")
    rows=[]
    for item in board['items']:
        meta=item['owner']['metadata'];stats={s['metadata']['key']:s['value'] for s in item['owner']['stats']}
        player=meta.get('platformUserIdentifier') or item['id'];shard=meta.get('platformUserRegion')
        if shard not in REGIONS[1:]:raise ValueError(f'Invalid player region: {shard}')
        rows.append(dict(key=shard+'|'+player.lower(),player=player,region=shard,country=(meta.get('countryCode') or '').lower() or None,rank=item['rank'],rr=item['value'],tier=stats['Tier'],wins=stats['Wins'],sourceUpdated=board['lastUpdated']))
    data=dict(region=region,page=page,rows=rows,checkedAt=datetime.datetime.now(datetime.timezone.utc).isoformat())
    temporary=cache.with_suffix('.tmp');temporary.write_text(json.dumps(data,ensure_ascii=False),encoding='utf-8');temporary.replace(cache);time.sleep(.35)
    return data

def checked_fetch(region,page):
    for attempt in range(3):
        try:return fetch(region,page,refresh=attempt>0)
        except ValueError as error:
            print(str(error),flush=True)
            if attempt==2:raise
            time.sleep(1)

def crawl(region):
    completed=CACHE/f'{region}-complete.json'
    if completed.exists():
        return json.loads(completed.read_text(encoding='utf-8'))
    players={};duplicates=0;raw=0;pages=0;conflicts=0;ended=False
    with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pages_pool:
        for first in range(1,2001,4):
            futures=[pages_pool.submit(checked_fetch,region,n) for n in range(first,first+4)]
            for future in futures:
                rows=future.result()['rows'];raw+=len(rows);added=0
                for row in rows:
                    if row['key'] in players:
                        duplicates+=1
                        if any(players[row['key']][k]!=row[k] for k in ['country','rr','tier','wins']):conflicts+=1
                    else:players[row['key']]=row;added+=1
                if len(rows)==100 and not added:raise ValueError('Pagination made no progress')
                if rows:pages+=1
                if len(rows)<100:ended=True;break
            if first%50==1:print(json.dumps(dict(region=region,pages=pages,players=len(players))),flush=True)
            if ended:break
    if not ended:raise ValueError('Page limit reached')
    result=dict(region=region,pages=pages,rawRows=raw,duplicates=duplicates,conflicts=conflicts,players=list(players.values()))
    completed.write_text(json.dumps(result,ensure_ascii=False),encoding='utf-8')
    print(json.dumps(dict(region=region,complete=True,pages=pages,players=len(players))),flush=True)
    return result

if __name__=='__main__':
    results={}
    with concurrent.futures.ThreadPoolExecutor(max_workers=1) as pool:
        futures={pool.submit(crawl,region):region for region in REGIONS}
        for future in concurrent.futures.as_completed(futures):results[futures[future]]=future.result()
    global_rows={p['key']:p for p in results['global']['players']}
    comparison=[];missing=[]
    for region in REGIONS[1:]:
        regional={p['key']:p for p in results[region]['players']}
        observed={k:p for k,p in global_rows.items() if p['region']==region}
        absent=[p for k,p in regional.items() if k not in observed]
        extras=[p for k,p in observed.items() if k not in regional]
        comparison.append(dict(region=region,regionalPages=results[region]['pages'],regionalUnique=len(regional),globalUnique=len(observed),matched=len(set(regional)&set(observed)),missingFromGlobal=len(absent),globalOnly=len(extras),regionalRadiants=sum(p['tier']=='Radiant' for p in regional.values()),globalRadiants=sum(p['tier']=='Radiant' for p in observed.values())))
        missing.extend(absent)
    report=dict(act=ACT,checkedAt=datetime.datetime.now(datetime.timezone.utc).isoformat(),globalPages=results['global']['pages'],globalRawRows=results['global']['rawRows'],globalUnique=len(global_rows),globalUnknownCountries=sum(not p['country'] for p in global_rows.values()),globalDuplicates=results['global']['duplicates'],globalConflictingDuplicates=results['global']['conflicts'],regions=comparison,missingFromGlobal=missing,notes=['Snapshots span crawl/cache windows, so small identity differences need not indicate truncation.','Region-qualified case-insensitive player identity prevents merging different regional accounts.'])
    (ROOT/'docs/global-pagination-verification.json').write_text(json.dumps(report,indent=2,ensure_ascii=False),encoding='utf-8')
    print(json.dumps({k:v for k,v in report.items() if k!='missingFromGlobal'},indent=2),flush=True)


