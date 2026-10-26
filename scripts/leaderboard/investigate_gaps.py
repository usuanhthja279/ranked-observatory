import sys,json,collections,datetime
from pathlib import Path
sys.path.insert(0,str(Path('scripts/leaderboard').resolve()))
import verify_global as v
base=v.ROOT/'build/gap-investigation'
results=[]
for attempt in range(3):
 v.CACHE=base/str(attempt);v.CACHE.mkdir(parents=True,exist_ok=True)
 for n in [1,300,301,302,510]:
  d=v.fetch('global',n,refresh=attempt>0);results.append(d)
  print(json.dumps(dict(attempt=attempt,page=n,rows=len(d['rows']),updated=d['rows'][0]['sourceUpdated'] if d['rows'] else None)),flush=True)
report=[]
for n in [1,300,301,302,510]:
 a=[x for x in results if x['page']==n]
 report.append(dict(page=n,uniqueSets=[len(set(r['key'] for r in x['rows'])) for x in a],pairwiseOverlap=[len(set(r['key'] for r in a[0]['rows'])&set(r['key'] for r in x['rows'])) for x in a[1:]],rrRanges=[(x['rows'][0]['rr'],x['rows'][-1]['rr']) for x in a]))
(base/'live-summary.json').write_text(json.dumps(report,indent=2))
print(json.dumps(report,indent=2))


