'use strict';
(()=>{
 const $=id=>document.getElementById(id), esc=v=>String(v??'—').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c])),
   fmt=v=>v==null?'—':Number(v).toLocaleString(), whole=v=>v==null?'—':Math.round(Number(v)).toLocaleString(),
   query=new URLSearchParams(location.search),view=query.get('view')||'world',initialRegion=query.get('region')||'all',initialCountry=query.get('country')||'all';
 const regions={ap:'Asia-Pacific',eu:'Europe',na:'North America',latam:'Latin America',br:'Brazil',kr:'South Korea'};
 const countryDisplay=code=>{if(!code||code==='unknown')return 'Unknown country';if(code==='kr')return 'South Korea';try{return new Intl.DisplayNames(['en'],{type:'region'}).of(code.toUpperCase())||code.toUpperCase()}catch{return code.toUpperCase()}};
 const state={acts:[],rows:[],allRows:[],capabilities:[],page:1,total:0,from:'',to:'',region:'all',country:'all',selectedCountries:[],countries:[],loading:false,chartPoints:[]};
 const isRegion=()=>view==='region',isCountry=()=>view==='country',estimateOn=()=>$('actTrendEstimateToggle').checked;
 const metricKey=()=>isCountry()?'count':$('actTrendMetric').value;
 const metricLabel=()=>`${$('actTrendTier').selectedOptions[0]?.textContent||'Players'} · ${metricKey()==='count'?(estimateOn()?'Estimated':'Observed')+' count':$('actTrendMetric').selectedOptions[0]?.textContent||'count'}`;
 async function get(path){const res=await fetch('/api/leaderboard/trends/'+path);if(!res.ok){const body=await res.json().catch(()=>({}));throw new Error(body.message||`Request failed (${res.status})`)}return res.json()}
 function value(row,key){
  const tier=$('actTrendTier').value,estimate=estimateOn(),base=({players:['players','region_unknown_players'],radiant:['radiant','region_unknown_radiant'],immortal1:[estimate?'estimated_immortal1':'immortal1',estimate?'region_unknown_estimated_immortal1':'region_unknown_immortal1'],immortal2:[estimate?'estimated_immortal2':'immortal2',estimate?'region_unknown_estimated_immortal2':'region_unknown_immortal2'],immortal3:[estimate?'estimated_immortal3':'immortal3',estimate?'region_unknown_estimated_immortal3':'region_unknown_immortal3']})[tier];
  if(!base)return null;const[field,unknownField]=base;let n=row[field]==null?null:Number(row[field]);if(n==null||!Number.isFinite(n))return null;
  if(estimate&&state.region!=='all'&&row.country!=='unknown'){
   const peers=state.allRows.filter(r=>r.act===row.act&&r.region===row.region&&r.country!=='unknown'),known=peers.reduce((sum,r)=>sum+(Number(r[field])||0),0),unknown=Number(row[unknownField])||0;
   n=known>0?n+unknown*n/known:null;
  }
  if(key==='share'){const d=(Number(row.region_known_players)||0)+(Number(row.region_unknown_players)||0);return d?n/d*100:null}
  if(key==='mix'){const d=Number(row.players)||0;return d?n/d*100:null}
  if(key==='index'){const firstAct=state.acts[0]?.act,first=state.rows.find(r=>r.act===firstAct&&label(r)===label(row));const baseline=first?value(first,'count'):0;return baseline?n/baseline*100:null}
  return n;
 }
 function label(row){if(state.region==='all')return regions[row.region]||row.region;if(isCountry())return countryDisplay(initialCountry);if(isRegion())return countryDisplay(row.country);if(state.country!=='all')return countryDisplay(state.country);return countryDisplay(row.country)}
 function rangeValue(row,act,key=metricKey()){const item=state.rows.find(r=>r.act===act&&label(r)===label(row));return item?value(item,key):null}
 function ensureCountryPicker(){
  if($('actTrendCountryMulti'))return;
  const host=document.createElement('div');host.id='actTrendCountryMulti';host.className='act-trend-country-multi';host.innerHTML='<div class="country-picker"><button id="actTrendCountryPickerToggle" type="button" class="country-picker-toggle" aria-expanded="false">Add country <span class="picker-caret" aria-hidden="true"></span></button><div id="actTrendCountryPickerMenu" class="country-picker-menu" hidden><input id="actTrendCountrySearch" type="search" placeholder="Search countries" aria-label="Search countries"><div id="actTrendCountryOptions" class="country-picker-options"></div></div></div><div id="actTrendCountryChips" class="country-picker-chips" aria-live="polite"></div><p id="actTrendCountryLimit" class="subtle country-picker-limit" aria-live="polite"></p>';
  $('actTrendsControls').querySelector('#actTrendCountry').parentElement.after(host);
  $('actTrendCountry').parentElement.classList.add('hidden');
  const menu=$('actTrendCountryPickerMenu'),toggle=$('actTrendCountryPickerToggle');document.body.append(menu);
  const placeMenu=()=>{if(menu.hidden)return;const r=toggle.getBoundingClientRect(),gap=6,pad=12,maxHeight=Math.min(330,innerHeight-pad*2),below=innerHeight-r.bottom-gap,above=r.top-gap,openAbove=below<Math.min(220,maxHeight)&&above>below,top=openAbove?Math.max(pad,r.top-gap-maxHeight):Math.min(innerHeight-pad-maxHeight,r.bottom+gap);menu.style.left=`${Math.max(pad,Math.min(r.left,innerWidth-Math.min(320,innerWidth-pad*2)-pad))}px`;menu.style.top=`${top}px`;menu.style.width=`${Math.min(320,innerWidth-pad*2)}px`;menu.style.maxHeight=`${maxHeight}px`};
  toggle.onclick=()=>{const open=menu.hidden;menu.hidden=!open;toggle.setAttribute('aria-expanded',String(open));if(open){placeMenu();$('actTrendCountrySearch').focus({preventScroll:true})}};
  window.addEventListener('scroll',placeMenu,true);window.addEventListener('resize',placeMenu);
  $('actTrendCountrySearch').oninput=()=>paintCountryOptions();
  $('actTrendCountryOptions').onclick=e=>{const button=e.target.closest('[data-country]');if(!button)return;const code=button.dataset.country;if(state.selectedCountries.includes(code))state.selectedCountries=state.selectedCountries.filter(x=>x!==code);else if(state.selectedCountries.length<6)state.selectedCountries=[...state.selectedCountries,code];else{$('actTrendCountryLimit').textContent='Select up to 6 countries so the chart stays readable.';return}paintCountryPicker();paintChart();paintTable()};
  $('actTrendCountryChips').onclick=e=>{const button=e.target.closest('[data-remove-country]');if(!button)return;state.selectedCountries=state.selectedCountries.filter(x=>x!==button.dataset.removeCountry);paintCountryPicker();paintChart();paintTable()};
  document.addEventListener('click',e=>{if(!host.contains(e.target)&&!menu.contains(e.target)){menu.hidden=true;toggle.setAttribute('aria-expanded','false')}});
 }
 function paintCountryOptions(){
  const term=($('actTrendCountrySearch')?.value||'').trim().toLowerCase(),items=state.countries.filter(code=>countryDisplay(code).toLowerCase().includes(term));
  $('actTrendCountryOptions').innerHTML=items.map(code=>`<button type="button" role="option" aria-selected="${state.selectedCountries.includes(code)}" data-country="${esc(code)}"><span class="country-option-check">${state.selectedCountries.includes(code)?'✓':''}</span>${esc(countryDisplay(code))}</button>`).join('')||'<p class="subtle">No matching countries.</p>';
 }
 function paintCountryPicker(){
  if(!isRegion()||!$('actTrendCountryMulti'))return;
  $('actTrendCountryChips').innerHTML=state.selectedCountries.map(code=>`<button class="country-chip" type="button" data-remove-country="${esc(code)}" aria-label="Remove ${esc(countryDisplay(code))}">${esc(countryDisplay(code))}<span aria-hidden="true">×</span></button>`).join('');
  $('actTrendCountryLimit').textContent=state.selectedCountries.length?`${state.selectedCountries.length} of 6 selected · leave empty to compare the leading countries.`:'';paintCountryOptions();
 }
 function setCountryOptions(rows){
  const countries=[...new Set(rows.map(r=>r.country).filter(c=>c&&c!=='all'&&c!=='unknown'))].sort((a,b)=>countryDisplay(a).localeCompare(countryDisplay(b)));
  state.countries=countries;state.selectedCountries=state.selectedCountries.filter(c=>countries.includes(c));
  const select=$('actTrendCountry'),previous=state.country;select.innerHTML='<option value="all">All countries</option>'+countries.map(c=>`<option value="${esc(c)}">${esc(countryDisplay(c))}</option>`).join('');select.value=isRegion()?'all':previous==='all'||countries.includes(previous)?previous:'all';state.country=isRegion()?'all':select.value;
  paintCountryPicker();
 }
 function selectedRows(){return state.rows.filter(r=>r.country!=='unknown'&&(!isRegion()||!state.selectedCountries.length||state.selectedCountries.includes(r.country)))}
 function visibleSeries(limit=6){
  const by=new Map();for(const row of selectedRows()){const name=label(row);if(!by.has(name))by.set(name,new Map());by.get(name).set(row.act,value(row,metricKey()))}
  let series=[...by].map(([name,points])=>({name,values:state.acts.map(a=>points.get(a.act)??null)}));
  series.sort((a,b)=>(b.values.filter(v=>v!=null).at(-1)||0)-(a.values.filter(v=>v!=null).at(-1)||0));return series.slice(0,limit);
 }
 function paintChart(){
  const canvas=$('actTrendChart'),ctx=canvas.getContext('2d'),width=Math.max(280,canvas.clientWidth||700),height=canvas.clientHeight||270,ratio=window.devicePixelRatio||1;
  canvas.width=width*ratio;canvas.height=height*ratio;ctx.setTransform(ratio,0,0,ratio,0,0);ctx.clearRect(0,0,width,height);state.chartPoints=[];
  const acts=state.acts,series=visibleSeries(6),key=metricKey(),left=50,right=width<460?42:60,top=24,bottom=40,plotW=Math.max(1,width-left-right),plotH=Math.max(1,height-top-bottom);
  if(!series.length){ctx.fillStyle='#a9bfd2';ctx.font='13px system-ui';ctx.fillText('No reported-country data in this selection.',16,34);$('actTrendChartNote').textContent='';$('actTrendLegend').innerHTML='';return}
  const vals=series.flatMap(s=>s.values.filter(v=>v!=null)),rawMax=Math.max(1,...vals),rawMin=Math.min(0,...vals),max=rawMax+(rawMax-rawMin)*.09,min=rawMin;
  const colors=['#71d3ca','#83b5ff','#f3ca72','#d39beb','#ff8d99','#9ad68f'];
  const pointsFor=s=>s.values.map((v,i)=>v==null?null:{x:left+(acts.length<=1?plotW/2:plotW*i/(acts.length-1)),y:top+plotH-(v-min)/(max-min||1)*plotH,v,act:acts[i]});
  ctx.font='10px system-ui';ctx.textAlign='right';ctx.fillStyle='#9ab0c5';
  for(let i=0;i<=4;i++){const y=top+plotH*i/4,n=max-(max-min)*i/4;ctx.strokeStyle=i===4?'#39536a':'#27394c';ctx.lineWidth=1;ctx.beginPath();ctx.moveTo(left,y);ctx.lineTo(width-right,y);ctx.stroke();ctx.fillText(key==='count'?whole(Math.round(n)):fmt(Math.round(n))+'%',left-8,y+3)}
  series.forEach((s,si)=>{
   const pts=pointsFor(s).filter(Boolean);if(!pts.length)return;const color=colors[si];
      const style=$('actTrendChartStyle')?.value||'smooth';
   const trace=(target,points)=>{target.moveTo(points[0].x,points[0].y);for(let i=1;i<points.length;i++){const p=points[i-1],q=points[i];if(style==='step'){target.lineTo(q.x,p.y);target.lineTo(q.x,q.y)}else if(style==='standard'){target.lineTo(q.x,q.y)}else{const before=points[Math.max(0,i-2)],after=points[Math.min(points.length-1,i+1)],dx=q.x-p.x;target.bezierCurveTo(p.x+dx/6,p.y+(q.y-before.y)/6,q.x-dx/6,q.y-(after.y-p.y)/6,q.x,q.y)}}};
   if(si===0&&series.length===1){const area=ctx.createLinearGradient(0,top,0,top+plotH);area.addColorStop(0,color+'55');area.addColorStop(1,color+'00');ctx.beginPath();ctx.moveTo(pts[0].x,top+plotH);ctx.lineTo(pts[0].x,pts[0].y);trace(ctx,pts);ctx.lineTo(pts.at(-1).x,top+plotH);ctx.closePath();ctx.fillStyle=area;ctx.fill()}
   ctx.strokeStyle=color;ctx.lineWidth=si===0?2.8:2.2;ctx.lineCap='round';ctx.lineJoin='round';ctx.shadowColor=color+'66';ctx.shadowBlur=si===0?7:3;ctx.beginPath();trace(ctx,pts);ctx.stroke();ctx.shadowBlur=0;
   const latest=pts.at(-1);pts.forEach(pt=>{ctx.beginPath();ctx.arc(pt.x,pt.y,pt===latest?4.2:2.5,0,Math.PI*2);ctx.fillStyle=pt===latest?'#eafffc':color;ctx.fill();if(pt===latest){ctx.strokeStyle=color;ctx.lineWidth=2;ctx.stroke()}state.chartPoints.push({...pt,name:s.name,color})});
   const text=key==='count'?whole(latest.v):fmt(latest.v)+'%';ctx.fillStyle=color;ctx.font='600 10px system-ui';ctx.textAlign='right';ctx.fillText(`${s.name} · ${text}`,width-8,top+12+si*15);
  });
  ctx.textAlign='center';ctx.fillStyle='#a3b7ca';ctx.font='10px system-ui';const important=acts.map((a,i)=>({a,i})).filter(x=>/ACT\s*III/i.test(x.a.label||''));const tickSet=new Set([0,Math.floor((acts.length-1)/2),acts.length-1,...important.map(x=>x.i)]);const ticks=[...tickSet].sort((a,b)=>a-b);ticks.forEach(i=>{const full=acts[i]?.label||'',short=full.replace(/^EPISODE\s+(\d+):\s*ACT\s+(\w+)/i,'E$1 · $2').replace(/^V(\d+):\s*ACT\s+(\w+)/i,'V$1 · $2');ctx.fillText(short,left+(acts.length<=1?plotW/2:plotW*i/(acts.length-1)),height-10)});
  $('actTrendChart').setAttribute('aria-label',`${metricLabel()} by act for ${series.map(s=>s.name).join(', ')}`);
  $('actTrendLegend').innerHTML=series.map((s,i)=>`<span class="act-trend-legend-item"><i style="--series-color:${colors[i]}"></i>${esc(s.name)} <b>${key==='count'?whole(s.values.filter(v=>v!=null).at(-1)):fmt(s.values.filter(v=>v!=null).at(-1))+'%'}</b></span>`).join('');
  const firstAct=acts[0]?.act,lastAct=acts.at(-1)?.act,summary=series[0],first=summary?.values.find(v=>v!=null),latest=summary?.values.filter(v=>v!=null).at(-1),delta=first==null||latest==null?null:latest-first,deltaDisplay=key==='count'?whole(Math.round(delta)):fmt(delta);
  const basis=estimateOn()?'Estimated allocation · fractional model values rounded for display':'Observed leaderboard counts';
  $('actTrendChartNote').innerHTML=`<span class="trend-summary-scope">${esc(summary?.name||'')} · ${esc(metricLabel())}</span><span class="trend-summary-value">${key==='count'?whole(latest):fmt(latest)} at ${esc(state.acts.at(-1)?.label||'latest')}</span>${delta==null?'':`<span class="trend-summary-change ${delta>=0?'up':'down'}">${delta>0?'+':''}${deltaDisplay} across selected acts</span>`}<span class="trend-summary-basis">${esc(basis)} · ${esc(state.acts.find(a=>a.act===firstAct)?.label||'')} → ${esc(state.acts.find(a=>a.act===lastAct)?.label||'')}</span>`;
 }
 function paintTable(){
  const series=visibleSeries(6),key=metricKey(),acts=state.acts,headers=['Country','From','To','Δ','Δ%','Peak / act'],start=acts[0],end=acts.at(-1);
  $('actTrendHead').innerHTML='<tr>'+headers.map(h=>`<th>${esc(h)}</th>`).join('')+'</tr>';
  const rows=[];
  series.forEach(s=>{
   const first=s.values[0]??null,last=s.values.at(-1)??null,delta=first==null||last==null?null:last-first,percent=first==null||last==null||first===0?null:delta/Math.abs(first)*100,peak=Math.max(...s.values.filter(v=>v!=null)),peakAt=s.values.findIndex(v=>v===peak),country=state.rows.find(r=>r.act===end?.act&&label(r)===s.name)?.country||'';
   const display=v=>v==null?'—':key==='count'?(estimateOn()?whole(v):fmt(v)):fmt(v)+(key==='share'||key==='mix'?'%':'');
   rows.push(`<tr data-country-row="${esc(country)}"${isRegion()?' tabindex="0"':''}><td>${esc(s.name)}</td><td>${display(first)}<small>${esc(start?.label||'')}</small></td><td>${display(last)}<small>${esc(end?.label||'')}</small></td><td class="${delta>0?'trend-positive':delta<0?'trend-negative':''}">${delta==null?'—':`${delta>0?'+':''}${display(delta)}`}</td><td class="${percent>0?'trend-positive':percent<0?'trend-negative':''}">${percent==null?'—':`${percent>0?'+':''}${fmt(percent)}%`}</td><td>${display(peak)}<small>${esc(acts[peakAt]?.label||'')}</small></td></tr>`);
  });
  $('actTrendRows').innerHTML=rows.join('')||`<tr><td colspan="${headers.length}">No country rows for these acts and filters.</td></tr>`;
  $('actTrendRows').onclick=e=>{const row=e.target.closest('[data-country-row]');if(isRegion()&&row?.dataset.countryRow)toggleCountry(row.dataset.countryRow)};
  $('actTrendRows').onkeydown=e=>{if((e.key==='Enter'||e.key===' ')&&e.target.matches('[data-country-row]')){e.preventDefault();toggleCountry(e.target.dataset.countryRow)}};
 }
 function toggleCountry(code){if(!code)return;if(state.selectedCountries.includes(code))state.selectedCountries=state.selectedCountries.filter(c=>c!==code);else if(state.selectedCountries.length<6)state.selectedCountries=[...state.selectedCountries,code];else{$('actTrendCountryLimit').textContent='Select up to 6 countries so the chart stays readable.';return}paintCountryPicker();paintChart();paintTable()} function paintTierCoverage(){const rows=state.capabilities.filter(r=>(state.region==='all'||r.region===state.region)),combined=rows.filter(r=>r.tier_detail==='COMBINED_ONLY'),mixed=rows.filter(r=>r.tier_detail==='MIXED'),combinedActs=[...new Set(combined.map(r=>`${r.label} (${regions[r.region]||r.region})`))],mixedActs=[...new Set(mixed.map(r=>`${r.label} (${regions[r.region]||r.region})`))];$('actTrendTierCoverageSummary').textContent=`${combined.length} combined-only shard${combined.length===1?'':'s'} · ${mixed.length} mixed · ${rows.filter(r=>r.tier_detail==='SPLIT').length} split`;$('actTrendTierCoverageNote').textContent=`All completed acts · ${state.region==='all'?'all regions':regions[state.region]||state.region}. “Observed” columns keep archive labels; “Estimated” columns split combined Immortal rows using act- and region-specific RR thresholds. Episode 3 regional thresholds are backcast before Episode 3; V26 Act II thresholds apply from V26 Act II onward. This does not infer Radiant. ${combinedActs.length?`Combined-only: ${combinedActs.join('; ')}.`:'No combined-only rows in this selection.'} ${mixedActs.length?`Mixed source labels: ${mixedActs.join('; ')}.`:''}`;$('actTrendTierCoverageRows').innerHTML=rows.map(r=>`<tr data-tier-detail="${esc(r.tier_detail)}"><td>${esc(r.label)}</td><td>${esc(regions[r.region]||r.region)}</td><td>${esc(({COMBINED_ONLY:'Combined only',MIXED:'Mixed source labels',SPLIT:'Immortal 1/2/3',NO_IMMORTAL_ROWS:'No Immortal rows'})[r.tier_detail]||r.tier_detail)}</td><td>${r.tier_detail==='COMBINED_ONLY'||r.tier_detail==='MIXED'?fmt(r.combined_immortal):'—'}</td><td>${r.tier_detail==='SPLIT'||r.tier_detail==='MIXED'?fmt(r.immortal3):'—'}</td><td>${r.tier_detail==='SPLIT'||r.tier_detail==='MIXED'?fmt(r.immortal2):'—'}</td><td>${r.tier_detail==='SPLIT'||r.tier_detail==='MIXED'?fmt(r.immortal1):'—'}</td><td>${fmt(r.estimated_immortal3)}</td><td>${fmt(r.estimated_immortal2)}</td><td>${fmt(r.estimated_immortal1)}</td><td>${esc(`${r.threshold_profile||'—'} · I2 ${r.immortal2_threshold_rr??'—'} / I3 ${r.immortal3_threshold_rr??'—'} RR`)}</td></tr>`).join('')||'<tr><td colspan="11">No completed act coverage in this selection.</td></tr>'}
 async function loadSeries(){
  if(!state.from||!state.to)return;state.loading=true;$('actTrendQuality').textContent='Loading act standings…';
  try{
   const queryCountry=isRegion()?'all':state.country,params=new URLSearchParams({fromAct:state.from,toAct:state.to,region:state.region,country:queryCountry,cohort:$('actTrendCohort').value}),data=await get('series?'+params);let allRows=data.rows||[];
   if(isCountry()){const allParams=new URLSearchParams({fromAct:state.from,toAct:state.to,region:state.region,country:'all',cohort:$('actTrendCohort').value});const allData=await get('series?'+allParams);allRows=allData.rows||[]}
   state.acts=data.acts||[];state.rows=data.rows||[];state.allRows=allRows;setCountryOptions(allRows);
   const estimate=estimateOn();$('actTrendQuality').textContent=state.rows.length?`${state.acts.some(a=>a.current_act)?'Latest complete current snapshot is included at the end. Earlier points are archived end-of-act standings, not match history.':'Completed archived end-of-act standings, not match history.'} Global ladder excluded to prevent cross-source double counting. Unknown country is not charted. ${estimate?'Unknown country counts are allocated proportionally; combined Immortal is split by regional RR thresholds. Estimated counts are rounded to whole players for display.':'Unknown players and combined Immortal subdivisions remain unestimated.'}`:(data.message||'No act data in this scope.');paintTable();paintChart();paintTierCoverage();
  }catch(e){$('actTrendQuality').textContent=e.message;$('actTrendRows').innerHTML='<tr><td colspan="8">Unable to load act comparisons.</td></tr>';paintChart()}finally{state.loading=false}
 }
 async function loadPlayers(){const act=$('actTrendTo').value;if(!act)return;const country=isRegion()?'all':state.country,p=new URLSearchParams({act,region:state.region,country,cohort:$('actTrendCohort').value,page:String(state.page),size:'10'});try{const data=await get('players?'+p);state.total=data.total||0;$('actTrendPlayerPage').textContent=`${state.total?state.page:'0'} / ${Math.max(1,Math.ceil(state.total/10))} · ${fmt(state.total)} players`;const cols='<thead><tr><th>Player</th><th>Country</th><th>Region</th><th>Regional rank</th><th>RR</th><th>Source tier</th><th>Tier interpretation</th><th>Wins</th></tr></thead>',rows=(data.items||[]).map(x=>`<tr><td>${esc(x.display_name)}</td><td>${esc(countryDisplay(x.country))}</td><td>${esc(regions[x.region]||x.region)}</td><td>${fmt(x.regional_rank)}</td><td>${fmt(x.rr)}</td><td>${esc(x.source_tier)}</td><td>${esc(x.estimated_tier)} <small>${esc(x.tier_basis)}</small></td><td>${fmt(x.wins)}</td></tr>`).join('');$('actTrendPlayerList').innerHTML='<table>'+cols+'<tbody>'+(rows||'<tr><td colspan="8">No archived players for this scope.</td></tr>')+'</tbody></table>';$('actTrendPlayerPrev').disabled=state.page<=1;$('actTrendPlayerNext').disabled=state.page>=Math.ceil(state.total/10)}catch(e){$('actTrendPlayerList').textContent=e.message}}
 function onRange(){state.from=$('actTrendFrom').value;state.to=$('actTrendTo').value;state.page=1;loadSeries();if($('actTrendPlayers').open)loadPlayers()}
 function organizeControls(){
  const controls=$('actTrendsControls');controls.classList.add('act-trends-controls-organized');
  let range=controls.querySelector('.act-trend-range-row'),scope=controls.querySelector('.act-trend-scope-row');
  if(!range){range=document.createElement('div');range.className='act-trend-range-row';scope=document.createElement('div');scope.className='act-trend-scope-row';controls.append(range,scope);const from=$('actTrendFrom').parentElement,to=$('actTrendTo').parentElement;range.append(from,to);scope.append($('actTrendRegion').parentElement,$('actTrendCountry').parentElement,$('actTrendCohort').parentElement,$('actTrendTier').parentElement,$('actTrendMetric').parentElement)}
 }
 async function init(){
  try{
   organizeControls();const[catalog,coverage]=await Promise.all([get('catalog'),get('capabilities')]);state.acts=catalog;state.capabilities=coverage.rows||[];
   if(!state.acts.length){$('actTrendCoverage').textContent='No complete historical acts yet';$('actTrendQuality').textContent='This panel will populate automatically after all six regional ladders for an act have reached a verified terminal page.';$('actTrendFrom').disabled=true;$('actTrendTo').disabled=true;return}
   const opts=state.acts.map(a=>`<option value="${esc(a.act)}">${esc(a.label)}</option>`).join('');$('actTrendFrom').innerHTML=opts;$('actTrendTo').innerHTML=opts;$('actTrendCoverage').textContent=`${state.acts.filter(a=>!a.current_act).length} completed historical acts${state.acts.some(a=>a.current_act)?' · current snapshot available':''}`;$('actTrendTo').value=state.acts.at(-1).act;$('actTrendFrom').value=state.acts[Math.max(0,state.acts.length-6)].act;
   state.region=regions[initialRegion]?initialRegion:'all';if(isRegion()||isCountry())state.region=initialRegion;if(isCountry())state.country=initialCountry;if(isRegion()&&initialCountry!=='all')state.selectedCountries=[initialCountry];
   $('actTrendRegion').value=state.region;$('actTrendsSection').classList.toggle('country-act-trends',isCountry());
   $('actTrendRegion').parentElement.style.display=isRegion()||isCountry()?'none':'';$('actTrendCountry').parentElement.style.display=isRegion()||isCountry()?'none':'';$('actTrendCohort').parentElement.style.display=isCountry()?'none':'';$('actTrendMetric').parentElement.style.display=isCountry()?'none':'';$('actTrendMetricHelp').style.display=isCountry()?'none':'';$('actTrendPlayers').hidden=isRegion();
   if(isRegion()){ensureCountryPicker();$('actTrendCohort').parentElement.before($('actTrendCountryMulti'));$('actTrendsSection').classList.add('region-act-trends')};if(isCountry())$('actTrendMetric').value='count';
   $('actTrendRegion').onchange=()=>{state.region=$('actTrendRegion').value;state.country='all';state.page=1;loadSeries();if($('actTrendPlayers').open)loadPlayers()};$('actTrendCountry').onchange=()=>{state.country=$('actTrendCountry').value;state.page=1;loadSeries();if($('actTrendPlayers').open)loadPlayers()};
   $('actTrendFrom').onchange=onRange;$('actTrendTo').onchange=onRange;
   $('actTrendMetric').onchange=()=>{const help={count:'Player count at each archived act end or latest complete current snapshot.',share:'Selected tier as a share of the full regional ladder.',mix:'Selected tier as a share of the selected scope.',index:'First selected act is indexed to 100 for comparison.'};$('actTrendMetricHelp').textContent=help[$('actTrendMetric').value]||'';paintTable();paintChart()};$('actTrendMetric').onchange();
   $('actTrendTier').onchange=()=>{paintTable();paintChart()};$('actTrendEstimateToggle').onchange=()=>loadSeries();$('actTrendCohort').onchange=()=>{state.page=1;loadSeries();if($('actTrendPlayers').open)loadPlayers()};$('actTrendPlayerPrev').onclick=()=>{if(state.page>1){state.page--;loadPlayers()}};$('actTrendPlayerNext').onclick=()=>{if(state.page<Math.ceil(state.total/10)){state.page++;loadPlayers()}};$('actTrendPlayers').ontoggle=()=>{if($('actTrendPlayers').open)loadPlayers()};
   const canvas=$('actTrendChart');if(!$('actTrendChartStyle')){const toolbar=document.createElement('div');toolbar.className='act-trend-chart-toolbar';toolbar.innerHTML='<label>Trend shape<select id="actTrendChartStyle"><option value="smooth">Smoothed · curved</option><option value="standard">Standard · straight</option><option value="step">Stepped · horizontal / vertical</option></select></label>';canvas.closest('.act-trend-chart-panel').insertBefore(toolbar,canvas.closest('.act-trend-chart-wrap'))}$('actTrendChartStyle').onchange=paintChart;canvas.onmousemove=e=>{const rect=canvas.getBoundingClientRect(),x=e.clientX-rect.left,y=e.clientY-rect.top,near=state.chartPoints.reduce((best,p)=>{const d=Math.hypot(p.x-x,p.y-y);return d<(best?.d??18)?{...p,d}:best},null),tip=$('actTrendTooltip');if(!near){tip.hidden=true;return}tip.innerHTML=`<b>${esc(near.name)}</b><span>${esc(near.act.label||'Act')} · ${metricKey()==='count'?whole(estimateOn()?Math.round(near.v):near.v):fmt(near.v)+'%'}</span>`;tip.hidden=false;tip.style.left=`${Math.min(Math.max(4,x+12),rect.width-170)}px`;tip.style.top=`${Math.max(4,y-42)}px`};canvas.onmouseleave=()=>{$('actTrendTooltip').hidden=true};
   state.from=$('actTrendFrom').value;state.to=$('actTrendTo').value;await loadSeries();window.addEventListener('resize',()=>{if(state.rows.length)paintChart()});
  }catch(e){$('actTrendCoverage').textContent='Historical trends unavailable';$('actTrendQuality').textContent=e.message;$('actTrendTierCoverageSummary').textContent='Tier coverage unavailable'}
 }
 init();
})();
