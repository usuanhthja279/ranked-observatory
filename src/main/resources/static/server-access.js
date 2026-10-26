/* Measured network-access proxy. No fabricated pings; missing, flagged or stale data is neutral. */
(function(root){'use strict';
 function factors(region,population,evidence,enabled=true){
  const config=evidence.serverAccess,countries=[...population.keys()],neutral=reason=>({factor:1,reason,measured:false});
  const result=new Map(countries.map(c=>[c,neutral('No published country measurements')]));
  if(!enabled||!config?.enabled){for(const c of countries)result.set(c,neutral('Access adjustment disabled'));return {countries:result,appliedCount:0};}
  const s=config.settings,definition=config.regions?.[region],source=config.source,asOf=Date.parse(evidence.completedAt),end=Date.parse(source.toDate+'T00:00:00Z'),age=(asOf-end)/86400000;
  if(!Number.isFinite(age)||age<0||age>s.maxAgeDays){for(const c of countries)result.set(c,neutral('Measurement period is stale, future or unavailable'));return {countries:result,appliedCount:0,source};}
  const endpoints=Object.keys(definition?.targets||{});if(!endpoints.length){for(const c of countries)result.set(c,neutral('No matching server-location proxies in this shard'));return {countries:result,appliedCount:0,source};}
  const eligible=[];
  for(const c of countries){const data=config.countries[c];if(!data)continue;
   let reason=data.farFromHome?'Publisher flags likely VPN exits':definition.homeCoverageGaps?.includes(c)?'Home server missing from proxy dataset':data.uncheckedShare>s.maxUncheckedShare?'Too many tunnel-unchecked tests':data.runs<s.minCountryRuns?'Insufficient country tests':null;
   const cells=endpoints.map(id=>({endpoint:id,server:definition.targets[id],...data.endpoints[id]}));
   if(!reason&&cells.some(r=>!Number.isFinite(r.medianMs)||r.medianMs<0||!Number.isFinite(r.samples)||r.samples<s.minCellSamples||!Number.isFinite(r.reachRate)||r.reachRate<0||r.reachRate>1))reason='Incomplete or insufficient same-shard measurements';
   if(reason){result.set(c,neutral(reason));continue;}
   for(const cell of cells)cell.quality=Math.exp(-cell.medianMs/s.pingScaleMs)*cell.reachRate;
   cells.sort((a,b)=>b.quality-a.quality||a.endpoint.localeCompare(b.endpoint));
   const best=cells.slice(0,3),denominator=s.qualityWeights.slice(0,best.length).reduce((n,w)=>n+w,0),score=best.reduce((n,r,i)=>n+r.quality*s.qualityWeights[i],0)/denominator;
   const coverage=endpoints.length/definition.officialServerCount,sample=Math.min(1,Math.min(...cells.map(r=>r.samples))/s.fullCellSamples),qualityWeight=s.proxyShrink*coverage*sample*(1-data.uncheckedShare);
   eligible.push({country:c,score,qualityWeight,best,countryTests:data.runs,uncheckedShare:data.uncheckedShare,coverage});
  }
  const total=eligible.reduce((n,c)=>n+population.get(c.country),0),reference=total?eligible.reduce((n,c)=>n+c.score*population.get(c.country),0)/total:0;
  if(eligible.length<2||!reference){for(const c of eligible)result.set(c.country,neutral('Insufficient comparable countries for regional reference'));return {countries:result,appliedCount:0,source};}
  for(const c of eligible){const raw=Math.max(-s.maxWeightAdjustment,Math.min(s.maxWeightAdjustment,c.score/reference-1)),factor=1+c.qualityWeight*raw;
   result.set(c.country,{...c,factor,referenceScore:reference,reason:'Measured HTTPS cloud proxy; quality-shrunk adjustment',measured:true,sourceType:source.type});}
  return {countries:result,appliedCount:eligible.filter(c=>result.get(c.country).factor!==1).length,source,referenceScore:reference};
 }
 const api={factors};if(typeof module!=='undefined')module.exports=api;else root.ServerAccess=api;
})(typeof globalThis==='undefined'?this:globalThis);
