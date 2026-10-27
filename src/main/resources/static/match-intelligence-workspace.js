(() => {
  'use strict';
  const same = (a, b) => String(a || '').trim().toLocaleLowerCase() === String(b || '').trim().toLocaleLowerCase();
  const finite = value => typeof value === 'number' && Number.isFinite(value) ? value : null;
  const sum = values => values.reduce((total, value) => total + (finite(value) ?? 0), 0);
  function summarize(matches, details) {
    const rows = matches.map(row => ({ row, view: details.get(row.providerMatchId) })).filter(item => item.view?.match);
    const outcomes = rows.map(({ view }) => {
      const score = view.match.teams.find(team => team.teamId === view.perspective.teamId)?.roundsWon;
      const other = view.match.teams.find(team => team.teamId !== view.perspective.teamId)?.roundsWon;
      return Number.isFinite(score) && Number.isFinite(other) ? (score > other ? 'win' : score < other ? 'loss' : 'draw') : 'unknown';
    });
    const wins = outcomes.filter(x => x === 'win').length, losses = outcomes.filter(x => x === 'loss').length;
    const stats = rows.map(({ view }) => view.match.participants.find(p => p.identity?.stableKey === view.perspective.identity || same(p.identity?.observedRiotId, view.perspective.riotId))?.totals).filter(Boolean);
    const available = key => stats.filter(s => finite(s[key]) !== null).map(s => s[key]);
    const maps = new Map(), agents = new Map(); let pistolRounds=0,pistolWins=0,economyRows=0,spentTotal=0,spentCount=0;
    for (const { view } of rows) {
      const match = view.match;
      if (match.mapName) maps.set(match.mapName, (maps.get(match.mapName) || 0) + 1);
      const player = match.participants.find(p => p.identity?.stableKey === view.perspective.identity || same(p.identity?.observedRiotId, view.perspective.riotId));
      if (player?.agent) agents.set(player.agent, (agents.get(player.agent) || 0) + 1);
      const mode=String(match.modeName||'').toLowerCase();
      if (/(competitive|unrated|premier)/.test(mode)) { const pistols=match.rounds.filter(r=>[1,13].includes(r.roundNumber)); pistolRounds+=pistols.length; pistolWins+=pistols.filter(r=>r.winningTeamId===view.perspective.teamId).length; }
      for (const r of match.playerRounds.filter(x=>x.identity?.stableKey===view.perspective.identity)) { if (finite(r.loadoutValue)!==null||finite(r.spentCredits)!==null||finite(r.remainingCredits)!==null) economyRows++; if(finite(r.spentCredits)!==null){spentTotal+=r.spentCredits;spentCount++;} }
    }
    return { sample: rows.length, wins, losses, unknownOutcomes: outcomes.filter(x => x === 'unknown' || x === 'draw').length,
      kills: available('kills').length ? sum(available('kills')) : null,
      deaths: available('deaths').length ? sum(available('deaths')) : null,
      assists: available('assists').length ? sum(available('assists')) : null,
      averageAcs: available('acs').length ? sum(available('acs')) / available('acs').length : null,
      averageAdr: available('adr').length ? sum(available('adr')) / available('adr').length : null,
      firstKills: available('firstKills').length ? sum(available('firstKills')) : null,
      firstDeaths: available('firstDeaths').length ? sum(available('firstDeaths')) : null,
      pistolRounds, pistolWins, economyRows, averageSpend:spentCount?spentTotal/spentCount:null,
      maps: [...maps].sort((a,b) => b[1]-a[1]), agents: [...agents].sort((a,b) => b[1]-a[1]) };
  }
  function filterRounds(rounds, selectedTeam, mode, selectedPlayerRounds = []) {
    const playerRows = new Map(selectedPlayerRounds.map(row => [row.roundNumber, row]));
    return rounds.filter(round => {
      const won = round.winningTeamId === selectedTeam;
      const ownRound = playerRows.get(round.roundNumber);
      if (mode === 'wins' && !won || mode === 'losses' && won) return false;
      if (mode === 'pistols' && ![1, 13].includes(round.roundNumber)) return false;
      if (mode === 'overtime' && !round.overtimeByRoundPosition) return false;
      if (mode === 'attack' && String(ownRound?.side||'').toUpperCase() !== 'ATTACK' || mode === 'defense' && String(ownRound?.side||'').toUpperCase() !== 'DEFENSE') return false;
      return true;
    });
  }
  const api = { summarize, filterRounds };
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
  if (typeof window === 'undefined' || typeof document === 'undefined') return;
  const style = document.createElement('link'); style.rel='stylesheet'; style.href='/match-analyzer.css?v=4'; document.head.append(style);
  const params = new URLSearchParams(location.search), profilePlayer = params.get('player');
  const host = profilePlayer ? document.getElementById('playerProfileWorkspace') : document.getElementById('matchAnalysisPageWorkspace');
  if (!host) return;
  const root = document.createElement('section'); root.className = 'mi-workspace'; root.setAttribute('aria-labelledby', 'miTitle');
  root.innerHTML = `<div class="mi-head"><div><p class="mi-kicker">SAVED MATCH INTELLIGENCE</p><h2 id="miTitle">${profilePlayer ? 'Player match intelligence' : 'Saved matches'}</h2><p class="subtle">Owner-private match imports. Observed source data and calculated summaries are labeled separately.</p></div><button type="button" data-mi-refresh>Refresh matches</button></div><p class="mi-status" role="status" aria-live="polite">Loading your saved matches…</p><div data-mi-content hidden></div>`;
  host.append(root);
  const status = root.querySelector('.mi-status'), content = root.querySelector('[data-mi-content]');
  let pageRows = [], selected = null, detailCache = new Map(), activeTab = 'overview', roundMode = 'all', selectedRound = null, generation = 0;
  const esc = value => String(value ?? '—').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
  const val = value => value === null || value === undefined ? 'Not reported' : esc(value);
  const duration = ms => Number.isFinite(ms) ? Math.floor(ms/60000) + 'm ' + Math.floor(ms%60000/1000) + 's' : 'Not reported';
  const playerFor = view => view.match.participants.find(p => p.identity?.stableKey === view.perspective.identity || same(p.identity?.observedRiotId, view.perspective.riotId));
  async function getJson(url) {
    const response = await fetch(url, { credentials: 'same-origin', headers: { Accept:'application/json' } });
    if (response.status === 401 || response.status === 403) throw new Error('Sign in to view matches saved to your Observatory account.');
    if (!response.ok) { let message = 'Saved match data could not be loaded.'; try { message=(await response.json()).message||message; } catch {} throw new Error(message); }
    const type = response.headers.get('content-type') || ''; if (!type.includes('application/json')) throw new Error('The match API returned an unexpected response.');
    return response.json();
  }
  async function load() {
    const current = ++generation; status.textContent = 'Loading saved matches…'; content.hidden = true;
    try {
      const path = profilePlayer ? '/api/match-intelligence/players/' + encodeURIComponent(profilePlayer) + '/matches?size=20' : '/api/match-intelligence/matches?size=20';
      const page = await getJson(path); if (current !== generation) return;
      pageRows = Array.isArray(page.matches) ? page.matches : [];
      if (!pageRows.length) { status.textContent = 'No saved match imports are available for this account and player yet.'; return; }
      status.textContent = 'Loading verified match summaries…';
      const queue = [...pageRows];
      await Promise.all(Array.from({length:Math.min(4,queue.length)}, async () => {
        while (queue.length) {
          const row = queue.shift();
          try { detailCache.set(row.providerMatchId, await getJson('/api/match-intelligence/matches/' + encodeURIComponent(row.providerMatchId))); }
          catch (error) { detailCache.set(row.providerMatchId, { loadError:String(error.message || error) }); }
        }
      }));
      if (current !== generation) return;
      renderList(); status.textContent = page.total + ' authorized match(es) · latest ' + pageRows.length + ' shown. Summary figures use stored source records.'; content.hidden=false;
      if (selected && detailCache.has(selected.providerMatchId)) renderDetail(detailCache.get(selected.providerMatchId));
    } catch (error) { if (current === generation) { status.textContent=String(error.message||error); content.hidden=true; } }
  }
  function renderList() {
    const s=summarize(pageRows,detailCache), sampleNote=s.sample<5?'Small sample · fewer than 5 matches are available.':'Based on '+s.sample+' saved matches.';
    const rows=pageRows.map(row=>`<button type="button" class="mi-match${selected?.providerMatchId===row.providerMatchId?' is-selected':''}" data-mi-match="${esc(row.providerMatchId)}"><strong>${esc(row.summary?.map||'Map not reported')} · ${esc(row.summary?.mode||'Mode not reported')}</strong><span>${esc(row.selectedPlayer)} · ${esc(row.summary?.startedAt?new Date(row.summary.startedAt).toLocaleString():'Date not reported')} · ${esc(row.quality)}</span><span>Team ${esc(row.selectedTeam||'unknown')} · ${esc(Object.entries(row.summary?.teamScores||{}).map(([team,score])=>team+': '+score).join(' / ')||'score unavailable')}</span></button>`).join('');
    content.innerHTML=`<div class="mi-overview-grid"><article><span>Saved sample</span><strong>${s.sample}</strong><small>${sampleNote}</small></article><article><span>Win / loss</span><strong>${s.wins} / ${s.losses}</strong><small>From selected-team final scores · ${s.unknownOutcomes} unknown</small></article><article><span>Aggregate K / D / A</span><strong>${s.kills??'—'} / ${s.deaths??'—'} / ${s.assists??'—'}</strong><small>Missing source stats are excluded</small></article><article><span>Average ACS · ADR</span><strong>${s.averageAcs===null?'Not reported':s.averageAcs.toFixed(1)} · ${s.averageAdr===null?'Not reported':s.averageAdr.toFixed(1)}</strong><small>Mean of matches reporting each measure</small></article><article><span>Opening K / D</span><strong>${s.firstKills===null?'Not reported':s.firstKills+' / '+(s.firstDeaths??'Not reported')}</strong><small>Source summary only; not yet contextualized</small></article><article><span>Regulation pistol rounds</span><strong>${s.pistolRounds ? `${s.pistolWins} / ${s.pistolRounds} wins` : 'Not reported'}</strong><small>Only rounds 1 and 13 where captured</small></article><article><span>Recorded economy rows</span><strong>${s.economyRows || 'Not reported'}</strong><small>${s.averageSpend===null ? 'Spend not reported' : 'Average recorded spend '+s.averageSpend.toFixed(0)} · buy categories not inferred</small></article><article><span>Maps · agents</span><strong>${[...s.maps.slice(0,2).map(x=>x[0]),...s.agents.slice(0,2).map(x=>x[0])].map(esc).join(' · ') || 'Not reported'}</strong><small>Observed sample usage</small></article></div><div class="mi-split"><section class="mi-match-list" aria-label="Saved matches"><h3>Recent analyzed matches</h3>${rows}</section><section class="mi-detail" data-mi-detail><p class="mi-empty">Choose a match to inspect verified rounds, events, economy fields and coverage.</p></section></div>`;
    content.querySelectorAll('[data-mi-match]').forEach(button=>button.addEventListener('click',()=>{selected=pageRows.find(r=>r.providerMatchId===button.dataset.miMatch)||null;activeTab='overview';roundMode='all';selectedRound=null;renderList();const view=detailCache.get(selected.providerMatchId);if(view)renderDetail(view);}));
  }
  function renderDetail(view) {
    const pane=content.querySelector('[data-mi-detail]'); if(!pane)return;
    if (!view || view.loadError) { pane.innerHTML=`<p class="mi-empty" role="alert">${esc(view?.loadError||'Match details unavailable.')}</p>`; return; }
    const m=view.match, p=playerFor(view), rounds=Array.isArray(m.rounds)?m.rounds:[], score=Object.fromEntries((m.teams||[]).map(t=>[t.teamId,t.roundsWon]));
    const own=score[view.perspective.teamId], opponent=Object.entries(score).find(([team])=>team!==view.perspective.teamId)?.[1];
    const roundRow=(m.playerRounds||[]).find(r=>r.identity?.stableKey===view.perspective.identity);
    const tabs=[['overview','Overview'],['rounds','Rounds'],['timeline','Timeline'],['economy','Economy'],['team','Team analysis'],['coaching','Coaching']];
    let body='';
    if(activeTab==='overview') {
      const roster=(m.participants||[]).map(x=>`<tr><td>${esc(x.identity?.observedRiotId||'Player identity unavailable')}</td><td>Team ${esc(x.teamId)}</td><td>${esc(x.agent||'Not reported')}</td><td>${x.totals?`${val(x.totals.kills)}/${val(x.totals.deaths)}/${val(x.totals.assists)}`:'Not reported'}</td><td>${val(x.totals?.acs)}</td></tr>`).join('');
      body=`<div class="mi-match-hero"><div><h3>${esc(m.mapName||'Map not reported')} · ${esc(m.modeName||'Mode not reported')}</h3><p>${esc(view.perspective.riotId)} · ${esc(p?.agent||'Agent unavailable')} · Team ${esc(view.perspective.teamId)} · ${esc(m.startedAt?new Date(m.startedAt).toLocaleString():'Timestamp not reported')}</p><p>${esc(duration(m.durationMillis))} · ${m.overtime===null?'Overtime unknown':m.overtime?'Overtime':'Regulation'} · ${esc(view.quality)} · ${esc(view.provenance?.captureMethod||'Capture method not reported')}</p></div><strong>${val(own)} – ${val(opponent)}<small>Selected team perspective</small></strong></div><p class="mi-coverage">Coverage: ${Object.entries(m.availability||{}).map(([k,v])=>`${esc(k)} ${v?'observed':'unavailable'}`).join(' · ')||'Not reported'} · ${rounds.length} rounds · ${m.events?.length||0} events · ${m.positions?.length||0} discrete position snapshots</p><h4>Team rosters and agents</h4><div class="mi-table-wrap"><table><thead><tr><th>Player</th><th>Team</th><th>Agent</th><th>K/D/A</th><th>ACS</th></tr></thead><tbody>${roster||'<tr><td colspan="5">Roster not reported.</td></tr>'}</tbody></table></div>`;
    } else if(activeTab==='rounds') {
      const roundRecords=(roundRow?m.playerRounds.filter(r=>r.identity?.stableKey===view.perspective.identity):[]);
      const visible=filterRounds(rounds,view.perspective.teamId,roundMode,roundRecords);
      const selected=visible.find(r=>r.roundNumber===selectedRound)||visible[0]; if(selected&&!visible.some(r=>r.roundNumber===selectedRound))selectedRound=selected.roundNumber;
      const options=[['all','All rounds'],['wins','Team wins'],['losses','Team losses'],['pistols','Pistol rounds'],['overtime','Overtime'],['attack','Attack'],['defense','Defense']];
      const record=roundRecords.find(r=>r.roundNumber===selected?.roundNumber), events=(m.events||[]).filter(e=>e.roundNumber===selected?.roundNumber),positions=(m.positions||[]).filter(e=>e.roundNumber===selected?.roundNumber);
      const eventRows=events.map(e=>`<tr><td>${esc(e.roundTimeRaw===null?'Not reported':e.roundTimeRaw+' source units')}</td><td>${esc(e.category)}</td><td>${esc(e.actor?.observedRiotId||'Unknown')}</td><td>${esc(e.target?.observedRiotId||'Not applicable')}</td><td>${esc(e.weaponName||e.rawSourceType||'Not reported')}</td></tr>`).join('');
      body=`<div class="mi-round-toolbar"><div class="mi-round-strip" role="group" aria-label="Select round">${rounds.map(r=>`<button type="button" data-mi-round="${r.roundNumber}" aria-pressed="${r.roundNumber===selectedRound}">R${r.roundNumber}</button>`).join('')}</div><label>Filter <select data-mi-round-filter>${options.map(([key,label])=>`<option value="${key}"${key===roundMode?' selected':''}>${label}</option>`).join('')}</select></label></div><div class="mi-round-cards">${visible.map(r=>`<button type="button" data-mi-round="${r.roundNumber}" aria-pressed="${r.roundNumber===selectedRound}"><strong>Round ${r.roundNumber}</strong><span>${r.winningTeamId===view.perspective.teamId?'Win':'Loss'} · ${esc(r.rawWinMethod||'Result unknown')}</span></button>`).join('')}</div>${selected?`<div class="mi-round-detail"><h4>Round ${selected.roundNumber} · ${selected.winningTeamId===view.perspective.teamId?'Team win':'Team loss'}</h4><p>Player K/D/A ${record?[record.kills,record.deaths,record.assists].map(val).join('/'):'Not reported'} · Damage ${val(record?.damage)} · side ${val(record?.side)} · score ${val(record?.score)}</p><p>Recorded loadout ${val(record?.loadoutValue)} · spent ${val(record?.spentCredits)} · remaining credits ${val(record?.remainingCredits)}</p><p>Spike evidence: plant ${selected.plantEvidence?'reported':'not reported'} · defuse ${selected.defuseEvidence?'reported':'not reported'} · detonation time ${val(selected.detonationTimeRaw)} source units</p><h4>Round events</h4><div class="mi-table-wrap"><table><thead><tr><th>Time (source units)</th><th>Type</th><th>Actor</th><th>Target</th><th>Weapon / source</th></tr></thead><tbody>${eventRows||'<tr><td colspan="5">No events reported for this round.</td></tr>'}</tbody></table></div><h4>Discrete position snapshots</h4><p>Snapshots are source coordinates, not movement paths. No map plot is available because a validated display transform and matching map art are not currently confirmed.</p><div class="mi-table-wrap"><table><thead><tr><th>Player</th><th>Snapshot</th><th>X</th><th>Y</th><th>Coordinate space</th></tr></thead><tbody>${positions.map(x=>`<tr><td>${esc(x.identity?.observedRiotId||'Unknown')}</td><td>${esc(x.snapshotType)}</td><td>${val(x.x)}</td><td>${val(x.y)}</td><td>${esc(x.coordinateSpace||'Unspecified')}</td></tr>`).join('')||'<tr><td colspan="5">No positions reported.</td></tr>'}</tbody></table></div></div>`:'<p class="mi-empty">No rounds match the selected filter.</p>'}`;
    } else if(activeTab==='timeline') {
      const events=[...(m.events||[])].sort((a,b)=>a.roundNumber-b.roundNumber||((finite(a.roundTimeRaw)??0)-(finite(b.roundTimeRaw)??0)));
      body=`<p>Events are sorted by round then reported raw time. Source time units are not independently verified.</p><div class="mi-table-wrap"><table><thead><tr><th>Round · time</th><th>Class</th><th>Actor</th><th>Target</th><th>Weapon / event</th><th>Assists</th></tr></thead><tbody>${events.map(e=>`<tr class="${e.category==='CREDITED_PLAYER_KILL'?'':'mi-special-event'}"><td>${e.roundNumber} · ${val(e.roundTimeRaw)} source units</td><td>${esc(e.category||'Unknown')}</td><td>${esc(e.actor?.observedRiotId||'Unknown')}</td><td>${esc(e.target?.observedRiotId||'Unknown')}</td><td>${esc(e.weaponName||e.rawSourceType||'Not reported')}</td><td>${Array.isArray(e.assistants)?e.assistants.length:'Not reported'}</td></tr>`).join('')||'<tr><td colspan="6">Event timeline was not captured.</td></tr>'}</tbody></table></div><p>Position snapshots: ${(m.positions||[]).length} reported. No continuous paths are inferred.</p>`;
    } else if(activeTab==='economy') {
      const playerRows=(m.playerRounds||[]).filter(r=>r.identity?.stableKey===view.perspective.identity);
      body=`<p>Only recorded values are shown; no force, bonus or buy category is inferred in M4.</p><div class="mi-table-wrap"><table><thead><tr><th>Round</th><th>Side</th><th>Loadout value</th><th>Spent</th><th>Remaining</th><th>Outcome</th></tr></thead><tbody>${playerRows.map(r=>`<tr><td>${r.roundNumber}</td><td>${val(r.side)}</td><td>${val(r.loadoutValue)}</td><td>${val(r.spentCredits)}</td><td>${val(r.remainingCredits)}</td><td>${rounds.find(x=>x.roundNumber===r.roundNumber)?.winningTeamId===view.perspective.teamId?'Win':'Loss'}</td></tr>`).join('')||'<tr><td colspan="6">Player economy rows were not captured.</td></tr>'}</tbody></table></div><p>Team equipment totals and conversion by economy category are unavailable unless reported by the source.</p>`;
    } else if(activeTab==='team') {
      const teamRounds=(m.rounds||[]), ownWins=teamRounds.filter(r=>r.winningTeamId===view.perspective.teamId).length;
      const attack=(m.playerRounds||[]).filter(r=>r.identity?.stableKey===view.perspective.identity&&String(r.side||'').toUpperCase()==='ATTACK'),defense=(m.playerRounds||[]).filter(r=>r.identity?.stableKey===view.perspective.identity&&String(r.side||'').toUpperCase()==='DEFENSE');const wonRound=new Map(teamRounds.map(r=>[r.roundNumber,r.winningTeamId===view.perspective.teamId]));
      body=`<div class="mi-overview-grid"><article><span>Team round results</span><strong>${ownWins} / ${teamRounds.length}</strong><small>Observed wins / rounds</small></article><article><span>Player on attack</span><strong>${attack.length||'Not reported'}</strong><small>${attack.length?attack.filter(r=>r.teamId===view.perspective.teamId).length+' rows':'Side coverage unavailable'}</small></article><article><span>Player on defense</span><strong>${defense.length||'Not reported'}</strong><small>${defense.length?defense.filter(r=>r.teamId===view.perspective.teamId).length+' rows':'Side coverage unavailable'}</small></article><article><span>Opening conversion</span><strong>Not analyzed</strong><small>Deterministic event analysis arrives in M5</small></article></div><p>Teamwork is not scored from aggregate K/D. Trade timing, opening conversion, objective context and economy-aware coordination are not yet analyzed.</p>`;
    } else {
      body=`<div class="mi-empty"><h4>No coaching claims yet</h4><p>The deterministic, evidence-linked analysis engine is the next phase. This view will only show findings that link to captured rounds and source events.</p><p>Current quality: ${esc(view.quality)} · ${rounds.length} rounds · ${m.events?.length||0} captured events.</p></div>`;
    }
    const source=view.provenance||{};
    pane.innerHTML=`<header class="mi-detail-heading"><div><p class="mi-kicker">${esc(view.quality)} · ${esc(m.providerMatchId)}</p><h3>${esc(m.mapName||'Match')} · ${esc(m.modeName||'Mode')}</h3></div><p>Captured ${esc(source.capturedAt?new Date(source.capturedAt).toLocaleString():'time unavailable')}</p></header><nav class="mi-tabs" role="tablist" aria-label="Match detail">${tabs.map(([id,label])=>`<button type="button" role="tab" id="miTab-${id}" aria-controls="miPanel" aria-selected="${id===activeTab}" tabindex="${id===activeTab?0:-1}" data-mi-tab="${id}">${label}</button>`).join('')}</nav><section class="mi-tab-panel" id="miPanel" role="tabpanel" aria-labelledby="miTab-${activeTab}" tabindex="0">${body}</section>`;
    pane.querySelectorAll('[data-mi-tab]').forEach((b,index,all)=>{b.addEventListener('click',()=>{activeTab=b.dataset.miTab;renderDetail(view);});b.addEventListener('keydown',e=>{if(!['ArrowLeft','ArrowRight','Home','End'].includes(e.key))return;e.preventDefault();const next=e.key==='Home'?0:e.key==='End'?all.length-1:(index+(e.key==='ArrowRight'?1:all.length-1))%all.length;all[next].focus();all[next].click();});});
    pane.querySelectorAll('[data-mi-round]').forEach(b=>b.addEventListener('click',()=>{selectedRound=Number(b.dataset.miRound);renderDetail(view);}));
    pane.querySelector('[data-mi-round-filter]')?.addEventListener('change',e=>{roundMode=e.target.value;selectedRound=null;renderDetail(view);});
  }
  root.querySelector('[data-mi-refresh]').addEventListener('click',()=>{selected=null;detailCache=new Map();load();});
  load();
})();
