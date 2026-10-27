'use strict';
const test=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const vm=require('node:vm');
const source=fs.readFileSync(require('node:path').join(__dirname,'../../main/resources/static/compare-analysis.js'),'utf8');
const context={Date,Set,Number,Array,globalThis:null};
context.globalThis=context;
vm.runInNewContext(source,context);
const summarize=context.compareAnalysis.summarizeWindow;

test('comparison summarizes only matching observations inside the selected window',()=>{
  const now=Date.now();
  const rows=[
    {completed_at:new Date(now-50*60000).toISOString(),players:100},
    {completed_at:new Date(now-20*60000).toISOString(),players:125},
    {completed_at:new Date(now-2*60*60000).toISOString(),players:900}
  ];
  const result=summarize(rows,60,['players']);
  assert.equal(result.available,true);
  assert.equal(result.changes.players.delta,25);
  assert.equal(result.points,2);
});

test('comparison rejects carried-forward and ambiguous player observations',()=>{
  const now=Date.now();
  const rows=[
    {completed_at:new Date(now-40*60000).toISOString(),rr:100},
    {completed_at:new Date(now-20*60000).toISOString(),rr:200,carried_forward:true},
    {completed_at:new Date(now-10*60000).toISOString(),rr:300,stats_ambiguous:true}
  ];
  const result=summarize(rows,60,['rr']);
  assert.equal(result.available,false);
  assert.match(result.reason,/Fewer than two/);
});

test('comparison excludes aggregate snapshots containing carried-forward or ambiguous rows',()=>{
  const now=Date.now();
  const rows=[
    {completed_at:new Date(now-40*60000).toISOString(),players:100,carried_forward:0,stats_ambiguous:0},
    {completed_at:new Date(now-20*60000).toISOString(),players:125,carried_forward:1,stats_ambiguous:0},
    {completed_at:new Date(now-10*60000).toISOString(),players:130,carried_forward:0,stats_ambiguous:2}
  ];
  const result=summarize(rows,60,['players']);
  assert.equal(result.available,false);
  assert.equal(result.points,1);
});

test('comparison explicitly reports insufficient archived history',()=>{
  const result=summarize([{completed_at:new Date().toISOString(),rr:520}],1440,['rr']);
  assert.equal(result.available,false);
  assert.equal(result.points,1);
});

test('comparison limits windows to supported bounded intervals',()=>{
  const now=Date.now();
  const rows=[
    {completed_at:new Date(now-50*60000).toISOString(),players:10},
    {completed_at:new Date(now-5*60000).toISOString(),players:15}
  ];
  const result=summarize(rows,999999,['players']);
  assert.equal(result.windowMinutes,60);
  assert.equal(result.available,true);
});

test('country comparison accepts at most ten available unique countries',()=>{
  const available=Array.from({length:12},(_,index)=>`c${index}`);
  assert.deepEqual(Array.from(context.compareAnalysis.normalizeCountrySelection(['c1','c1','missing',...available],available)),['c1','c0','c2','c3','c4','c5','c6','c7','c8','c9']);
});

test('country trend alignment shares timestamps and only omits explicitly invalid snapshots',()=>{
  const aligned=context.compareAnalysis.alignCountryHistories([
    {label:'India',rows:[{completed_at:'2026-10-01T00:00:00Z',players:100,carried_forward:4,stats_ambiguous:2},{completed_at:'2026-10-02T00:00:00Z',players:120,carried_forward:true}]},
    {label:'Japan',rows:[{completed_at:'2026-10-01T00:00:00Z',players:80},{completed_at:'2026-10-03T00:00:00Z',players:90,stats_ambiguous:true}]}
  ]);
  assert.equal(aligned.times.length,3);
  assert.deepEqual(Array.from(aligned.series[0].values),[100,null,null]);
  assert.deepEqual(Array.from(aligned.series[1].values),[80,null,null]);
});

test('country trend alignment preserves a single current snapshot for point-only charts',()=>{
  const aligned=context.compareAnalysis.alignCountryHistories([
    {label:'Türkiye',rows:[{completed_at:'2026-10-27T00:00:00Z',players:1041}]},
    {label:'Germany',rows:[{completed_at:'2026-10-27T00:00:00Z',players:906}]}
  ]);
  assert.equal(aligned.times.length,1);
  assert.deepEqual(Array.from(aligned.series[0].values),[1041]);
  assert.deepEqual(Array.from(aligned.series[1].values),[906]);
});

test('country alignment preserves missing measurements as gaps and preserves genuine zero',()=>{
  const aligned=context.compareAnalysis.alignCountryHistories([{label:'India',rows:[
    {completed_at:'2026-10-01T00:00:00Z',players:0},
    {completed_at:'2026-10-02T00:00:00Z',players:null},
    {completed_at:'2026-10-03T00:00:00Z',players:''},
    {completed_at:'2026-10-04T00:00:00Z',players:'not-a-number'}
  ]}]);
  assert.equal(aligned.times.length,4);
  assert.deepEqual(Array.from(aligned.series[0].values),[0,null,null,null]);
});

test('trend segment model keeps all missing act positions as disconnected gaps',()=>{
  const split=context.compareAnalysis.trendSegments;
  assert.deepEqual(JSON.parse(JSON.stringify(split([null,2,3,null,5,null]))),[[{index:1,value:2},{index:2,value:3}],[{index:4,value:5}]]);
  assert.deepEqual(JSON.parse(JSON.stringify(split([null,1,null]))),[[{index:1,value:1}]]);
  assert.deepEqual(JSON.parse(JSON.stringify(split([1,null]))),[[{index:0,value:1}]]);
});

test('trend endpoint summary never substitutes a missing selected endpoint',()=>{
  const summarize=context.compareAnalysis.endpointSummary;
  assert.deepEqual(JSON.parse(JSON.stringify(summarize([null,20,30]))),{start:null,end:30,delta:null});
  assert.deepEqual(JSON.parse(JSON.stringify(summarize([10,20,null]))),{start:10,end:null,delta:null});
  assert.deepEqual(JSON.parse(JSON.stringify(summarize([10,20,30]))),{start:10,end:30,delta:20});
});

test('comparison metric semantics distinguish numeric and categorical values without NaN',()=>{
  const compare=context.compareAnalysis.metricDifference;
  assert.deepEqual(JSON.parse(JSON.stringify(compare({type:'category',a:'Radiant',b:'Immortal 3'}))),{text:'Different',value:null});
  assert.deepEqual(JSON.parse(JSON.stringify(compare({type:'category',a:'India',b:'india'}))),{text:'Same',value:null});
  assert.deepEqual(JSON.parse(JSON.stringify(compare({type:'category',a:'',b:'India'}))),{text:'Not comparable',value:null});
  assert.deepEqual(JSON.parse(JSON.stringify(compare({type:'incomparable',a:3,b:7}))),{text:'Not comparable',value:null});
  assert.deepEqual(JSON.parse(JSON.stringify(compare({a:3,b:7,lowerIsBetter:true}))),{text:'A better by 4',value:-4});
  assert.deepEqual(JSON.parse(JSON.stringify(compare({a:null,b:0}))),{text:'Not comparable',value:null});
  assert.deepEqual(JSON.parse(JSON.stringify(compare({a:'',b:0}))),{text:'Not comparable',value:null});
  assert.deepEqual(JSON.parse(JSON.stringify(compare({a:0,b:0}))),{text:'Same',value:0});
  assert.equal(context.compareAnalysis.seriesColors.length>=10,true);
});
