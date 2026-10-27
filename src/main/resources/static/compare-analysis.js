'use strict';

(function(root){
  const allowedWindows=new Set([60,360,1440]);
  const finiteValue=value=>value==null||typeof value==='string'&&!value.trim()||typeof value==='boolean'?null:Number.isFinite(Number(value))?Number(value):null;
  function metricDifference(item){
    const a=item.a==null?null:String(item.a),b=item.b==null?null:String(item.b);
    if(item.type==='incomparable')return {text:'Not comparable',value:null};
    if(item.type==='category')return a==null||!a.trim()||b==null||!b.trim()?{text:'Not comparable',value:null}:a.toLocaleLowerCase()===b.toLocaleLowerCase()?{text:'Same',value:null}:{text:'Different',value:null};
    const av=finiteValue(item.a),bv=finiteValue(item.b);
    if(av==null||bv==null)return {text:'Not comparable',value:null};
    const difference=av-bv;
    return {text:difference===0?'Same':item.lowerIsBetter?(difference<0?'A better by ':'B better by ')+Math.abs(difference).toLocaleString():(difference>0?'A +':'B +')+Math.abs(difference).toLocaleString(),value:difference};
  }
  function trendSegments(values){const segments=[];let segment=[];(Array.isArray(values)?values:[]).forEach((value,index)=>{if(value==null||!Number.isFinite(Number(value))){if(segment.length)segments.push(segment);segment=[]}else segment.push({index,value:Number(value)})});if(segment.length)segments.push(segment);return segments;}
  function endpointSummary(values){const list=Array.isArray(values)?values:[],start=list[0],end=list.at(-1),validStart=start!=null&&Number.isFinite(Number(start)),validEnd=end!=null&&Number.isFinite(Number(end));return {start:validStart?Number(start):null,end:validEnd?Number(end):null,delta:validStart&&validEnd?Number(end)-Number(start):null};}
  const seriesColors=['#71d3ca','#83b5ff','#f3ca72','#d39beb','#ff8d99','#9ad68f','#f79c4a','#59c6ef','#c4e06b','#e38bbd'];
  function summarizeWindow(rows,minutes,fields){
    const windowMinutes=allowedWindows.has(Number(minutes))?Number(minutes):60;
    const now=Date.now();
    const valid=(Array.isArray(rows)?rows:[]).filter(row=>{
      const stamp=row.completed_at||row.source_updated_at||row.observed_at;
      const time=stamp?Date.parse(stamp):NaN;
      if(!Number.isFinite(time)||time>now||time<now-windowMinutes*60000)return false;
      if(row.stats_ambiguous===true||row.carried_forward===true)return false;
      if(Number(row.stats_ambiguous||0)>0||Number(row.carried_forward||0)>0)return false;
      return fields.some(field=>finiteValue(row[field])!=null);
    }).sort((a,b)=>Date.parse(a.completed_at||a.source_updated_at||a.observed_at)-Date.parse(b.completed_at||b.source_updated_at||b.observed_at));
    if(valid.length<2)return {available:false,reason:'Fewer than two comparable observations in this window.',points:valid.length};
    const first=valid[0],last=valid[valid.length-1],changes={};
    for(const field of fields){
      const start=finiteValue(first[field]),end=finiteValue(last[field]);
      if(start==null||end==null)continue;
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
        const stamp=row.completed_at||row.source_updated_at||row.observed_at,time=stamp?Date.parse(stamp):NaN,value=finiteValue(row.players);
        if(!Number.isFinite(time))continue;
        const explicitlyInvalid=row.stats_ambiguous===true||row.carried_forward===true;
        values.set(time,{time,value:explicitlyInvalid?null:value});timeSet.add(time);
      }
      byLabel.set(String(item.label),values);
    }
    const times=[...timeSet].sort((a,b)=>a-b);
    return {times,series:labels.map(label=>({label,values:times.map(time=>byLabel.get(label)?.get(time)?.value??null)}))};
  }
  root.compareAnalysis={summarizeWindow,normalizeCountrySelection,alignCountryHistories,finiteValue,metricDifference,trendSegments,endpointSummary,seriesColors};
})(globalThis);
