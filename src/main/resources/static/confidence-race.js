function renderConfidence(status,coverage){
  let panel=document.getElementById('dataConfidence');
  if(!panel){panel=document.createElement('details');panel.id='dataConfidence';panel.className='panel confidence-panel';document.getElementById('masthead').after(panel);}
  if(status.archiveOnly){const p=status.archiveProgress||{},sources=status.runs||[];panel.innerHTML=`<summary><span class="status-dot"></span>Data confidence · ${Number(p.completedSources||0)===Number(p.totalSources||7)?'Archive complete':'Archive collecting'} <span class="collector-summary">${fmt(p.completedSources||0)}/${fmt(p.totalSources||7)} source ladders · ${fmt(p.pages||0)} pages</span></summary><div class="confidence-facts"><div><small>Archived global players</small><strong>${fmt(p.players||0)}</strong></div><div><small>Source rows</small><strong>${fmt(p.sourceRows||0)}</strong></div><div><small>Last page saved</small><strong>${esc(time(p.lastPageFetchedAt))}</strong></div><div><small>Tracker source time</small><strong>${esc(time(p.sourceUpdatedAt))}</strong></div></div><p class="subtle">This is a one-time archive of leaderboard snapshots. Each act includes a Global ladder and six regional ladders. Progress is saved page by page and resumes after restart. The current-act extraction runs independently and keeps priority. A single archived snapshot does not contain hourly movement, match totals, or daily Radiant cutoffs.</p>${sources.map(s=>`<p class="subtle">${esc(s.source_region)} · ${esc(s.status)} · page ${fmt(s.pages||0)} saved · next page ${fmt(s.next_page||1)}${s.error?` · ${esc(s.error)}`:''}${s.retry_after?` · retry after ${esc(time(s.retry_after))}`:''}</p>`).join('')}`;return;}
  const last=status.latestSnapshot?.[0],run=status.runs?.[0],schedule=status.schedule||{},live=status.liveProgress||{};
  const age=last?Math.max(0,(Date.now()-new Date(last.completed_at))/60000):null;
  const state=!last?'Awaiting data':status.lastError?'Update failed':age>40?'Delayed':status.running?'Updating':'Up to date';
  const percent=(n,d)=>d?`${(100*Number(n||0)/Number(d)).toFixed(1)}%`:'—';
  const c=coverage||{},next=schedule.nextRunAt;
  const remaining=next?Math.max(0,Math.ceil((new Date(next)-Date.now())/1000)):null;
  panel.innerHTML=`<summary><span class="status-dot"></span>Data confidence · ${esc(state)} <span class="collector-summary">${status.running?`Live: ${esc(live.source||'starting')} · page ${esc(live.sourcePage||'—')}`:next?`Next trigger ${time(next)}`:'Not scheduled'}</span></summary>
    <div class="confidence-facts"><div><small>Published snapshot</small><strong>${last?time(last.completed_at):'Awaiting extraction'}</strong></div><div><small>Source observations</small><strong>${c.source_from?`${time(c.source_from)} – ${time(c.source_to)}`:'—'}</strong></div><div><small>Reported countries</small><strong>${percent(c.known_country,c.players)}</strong></div><div><small>Carried / conflicting</small><strong>${percent(c.carried,c.players)} / ${percent(c.conflicting,c.players)}</strong></div></div>
    <div class="collector-live"><p>Next scheduled trigger: ${next?time(next):'Not scheduled'}${remaining!==null?` · ${Math.floor(remaining/60)}m ${remaining%60}s`:''}</p><p>Source: ${esc(live.source||'—')} · page ${esc(live.sourcePage||'—')} · ${fmt(live.pagesFetched||0)} pages processed. Status refreshes every 10 seconds while this page is visible.</p></div>
    ${schedule.pendingRun?'<p><strong>Next extraction queued.</strong> It will start immediately after the current extraction finishes. Only one pending run is kept.</p>':''}
    ${status.lastError?`<p>${esc(status.lastError)}</p>`:''}
    <p class="subtle">The next trigger is not a completion estimate. A trigger during extraction queues one run; it starts as soon as the active extraction finishes. Last automatic trigger: ${esc(schedule.lastTriggerOutcome||'not triggered')}. Status is uncached.</p>
    <p class="subtle">Time filters end at the published snapshot. Collection spans multiple source observation times; carried records and conflicting observations can limit movement coverage. Reported-country coverage is not statistical confidence.</p>`;
}

function renderRadiantRace(race,prefix){
  let box=document.getElementById(prefix+'Race');const chart=document.getElementById(prefix+'Chart');
  if(!box){box=document.createElement('div');box.id=prefix+'Race';box.className='radiant-race';chart.before(box);}
  const rows=race.points||[],last=rows.at(-1),comparable=rows.filter(p=>p.rr!=null&&p.cutoff!=null),first=comparable[0];
  if(!last||last.completed_at!==data.status.latestSnapshot?.[0]?.completed_at){box.innerHTML='<p class="subtle">Radiant race is unavailable for this snapshot.</p>';return false;}
  const gap=last.rr!=null&&last.cutoff!=null?last.rr-last.cutoff:null;
  const closed=gap!=null&&first?gap-(first.rr-first.cutoff):null;
  const gapLabel=gap==null?'Unavailable':gap===0?'At observed boundary':`${fmt(Math.abs(gap))} RR ${gap>0?'above':'below'}`;
  box.innerHTML=`<h3>Radiant race</h3><div class="race-metrics"><div><small>Observed regional boundary</small><strong>${last.cutoff==null?'Unavailable':fmt(last.cutoff)+' RR'}</strong></div><div><small>Player position</small><strong>${gapLabel}</strong></div><div><small>Relative progress</small><strong>${comparable.length<2||closed==null?'More observations needed':closed===0?'Gap unchanged':`${fmt(Math.abs(closed))} RR ${closed>0?'gained on':'lost to'} boundary`}</strong></div></div><p class="subtle">Player RR · Observed Radiant boundary (dashed)</p><p class="subtle">${esc(race.definition||'The boundary uses usable observed Radiant accounts, not official promotion eligibility.')} Missing or conflicting observations and gaps over 45 minutes are not connected.</p>`;
  chart.classList.remove('hidden');chart.setAttribute('aria-label','Player RR and observed regional Radiant boundary by completed snapshot');
  drawLines(rows,prefix+'Chart',[{key:'rr',color:'#73c9c4'},{key:'cutoff',color:'#f2d589',dashed:true}],'completed_at',true);return true;
}
