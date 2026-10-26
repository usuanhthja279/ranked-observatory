'use strict';
const assert=require('node:assert/strict'),fs=require('node:fs'),M=require('../../leaderboard-service/src/main/resources/static/country-model.js');
const row=(country,radiant,immortal1)=>({country,players:radiant+immortal1,radiant,immortal3:0,immortal2:0,immortal1});
function conserve(rows,result){for(const t of ['players',...M.tiers])assert.equal([...result.counts.values()].reduce((n,c)=>n+c['estimated_'+t],0)+(result.unresolved[t]||0),rows.reduce((n,c)=>n+Number(c[t]||0),0),t);}
const sample=[row('in',3,97),row('sg',20,80),row('unknown',23,177)],saved=JSON.stringify(sample),result=M.estimate(sample,'ap');
assert.equal(result.counts.get('in').estimated_radiant,6);assert.equal(result.counts.get('sg').estimated_radiant,40);conserve(sample,result);assert.equal(JSON.stringify(sample),saved);
const recovered=M.estimate(sample,'ap',{recoveries:[{region:'ap',country:'in',tier:'Radiant',players:5},{region:'eu',country:'in',tier:'Radiant',players:10}]});assert.equal(recovered.recoveryTotal,5);assert(recovered.counts.get('in').estimated_radiant>=8);conserve(sample,recovered);
const absent=[row('in',0,100),row('unknown',20,100)],none=M.estimate(absent,'ap');assert.equal(none.unresolved.radiant,20);conserve(absent,none);
const onlyUnknown=M.estimate([row('unknown',5,10)],'ap');assert.equal(onlyUnknown.counts.size,0);assert.equal(onlyUnknown.unresolved.players,15);
const now='2026-10-21T12:00:00Z',history=[1,2,3].flatMap(day=>[{region:'ap',country:'in',tier:'Radiant',players:30,completed_at:`2026-10-${21-day}T12:00:00Z`},{region:'ap',country:'in',tier:'Immortal 1',players:70,completed_at:`2026-10-${21-day}T12:00:00Z`}]);
const historical=M.estimate(sample,'ap',{completedAt:now,history});assert(historical.historyApplied);assert(historical.counts.get('in').estimated_radiant>result.counts.get('in').estimated_radiant);conserve(sample,historical);
const oneDay=M.estimate(sample,'ap',{completedAt:now,history:history.slice(0,2)});assert(!oneDay.historyApplied);assert.deepEqual(oneDay.counts,result.counts);
const newCountry=M.estimate(sample,'ap',{recoveries:[{region:'ap',country:'jp',tier:'Radiant',players:2}]});assert.equal(newCountry.recovered.get('jp').radiant,2);conserve(sample,newCountry);
for(const c of result.counts.values())for(const t of ['players',...M.tiers])assert(Number.isInteger(c['estimated_'+t])&&c['estimated_'+t]>=0);
console.log('PASS: country/tier composition, reported recovery precedence, shard isolation, history thresholds, unseen recovered countries, unresolved tiers, immutable observations and exact population conservation.');
if(process.argv.includes('--live'))(async()=>{
 const evidence=await(await fetch('http://127.0.0.1:8090/api/leaderboard/country-model')).json(),regions={};
 for(const region of ['ap','eu','na','br','kr','latam']){const rows=M.inputRows(evidence,region),old=M.estimate(rows,region,{},true),next=M.estimate(rows,region,evidence);conserve(rows,next);regions[region]={recovered:next.recoveryTotal,remainingUnknown:next.remainingUnknown,historyDays:next.historyDays,historyApplied:next.historyApplied,unresolved:next.unresolved,countries:[...next.counts].map(([country,value])=>({country,observed:rows.find(r=>r.country===country)||{},recovered:next.recovered.get(country)||{},old:old.counts.get(country)||{},new:value}))};}
 const report={snapshotId:evidence.snapshotId,completedAt:evidence.completedAt,generatedAt:new Date().toISOString(),assumptions:'Equal missing-label odds; no server multipliers. Source countries unchanged. Historical policy is not validated accuracy on real Unknown accounts.',regions};
 fs.writeFileSync('docs/country-model-comparison.json',JSON.stringify(report,null,2));fs.writeFileSync('docs/country-model-evidence.json',JSON.stringify(evidence,null,2));
 const csv=[['snapshot','region','country','metric','source_observed','reported_recovered','previous_estimate','new_estimate','change']];
 for(const [region,data]of Object.entries(regions))for(const r of data.countries)for(const metric of ['players',...M.tiers]){const key='estimated_'+metric;csv.push([report.snapshotId,region,r.country,metric,r.observed[metric]||0,r.recovered[metric]||0,r.old[key]||0,r.new[key],r.new[key]-(r.old[key]||0)]);}
 fs.writeFileSync('docs/country-model-comparison.csv',csv.map(r=>r.join(',')).join('\n'));
 console.log('PASS live conservation; comparison written for snapshot',evidence.snapshotId);
 for(const c of ['in','jp','sg','ph','kr','au']){const r=regions.ap.countries.find(r=>r.country===c);if(r)console.log(c,JSON.stringify({observed:r.observed,old:r.old,new:r.new,recovered:r.recovered}));}
})().catch(e=>{console.error(e);process.exitCode=1;});


