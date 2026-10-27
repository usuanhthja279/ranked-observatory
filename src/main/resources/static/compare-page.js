'use strict';

(function(){
  const root=document.getElementById('comparePage');
  if(!root)return;
  const esc=value=>String(value??'—').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
  const n=value=>value==null?'—':Number(value).toLocaleString();
  const date=value=>value?new Date(value).toLocaleString():'—';
  const params=new URLSearchParams(location.search);
  let mode=['country','region','player'].includes(params.get('mode'))?params.get('mode'):'country';
  let first=params.get('a')||'',second=params.get('b')||'',selectedCountries=(params.get('countries')||'').split(',').filter(Boolean),minutes=[60,360,1440].includes(Number(params.get('window')))?Number(params.get('window')):60;
  let acts=[],selectedAct='',countriesByCode={},regionsByCode={},timer=0,lookup={a:[],b:[]};
  const api=async path=>{
    const url=new URLSearchParams(path.split('?')[1]||'');
    if(selectedAct&&!url.has('act'))url.set('act',selectedAct);
    const response=await fetch('/api/leaderboard/'+path.split('?')[0]+'?'+url.toString());
    if(!response.ok)throw new Error('Could not load comparison data ('+response.status+').');
    return response.json();
  };
  const queryState=()=>{
    const next=new URLSearchParams(location.search);
    next.set('mode',mode);
    if(first)next.set('a',first);else next.delete('a');
    if(second)next.set('b',second);else next.delete('b');
    if(mode==='country'&&selectedCountries.length)next.set('countries',selectedCountries.join(','));else next.delete('countries');
    next.set('window',String(minutes));
    const current=acts.find(a=>a.current_act);
    if(selectedAct&&selectedAct!==current?.act)next.set('act',selectedAct);else next.delete('act');
    history.replaceState({compare:true},'',location.pathname+'?'+next.toString());
  };
  const tabMarkup=()=>'<nav class="compare-tabs" role="tablist" aria-label="Comparison type">'+
    [['country','Countries'],['region','Regions'],['player','Players']].map(item=>'<button type="button" role="tab" aria-selected="'+(mode===item[0])+'" data-compare-mode="'+item[0]+'">'+item[1]+'</button>').join('')+'</nav>';
  const selectMarkup=(label,id,rows,selected,labelOf,valueOf)=>'<label class="compare-field">'+esc(label)+'<select id="'+id+'">'+
    rows.map(row=>'<option value="'+esc(valueOf(row))+'" '+(String(valueOf(row))===String(selected)?'selected':'')+'>'+esc(labelOf(row))+'</option>').join('')+'</select></label>';
  const delta=value=>value==null?'—':(Number(value)>0?'+':'')+n(value);
  const metrics=(a,b,defs)=>'<div class="compare-table-wrap"><table><thead><tr><th>Measure</th><th>'+esc(a)+'</th><th>'+esc(b)+'</th><th>Difference</th></tr></thead><tbody>'+
    defs.map(item=>{
      const av=item.a==null?null:Number(item.a),bv=item.b==null?null:Number(item.b);
      const difference=av==null||bv==null?null:av-bv;
      return '<tr><th scope="row">'+esc(item.label)+'</th><td>'+esc(item.format?item.format(item.a):n(item.a))+'</td><td>'+esc(item.format?item.format(item.b):n(item.b))+'</td><td>'+esc(difference==null?'Not comparable':(difference===0?'Same':(difference>0?'A +':'B +')+n(Math.abs(difference))))+'</td></tr>';
    }).join('')+'</tbody></table></div>';
  const metricCard=(title,value,note)=>'<article class="compare-metric"><span>'+esc(title)+'</span><strong>'+esc(value)+'</strong><small>'+esc(note)+'</small></article>';
  const historyText=(label,result,field)=>{
    if(!result.available)return '<p class="compare-muted">'+esc(label)+': '+esc(result.reason||'Change is unavailable.')+'</p>';
    const change=result.changes[field];
    return '<p><strong>'+esc(label)+'</strong> changed from '+esc(n(change.from))+' to '+esc(n(change.to))+' ('+esc(delta(change.delta))+') across '+esc(n(result.points))+' valid observations, '+esc(date(result.first.completed_at||result.first.source_updated_at))+' to '+esc(date(result.last.completed_at||result.last.source_updated_at))+'.</p>';
  };
  const refresh=async()=>{
    if(!root.isConnected)return;
    selectedAct=document.getElementById('actFilter')?.value||params.get('act')||'';
    queryState();
    root.innerHTML='<section class="compare-panel"><p role="status">Loading source-matched comparisons…</p></section>';
    try{
      const [status,actList]=await Promise.all([api('status'),api('acts')]);
      acts=actList||[];countriesByCode=status.countries||{};regionsByCode=status.regions||{};
      if(mode==='country')await renderCountries();
      else if(mode==='region')await renderRegions();
      else await renderPlayers();
    }catch(error){
      root.innerHTML='<section class="compare-panel compare-error" role="alert"><h2>Comparison unavailable</h2><p>'+esc(error.message)+'</p><button type="button" data-compare-retry>Retry</button></section>';
    }
  };
  const finish=(scope,content)=>{
    root.innerHTML=tabMarkup()+scope+content;
    root.querySelectorAll('[data-compare-mode]').forEach(button=>button.addEventListener('click',()=>{
      mode=button.dataset.compareMode;first='';second='';queryState();refresh();
    }));
    root.querySelector('[data-compare-retry]')?.addEventListener('click',refresh);
  };
  const renderCountries=async()=>{
    const rows=await api('countries?region=all&country=all');
    const list=(rows||[]).filter(row=>row.country!=null);
    const key=row=>String(row.country);
    const title=row=>countriesByCode[key(row)]|| (key(row)==='unknown'?'Unknown country':key(row));
    if(!list.length){finish('<section class="compare-panel"><h2>Compare countries</h2></section>','<section class="compare-panel"><p>No country summaries are available for this Act.</p></section>');return;}
    if(!list.some(r=>key(r)===first))first=key(list.find(r=>key(r)==='in')||list[0]);
    if(!list.some(r=>key(r)===second)||second===first)second=key(list.find(r=>key(r)==='jp'&&key(r)!==first)||list.find(r=>key(r)!==first)||list[0]);
    const available=list.map(key),legacy=[first,second].filter(Boolean);
    selectedCountries=globalThis.compareAnalysis.normalizeCountrySelection(selectedCountries.length?selectedCountries:legacy,available);
    if(selectedCountries.length<2)selectedCountries=globalThis.compareAnalysis.normalizeCountrySelection(['in','jp',...available],available);
    first=selectedCountries[0];second=selectedCountries[1];
    const currentAct=acts.find(item=>item.act===selectedAct)?.current_act;
    const selector='<section class="compare-panel"><div class="compare-section-heading"><div><p class="compare-kicker">MATCHED SNAPSHOT</p><h2>Compare countries</h2></div><span class="compare-basis">Same Act · all regions · global source</span></div><label class="compare-field">Find a country<input id="compareCountrySearch" type="search" placeholder="Search country names" autocomplete="off"></label><div id="compareCountryOptions" class="compare-country-options" role="group" aria-label="Select countries to compare"></div><div id="compareCountryChips" class="compare-country-chips" aria-live="polite"></div><p id="compareCountryHelp" class="compare-muted">Select 2 to 10 countries. Counts are observed source rows; country labels are not verified player populations.</p></section>';
    finish(selector,'<section id="compareCountryResults" class="compare-panel" aria-live="polite"><p class="compare-muted" role="status">Choose at least two countries to compare.</p></section>');
    let renderToken=0;
    const paintPicker=()=>{
      const term=(root.querySelector('#compareCountrySearch')?.value||'').trim().toLocaleLowerCase();
      const visible=list.filter(row=>!term||title(row).toLocaleLowerCase().includes(term)||key(row).toLocaleLowerCase().includes(term)).slice(0,80);
      root.querySelector('#compareCountryOptions').innerHTML=visible.map(row=>`<button type="button" class="compare-country-option" data-country="${esc(key(row))}" aria-pressed="${selectedCountries.includes(key(row))}"><span aria-hidden="true">${selectedCountries.includes(key(row))?'✓':'+'}</span>${esc(title(row))}<small>${n(row.players)} rows</small></button>`).join('')||'<p class="compare-muted">No countries match this search.</p>';
      root.querySelector('#compareCountryChips').innerHTML=selectedCountries.map(code=>`<button type="button" class="compare-country-chip" data-remove-country="${esc(code)}" aria-label="Remove ${esc(title(list.find(row=>key(row)===code)||{country:code}))}">${esc(title(list.find(row=>key(row)===code)||{country:code}))} <span aria-hidden="true">×</span></button>`).join('');
      root.querySelector('#compareCountryHelp').textContent=`${selectedCountries.length} selected · choose 2 to 10. Use the chart to compare saved snapshots over time.`;
    };
    const chartMarkup=(histories)=>{
      const aligned=globalThis.compareAnalysis.alignCountryHistories(histories);
      if(!aligned.times.length)return '<div class="compare-country-chart-empty">No comparable saved country snapshots are available for this Act yet.</div>';
      const width=900,height=300,left=54,right=24,top=20,bottom=42,values=aligned.series.flatMap(series=>series.values.filter(value=>value!=null)),max=Math.max(1,...values),x=index=>left+(width-left-right)*index/Math.max(1,aligned.times.length-1),y=value=>top+(height-top-bottom)*(1-value/max),colors=['#71d3ca','#83b5ff','#f3ca72','#d39beb','#ff8d99','#9ad68f','#f79c4a','#59c6ef','#c4e06b','#e38bbd'];
      const grid=Array.from({length:5},(_,index)=>{const value=max*(4-index)/4,yy=y(value);return `<line x1="${left}" y1="${yy}" x2="${width-right}" y2="${yy}" stroke="#294157"/><text x="${left-8}" y="${yy+4}" text-anchor="end" fill="#9ab0c5" font-size="11">${n(Math.round(value))}</text>`}).join('');
      const paths=aligned.series.map((series,si)=>{const points=series.values.map((value,index)=>value==null?null:[x(index),y(value)]);let segments=[],segment=[];points.forEach(point=>{if(point)segment.push(point);else if(segment.length){segments.push(segment);segment=[]}});if(segment.length)segments.push(segment);const line=segments.filter(points=>points.length>1).map(points=>`<polyline fill="none" stroke="${colors[si%colors.length]}" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round" points="${points.map(point=>point.join(',')).join(' ')}"><title>${esc(series.label)}</title></polyline>`).join('');const dots=segments.filter(points=>points.length===1).map(points=>`<circle cx="${points[0][0]}" cy="${points[0][1]}" r="4" fill="${colors[si%colors.length]}"><title>${esc(series.label)}</title></circle>`).join('');return line+dots}).join('');
      const ticks=[0,Math.floor((aligned.times.length-1)/2),aligned.times.length-1].filter((value,index,array)=>array.indexOf(value)===index).map(index=>{const anchor=index===0?'start':index===aligned.times.length-1?'end':'middle';return `<text x="${x(index)}" y="${height-12}" text-anchor="${anchor}" fill="#9ab0c5" font-size="11">${esc(new Date(aligned.times[index]).toLocaleDateString())}</text>`}).join('');
      const legend=aligned.series.map((series,index)=>`<span class="compare-country-legend-item"><i style="--series-color:${colors[index%colors.length]}"></i>${esc(series.label)}</span>`).join('');
      return `<div class="compare-country-chart-scroll"><svg viewBox="0 0 ${width} ${height}" role="img" aria-label="Observed country rows across saved snapshots">${grid}${paths}${ticks}</svg></div><div class="compare-country-legend">${legend}</div>${aligned.times.length===1?'<p class="compare-muted">Only one complete current snapshot is available, so each selected country is shown as a point without a trend line.</p>':''}`;
    };
    const renderResults=async()=>{
      const token=++renderToken,selected=list.filter(row=>selectedCountries.includes(key(row))),target=root.querySelector('#compareCountryResults');
      if(selected.length<2){target.innerHTML='<p class="compare-muted" role="status">Select at least two countries to compare.</p>';return}
      target.innerHTML='<p class="compare-muted" role="status">Loading saved country trends…</p>';
      const histories=[];
      if(currentAct){
        try{histories.push(...await Promise.all(selected.map(async row=>({label:title(row),rows:await api('country-history?region=all&country='+encodeURIComponent(key(row)))}))))}catch(error){if(token===renderToken)target.innerHTML='<p class="compare-error" role="alert">Country trend could not be loaded: '+esc(error.message)+'</p>';return}
      }
      if(token!==renderToken||!target.isConnected)return;
      const tableRows=selected.map(row=>{
        const history=histories.find(item=>item.label===title(row))?.rows||[],valid=history.filter(item=>Number.isFinite(Date.parse(item.completed_at||item.source_updated_at||item.observed_at))&&Number.isFinite(Number(item.players))).sort((a,b)=>Date.parse(a.completed_at||a.source_updated_at||a.observed_at)-Date.parse(b.completed_at||b.source_updated_at||b.observed_at)),firstPoint=valid[0],lastPoint=valid.at(-1),delta=firstPoint&&lastPoint?Number(lastPoint.players)-Number(firstPoint.players):null;
        return `<tr><th scope="row">${esc(title(row))}</th><td>${n(row.players)}</td><td>${n(row.radiant_confirmed??row.radiant)}</td><td>${n(row.immortal_all??row.immortal)}</td><td>${n(row.median_rr)}</td><td>${delta==null?'—':`${delta>0?'+':''}${n(delta)}`}</td></tr>`;
      }).join('');
      const selectedActInfo=acts.find(item=>item.act===selectedAct),trend=currentAct?'<section class="compare-country-trend"><h3>Country trends</h3>'+chartMarkup(histories)+'<p class="compare-muted">Observed rows by saved snapshot. The change reflects source labels and coverage; it does not prove player movement.</p></section>':'<p class="compare-muted">Archived Acts contain one published snapshot, so a within-Act trend is unavailable.</p>';
      target.innerHTML='<div class="compare-country-result-heading"><div><p class="compare-kicker">'+(selectedActInfo?.current_act?'SAVED SNAPSHOTS':'ARCHIVED ACT')+'</p><h3>'+selected.length+' countries · '+esc(selectedActInfo?.label||'selected Act')+'</h3></div><span class="compare-basis">Global ladder</span></div><div class="compare-country-table-wrap"><table><thead><tr><th>Country</th><th>Observed rows</th><th>Radiant</th><th>Immortal total</th><th>Median RR</th><th>Saved-history change</th></tr></thead><tbody>'+tableRows+'</tbody></table></div>'+trend+'<p class="compare-muted">Tier totals retain source semantics. Combined Immortal labels and threshold estimates are not silently treated as reported subtiers.</p>';
    };
    root.onclick=event=>{
      const option=event.target.closest('[data-country]'),remove=event.target.closest('[data-remove-country]');
      if(!option&&!remove)return;
      const code=option?.dataset.country||remove.dataset.removeCountry;
      if(selectedCountries.includes(code))selectedCountries=selectedCountries.filter(value=>value!==code);
      else if(selectedCountries.length<10)selectedCountries=[...selectedCountries,code];
      else{root.querySelector('#compareCountryHelp').textContent='The comparison is limited to 10 countries so the chart stays readable.';return}
      selectedCountries=globalThis.compareAnalysis.normalizeCountrySelection(selectedCountries,available);
      first=selectedCountries[0]||'';second=selectedCountries[1]||'';queryState();paintPicker();renderResults();
    };
    root.oninput=event=>{if(event.target.id==='compareCountrySearch')paintPicker()};
    paintPicker();renderResults();
  };
  const renderRegions=async()=>{
    const rows=await api('regions?country=all');
    const list=(rows||[]).filter(row=>row.region!=null);
    const key=row=>String(row.region);
    const title=row=>regionsByCode[key(row)]||key(row);
    if(!list.length){finish('<section class="compare-panel"><h2>Compare regions</h2></section>','<section class="compare-panel"><p>No region summaries are available for this Act.</p></section>');return;}
    if(!list.some(r=>key(r)===first))first=key(list.find(r=>key(r)==='ap')||list[0]);
    if(!list.some(r=>key(r)===second)||second===first)second=key(list.find(r=>key(r)==='eu'&&key(r)!==first)||list.find(r=>key(r)!==first)||list[0]);
    const a=list.find(r=>key(r)===first)||list[0],b=list.find(r=>key(r)===second)||list[0];
    const selected=acts.find(item=>item.act===selectedAct);
    const basis=selected?.current_act?'Current Act global source partition':'Archived per-region ladders';
    const selector='<section class="compare-panel"><div class="compare-section-heading"><div><p class="compare-kicker">MATCHED ACT</p><h2>Compare regions</h2></div><span class="compare-basis">'+esc(basis)+'</span></div><div class="compare-selectors">'+
      selectMarkup('Region A','compareA',list,first,title,key)+selectMarkup('Region B','compareB',list,second,title,key)+'</div><p class="compare-muted">Both regions come from the same selected Act. Archived values are taken from each region’s own completed source ladder.</p></section>';
    const totalImmortal=row=>row.immortal_all??row.immortal??null;
    const data='<section class="compare-panel"><div class="compare-metrics">'+
      metricCard('Observed rows · '+title(a),n(a.players),'Source-reported rows in this region')+
      metricCard('Observed rows · '+title(b),n(b.players),'Source-reported rows in this region')+
      metricCard('Source basis',basis,'Selected Act only')+
      metricCard('Top observed RR',n(a.top_rr)+' / '+n(b.top_rr),'A / B where reported')+'</div>'+
      metrics(title(a),title(b),[
        {label:'Observed player rows',a:a.players,b:b.players},
        {label:'Radiant rows',a:a.radiant_confirmed??a.radiant,b:b.radiant_confirmed??b.radiant},
        {label:'Immortal total · source-reported',a:totalImmortal(a),b:totalImmortal(b)},
        {label:'Unknown country rows',a:a.unknown_country,b:b.unknown_country}
      ])+'<p class="compare-muted">A historic regional comparison uses each region’s own source ladder. It does not combine incomplete sources or infer missing rows.</p></section>';
    const explanation='<section class="compare-panel"><h2>Observed change explanation</h2><p class="compare-muted">The available API does not provide a same-Act regional time series for these two aggregates. No cause or movement attribution is inferred. Use the Historical trends page to compare published Act snapshots.</p><a class="compare-link" href="/trends">Open historical trends →</a></section>';
    finish(selector,data+explanation);
    root.querySelector('#compareA').value=first;root.querySelector('#compareB').value=second;
    root.querySelector('#compareA').addEventListener('change',event=>{first=event.target.value;queryState();refresh();});
    root.querySelector('#compareB').addEventListener('change',event=>{second=event.target.value;queryState();refresh();});
  };
  const playerInput=(side,value)=>'<label class="compare-field">Player '+side.toUpperCase()+'<input type="search" id="player'+side.toUpperCase()+'" list="playerOptions'+side.toUpperCase()+'" maxlength="120" placeholder="Riot name#tag" value="'+esc(value)+'" autocomplete="off"><datalist id="playerOptions'+side.toUpperCase()+'"></datalist></label>';
  const exactPlayer=(rows,value)=>rows.find(row=>String(row.display_name||'').toLocaleLowerCase()===String(value).trim().toLocaleLowerCase());
  const renderPlayers=async()=>{
    const selected=acts.find(item=>item.act===selectedAct);
    const selector='<section class="compare-panel"><div class="compare-section-heading"><div><p class="compare-kicker">EXACT IDENTITY</p><h2>Compare players</h2></div><span class="compare-basis">'+(selected?.current_act?'Current Act':'Archived Act')+' · same selected snapshot</span></div><div class="compare-selectors">'+playerInput('a',first)+playerInput('b',second)+'</div><p class="compare-muted">Search by exact Riot ID and choose a result. Name or tag changes are not linked to a stable account identity.</p></section>';
    finish(selector,'<section id="playerCompareResults" class="compare-panel"><p class="compare-muted">Enter a Riot ID in both fields to compare the selected snapshot.</p></section>');
    for(const side of ['a','b']){
      const input=root.querySelector('#player'+side.toUpperCase());
      input.addEventListener('input',()=>{
        if(side==='a')first=input.value;else second=input.value;
        clearTimeout(timer);timer=setTimeout(()=>lookupPlayer(side,input.value),350);
      });
      input.addEventListener('change',()=>{if(side==='a')first=input.value;else second=input.value;queryState();lookupPlayer(side,input.value);});
    }
    if(first)await lookupPlayer('a',first);
    if(second)await lookupPlayer('b',second);
  };
  const lookupPlayer=async(side,value)=>{
    const target=root.querySelector('#playerCompareResults');
    if(!value.trim()){lookup[side]=[];return;}
    try{
      const rows=await api('players?region=all&country=all&search='+encodeURIComponent(value.trim())+'&page=1&size=50&sort=globalRank&direction=asc');
      lookup[side]=rows.items||[];
      const list=root.querySelector('#playerOptions'+side.toUpperCase());
      if(list)list.innerHTML=lookup[side].map(row=>'<option value="'+esc(row.display_name)+'"></option>').join('');
      const match=exactPlayer(lookup[side],value);
      if(match&&lookup.a.length&&lookup.b.length)await showPlayerComparison(exactPlayer(lookup.a,first),exactPlayer(lookup.b,second));
      else if(target)target.innerHTML='<p class="compare-muted">Choose an exact Riot ID from the suggestions to load source-matched details.</p>';
    }catch(error){if(target)target.innerHTML='<p class="compare-error" role="alert">'+esc(error.message)+'</p>';}
  };
  const showPlayerComparison=async(a,b)=>{
    const target=root.querySelector('#playerCompareResults');
    if(!target||!a||!b)return;
    const ha=await api('history?player='+encodeURIComponent(a.display_name)+'&region='+encodeURIComponent(a.region));
    const hb=await api('history?player='+encodeURIComponent(b.display_name)+'&region='+encodeURIComponent(b.region));
    const windowA=globalThis.compareAnalysis.summarizeWindow(ha.filter(row=>!String(row.source||'').toLowerCase().includes('legacy')),minutes,['rr','global_rank','regional_rank']);
    const windowB=globalThis.compareAnalysis.summarizeWindow(hb.filter(row=>!String(row.source||'').toLowerCase().includes('legacy')),minutes,['rr','global_rank','regional_rank']);
    const definitions=[
      {label:'RR',a:a.rr,b:b.rr},
      {label:'Global rank · lower is better',a:a.global_rank,b:b.global_rank},
      {label:'Regional rank · separate regional scopes',a:a.regional_rank,b:b.regional_rank},
      {label:'Reported tier',a:a.tier,b:b.tier,format:value=>String(value??'—')},
      {label:'Wins',a:a.wins,b:b.wins},
      {label:'Country label',a:countriesByCode[a.country]||a.country,b:countriesByCode[b.country]||b.country,format:value=>String(value??'Unknown')}
    ];
    let trend='<section class="compare-panel"><h3>Change within selected window</h3><label class="compare-window">Time window<select id="compareWindow"><option value="60" '+(minutes===60?'selected':'')+'>1 hour</option><option value="360" '+(minutes===360?'selected':'')+'>6 hours</option><option value="1440" '+(minutes===1440?'selected':'')+'>24 hours</option></select></label>';
    trend+=historyText(a.display_name,windowA,'rr')+historyText(b.display_name,windowB,'rr');
    trend+='<p class="compare-muted">Only exact Riot ID matches, non-carried-forward rows and unambiguous observations are used. This shows observed RR change, not why it changed. Rank movement is based on complete stored snapshots only.</p></section>';
    target.innerHTML='<div class="compare-metrics">'+metricCard('Player A · '+a.display_name,n(a.rr)+' RR',regionsByCode[a.region]||a.region)+metricCard('Player B · '+b.display_name,n(b.rr)+' RR',regionsByCode[b.region]||b.region)+metricCard('Act',selected?.label||'Current Act','Same selected Act')+metricCard('Source updated',date(a.source_updated_at)+' / '+date(b.source_updated_at),'A / B')+'</div>'+metrics(a.display_name,b.display_name,definitions)+trend;
    root.querySelector('#compareWindow')?.addEventListener('change',event=>{minutes=Number(event.target.value);queryState();showPlayerComparison(a,b);});
  };
  globalThis.refreshComparePage=refresh;
})();
