/* Shared by the dashboard, comparison page and verification scripts. No network or source mutations. */
(function(root){
 'use strict';
 const tiers=['radiant','immortal3','immortal2','immortal1'];
 const tierKey=t=>({'Radiant':'radiant','Immortal 3':'immortal3','Immortal 2':'immortal2','Immortal 1':'immortal1'}[t]||t);
 const legacy={ap:{alpha:250,tier:true},br:{alpha:250,tier:true}};
 const accessEngine=typeof module!=='undefined'?require('./server-access.js'):root.ServerAccess;
 function allocate(total,weights){
  const sum=weights.reduce((n,c)=>n+c.weight,0);if(!sum)return new Map();
  const parts=weights.map(c=>{const x=total*c.weight/sum;return {...c,value:Math.floor(x),fraction:x-Math.floor(x)};});
  let left=total-parts.reduce((n,c)=>n+c.value,0);parts.sort((a,b)=>b.fraction-a.fraction||a.country.localeCompare(b.country));
  return new Map(parts.map(c=>[c.country,c.value+(left-->0?1:0)]));
 }
 function inputRows(evidence,region){
  const out=new Map();for(const r of evidence.inventory||[]){if(r.region!==region)continue;
   if(!out.has(r.country))out.set(r.country,{country:r.country,players:0,...Object.fromEntries(tiers.map(t=>[t,0]))});
   const c=out.get(r.country);c.players+=Number(r.players);const key=tierKey(r.tier);if(tiers.includes(key))c[key]+=Number(r.players);
  }return [...out.values()];
 }
 function estimate(rows,region,evidence={},old=false){
  const adjusted=new Map(rows.filter(c=>c.country!=='unknown').map(c=>[c.country,{...c}]));
  const unknown={...(rows.find(c=>c.country==='unknown')||{})},recovered=new Map();let recoveryTotal=0;
  if(!old)for(const r of evidence.recoveries||[]){if(r.region!==region)continue;
   const key=tierKey(r.tier),n=Number(r.players);if(!/^[a-z]{2}$/.test(r.country)||!tiers.includes(key)||n<0||n>Number(unknown[key]||0))continue;
   if(!adjusted.has(r.country))adjusted.set(r.country,{country:r.country,players:0});
   const c=adjusted.get(r.country);c[key]=Number(c[key]||0)+n;c.players=Number(c.players||0)+n;
   unknown[key]-=n;unknown.players=Number(unknown.players||0)-n;recoveryTotal+=n;
   if(!recovered.has(c.country))recovered.set(c.country,{players:0});const rec=recovered.get(c.country);rec[key]=(rec[key]||0)+n;rec.players+=n;
  }
  const known=[...adjusted.values()],total=known.reduce((n,c)=>n+Number(c.players||0),0),counts=new Map(known.map(c=>[c.country,{}]));
  const prior=known.map(c=>({country:c.country,weight:total?Number(c.players||0)/total:0}));
  const days=new Set(),hist=new Map(),countryDays=new Map();let historyApplied=false;
  if(!old)for(const h of evidence.history||[]){const t=tierKey(h.tier);if(h.region!==region||!tiers.includes(t)||!adjusted.has(h.country))continue;
   days.add(String(h.completed_at).slice(0,10));const age=Math.max(0,(Date.parse(evidence.completedAt)-Date.parse(h.completed_at))/86400000),weight=Math.pow(.5,age/7);
   if(!countryDays.has(h.country))countryDays.set(h.country,new Set());countryDays.get(h.country).add(String(h.completed_at).slice(0,10));
   if(!hist.has(h.country))hist.set(h.country,{});const c=hist.get(h.country);c[t]=(c[t]||0)+Number(h.players)*weight;
  }
  const weights=new Map();
  for(const c of known){const h=hist.get(c.country)||{},hTotal=tiers.reduce((n,t)=>n+(h[t]||0),0),kTotal=tiers.reduce((n,t)=>n+Number(c[t]||0),0);
   // Historical evidence is capped at 25 pseudo-accounts and cannot outweigh current country data.
   // At least three earlier UTC dates are required; repeated intraday snapshots add no weight.
   const strength=(countryDays.get(c.country)?.size||0)>=3&&hTotal>0?Math.min(25,kTotal):0;if(strength>0)historyApplied=true;
   weights.set(c.country,Object.fromEntries(tiers.map(t=>[t,kTotal?Number(c.players||0)*(Number(c[t]||0)+strength*(h[t]||0)/(hTotal||1))/(kTotal+strength):0])));
  }
  const unresolved={};for(const tier of tiers){const n=known.reduce((s,c)=>s+Number(c[tier]||0),0),model=legacy[region];
   const w=known.map((c,i)=>({country:c.country,weight:old?(model?.tier?(Number(c[tier]||0)+model.alpha*prior[i].weight)/(n+model.alpha):prior[i].weight):weights.get(c.country)[tier]}));
   const assigned=allocate(Number(unknown[tier]||0),w);unresolved[tier]=Number(unknown[tier]||0)-[...assigned.values()].reduce((a,b)=>a+b,0);
   for(const c of known)counts.get(c.country)['estimated_'+tier]=Number(c[tier]||0)+(assigned.get(c.country)||0);
  }
  const extra=Math.max(0,Number(unknown.players||0)-tiers.reduce((n,t)=>n+Number(unknown[t]||0),0)),other=allocate(extra,prior);
  unresolved.players=tiers.reduce((n,t)=>n+unresolved[t],0)+extra-[...other.values()].reduce((a,b)=>a+b,0);
  for(const c of known)counts.get(c.country).estimated_players=Math.max(0,Number(c.players||0)-tiers.reduce((n,t)=>n+Number(c[t]||0),0))+(other.get(c.country)||0)+tiers.reduce((n,t)=>n+counts.get(c.country)['estimated_'+t],0);
  return {counts,recovered,recoveryTotal,remainingUnknown:Number(unknown.players||0),unresolved,historyDays:days.size,historyApplied,missingLabelOdds:1};
 }
 function estimatePolicy(rows,region,evidence,options={}){
  if(evidence.policyVersion!==3||!Array.isArray(evidence.countryStats))throw Error('Country policy evidence is unavailable.');
  if(region==='all'){
   const children=[...new Set((evidence.inventory||[]).map(r=>r.region))].map(shard=>estimatePolicy(inputRows(evidence,shard),shard,evidence,options));
   const counts=new Map(),recovered=new Map(),unresolved=Object.fromEntries(['players',...tiers].map(t=>[t,0]));
   for(const result of children){for(const [c,values]of result.counts){if(!counts.has(c))counts.set(c,{});for(const [key,value]of Object.entries(values))counts.get(c)[key]=(counts.get(c)[key]||0)+value;}
    for(const [c,values]of result.recovered){if(!recovered.has(c))recovered.set(c,{});for(const [key,value]of Object.entries(values))recovered.get(c)[key]=(recovered.get(c)[key]||0)+value;}
    for(const key of Object.keys(unresolved))unresolved[key]+=result.unresolved[key];}
   return {counts,recovered,unresolved,recoveryTotal:children.reduce((n,r)=>n+r.recoveryTotal,0),remainingUnknown:children.reduce((n,r)=>n+r.remainingUnknown,0),historyDays:0,historyApplied:false,policyVersion:3,perShard:true,access:{appliedCount:children.reduce((n,r)=>n+(r.access?.appliedCount||0),0),source:evidence.serverAccess?.source}};
  }
  const groups=new Map(),rawCountries=new Map(rows.map(c=>[c.country,c])),recovered=new Map(),unknownBefore=rows.find(c=>c.country==='unknown')?.players||0;
  // The former split is retained only as an explicit comparison control.
  const groupKey=(tier,band)=>options.splitImmortal2&&tier==='immortal2'?tier+':'+band:tier;
  for(const r of evidence.inventory||[]){if(r.region!==region)continue;const tier=tierKey(r.tier);if(!tiers.includes(tier))continue;
   const key=groupKey(tier,r.rr_band);if(!groups.has(key))groups.set(key,{tier,high:tier==='radiant'||tier==='immortal3'||tier==='immortal2'&&(!options.splitImmortal2||r.rr_band==='150+'),countries:new Map()});
   const countries=groups.get(key).countries;countries.set(r.country,(countries.get(r.country)||0)+Number(r.players));
  }
  let recoveryTotal=0;
  for(const r of evidence.recoveries||[]){if(r.region!==region)continue;const group=groups.get(groupKey(tierKey(r.tier),r.rr_band)),n=Number(r.players);
   if(!group||!/^[a-z]{2}$/.test(r.country)||n<0||n>Number(group.countries.get('unknown')||0))throw Error('Invalid reported-country recovery evidence.');
   group.countries.set('unknown',group.countries.get('unknown')-n);group.countries.set(r.country,(group.countries.get(r.country)||0)+n);
   if(!recovered.has(r.country))recovered.set(r.country,{players:0});const rec=recovered.get(r.country);rec[group.tier]=(rec[group.tier]||0)+n;rec.players+=n;recoveryTotal+=n;
  }
  const countries=new Set(rows.filter(c=>c.country!=='unknown').map(c=>c.country));for(const c of recovered.keys())countries.add(c);
  const counts=new Map([...countries].map(c=>[c,Object.fromEntries(tiers.map(t=>['estimated_'+t,0]))])),population=new Map([...countries].map(c=>[c,Number(rawCountries.get(c)?.players||0)+Number(recovered.get(c)?.players||0)]));
  const strength=new Map([...countries].map(c=>[c,0])),points={radiant:3,immortal3:2,immortal2:1};
  for(const g of groups.values())if(g.high)for(const c of countries)strength.set(c,strength.get(c)+(g.countries.get(c)||0)*points[g.tier]);
  const medians=new Map(evidence.countryStats.filter(r=>r.region===region).map(r=>[r.country,r.median_rr==null?null:Number(r.median_rr)]));
  const access=accessEngine?.factors(region,population,evidence,options.serverAccess!==false)||{countries:new Map(),appliedCount:0};
  const factor=c=>access.countries.get(c)?.factor??1;
  const lowWeight=c=>population.get(c)*(1+Math.min(150,Math.max(0,medians.get(c)??0))/150)*factor(c);
  const unresolved=Object.fromEntries(tiers.map(t=>[t,0])),breakdown=[];
  for(const [key,g]of groups){const n=Number(g.countries.get('unknown')||0),weights=[...countries].map(c=>({country:c,weight:g.high?(g.countries.get(c)||0)*strength.get(c)*factor(c):lowWeight(c)})),assigned=allocate(n,weights);
   unresolved[g.tier]+=n-[...assigned.values()].reduce((a,b)=>a+b,0);
   for(const c of countries)counts.get(c)['estimated_'+g.tier]+=(g.countries.get(c)||0)+(assigned.get(c)||0);
   breakdown.push({group:key,high:g.high,unknown:n,allocated:Object.fromEntries(assigned)});
  }
  // Non-tier inventory is kept in Players and uses the lower-rank population/median policy.
  const classified=new Map([...countries].map(c=>[c,0]));for(const g of groups.values())for(const c of countries)classified.set(c,classified.get(c)+(g.countries.get(c)||0));
  const rawUnknown=rows.find(c=>c.country==='unknown')||{},unknownOther=Math.max(0,Number(rawUnknown.players||0)-tiers.reduce((n,t)=>n+Number(rawUnknown[t]||0),0)),other=allocate(unknownOther,[...countries].map(c=>({country:c,weight:lowWeight(c)})));
  unresolved.players=tiers.reduce((n,t)=>n+unresolved[t],0)+unknownOther-[...other.values()].reduce((a,b)=>a+b,0);
  for(const c of countries)counts.get(c).estimated_players=Math.max(0,population.get(c)-classified.get(c))+(other.get(c)||0)+tiers.reduce((n,t)=>n+counts.get(c)['estimated_'+t],0);
  return {counts,recovered,recoveryTotal,remainingUnknown:Number(unknownBefore)-recoveryTotal,unresolved,historyDays:0,historyApplied:false,policyVersion:3,strength,medians,breakdown,access};
 }
 const estimateCurrent=(rows,region,evidence={},old=false)=>!old&&evidence.policyVersion===3?estimatePolicy(rows,region,evidence):estimate(rows,region,evidence,old);
 const api={tiers,tierKey,allocate,inputRows,estimate:estimateCurrent,estimatePrevious:estimate,estimatePolicy};if(typeof module!=='undefined')module.exports=api;else root.CountryModel=api;
})(typeof globalThis==='undefined'?this:globalThis);
