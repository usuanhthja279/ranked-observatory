'use strict';
const fs=require('node:fs'),assert=require('node:assert/strict'),M=require('../../leaderboard-service/src/main/resources/static/country-model.js');
const e=JSON.parse(fs.readFileSync('docs/server-access-evidence.json','utf8'));
const report={snapshotId:e.snapshotId,completedAt:e.completedAt,comparison:'Previous RR split versus full Immortal 2; identical server access and recovered reports',regions:{}};
for(const region of ['ap','eu','na','br','kr','latam']){
 const rows=M.inputRows(e,region),before=JSON.stringify(e),old=M.estimatePolicy(rows,region,e,{splitImmortal2:true}),next=M.estimatePolicy(rows,region,e);
 assert.equal(JSON.stringify(e),before);assert.deepEqual(old.recovered,next.recovered);assert.deepEqual(old.access,next.access);
 for(const t of ['players',...M.tiers])for(const model of [old,next])assert.equal([...model.counts.values()].reduce((n,c)=>n+c['estimated_'+t],0)+model.unresolved[t],rows.reduce((n,c)=>n+Number(c[t]||0),0),region+'/'+t);
 report.regions[region]=[...next.counts].map(([country,value])=>({country,observed:rows.find(r=>r.country===country),factor:next.access.countries.get(country)?.factor??1,previous:old.counts.get(country),current:value}));
}
const world=M.estimatePolicy([], 'all',e);for(const t of ['players',...M.tiers])assert.equal([...world.counts.values()].reduce((n,c)=>n+c['estimated_'+t],0)+world.unresolved[t],e.inventory.filter(r=>t==='players'||M.tierKey(r.tier)===t).reduce((n,r)=>n+Number(r.players),0));
fs.writeFileSync('docs/full-immortal2-comparison.json',JSON.stringify(report,null,2));
const csv=[['snapshot','region','country','metric','observed','access_factor','previous_split','full_immortal2','change']];for(const [region,countries]of Object.entries(report.regions))for(const c of countries)for(const t of ['players',...M.tiers]){const k='estimated_'+t;csv.push([e.snapshotId,region,c.country,t,c.observed?.[t]||0,c.factor,c.previous[k],c.current[k],c.current[k]-c.previous[k]]);}
fs.writeFileSync('docs/full-immortal2-comparison.csv',csv.map(r=>r.join(',')).join('\n'));
for(const country of ['in','jp','sg','ph','my','au','vn','id'])console.log(JSON.stringify(report.regions.ap.find(c=>c.country===country)));
console.log('PASS: six-shard and world conservation; identical access factors, recoveries and source inventory. Snapshot '+e.snapshotId);


