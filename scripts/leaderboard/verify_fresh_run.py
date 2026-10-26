"""Wait for a fresh collection, then independently reconcile all published APIs."""
import collections,concurrent.futures,datetime,json,math,time,urllib.request,urllib.parse,sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
BASE='http://127.0.0.1:8090/api/leaderboard/'
def api(path,**params):
 url=BASE+path+('?' + urllib.parse.urlencode(params) if params else '')
 with urllib.request.urlopen(url,timeout=45) as r:return json.load(r)
def now():return datetime.datetime.now(datetime.timezone.utc)
def dt(s):return datetime.datetime.fromisoformat(s)
def require(ok,label):
 if not ok:raise AssertionError(label)
def validate(run):
 before=api('status');require(before['latestSnapshot'][0]['id']==run['id'],'Expected target run to remain latest')
 first=api('players',size=250,page=1)
 pages=math.ceil(first['total']/250)
 with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
  rest=list(pool.map(lambda p:api('players',size=250,page=p),range(2,pages+1)))
 rows=first['items']+[r for result in rest for r in result['items']]
 require(len(rows)==first['total']==run['players'],'Published player row count')
 require(len({(r['region'],r['player_key']) for r in rows})==len(rows),'Unique region-qualified player keys')
 require(run['raw_rows']-run['duplicate_rows']==run['players'],'Raw-minus-duplicate accounting')
 require(all(r['run_id']==run['id'] for r in rows),'Single snapshot across player pages')
 regions=api('regions');region_report=[];history=[]
 keys=['rr_0_100','rr_100_300','rr_300_cutoff','rr_cutoff_half','rr_half_ninety','rr_top_ten']
 for region in sorted(before['regions']):
  rr=[r for r in rows if r['region']==region];require(bool(rr),region+' present')
  observed=next(r for r in regions if r['region']==region)
  counts=collections.Counter(r['tier'] for r in rr)
  for field,tier in [('radiant','Radiant'),('immortal3','Immortal 3'),('immortal2','Immortal 2'),('immortal1','Immortal 1')]:require(observed[field]==counts[tier],region+' region tier '+field)
  require(observed['players']==len(rr),region+' region total')
  require(sum(counts[t] for t in ['Radiant','Immortal 3','Immortal 2','Immortal 1'])==len(rr),region+' tier partition')
  countries=api('countries',region=region)
  require(sum(c['players'] for c in countries)==len(rr),region+' country sum')
  radiants=[r['rr'] for r in rr if r['tier']=='Radiant' and not r['stats_ambiguous']]
  threshold=min(radiants) if radiants else None;top=max(radiants) if radiants else None
  cutoff=max(300,threshold) if radiants else None
  half=max(cutoff,math.ceil(top*.5)) if radiants else None
  ninety=max(half,math.ceil(top*.9)) if radiants else None
  good=sum(not r['stats_ambiguous'] for r in rr);band_total=0
  for c in countries:
   cr=[r for r in rr if (r['country'] or 'unknown')==c['country']];clean=[r for r in cr if not r['stats_ambiguous']]
   require(c['players']==len(cr),region+'/'+c['country']+' country total')
   for field,tier in [('radiant','Radiant'),('immortal3','Immortal 3'),('immortal2','Immortal 2'),('immortal1','Immortal 1')]:require(c[field]==sum(r['tier']==tier for r in cr),region+'/'+c['country']+' tier '+field)
   expected=[sum(0<=r['rr']<100 for r in clean),sum(100<=r['rr']<300 for r in clean)]
   expected += [sum(300<=r['rr']<cutoff for r in clean),sum(cutoff<=r['rr']<half for r in clean),sum(half<=r['rr']<ninety for r in clean),sum(r['rr']>=ninety for r in clean)] if radiants else [0]*4
   require([c[k] for k in keys]==expected,region+'/'+c['country']+' exact RR bands')
   require(c['radiant_threshold']==threshold and c['top_rr']==top,region+' country-independent threshold')
   require(c['avg_rr'] is None if not clean else abs(c['avg_rr']-sum(r['rr'] for r in clean)/len(clean))<1e-7,region+'/'+c['country']+' mean RR')
   band_total+=sum(expected)
  if radiants:require(band_total==good,region+' RR band partition excluding conflicting stats')
  samples=[rr[0]]
  for code in ['unknown','in','ph']:
   candidate=next((r for r in rr if (r['country'] or 'unknown')==code),None)
   if candidate and candidate not in samples:samples.append(candidate)
  for r in samples:
   h=api('history',player=r['player_key'],region=region)
   require(len(h)==1,region+' fresh history contains exactly one snapshot')
   require(h[0]['rr']==r['rr'] and h[0]['region']==region,region+' history matches current row')
   require('legacy' not in h[0]['source'],region+' no retained legacy history')
   history.append(dict(region=region,player=r['display_name'],country=r['country'],observations=len(h)))
  insight=api('insights',region=region)['summary']
  require(insight['players']==len(rr) and not insight['has_previous'],region+' baseline insights')
  trend=api('country-history',region=region)
  require(len(trend)==1 and trend[0]['players']==len(rr),region+' baseline population history')
  region_report.append(dict(region=region,players=len(rr),countries=len(countries),radiant=counts['Radiant'],immortal3=counts['Immortal 3'],immortal2=counts['Immortal 2'],immortal1=counts['Immortal 1'],unknownCountry=sum(r['country'] is None for r in rr),radiantThreshold=threshold,topRR=top,rrBandRows=band_total,excludedConflictingStats=len(rr)-good))
 world_countries=api('countries');require(sum(c['players'] for c in world_countries)==len(rows),'World country sum')
 for c in world_countries:require(c['players']==sum((r['country'] or 'unknown')==c['country'] for r in rows),'World country '+c['country'])
 after=api('status');require(after['latestSnapshot'][0]['id']==run['id'],'Snapshot stable through verification')
 coverage=json.loads(run['coverage_json']);duration=(dt(run['completed_at'])-dt(run['started_at'])).total_seconds()
 result=dict(verifiedAt=now().isoformat(),runId=run['id'],startedAt=run['started_at'],completedAt=run['completed_at'],durationSeconds=round(duration,3),durationMinutes=round(duration/60,2),pages=run['pages'],rawRows=run['raw_rows'],players=len(rows),duplicates=run['duplicate_rows'],uncertainPlayers=run['uncertain_players'],countryLabels=len(world_countries),coverage=coverage,regions=region_report,historyChecks=history,checks='PASS: complete paginated player reconciliation; region/country/tier totals; exact six RR bands and means; thresholds independent of country; nullable countries; baseline insights and population history; sampled player histories contain no pre-reset data. No visual browser inspection.')
 (ROOT/'docs/fresh-run-verification.json').write_text(json.dumps(result,indent=2,ensure_ascii=False),encoding='utf-8')
 print(json.dumps(result,ensure_ascii=True),flush=True)
if __name__=='__main__':
 target=int(sys.argv[1]) if len(sys.argv)>1 else 1
 deadline=time.monotonic()+7200
 while time.monotonic()<deadline:
  status=api('status');run=next((r for r in status['runs'] if r['id']==target),None)
  if not run:raise RuntimeError('Fresh run 1 not found')
  elapsed=((dt(run['completed_at']) if run['completed_at'] else now())-dt(run['started_at'])).total_seconds()
  print(json.dumps(dict(at=now().isoformat(),run=run['id'],status=run['status'],pages=run['pages'],players=run['players'],elapsedMinutes=round(elapsed/60,2),progress=json.loads(run['coverage_json']) if run.get('coverage_json') else None,requestMetrics=status.get('requestMetrics'),error=run['error'])),flush=True)
  if run['status']=='COMPLETE':validate(run);break
  if run['status']=='FAILED':raise RuntimeError('Fresh collection failed: '+str(run['error']))
  time.sleep(45)
 else:raise TimeoutError('Run has not finished after two hours')


