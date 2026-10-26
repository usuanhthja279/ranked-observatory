'use strict';
const fs=require('node:fs'),assert=require('node:assert/strict'),M=require('../../leaderboard-service/src/main/resources/static/country-model.js');
const input=JSON.parse(fs.readFileSync('build/act-grinding-inputs.json')),baselines={};
const report=process.argv.includes('--export-baselines')?null:JSON.parse(fs.readFileSync('docs/act-grinding-comparison.json'));
if(report)assert.equal(input.snapshotId,report.snapshotId);
for(const region of ['ap','eu','na','br','kr','latam']){
 const players=input.players.filter(p=>p.region===region),inventory=[],recoveries=[];
 const groups=new Map(),recovered=new Map();
 for(const p of players){const k=JSON.stringify([p.country||'unknown',p.tier]);groups.set(k,(groups.get(k)||0)+1);if(!p.country&&p.resolved_country){const r=JSON.stringify([p.resolved_country,p.tier]);recovered.set(r,(recovered.get(r)||0)+1);}}
 for(const [k,n]of groups){const [country,tier]=JSON.parse(k);inventory.push({region,country,tier,players:n});}
 for(const [k,n]of recovered){const [country,tier]=JSON.parse(k);recoveries.push({region,country,tier,players:n});}
 const rows=M.inputRows({inventory},region),old=M.estimate(rows,region,{},true),current=M.estimate(rows,region,{recoveries});
 baselines[region]={original:Object.fromEntries(old.counts),current:Object.fromEntries(current.counts)};
 if(!report)continue;
 for(const c of report.regions[region].countries)for(const key of ['players',...M.tiers]){
  assert.equal(c.estimates.current[key],current.counts.get(c.country)['estimated_'+key],region+'/'+c.country+'/'+key+' current baseline');
  assert.equal(c.estimates.original[key],old.counts.get(c.country)?.['estimated_'+key]||0,region+'/'+c.country+'/'+key+' original baseline');
 }
 for(const mode of ['original','current','wins','history','global_history'])for(const key of ['players',...M.tiers])assert.equal(report.regions[region].countries.reduce((n,c)=>n+c.estimates[mode][key],0),rows.reduce((n,r)=>n+Number(r[key]||0),0),region+'/'+mode+'/'+key+' conservation');
}
if(!report){fs.writeFileSync('build/act-grinding-baselines.json',JSON.stringify({snapshotId:input.snapshotId,regions:baselines}));console.log('Exported canonical dashboard baselines.');}
else console.log('PASS: same snapshot, original/current estimates match deployed calculation module, and all five scenarios conserve all tiers across six shards.');


