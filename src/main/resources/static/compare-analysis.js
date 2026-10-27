'use strict';

(function(root){
  const allowedWindows=new Set([60,360,1440]);
  function summarizeWindow(rows,minutes,fields){
    const windowMinutes=allowedWindows.has(Number(minutes))?Number(minutes):60;
    const now=Date.now();
    const valid=(Array.isArray(rows)?rows:[]).filter(row=>{
      const stamp=row.completed_at||row.source_updated_at||row.observed_at;
      const time=stamp?Date.parse(stamp):NaN;
      if(!Number.isFinite(time)||time>now||time<now-windowMinutes*60000)return false;
      if(row.stats_ambiguous===true||row.carried_forward===true)return false;
      if(Number(row.stats_ambiguous||0)>0||Number(row.carried_forward||0)>0)return false;
      return fields.some(field=>row[field]!=null&&Number.isFinite(Number(row[field])));
    }).sort((a,b)=>Date.parse(a.completed_at||a.source_updated_at||a.observed_at)-Date.parse(b.completed_at||b.source_updated_at||b.observed_at));
    if(valid.length<2)return {available:false,reason:'Fewer than two comparable observations in this window.',points:valid.length};
    const first=valid[0],last=valid[valid.length-1],changes={};
    for(const field of fields){
      const start=Number(first[field]),end=Number(last[field]);
      changes[field]={from:start,to:end,delta:end-start};
    }
    return {available:true,first,last,changes,points:valid.length,windowMinutes};
  }
  function normalizeCountrySelection(requested,available,limit=10){
    const allowed=new Set((Array.isArray(available)?available:[]).map(String));
    return [...new Set((Array.isArray(requested)?requested:[]).map(String).filter(code=>allowed.has(code)))].slice(0,Math.max(2,Math.min(10,Number(limit)||10)));
  }
  function alignCountryHistories(entries){
    const labels=[...new Set((Array.isArray(entries)?entries:[]).map(item=>String(item.label)))],byLabel=new Map(),timeSet=new Set();
    for(const item of entries||[]){
      const values=new Map();
      for(const row of Array.isArray(item.rows)?item.rows:[]){
        const stamp=row.completed_at||row.source_updated_at||row.observed_at,time=stamp?Date.parse(stamp):NaN,value=Number(row.players);
        if(!Number.isFinite(time)||!Number.isFinite(value))continue;
        const explicitlyInvalid=row.stats_ambiguous===true||row.carried_forward===true;
        values.set(time,{time,value:explicitlyInvalid?null:value});timeSet.add(time);
      }
      byLabel.set(String(item.label),values);
    }
    const times=[...timeSet].sort((a,b)=>a-b);
    return {times,series:labels.map(label=>({label,values:times.map(time=>byLabel.get(label)?.get(time)?.value??null)}))};
  }
  root.compareAnalysis={summarizeWindow,normalizeCountrySelection,alignCountryHistories};
})(globalThis);
