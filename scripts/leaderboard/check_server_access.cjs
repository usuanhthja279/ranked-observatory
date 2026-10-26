'use strict';
const assert=require('node:assert/strict'),fs=require('node:fs'),M=require('../../leaderboard-service/src/main/resources/static/country-model.js'),A=require('../../leaderboard-service/src/main/resources/static/server-access.js');
const published=JSON.parse(fs.readFileSync('leaderboard-service/src/main/resources/server-access-data.json','utf8'));
const population=new Map([['in',300],['my',150],['ru',50],['sg',200],['jp',1200],['ph',500]]),e={serverAccess:published,completedAt:'2026-10-21T00:00:00Z'};
const result=A.factors('ap',population,e);assert(result.countries.get('in').measured);assert.equal(result.countries.get('sg').factor,1);assert.match(result.countries.get('sg').reason,/VPN/);assert.equal(result.countries.get('jp').factor,1);assert.equal(result.countries.get('ph').factor,1);assert.match(result.countries.get('ph').reason,/Home server/);
for(const v of result.countries.values())assert(v.factor>=.975&&v.factor<=1.025);
const wrongShard=structuredClone(e);wrongShard.serverAccess.countries.in.endpoints['ap-northeast-2'].medianMs=1;assert.equal(A.factors('ap',population,wrongShard).countries.get('in').factor,result.countries.get('in').factor);
for(const stamp of ['2026-12-31T00:00:00Z','2026-09-01T00:00:00Z'])for(const value of A.factors('ap',population,{...e,completedAt:stamp}).countries.values())assert.equal(value.factor,1);
const gap=structuredClone(e);delete gap.serverAccess.countries.in.endpoints['ap-east-1'];assert.equal(A.factors('ap',population,gap).countries.get('in').factor,1);
const tooFew=structuredClone(e);tooFew.serverAccess.countries.in.runs=39;assert.equal(A.factors('ap',population,tooFew).countries.get('in').factor,1);
const single=A.factors('ap',new Map([['in',300],['jp',100]]),e);assert.equal(single.countries.get('in').factor,1);
for(const v of A.factors('latam',population,e).countries.values())assert.equal(v.factor,1);
console.log('PASS: measured proxy quality gates, VPN/home-server exclusions, stale/future data, sparse coverage, factor bounds and shard eligibility.');
async function compare(live){
 const evidence=live?await(await fetch('http://127.0.0.1:8090/api/leaderboard/country-model')).json():JSON.parse(fs.readFileSync('docs/country-policy-evidence.json','utf8'));
 evidence.serverAccess=live?evidence.serverAccess:published;assert(evidence.serverAccess?.enabled,'Published source metadata is missing');
 const report={snapshotId:evidence.snapshotId,completedAt:evidence.completedAt,source:evidence.serverAccess.source,settings:evidence.serverAccess.settings,regions:{}};
 for(const region of ['ap','eu','na','br','kr','latam']){const rows=M.inputRows(evidence,region),before=JSON.stringify(rows),base=M.estimatePolicy(rows,region,evidence,{serverAccess:false}),next=M.estimatePolicy(rows,region,evidence);
 for(const metric of ['players',...M.tiers]){const total=rows.reduce((n,r)=>n+Number(r[metric]||0),0);for(const model of [base,next])assert.equal([...model.counts.values()].reduce((n,r)=>n+r['estimated_'+metric],0)+model.unresolved[metric],total,region+'/'+metric);}
 assert.equal(JSON.stringify(rows),before);assert.deepEqual(next.recovered,base.recovered);assert.equal(next.recoveryTotal,base.recoveryTotal);
 const countries=[...next.counts].map(([country,counts])=>({country,observed:rows.find(r=>r.country===country)||{},access:next.access.countries.get(country),withoutAccess:base.counts.get(country),withAccess:counts}));
 report.regions[region]={adjusted:next.access.appliedCount,reference:next.access.referenceScore,officialServerCount:evidence.serverAccess.regions[region].officialServerCount,matchedLocations:Object.keys(evidence.serverAccess.regions[region].targets).length,countries};}
 const world=M.estimatePolicy([], 'all', evidence);for(const t of ['players',...M.tiers])assert.equal([...world.counts.values()].reduce((n,r)=>n+r['estimated_'+t],0)+world.unresolved[t],evidence.inventory.filter(r=>t==='players'||M.tierKey(r.tier)===t).reduce((n,r)=>n+r.players,0));
 fs.writeFileSync('docs/server-access-comparison.json',JSON.stringify(report,null,2));fs.writeFileSync('docs/server-access-evidence.json',JSON.stringify(evidence,null,2));
 const csv=[['snapshot','region','country','metric','observed','access_factor','status','without_access','with_access','change']];for(const [region,data]of Object.entries(report.regions))for(const c of data.countries)for(const t of ['players',...M.tiers]){const k='estimated_'+t;csv.push([evidence.snapshotId,region,c.country,t,c.observed[t]||0,c.access.factor,c.access.measured?'HTTPS cloud proxy':'neutral',c.withoutAccess[k],c.withAccess[k],c.withAccess[k]-c.withoutAccess[k]]);}fs.writeFileSync('docs/server-access-comparison.csv',csv.map(r=>r.join(',')).join('\n'));
 for(const c of ['in','jp','sg','kr','ph','my','au','vn','id']){const row=report.regions.ap.countries.find(r=>r.country===c);if(row)console.log(c,JSON.stringify({factor:row.access.factor,status:row.access.reason,pings:row.access.best?.map(p=>[p.server,p.medianMs]),without:row.withoutAccess,with:row.withAccess}));}
 console.log('PASS: reported data unchanged; all regional and world totals conserved; comparison snapshot '+evidence.snapshotId);
}
if(process.argv.includes('--live')||process.argv.includes('--saved'))compare(process.argv.includes('--live')).catch(e=>{console.error(e);process.exitCode=1;});


