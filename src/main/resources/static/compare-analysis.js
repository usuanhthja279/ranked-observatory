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
  root.compareAnalysis={summarizeWindow};
})(globalThis);
