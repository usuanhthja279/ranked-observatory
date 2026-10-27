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
