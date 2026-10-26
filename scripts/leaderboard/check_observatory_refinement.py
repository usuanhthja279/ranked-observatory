import concurrent.futures,collections,datetime,json,math,urllib.request,urllib.parse,urllib.error,http.cookiejar
from pathlib import Path
root=Path.cwd();base='http://127.0.0.1:8090'
def api(path,**params):
 with urllib.request.urlopen(base+'/api/leaderboard/'+path+('?' + urllib.parse.urlencode(params) if params else ''),timeout=45) as r:return json.load(r)
s=api('status');snapshot=s['latestSnapshot'][0];first=api('players',size=250)
with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:rest=list(pool.map(lambda p:api('players',size=250,page=p),range(2,math.ceil(first['total']/250)+1)))
rows=first['items']+[p for result in rest for p in result['items']];checks=[]
for region in sorted(s['regions']):
 local=[p for p in rows if p['region']==region];clean=[p for p in local if not p['carried_forward'] and not p['stats_ambiguous']];radiants=[p for p in clean if p['tier']=='Radiant'];countries=api('countries',region=region)
 if radiants:
  threshold=min(p['rr'] for p in radiants)
  assert sum(c['radiant_top50'] for c in countries)==math.ceil(len(radiants)*.5)
  assert sum(c['radiant_top10'] for c in countries)==math.ceil(len(radiants)*.1)
  for c in countries:
   rr=[p for p in clean if (p['country'] or 'unknown')==c['country']]
   assert c['near_radiant']==sum(p['tier'].startswith('Immortal') and threshold-50<=p['rr']<=threshold for p in rr)
   assert c['radiant_boundary']==sum(p['tier']=='Radiant' and p['rr']<threshold+50 for p in rr)
   assert 0<=c['radiant_top10']<=c['radiant_top50']<=sum(p['tier']=='Radiant' for p in rr)
  checks.append(dict(region=region,eligibleRadiants=len(radiants),top50=sum(c['radiant_top50'] for c in countries),top10=sum(c['radiant_top10'] for c in countries),nearRadiant=sum(c['near_radiant'] for c in countries),radiantBoundary=sum(c['radiant_boundary'] for c in countries),cutoff=threshold))
assert api('status')['latestSnapshot'][0]['id']==snapshot['id'],'Snapshot changed during verification'
# Verify browser-style CSRF and administrator session without creating a production test user.
jar=http.cookiejar.CookieJar();opener=urllib.request.build_opener(urllib.request.HTTPCookieProcessor(jar))
def request(path,body=None,headers=None):
 with opener.open(urllib.request.Request(base+path,data=body,headers=headers or {}),timeout=30) as r:return r.status,r.read()
csrf=json.loads(request('/api/leaderboard/csrf')[1]);invalid=json.dumps(dict(username='x',name='Validation check',email='validation@example.test',password='too-short')).encode()
for headers,expected in [({'Content-Type':'application/json'},403),({'Content-Type':'application/json',csrf['header']:csrf['token']},400)]:
 try:request('/api/account/register',invalid,headers);raise AssertionError('Invalid registration accepted')
 except urllib.error.HTTPError as e:assert e.code==expected
cfg=json.loads((root/'config/leaderboard.local.json').read_text(encoding='utf-8-sig'));password=(root/cfg['adminPasswordFile']).read_text(encoding='utf-8-sig').strip();csrf=json.loads(request('/api/leaderboard/csrf')[1])
request('/login',urllib.parse.urlencode(dict(username=cfg['adminUser'],password=password,_csrf=csrf['token'])).encode(),{'Content-Type':'application/x-www-form-urlencoded'})
me=json.loads(request('/api/account/me')[1]);assert me['authenticated'] and me['administrator']
csrf=json.loads(request('/api/leaderboard/csrf')[1]);request('/logout',b'',{csrf['header']:csrf['token']});assert not json.loads(request('/api/account/me')[1])['authenticated']
report=dict(verifiedAt=datetime.datetime.now(datetime.timezone.utc).isoformat(),snapshotId=snapshot['id'],playersChecked=len(rows),radiantChecks=checks,accountChecks='PASS: anonymous registration CSRF enforcement, validation rejection, native form CSRF login with existing administrator, authenticated administrator identity and sign-out. New-user hashed registration/login/privilege isolation tested in isolated H2; no production test account created.',uiChecks='PASS: updated DOM/API checks; visual browser layout not inspected.')
(root/'docs/observatory-refinement-verification.json').write_text(json.dumps(report,indent=2),encoding='utf-8');print(json.dumps(report,indent=2))


