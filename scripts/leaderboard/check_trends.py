import concurrent.futures,datetime,json,urllib.request,urllib.parse,urllib.error
from pathlib import Path
base='http://127.0.0.1:8090/api/leaderboard/'
def api(path,**params):
 with urllib.request.urlopen(base+path+('?' + urllib.parse.urlencode(params) if params else ''),timeout=45) as r:return json.load(r)
def dt(x):return datetime.datetime.fromisoformat(x.replace('Z','+00:00'))
s=api('status');snapshot=s['latestSnapshot'][0];anchor=dt(snapshot['completed_at']);cases=[]
for minutes in [15,30,60,360,720,1440]:
 for direction in ['overall','increment','decrement']:
  t=api('trends',region='ap',country='all',minutes=minutes,direction=direction)
  cutoff=anchor-datetime.timedelta(minutes=minutes)
  assert t['windowMinutes']==minutes and t['direction']==direction
  assert all(cutoff<=dt(p['completed_at'])<=anchor for p in t['points'])
  assert len(t['movers'])<=20
  assert all(p['region']=='ap' and p['rr_change']==p['current_rr']-p['previous_rr'] for p in t['movers'])
  assert all(dt(p['previous_observed_at'])>=cutoff and dt(p['current_observed_at'])>dt(p['previous_observed_at']) for p in t['movers'])
  if direction=='increment':assert all(p['rr_change']>0 for p in t['movers'])
  if direction=='decrement':assert all(p['rr_change']<0 for p in t['movers'])
  values=[abs(p['rr_change']) for p in t['movers']];assert values==sorted(values,reverse=True)
  cases.append(dict(minutes=minutes,direction=direction,snapshots=t['snapshotCount'],movers=len(t['movers'])))
world=api('trends',minutes=60);assert {p['region'] for p in world['regionPoints']}==set(s['regions'])
japan=api('trends',region='ap',country='jp',minutes=60);assert all(p['country']=='jp' for p in japan['movers'])
country=api('countries',region='ap',country='jp')[0];assert japan['points'][-1]['players']==country['players']
for params in [dict(minutes=17),dict(direction='bad')]:
 try:api('trends',**params);raise AssertionError('Invalid filter accepted')
 except urllib.error.HTTPError as e:assert e.code==400
assert api('status')['latestSnapshot'][0]['id']==snapshot['id'],'Snapshot changed during verification'
report=dict(verifiedAt=datetime.datetime.now(datetime.timezone.utc).isoformat(),snapshotId=snapshot['id'],cases=cases,checks='PASS: all six time ranges and three directions; source timestamp eligibility; magnitude ordering; six-region graph series; Japan scope/counts; invalid filter rejection. DOM/API UI checks separately passed; visual browser layout not inspected.')
Path('docs/dashboard-redesign-verification.json').write_text(json.dumps(report,indent=2),encoding='utf-8')
print(json.dumps(report,indent=2))


