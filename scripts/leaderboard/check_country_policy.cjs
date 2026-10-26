'use strict';
const assert=require('node:assert/strict'),fs=require('node:fs'),M=require('../../leaderboard-service/src/main/resources/static/country-model.js');
const evidence={policyVersion:3,inventory:[
 {region:'ap',country:'in',tier:'Radiant',rr_band:'150+',players:3},
 {region:'ap',country:'in',tier:'Immortal 3',rr_band:'150+',players:10},
 {region:'ap',country:'in',tier:'Immortal 2',rr_band:'150+',players:5},
 {region:'ap',country:'in',tier:'Immortal 2',rr_band:'below150',players:20},
 {region:'ap',country:'in',tier:'Immortal 1',rr_band:'below150',players:62},
 {region:'ap',country:'sg',tier:'Radiant',rr_band:'150+',players:3},
 {region:'ap',country:'sg',tier:'Immortal 3',rr_band:'150+',players:30},
 {region:'ap',country:'sg',tier:'Immortal 2',rr_band:'150+',players:25},
 {region:'ap',country:'sg',tier:'Immortal 2',rr_band:'below150',players:20},
 {region:'ap',country:'sg',tier:'Immortal 1',rr_band:'below150',players:22},
 ...['Radiant','Immortal 3','Immortal 2','Immortal 1'].map(tier=>({region:'ap',country:'unknown',tier,rr_band:'150+',players:20})),
 {region:'ap',country:'unknown',tier:'Immortal 2',rr_band:'below150',players:20}
],countryStats:[{region:'ap',country:'in',median_rr:50},{region:'ap',country:'sg',median_rr:150}],recoveries:[]};
const rows=M.inputRows(evidence,'ap'),before=JSON.stringify(evidence),policy=M.estimate(rows,'ap',evidence),previous=M.estimatePrevious(rows,'ap');
assert.equal(policy.strength.get('in'),54);assert.equal(policy.strength.get('sg'),114);
assert(policy.counts.get('sg').estimated_radiant>policy.counts.get('in').estimated_radiant,'Stronger upper mix receives more unknown Radiants despite equal observed Radiants');
assert(policy.counts.get('in').estimated_radiant<previous.counts.get('in').estimated_radiant);
const upper=policy.breakdown.find(r=>r.group==='immortal2');assert(upper.high);assert.equal(upper.unknown,40);assert.equal(upper.allocated.in,8);assert.equal(upper.allocated.sg,32);const low=policy.breakdown.find(r=>r.group==='immortal1');assert.equal(low.allocated.in,8);assert.equal(low.allocated.sg,12);
for(const tier of ['players',...M.tiers])assert.equal([...policy.counts.values()].reduce((n,c)=>n+c['estimated_'+tier],0)+policy.unresolved[tier],rows.reduce((n,c)=>n+Number(c[tier]||0),0));
assert.equal(JSON.stringify(evidence),before);
const capped=structuredClone(evidence);capped.countryStats[1].median_rr=9999;assert.deepEqual(M.estimate(rows,'ap',capped).counts,policy.counts);
const noMedians=structuredClone(evidence);for(const r of noMedians.countryStats)r.median_rr=null;const proportional=M.estimate(rows,'ap',noMedians).breakdown.find(r=>r.group==='immortal1');assert.equal(proportional.allocated.in,10);assert.equal(proportional.allocated.sg,10);
const recovered=structuredClone(evidence);recovered.recoveries=[{region:'ap',country:'in',tier:'Immortal 2',rr_band:'150+',players:2}];const withReport=M.estimate(rows,'ap',recovered);assert.equal(withReport.recoveryTotal,2);assert.equal(withReport.strength.get('in'),56);assert.equal(withReport.recovered.get('in').immortal2,2);
const second=structuredClone(evidence);second.inventory.push({region:'eu',country:'fr',tier:'Radiant',rr_band:'150+',players:2},{region:'eu',country:'unknown',tier:'Radiant',rr_band:'150+',players:4});second.countryStats.push({region:'eu',country:'fr',median_rr:500});const world=M.estimate(M.inputRows(second,'ap'),'all',second);assert.equal(world.counts.get('fr').estimated_radiant,6);assert.deepEqual(world.counts.get('in'),policy.counts.get('in'),'Shards are allocated independently');
assert.throws(()=>M.estimatePolicy(rows,'ap',{}),/unavailable/);
console.log('PASS: compounded upper strength, full-tier Immortal 2, median-weighted lower allocation, cap, recovery priority, immutable source, shard isolation and tier conservation.');
if(process.argv.includes('--live'))(async()=>{
 const e=await(await fetch('http://127.0.0.1:8090/api/leaderboard/country-model')).json();assert.equal(e.policyVersion,3);const report={snapshotId:e.snapshotId,completedAt:e.completedAt,formula:{strength:'3R+2I3+I2',upper:'groupCount*strength',lower:'countryPopulation*(1+clamp(medianRR,0,150)/150)'},regions:{}};
 for(const region of ['ap','eu','na','br','kr','latam']){const raw=M.inputRows(e,region),next=M.estimate(raw,region,e),old=M.estimatePrevious(raw,region,e);for(const t of ['players',...M.tiers])assert.equal([...next.counts.values()].reduce((n,c)=>n+c['estimated_'+t],0)+next.unresolved[t],raw.reduce((n,c)=>n+Number(c[t]||0),0),region+'/'+t);
 report.regions[region]={recovered:next.recoveryTotal,remainingUnknown:next.remainingUnknown,unresolved:next.unresolved,groups:next.breakdown,countries:[...next.counts].map(([country,value])=>({country,observed:raw.find(c=>c.country===country)||{},recovered:next.recovered.get(country)||{},strength:next.strength.get(country),medianRR:next.medians.get(country),previous:old.counts.get(country)||{},policy:value}))};}
 const world=M.estimate([], 'all', e);for(const t of ['players',...M.tiers]){const total=e.inventory.filter(r=>t==='players'||M.tierKey(r.tier)===t).reduce((n,r)=>n+Number(r.players),0);assert.equal([...world.counts.values()].reduce((n,r)=>n+r['estimated_'+t],0)+world.unresolved[t],total,'World '+t);}
 fs.writeFileSync('docs/country-policy-comparison.json',JSON.stringify(report,null,2));fs.writeFileSync('docs/country-policy-evidence.json',JSON.stringify(e,null,2));
 const csv=[['snapshot','region','country','metric','source_observed','reported_recovered','previous','policy','change']];for(const [region,data]of Object.entries(report.regions))for(const c of data.countries)for(const t of ['players',...M.tiers]){const key='estimated_'+t;csv.push([e.snapshotId,region,c.country,t,c.observed[t]||0,c.recovered[t]||0,c.previous[key]||0,c.policy[key],c.policy[key]-(c.previous[key]||0)]);}fs.writeFileSync('docs/country-policy-comparison.csv',csv.map(r=>r.join(',')).join('\n'));
 for(const c of ['in','jp','sg','kr','ph','au'])console.log(c,JSON.stringify(report.regions.ap.countries.find(r=>r.country===c)));console.log('PASS: live tier conservation for all six shards; comparison saved for snapshot '+e.snapshotId);
})().catch(e=>{console.error(e);process.exitCode=1;});


