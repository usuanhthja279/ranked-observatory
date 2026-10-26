'use strict';
function boundaryCountCell(c,field,boundary){
 const summary=boundary?.summary,number=Number(c[field]||0);
 if(!summary||Number(summary.eligible_pairs)===0)return '<span class="subtle" title="No eligible comparable scan pair in this window">—</span>';
 if(field==='possible_demotions'&&Number(summary.demotion_eligible_pairs)===0)return '<span class="subtle" title="No full regional reconciliation in the selected window">Pending</span>';
 if(number===0)return '<span class="boundary-count boundary-count-zero" title="No eligible players crossed this boundary in the selected time range">0</span>';
 return `<button class="boundary-count ${field==='possible_promotions'?'positive':'negative'}" data-boundary-country="${esc(c.country)}" data-boundary-signal="${field==='possible_promotions'?'possible_promotion':'possible_demotion'}" aria-label="Show ${esc(field.replaceAll('_',' '))} for ${esc(countryName(c.country))}">${fmt(c[field]||0)}</button>`;
}
function renderCountryBoundaryColumns(boundary){
 let box=document.getElementById('countryBoundaryColumns');if(!box){box=document.createElement('div');box.id='countryBoundaryColumns';box.className='scroll';document.getElementById('countryMovementNote').after(box);}
 const c={country,possible_promotions:0,possible_demotions:0,...boundary?.countries.find(p=>p.country===country)};
 box.innerHTML=`<table><thead><tr><th>Possible promotions · Ascendant 3 → Immortal 1</th><th>Possible demotions · Immortal 1 → Ascendant 3</th></tr></thead><tbody><tr><td>${boundaryCountCell(c,'possible_promotions',boundary)}</td><td>${boundaryCountCell(c,'possible_demotions',boundary)}</td></tr></tbody></table><p class="subtle">Distinct inferred players in the selected time range. These columns are separate from confirmed movement. Click a positive count to open its player list. Zero means no possible crossings in this window; Pending means no eligible full regional scan.</p>`;
 box.onclick=e=>{const b=e.target.closest('[data-boundary-signal]');if(b)openBoundaryPlayers(b.dataset.boundaryCountry,b.dataset.boundarySignal);};
}

let boundaryDialog=null,boundaryDialogToken=0;
async function openBoundaryPlayers(selectedCountry,signal,page=1){
 if(!boundaryDialog){boundaryDialog=document.createElement('dialog');boundaryDialog.id='boundaryPlayersDialog';boundaryDialog.setAttribute('aria-labelledby','boundaryPlayersTitle');document.body.append(boundaryDialog);}
 const token=++boundaryDialogToken,title=(signal==='possible_promotion'?'Possible promotions':'Possible demotions')+' · '+countryName(selectedCountry);
 boundaryDialog.innerHTML=`<div class="chart-heading"><h2 id="boundaryPlayersTitle">${esc(title)}</h2><button class="icon-button" aria-label="Close boundary player list" data-boundary-close>×</button></div><p class="subtle" role="status">Loading players…</p>`;
 boundaryDialog.onclick=e=>{if(e.target.closest('[data-boundary-close]')){boundaryDialogToken++;boundaryDialog.close();}};
 if(!boundaryDialog.open)boundaryDialog.showModal();
 try{const result=await api('movement-boundary-signals?'+new URLSearchParams({region,country:selectedCountry,minutes:activityMinutes,signal,page}));
  if(token!==boundaryDialogToken||!boundaryDialog.open)return;
  if(result.summary.snapshot_id!==data.status.latestSnapshot[0]?.id)throw Error('Snapshot changed. Refresh the movement data before opening this list.');
  const total=Number(result.summary.total),pages=Math.max(1,Math.ceil(total/result.size));
  boundaryDialog.innerHTML=`<div class="chart-heading"><h2 id="boundaryPlayersTitle">${esc(title)}</h2><button class="icon-button" aria-label="Close boundary player list" data-boundary-close>×</button></div><p class="subtle">${result.minutes} min ending ${esc(time(result.summary.window_end))} · ${fmt(total)} distinct players · observed RR shown, not an inferred Ascendant rating</p><p class="subtle">${esc(result.definition)}</p><div class="scroll"><table><thead><tr><th>Player</th><th>Observed RR</th><th>Signal observed</th></tr></thead><tbody>${result.items.map(p=>`<tr><td><button data-boundary-player="${esc(p.player_key)}" data-region="${esc(p.region)}">${playerIdentity(p.display_name)}</button></td><td>${fmt(p.rr)}</td><td>${esc(time(p.event_at))}</td></tr>`).join('')||'<tr><td colspan="3">No eligible players in this window.</td></tr>'}</tbody></table></div><nav class="pagination" aria-label="Boundary signal pages"><span>Page ${page} of ${pages} · ${fmt(total)} players</span><button data-boundary-page="${page-1}" ${page<=1?'disabled':''}>Previous</button><button data-boundary-page="${page+1}" ${page>=pages?'disabled':''}>Next</button></nav>`;
  boundaryDialog.onclick=e=>{if(e.target.closest('[data-boundary-close]')){boundaryDialogToken++;boundaryDialog.close();return;}const pager=e.target.closest('[data-boundary-page]');if(pager){openBoundaryPlayers(selectedCountry,signal,Number(pager.dataset.boundaryPage));return;}const player=e.target.closest('[data-boundary-player]');if(player){boundaryDialog.close();showSelectedPlayer(player.dataset.boundaryPlayer,player.dataset.region,false,true,view==='world');}};
 }catch(error){if(token===boundaryDialogToken){const note=boundaryDialog.querySelector('[role=status]');if(note)note.textContent=error.message;}}
}

let collectorPollBusy=false;
async function pollCollectorStatus(){
 if(collectorPollBusy||!data)return;collectorPollBusy=true;
 try{if(document.hidden)return;const status=await api('status?compact=true');if(status.latestSnapshot[0]?.id!==data.status.latestSnapshot[0]?.id){await refresh();return;}data.status={...data.status,...status};renderConfidence(data.status,data.confidence);}catch(ignored){}finally{collectorPollBusy=false;}
}
setInterval(pollCollectorStatus,10000);
