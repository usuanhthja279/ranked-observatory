'use strict';

(function(){
  const root=document.getElementById('comparePage');
  if(!root)return;
  const esc=value=>String(value??'—').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
  const n=value=>value==null?'—':Number(value).toLocaleString();
  const date=value=>value?new Date(value).toLocaleString():'—';
  const params=new URLSearchParams(location.search);
  let mode=['country','region','player'].includes(params.get('mode'))?params.get('mode'):'country';
  let first=params.get('a')||'',second=params.get('b')||'',minutes=[60,360,1440].includes(Number(params.get('window')))?Number(params.get('window')):60;
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
    const a=list.find(r=>key(r)===first)||list[0],b=list.find(r=>key(r)===second)||list[0];
    const currentAct=acts.find(item=>item.act===selectedAct)?.current_act;
    const windowControl=currentAct?'<label class="compare-window">Change window<select id="compareWindow"><option value="60" '+(minutes===60?'selected':'')+'>1 hour</option><option value="360" '+(minutes===360?'selected':'')+'>6 hours</option><option value="1440" '+(minutes===1440?'selected':'')+'>24 hours</option></select></label>':'';
    const selector='<section class="compare-panel"><div class="compare-section-heading"><div><p class="compare-kicker">MATCHED SNAPSHOT</p><h2>Compare countries</h2></div><span class="compare-basis">Same Act · all regions · global source</span></div><div class="compare-selectors">'+
      selectMarkup('Country A','compareA',list,first,title,key)+selectMarkup('Country B','compareB',list,second,title,key)+windowControl+'</div><p class="compare-muted">Both sides use the same Act and global source scope. Counts are observed source rows; a country is a source label, not a verified player population.</p></section>';
    const totalImmortal=row=>row.immortal_all??row.immortal??null;
    const data='<section class="compare-panel"><div class="compare-metrics">'+
      metricCard('Observed rows · '+title(a),n(a.players),'Global source rows for this Act')+
      metricCard('Observed rows · '+title(b),n(b.players),'Global source rows for this Act')+
      metricCard('Source basis','Global ladder','Same selected Act and scope')+
      metricCard('Publication time',date(a.source_updated_at||b.source_updated_at),'Time recorded by the source')+'</div>'+
      metrics(title(a),title(b),[
        {label:'Observed player rows',a:a.players,b:b.players},
        {label:'Radiant rows',a:a.radiant_confirmed??a.radiant,b:b.radiant_confirmed??b.radiant},
        {label:'Immortal total · source-reported',a:totalImmortal(a),b:totalImmortal(b)},
        {label:'Median observed RR',a:a.median_rr,b:b.median_rr}
      ])+'<p class="compare-muted">Tier totals retain source semantics. Combined Immortal labels and threshold estimates are not silently treated as reported subtiers.</p></section>';
    let explanation='<section class="compare-panel"><h2>Observed change explanation</h2>';
    const selected=acts.find(item=>item.act===selectedAct);
    if(!selected||selected.current_act){
      const [ha,hb]=await Promise.all([
        api('country-history?region=all&country='+encodeURIComponent(first)),
        api('country-history?region=all&country='+encodeURIComponent(second))
      ]);
      const ca=globalThis.compareAnalysis.summarizeWindow(ha,minutes,['players']);
      const cb=globalThis.compareAnalysis.summarizeWindow(hb,minutes,['players']);
      explanation+='<p class="compare-muted">This describes the change in rows carrying each country label across completed snapshots. It cannot attribute the change to player movement, a changed country label, or collection coverage.</p>'+
        historyText(title(a),ca,'players')+historyText(title(b),cb,'players');
    }else{
      explanation+='<p class="compare-muted">Archived Acts contain a single published source snapshot. There is no within-Act time series to explain country count changes.</p>';
    }
    explanation+='</section>';
    finish(selector,data+explanation);
    root.querySelector('#compareA').value=first;root.querySelector('#compareB').value=second;
    root.querySelector('#compareA').addEventListener('change',event=>{first=event.target.value;queryState();refresh();});
    root.querySelector('#compareB').addEventListener('change',event=>{second=event.target.value;queryState();refresh();});
    root.querySelector('#compareWindow')?.addEventListener('change',event=>{minutes=Number(event.target.value);queryState();refresh();});
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
