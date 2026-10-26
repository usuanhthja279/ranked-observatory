'use strict';
const fs=require('node:fs'),vm=require('node:vm'),assert=require('node:assert/strict');
const html=fs.readFileSync('leaderboard-service/src/main/resources/static/act-grinding-comparison.html','utf8'),source=fs.readFileSync('leaderboard-service/src/main/resources/static/act-grinding-comparison.js','utf8'),report=JSON.parse(fs.readFileSync('docs/act-grinding-comparison.json','utf8'));
const elements=new Map([...html.matchAll(/id="([^"]+)"/g)].map(m=>[m[1],{value:'',innerHTML:'',textContent:'',clientWidth:900,clientHeight:350,selectedOptions:[{text:''}],getContext:()=>new Proxy({},{get:()=>()=>{}})}]));
elements.get('region').value='ap';elements.get('metric').value='radiant';
const context=vm.createContext({document:{getElementById:id=>{assert(elements.has(id));return elements.get(id);}},window:{addEventListener(){}},devicePixelRatio:1,console,fetch:async()=>({ok:true,json:async()=>report})});
(async()=>{
 vm.runInContext(source,context);await new Promise(setImmediate);assert.equal(elements.get('error').textContent,'');
 assert(elements.get('rows').innerHTML.includes('<td>India</td><td>3</td><td>0</td><td>43</td><td>14</td><td>7</td><td>6</td><td>6</td>'));
 for(const region of Object.keys(report.regions))for(const metric of ['players','radiant','immortal3','immortal2','immortal1']){
  elements.get('region').value=region;elements.get('metric').value=metric;vm.runInContext('render()',context);
  assert.equal((elements.get('rows').innerHTML.match(/<tr/g)||[]).length,report.regions[region].countries.length);
  assert(elements.get('source').textContent.includes('snapshot '+report.snapshotId));assert(elements.get('coverage').textContent.includes('4.85 hours'));
  assert(elements.get('validation').innerHTML.includes((report.regions[region].knownLabelFiveFoldAllocationError.current*100).toFixed(2)+'%'));
 }
 assert(html.includes('Production country estimates are unchanged'));assert(html.includes('not a confidence interval'));
 console.log('PASS: all six regions × five metrics, snapshot provenance, India actual values, history coverage, known-label validation and experimental status. DOM checks only.');
})().catch(e=>{console.error(e);process.exitCode=1;});


