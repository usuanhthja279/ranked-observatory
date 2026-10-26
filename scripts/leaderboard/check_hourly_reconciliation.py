import json,urllib.request,urllib.parse,concurrent.futures,datetime,time
from pathlib import Path
base='http://127.0.0.1:8090/api/leaderboard/'
def get(path,**params):
 with urllib.request.urlopen(base+path+('?' + urllib.parse.urlencode(params) if params else ''),timeout=45) as r:return json.load(r)
s=get('status')
for _ in range(20):
 if not s['running']:break
 time.sleep(1);s=get('status')
assert not s['running'],'Global publication still active'
r=s['latestSnapshot'][0];assert r['source']=='global'
c=json.loads(r['coverage_json']);assert not c['regionalReconciliation'];assert all(k not in c for k in ['ap','br','eu','kr','latam','na'])
assert s['globalIntervalMinutes']==20 and s['regionalIntervalHours']==1 and s['scheduleZone']=='Asia/Kolkata'
first=get('players',size=250);n=(first['total']+249)//250
with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
 rest=list(pool.map(lambda p:get('players',size=250,page=p),range(2,n+1)))
rows=first['items']+[p for x in rest for p in x['items']];carried=[p for p in rows if p['carried_forward']]
assert len(rows)==r['players']==first['total'];assert len(carried)==c['carriedForwardPlayers']>0
assert r['raw_rows']-r['duplicate_rows']+len(carried)==len(rows)
assert all(p['global_rank'] is None and p['rr_change'] is None and p['rank_gain'] is None and p['movement']=='CARRIED_FORWARD' for p in carried)
assert all(datetime.datetime.fromisoformat(p['source_updated_at'].replace('Z','+00:00'))<datetime.datetime.fromisoformat(r['completed_at'].replace('Z','+00:00')) for p in carried)
regions=get('regions');countries=get('countries');assert sum(p['players'] for p in regions)==len(rows)==sum(p['players'] for p in countries)
assert sum(p['carried_forward'] for p in regions)==len(carried)==sum(p['carried_forward'] for p in countries)
checks=[]
for region in s['regions']:
 sample=next((p for p in carried if p['region']==region),None)
 if sample:
  h=get('history',player=sample['player_key'],region=region)
  assert all(p['source_updated_at']!=sample['source_updated_at'] or p['completed_at']!=r['completed_at'] for p in h)
  assert len(h)>=1
  checks.append(dict(player=sample['display_name'],region=region,sourceAsOf=sample['source_updated_at'],historyPoints=len(h),noCarriedSnapshotInHistory=all(p['completed_at']!=r['completed_at'] for p in h)))
  assert checks[-1]['noCarriedSnapshotInHistory']
report=dict(verifiedAt=datetime.datetime.now(datetime.timezone.utc).isoformat(),runId=r['id'],source=r['source'],globalIntervalMinutes=s['globalIntervalMinutes'],regionalIntervalHours=s['regionalIntervalHours'],scheduleZone=s['scheduleZone'],lastRegionalReconciliation=s['lastRegionalReconciliation'],players=len(rows),carriedForwardPlayers=len(carried),freshGlobalPlayers=len(rows)-len(carried),sourcePages=r['pages'],sourceCoverageKeys=list(c.keys()),requestMetrics=s['requestMetrics'],historyChecks=checks,checks='PASS: global-only source, no regional source pages, retained observation times, suppressed carried deltas/history points, region/country inventory and carried-count reconciliation.')
Path('docs/hourly-reconciliation-verification.json').write_text(json.dumps(report,indent=2,ensure_ascii=False),encoding='utf-8')
print(json.dumps(report,ensure_ascii=True,indent=2))


