import json,urllib.request,urllib.error,concurrent.futures,numpy as np,datetime
from pathlib import Path
base='http://127.0.0.1:8090/api/leaderboard/'
def get(path):return json.load(urllib.request.urlopen(base+path))
s=get('status');codes=['ap','eu','na','br','kr','latam'];tiers=['radiant','immortal3','immortal2','immortal1'];grid=[0,1,2,5,10,25,50,100,250,500]
with concurrent.futures.ThreadPoolExecutor(max_workers=6) as pool:raw=dict(zip(codes,pool.map(lambda c:get('countries?region='+c),codes)))
if get('status')['latestSnapshot'][0]['id']!=s['latestSnapshot'][0]['id']:raise RuntimeError('Snapshot changed during aggregate capture; rerun for consistent inputs.')
report={'snapshotId':s['latestSnapshot'][0]['id'],'verifiedAt':datetime.datetime.now(datetime.timezone.utc).isoformat(),'formula':'p_ct=(K_ct + alpha_r * K_c/K)/(K_t + alpha_r); E_ct=K_ct + U_t*p_ct','method':'20 tuning masks, 20 model-selection masks (require >=2% relative gain over pooled), and 20 untouched final-test masks; binomial masking at each region actual Unknown fraction (max 80%); metric=sum absolute predicted-minus-heldout country/tier counts divided by total heldout players. Conditional test on known labels, not measured accuracy on Unknown.','regions':{}}
for code in codes:
 rows=raw[code];known=[r for r in rows if r['country']!='unknown'];unknown=next((r for r in rows if r['country']=='unknown'),{});counts=np.array([[r[t] for t in tiers] for r in known],dtype=int);u=np.array([unknown.get(t,0) for t in tiers]);fraction=min(.8,max(.1,float(u.sum()/(u.sum()+counts.sum()))));rng=np.random.default_rng(20261020+codes.index(code));splits=[rng.binomial(counts,fraction) for _ in range(60)]
 def score(a,start,end,pooled=False):
  totalerror=totalheld=0
  for hold in splits[start:end]:
   train=counts-hold;prior=train.sum(axis=1)/max(1,train.sum());tot=train.sum(axis=0);den=tot+a;prob=np.divide(train+a*prior[:,None],den[None,:],out=np.tile(prior[:,None],(1,4)),where=den[None,:]>0)
   if pooled:prob=np.tile(prior[:,None],(1,4))
   pred=prob*hold.sum(axis=0)[None,:];totalerror+=np.abs(pred-hold).sum();totalheld+=hold.sum()
  return float(totalerror/max(1,totalheld))
 scores={a:score(a,0,20) for a in grid};strength=min(scores,key=scores.get);validation=score(strength,20,40);baseline=score(0,20,40,True);rawtier=score(0,20,40)
 mode='tier-aware' if validation<baseline*.98 else 'pooled'
 final=score(strength,40,60,mode=='pooled');finalbase=score(0,40,60,True)
 report['regions'][code]={'mode':mode,'finalTestAllocationError':final,'finalTestPooledError':finalbase,'strength':strength,'known':int(counts.sum()),'unknown':int(u.sum()),'maskFraction':fraction,'validationAllocationError':validation,'pooledAllocationError':baseline,'rawTierAllocationError':rawtier,'relativeImprovementOverPooled':1-validation/baseline,'tuning':scores}
 print(code,'mode',mode,'test',round(final,4),'baseline',round(finalbase,4),'alpha',strength,'validation',round(validation,4),'pooled',round(baseline,4),'gain',round(100*(1-validation/baseline),1),'%')
Path('docs/unknown-country-model-validation.json').write_text(json.dumps(report,indent=2),encoding='utf-8');Path('build/estimate-inputs.json').write_text(json.dumps(raw),encoding='utf-8')


