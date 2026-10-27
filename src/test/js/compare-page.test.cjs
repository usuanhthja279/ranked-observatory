'use strict';
const test=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const vm=require('node:vm');
const path=require('node:path');

function harness({failHistoryFor}={}){
  const source=fs.readFileSync(path.join(__dirname,'../../main/resources/static/compare-page.js'),'utf8');
  const elements=new Map();
  const element=id=>{
    if(!elements.has(id))elements.set(id,{id,value:'',innerHTML:'',isConnected:true,listeners:{},addEventListener(type,callback){this.listeners[type]=callback},querySelector(){return null}});
    return elements.get(id);
  };
  const root={isConnected:true,innerHTML:'',querySelector(selector){return element(selector.slice(1))},querySelectorAll(){return []}};
  const players={
    'Alpha#One':{display_name:'Alpha#One',region:'ap',country:'in',rr:450,global_rank:20,regional_rank:3,tier:'Radiant',wins:12,source_updated_at:'2026-10-27T10:00:00Z'},
    'Beta#Two':{display_name:'Beta#Two',region:'eu',country:'de',rr:300,global_rank:41,regional_rank:8,tier:'Immortal 3',wins:8,source_updated_at:'2026-10-27T10:00:00Z'},
    'Gamma#Three':{display_name:'Gamma#Three',region:'ap',country:'in',rr:200,global_rank:70,regional_rank:14,tier:'Immortal 2',wins:4},
    'Delta#Four':{display_name:'Delta#Four',region:'ap',country:'in',rr:510,global_rank:8,regional_rank:1,tier:'Radiant',wins:15}
  };
  const fetch=async(url)=>{
    const parsed=new URL(url,'http://localhost');
    if(parsed.pathname.endsWith('/status'))return {ok:true,json:async()=>({countries:{in:'India',de:'Germany'},regions:{ap:'Asia-Pacific',eu:'Europe'}})};
    if(parsed.pathname.endsWith('/acts'))return {ok:true,json:async()=>[{act:'act-1',label:'V26: ACT I',current_act:false}]};
    if(parsed.pathname.endsWith('/players')){const term=parsed.searchParams.get('search');if(term==='Gamma#Three')await new Promise(resolve=>setTimeout(resolve,150));const row=players[term];return {ok:true,json:async()=>({items:row?[row]:[]})}}
    if(parsed.pathname.endsWith('/history')){
      const name=parsed.searchParams.get('player');
      if(name===failHistoryFor)throw new Error('synthetic history outage');
      return {ok:true,json:async()=>[{completed_at:new Date().toISOString(),rr:players[name].rr,global_rank:players[name].global_rank,regional_rank:players[name].regional_rank}]};
    }
    throw new Error('Unexpected API path '+parsed.pathname);
  };
  const location={pathname:'/compare',search:'?mode=player&a=Alpha%23One&b=Beta%23Two&act=act-1'};
  const context={document:{getElementById:id=>id==='comparePage'?root:null},location,history:{replaceState(){}},URLSearchParams,URL,fetch,AbortController,Date,Map,Set,Number,Array,String,encodeURIComponent,setTimeout,clearTimeout,globalThis:null};
  context.globalThis=context;
  vm.runInNewContext(fs.readFileSync(path.join(__dirname,'../../main/resources/static/compare-analysis.js'),'utf8'),context);
  vm.runInNewContext(source,context);
  return {context,root,elements};
}

test('player comparison renders for two exact Riot IDs with archived Act context',async()=>{
  const {context,elements}=harness();
  await context.refreshComparePage();
  const rendered=elements.get('playerCompareResults').innerHTML;
  assert.match(rendered,/V26: ACT I/);
  assert.match(rendered,/Reported tier/);
  assert.match(rendered,/Different/);
  assert.match(rendered,/A better by 21/);
  assert.match(rendered,/Regional rank · separate regional scopes[\s\S]*?Not comparable/);
  assert.doesNotMatch(rendered,/ReferenceError|NaN|Infinity|undefined/);
  const window=elements.get('compareWindow');window.listeners.change({target:{value:'360'}});
  await new Promise(resolve=>setTimeout(resolve,0));
  assert.match(elements.get('playerCompareResults').innerHTML,/value="360" selected/);
});

test('player comparison remains available when a history request fails',async()=>{
  const {context,elements}=harness({failHistoryFor:'Alpha#One'});
  await context.refreshComparePage();
  const rendered=elements.get('playerCompareResults').innerHTML;
  assert.match(rendered,/Player A history unavailable: synthetic history outage/);
  assert.match(rendered,/Reported tier/);
  assert.doesNotMatch(rendered,/NaN|Infinity|undefined/);
});

test('a late player suggestion response cannot replace a newer selection',async()=>{
  const {context,elements}=harness();
  await context.refreshComparePage();
  const input=elements.get('playerA');
  input.value='Gamma#Three';input.listeners.input();
  await new Promise(resolve=>setTimeout(resolve,360)); // let the older, deliberately slow request start
  input.value='Delta#Four';input.listeners.input();
  await new Promise(resolve=>setTimeout(resolve,560));
  const rendered=elements.get('playerCompareResults').innerHTML;
  assert.match(rendered,/Delta#Four/);
  assert.doesNotMatch(rendered,/Gamma#Three/);
});

test('player A and B search debounce independently',async()=>{
  const {context,elements}=harness();
  await context.refreshComparePage();
  const a=elements.get('playerA'),b=elements.get('playerB');
  a.value='Gamma#Three';a.listeners.input();
  b.value='Delta#Four';b.listeners.input();
  await new Promise(resolve=>setTimeout(resolve,560));
  const rendered=elements.get('playerCompareResults').innerHTML;
  assert.match(rendered,/Gamma#Three/);
  assert.match(rendered,/Delta#Four/);
});
