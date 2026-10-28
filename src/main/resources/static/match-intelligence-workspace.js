(() => {
  'use strict';
  const same = (a, b) => String(a || '').trim().toLocaleLowerCase() === String(b || '').trim().toLocaleLowerCase();
  const finite = value => typeof value === 'number' && Number.isFinite(value) ? value : null;
  const sum = values => values.reduce((total, value) => total + (finite(value) ?? 0), 0);
  const escapeChartText = value => String(value ?? '').replace(/[&<>"']/g, character => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[character]));
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
  function filterEventPositions(positions, sourceRef) {
    return !sourceRef ? positions : positions.filter(position => position.sourceRef === sourceRef);
  }
  function groupPositionEvents(events) {
    const bySourceRef = new Map();
    for (const event of events) {
      if (!event?.sourceRef) continue;
      const prior = bySourceRef.get(event.sourceRef);
      if (!prior || (prior.category === 'PLAYER_DEATH' && event.category !== 'PLAYER_DEATH')) bySourceRef.set(event.sourceRef, event);
    }
    return [...bySourceRef.values()];
  }
  function equipmentByRound(match) {
    const participants = match?.participants || [];
    const teamIds = [...new Set(participants.map(player => player.teamId).filter(Boolean))].sort();
    if (teamIds.length !== 2) return [];
    const playerRounds = match?.playerRounds || [];
    return (match?.rounds || []).map(round => {
      const totals = {};
      for (const teamId of teamIds) {
        const members = participants.filter(player => player.teamId === teamId);
        let total = 0, complete = members.length > 0;
        for (const player of members) {
          const key = player.identity?.stableKey;
          const row = key ? playerRounds.find(item => item.roundNumber === round.roundNumber && item.identity?.stableKey === key) : null;
          const value = finite(row?.loadoutValue);
          if (value === null) { complete = false; break; }
          total += value;
        }
        totals[teamId] = complete ? total : null;
      }
      return { roundNumber: round.roundNumber, teams: totals };
    });
  }
  function equipmentChart(match, selectedTeam) {
    const series = equipmentByRound(match), teamIds = [...new Set(series.flatMap(point => Object.keys(point.teams)))];
    if (teamIds.length !== 2) return '<p class="mi-empty">Team equipment comparison is unavailable; two complete teams were not reported.</p>';
    const opponent = teamIds.find(team => team !== selectedTeam);
    const complete = series.filter(point => finite(point.teams[selectedTeam]) !== null && finite(point.teams[opponent]) !== null);
    if (!complete.length) return '<p class="mi-empty">No rounds have complete recorded loadout values for both teams.</p>';
    const width=960,height=260,left=56,right=14,top=18,bottom=34,plotW=width-left-right,plotH=height-top-bottom;
    const max=Math.max(1,...complete.flatMap(point=>[point.teams[selectedTeam],point.teams[opponent]]));
    const step=plotW/Math.max(1,series.length),barW=Math.min(14,step*.32),tickStep=Math.ceil(series.length/10);
    const grids=[0,.5,1].map(fraction=>{const y=top+plotH*(1-fraction),value=Math.round(max*fraction);return `<g><line x1="${left}" y1="${y}" x2="${width-right}" y2="${y}" class="mi-economy-gridline"/><text x="${left-8}" y="${y+4}" text-anchor="end">${value.toLocaleString()}</text></g>`;}).join('');
    const bars=series.map((point,index)=>{
      const center=left+step*(index+.5), own=finite(point.teams[selectedTeam]),other=finite(point.teams[opponent]);
      const bar=(value,x,team,className)=>value===null?'':`<rect class="${className}" x="${x}" y="${top+plotH-(value/max)*plotH}" width="${barW}" height="${Math.max(0,(value/max)*plotH)}"><title>Round ${point.roundNumber}, Team ${escapeChartText(team)}: ${value.toLocaleString()} recorded equipment value</title></rect>`;
      const label=index%tickStep===0||index===series.length-1?`<text x="${center}" y="${height-9}" text-anchor="middle">R${point.roundNumber}</text>`:'';
      if(own===null||other===null) return `<text x="${center}" y="${top+plotH-4}" text-anchor="middle" aria-hidden="true">—</text>${label}`;
      return `${bar(own,center-barW-1,selectedTeam,'mi-economy-own')}${bar(other,center+1,opponent,'mi-economy-opponent')}${label}`;
    }).join('');
    return `<figure class="mi-economy-chart"><figcaption><strong>Recorded team equipment by round</strong><span>Sum of player loadout values · source-reported units. Incomplete team rounds are left blank.</span></figcaption><div class="mi-economy-legend"><span><i class="mi-economy-own"></i>Selected team (${escapeChartText(selectedTeam)})</span><span><i class="mi-economy-opponent"></i>Opponent (${escapeChartText(opponent)})</span></div><svg viewBox="0 0 ${width} ${height}" role="img" aria-label="Recorded equipment value by round for both teams"><title>Recorded equipment value by round</title><desc>Each team bar is the sum of its players' recorded loadout values. A round is plotted only when both teams have complete player values.</desc>${grids}${bars}</svg></figure>`;
  }
  function resolveMap(match, catalog, tolerance = 1e-6) {
    const unavailable = reason => ({ map: null, reason });
    if (!match || !match.mapId) return unavailable('This match does not include a verified map ID.');
    const map = (catalog?.maps || []).find(item => String(item.mapId).toLowerCase() === String(match.mapId).toLowerCase());
    if (!map) return unavailable('No tactical map is available for this map ID.');
    const startedAt = Date.parse(match.startedAt || '');
    const validFrom = Date.parse((map.validFrom || '') + 'T00:00:00Z');
    const validThrough = Date.parse((catalog?.validThrough || '') + 'T23:59:59.999Z');
    if (!Number.isFinite(startedAt) || !Number.isFinite(validFrom) || !Number.isFinite(validThrough)) return unavailable('Map alignment is unavailable because the match date or catalog validity window is missing.');
    if (startedAt < validFrom) return unavailable('This match predates the verified map artwork and transform.');
    if (startedAt > validThrough) return unavailable('This match is newer than the verified map catalog; its layout may have changed.');
    const source = match.mapTransform;
    const keys = ['xMultiplier', 'yMultiplier', 'xScalarToAdd', 'yScalarToAdd'];
    if (!source || keys.some(key => finite(source[key]) === null || finite(map[key]) === null || Math.abs(source[key] - map[key]) > tolerance)) {
      return unavailable('The captured coordinate transform does not match this map catalog version.');
    }
    if (!map.asset || !Number.isFinite(map.width) || !Number.isFinite(map.height)) return unavailable('The verified map image is unavailable.');
    return { map, reason: null };
  }
  function projectPositions(positions, map, epsilon = 1e-9) {
    return (positions || []).map(position => {
      if (position?.coordinateSpace !== 'TRACKER_WORLD_RAW' || finite(position?.x) === null || finite(position?.y) === null) {
        return { position, placement: 'unavailable', x: null, y: null };
      }
      let x = position.x * map.xMultiplier + map.xScalarToAdd;
      let y = position.y * map.yMultiplier + map.yScalarToAdd;
      if (!Number.isFinite(x) || !Number.isFinite(y)) return { position, placement: 'unavailable', x: null, y: null };
      if (x < -epsilon || x > 1 + epsilon || y < -epsilon || y > 1 + epsilon) {
        return { position, placement: 'outside', x: null, y: null };
      }
      x = Math.min(1, Math.max(0, x));
      y = Math.min(1, Math.max(0, y));
      return { position, placement: 'plotted', x, y };
    });
  }
  function filterRounds(rounds, selectedTeam, mode, selectedPlayerRounds = [], events = [], selectedIdentity = '', findings = []) {
    const playerRows = new Map(selectedPlayerRounds.map(row => [row.roundNumber, row]));
    return rounds.filter(round => {
      const won = round.winningTeamId === selectedTeam;
      const ownRound = playerRows.get(round.roundNumber);
      const roundEvents = events.filter(event => event.roundNumber === round.roundNumber);
      if (mode === 'wins' && !won || mode === 'losses' && won) return false;
      if (mode === 'pistols' && ![1, 13].includes(round.roundNumber)) return false;
      if (mode === 'overtime' && !round.overtimeByRoundPosition) return false;
      if (mode === 'attack' && String(ownRound?.side||'').toUpperCase() !== 'ATTACK' || mode === 'defense' && String(ownRound?.side||'').toUpperCase() !== 'DEFENSE') return false;
      if (mode === 'multi-kill' && !(Number.isFinite(ownRound?.kills) && ownRound.kills >= 2)) return false;
      if (mode === 'objective' && !roundEvents.some(event => ['PLANT','DEFUSE','DETONATION'].includes(event.category))) return false;
      if (mode === 'plant' && !roundEvents.some(event => event.category === 'PLANT')) return false;
      if (mode === 'defuse' && !roundEvents.some(event => event.category === 'DEFUSE')) return false;
      if (mode === 'detonation' && !roundEvents.some(event => event.category === 'DETONATION')) return false;
      if (mode === 'review' && !findings.some(finding => finding.roundNumber === round.roundNumber)) return false;
      if (mode === 'opening-kill' || mode === 'opening-death') {
        const kills = roundEvents.filter(event => event.category === 'CREDITED_PLAYER_KILL');
        if (!kills.length || kills.some(event => !Number.isFinite(event.roundTimeRaw))) return false;
        const earliestTime = Math.min(...kills.map(event => event.roundTimeRaw));
        const earliest = kills.filter(event => event.roundTimeRaw === earliestTime);
        if (earliest.length !== 1) return false;
        const opening = earliest[0];
        const selectedIsActor = opening.actor?.stableKey === selectedIdentity;
        const selectedIsTarget = opening.target?.stableKey === selectedIdentity;
        if (mode === 'opening-kill' && !selectedIsActor || mode === 'opening-death' && !selectedIsTarget) return false;
      }
      return true;
    });
  }
  const api = { summarize, filterRounds, filterEventPositions, groupPositionEvents, equipmentByRound, equipmentChart, resolveMap, projectPositions };
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
  if (typeof window === 'undefined' || typeof document === 'undefined') return;
  const style = document.createElement('link'); style.rel='stylesheet'; style.href='/match-analyzer.css?v=10'; document.head.append(style);
  const params = new URLSearchParams(location.search), profilePlayer = params.get('player');
  const host = profilePlayer ? document.getElementById('playerProfileWorkspace') : document.getElementById('matchAnalysisPageWorkspace');
  if (!host) return;
  let mapCatalog={maps:[]};
  const mapCatalogRequest = fetch('/maps/map-manifest-13.08.json', { credentials:'same-origin', headers:{ Accept:'application/json' } })
    .then(response => response.ok ? response.json() : { maps:[] }).then(catalog => { mapCatalog=catalog; return catalog; }).catch(() => ({ maps:[] }));
  const root = document.createElement('section'); root.className = 'mi-workspace'; root.setAttribute('aria-labelledby', 'miTitle');
  root.innerHTML = `<div class="mi-head"><div><p class="mi-kicker">SAVED MATCH INTELLIGENCE</p><h2 id="miTitle">${profilePlayer ? 'Player match intelligence' : 'Saved matches'}</h2><p class="subtle">Owner-private match imports. Observed source data and calculated summaries are labeled separately.</p></div><button type="button" data-mi-refresh>Refresh matches</button></div><p class="mi-status" role="status" aria-live="polite">Loading your saved matches…</p><div data-mi-content hidden></div>`;
  host.append(root);
  const status = root.querySelector('.mi-status'), content = root.querySelector('[data-mi-content]');
  let pageRows = [], selected = null, detailCache = new Map(), analysisCache = new Map(), annotationCache = new Map(), baselineCache = new Map(), activeTab = 'overview', roundMode = 'all', selectedRound = null, selectedPositionEventRef = '', generation = 0;
  let benchmarkCohorts=[], benchmarkSelectedId='', benchmarkResult=null, benchmarkMessage='', benchmarkLoading=false;
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
  async function postJson(url, body) {
    return mutateJson(url, 'POST', body);
  }
  async function mutateJson(url, method, body) {
    const csrf=await getJson('/csrf');
    const response=await fetch(url,{method,credentials:'same-origin',headers:{Accept:'application/json','Content-Type':'application/json',[csrf.header]:csrf.token},...(body===undefined?{}:{body:JSON.stringify(body)})});
    if(response.status===401||response.status===403)throw new Error('Sign in is required to manage private match reviews.');
    if(!response.ok){let message='The review could not be saved.';try{message=(await response.json()).message||message;}catch{}throw new Error(message);}
    if(response.status===204)return null;
    return response.json();
  }
  async function loadBenchmarks() {
    if(!profilePlayer)return;
    try { benchmarkCohorts=await getJson('/api/match-intelligence/benchmarks'); if(!benchmarkCohorts.some(c=>c.cohortId===benchmarkSelectedId))benchmarkSelectedId=benchmarkCohorts[0]?.cohortId||''; }
    catch(error){benchmarkMessage=String(error.message||error);}
  }
  async function load() {
    const current = ++generation; status.textContent = 'Loading saved matches…'; content.hidden = true;
    try {
      await mapCatalogRequest;
      const path = profilePlayer ? '/api/match-intelligence/players/' + encodeURIComponent(profilePlayer) + '/matches?size=20' : '/api/match-intelligence/matches?size=20';
      const page = await getJson(path); if (current !== generation) return;
      pageRows = Array.isArray(page.matches) ? page.matches : [];
      if (!pageRows.length) { await loadBenchmarks(); if(current!==generation)return; renderList(); status.textContent = 'No saved match imports are available for this account and player yet.'; content.hidden=false; return; }
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
      await loadBenchmarks(); if(current!==generation)return;
      renderList(); status.textContent = page.total + ' authorized match(es) · latest ' + pageRows.length + ' shown. Summary figures use stored source records.'; content.hidden=false;
      if (selected && detailCache.has(selected.providerMatchId)) renderDetail(detailCache.get(selected.providerMatchId));
    } catch (error) { if (current === generation) { status.textContent=String(error.message||error); content.hidden=true; } }
  }
  function renderList() {
    const s=summarize(pageRows,detailCache), sampleNote=s.sample<5?'Small sample · fewer than 5 matches are available.':'Based on '+s.sample+' saved matches.';
    const rows=pageRows.map(row=>`<button type="button" class="mi-match${selected?.providerMatchId===row.providerMatchId?' is-selected':''}" data-mi-match="${esc(row.providerMatchId)}"><strong>${esc(row.summary?.map||'Map not reported')} · ${esc(row.summary?.mode||'Mode not reported')}</strong><span>${esc(row.selectedPlayer)} · ${esc(row.summary?.startedAt?new Date(row.summary.startedAt).toLocaleString():'Date not reported')} · ${esc(row.quality)}</span><span>Team ${esc(row.selectedTeam||'unknown')} · ${esc(Object.entries(row.summary?.teamScores||{}).map(([team,score])=>team+': '+score).join(' / ')||'score unavailable')}</span></button>`).join('');
    content.innerHTML=`<div class="mi-overview-grid"><article><span>Saved sample</span><strong>${s.sample}</strong><small>${sampleNote}</small></article><article><span>Win / loss</span><strong>${s.wins} / ${s.losses}</strong><small>From selected-team final scores · ${s.unknownOutcomes} unknown</small></article><article><span>Aggregate K / D / A</span><strong>${s.kills??'—'} / ${s.deaths??'—'} / ${s.assists??'—'}</strong><small>Missing source stats are excluded</small></article><article><span>Average ACS · ADR</span><strong>${s.averageAcs===null?'Not reported':s.averageAcs.toFixed(1)} · ${s.averageAdr===null?'Not reported':s.averageAdr.toFixed(1)}</strong><small>Mean of matches reporting each measure</small></article><article><span>Opening K / D</span><strong>${s.firstKills===null?'Not reported':s.firstKills+' / '+(s.firstDeaths??'Not reported')}</strong><small>Source summary only; not yet contextualized</small></article><article><span>Regulation pistol rounds</span><strong>${s.pistolRounds ? `${s.pistolWins} / ${s.pistolRounds} wins` : 'Not reported'}</strong><small>Only rounds 1 and 13 where captured</small></article><article><span>Recorded economy rows</span><strong>${s.economyRows || 'Not reported'}</strong><small>${s.averageSpend===null ? 'Spend not reported' : 'Average recorded spend '+s.averageSpend.toFixed(0)} · buy categories not inferred</small></article><article><span>Maps · agents</span><strong>${[...s.maps.slice(0,2).map(x=>x[0]),...s.agents.slice(0,2).map(x=>x[0])].map(esc).join(' · ') || 'Not reported'}</strong><small>Observed sample usage</small></article></div>${profilePlayer?benchmarkPanel():''}<div class="mi-split"><section class="mi-match-list" aria-label="Saved matches"><h3>Recent analyzed matches</h3>${rows||'<p class="mi-empty">No saved matches are available for this player yet.</p>'}</section><section class="mi-detail" data-mi-detail><p class="mi-empty">Choose a match to inspect verified rounds, events, economy fields and coverage.</p></section></div>`;
    content.querySelectorAll('[data-mi-match]').forEach(button=>button.addEventListener('click',()=>{selected=pageRows.find(r=>r.providerMatchId===button.dataset.miMatch)||null;activeTab='overview';roundMode='all';selectedRound=null;selectedPositionEventRef='';renderList();const view=detailCache.get(selected.providerMatchId);if(view)renderDetail(view);}));
    if(profilePlayer)wireBenchmarkPanel();
  }
  function benchmarkPanel() {
    const options=benchmarkCohorts.map(c=>`<option value="${esc(c.cohortId)}"${c.cohortId===benchmarkSelectedId?' selected':''}>${esc(c.name)} · ${esc(c.region.toUpperCase())} · ${esc(c.actId)}</option>`).join('');
    let result='<p class="mi-empty">Choose a saved cohort to compare this player.</p>';
    if(benchmarkMessage)result=`<p class="mi-empty" role="alert">${esc(benchmarkMessage)}</p>`;
    if(benchmarkLoading)result='<p class="mi-empty" role="status">Calculating an owner-scoped comparison…</p>';
    if(benchmarkResult){
      const c=benchmarkResult;
      const metricRows=(c.metrics||[]).map(m=>`<article><span>${esc(m.label)}</span><strong>${m.subjectValue===null?'Not available':Number(m.subjectValue).toFixed(3)} vs ${m.referenceMedian===null?'Insufficient evidence':Number(m.referenceMedian).toFixed(3)}</strong><small>Subject sample: ${m.subjectMatches} matches · reference: ${m.playersWithMetric} players, ${m.uniqueReferenceMatches} unique matches · IQR ${m.referenceP25===null?'not available':Number(m.referenceP25).toFixed(3)+'–'+Number(m.referenceP75).toFixed(3)}</small>${m.difference===null?'':`<small>Observed difference from median: ${Number(m.difference).toFixed(3)} ${esc(m.unit)}</small>`}</article>`).join('');
      const cohort=benchmarkCohorts.find(x=>x.cohortId===c.cohortId);
      result=`<div class="mi-benchmark-summary"><strong>${c.status==='OBSERVED'?'Reference comparison available':'Insufficient Evidence'}</strong><span>${esc(c.scenario)}</span><small>${c.eligibleReferencePlayers} reference players · ${c.uniqueReferenceMatches} unique reference matches · ${c.subjectUniqueMatches} subject matches${c.excludedSubjectMatches?` · excluded ${c.excludedSubjectMatches} subject match(es)`:''}</small></div>${c.status==='INSUFFICIENT_EVIDENCE'?'<p>Reference results stay hidden until at least 10 eligible players and 20 unique reference matches are available.</p>':''}<div class="mi-overview-grid">${metricRows}</div><details class="mi-benchmark-coverage"><summary>Members and source coverage</summary>${cohort?`<div class="mi-table-wrap"><table><thead><tr><th>Selection rank</th><th>Player</th><th>Selection rank vs match rank</th><th>Imported / unique</th><th>Coverage</th></tr></thead><tbody>${cohort.members.map(m=>`<tr><td>${m.selectionRank}</td><td>${esc(m.displayName||m.riotId)}</td><td>Selected #${m.selectionRank} · match ranks ${esc(m.rankAtMatch.join(', ')||'not reported')}</td><td>${m.matchesSuccessfullyImported} / ${m.uniqueMatches} of ${m.matchesRequested} requested</td><td>${esc(Object.entries(m.sourceCoverage).map(([k,v])=>k+': '+v).join(' · ')||'No analyzable source coverage')}</td></tr>`).join('')}</tbody></table></div>`:''}</details><ul class="mi-benchmark-limits">${c.limitations.map(x=>`<li>${esc(x)}</li>`).join('')}</ul>`;
    }
    return `<section class="mi-benchmark" aria-labelledby="miBenchmarkTitle"><p class="mi-kicker">CONTEXTUAL REFERENCE</p><h3 id="miBenchmarkTitle">Benchmark comparison</h3><p>Compare this imported player with a private, owner-entered reference cohort. Selected leaderboard rank and rank reported in match data are shown separately.</p><div class="mi-benchmark-controls"><label>Reference cohort<select data-mi-benchmark-cohort><option value="">${benchmarkCohorts.length?'Choose a cohort':'No cohorts saved yet'}</option>${options}</select></label><label>Map filter<input data-mi-benchmark-map maxlength="100" placeholder="Any map"></label><label>Mode filter<input data-mi-benchmark-mode maxlength="80" placeholder="Competitive"></label><label>Agent filter<input data-mi-benchmark-agent maxlength="80" placeholder="Any agent"></label><button type="button" data-mi-benchmark-compare${benchmarkCohorts.length?'':' disabled'}>Compare selected player</button></div><p class="mi-benchmark-note" role="status" aria-live="polite">${benchmarkLoading?'Loading…':''}</p>${result}<details class="mi-benchmark-create"><summary>Create a 10-player reference cohort</summary><p>Enter ten Riot IDs, one per line, in selection-rank order. Optional display name can follow a vertical bar. Matches and evidence coverage are derived only from imports in this account. Rank/date and permission status are owner-entered and not independently verified.</p><form data-mi-benchmark-form><label>Cohort name<input name="name" required maxlength="160" value="Radiant reference · ${new Date().toISOString().slice(0,10)}"></label><div class="mi-benchmark-controls"><label>Region<select name="region"><option value="ap">Asia-Pacific</option><option value="na">North America</option><option value="eu">Europe</option><option value="br">Brazil</option><option value="kr">Korea</option><option value="latam">Latin America</option></select></label><label>Act identifier<input name="actId" required maxlength="160" placeholder="Act identifier"></label><label>Leaderboard snapshot date<input name="snapshotDate" type="date" required value="${new Date().toISOString().slice(0,10)}"></label></div><label>Ten selected players<textarea name="members" rows="6" required placeholder="PlayerOne#TAG\nPlayerTwo#TAG\n…\nPlayerTen#TAG"></textarea></label><label class="mi-benchmark-consent"><input type="checkbox" name="permission"> I report that I have permission to use the listed match data (stored as an owner statement only; not verified by the service).</label><button type="submit">Save reference cohort</button></form></details></section>`;
  }
  function wireBenchmarkPanel() {
    const select=content.querySelector('[data-mi-benchmark-cohort]');
    select?.addEventListener('change',()=>{benchmarkSelectedId=select.value;benchmarkResult=null;benchmarkMessage='';renderList();});
    content.querySelector('[data-mi-benchmark-compare]')?.addEventListener('click',async()=>{
      if(!benchmarkSelectedId)return;
      const button=content.querySelector('[data-mi-benchmark-compare]');button.disabled=true;benchmarkLoading=true;benchmarkMessage='';benchmarkResult=null;renderList();
      const q=new URLSearchParams({cohortId:benchmarkSelectedId});
      for(const [key,selector] of [['map','[data-mi-benchmark-map]'],['mode','[data-mi-benchmark-mode]'],['agent','[data-mi-benchmark-agent]']]){const value=content.querySelector(selector)?.value.trim();if(value)q.set(key,value);}
      try{benchmarkResult=await getJson('/api/match-intelligence/players/'+encodeURIComponent(profilePlayer)+'/benchmark-comparison?'+q.toString());}
      catch(error){benchmarkMessage=String(error.message||error);}
      finally{benchmarkLoading=false;renderList();}
    });
    content.querySelector('[data-mi-benchmark-form]')?.addEventListener('submit',async event=>{
      event.preventDefault();const form=event.currentTarget,button=form.querySelector('button[type=submit]');button.disabled=true;benchmarkMessage='Saving the cohort…';renderList();
      const data=new FormData(form), lines=String(data.get('members')||'').split(/\r?\n/).map(line=>line.trim()).filter(Boolean);
      const members=lines.map((line,index)=>{const [riotId,...label]=line.split('|');return{selectionRank:index+1,riotId:riotId.trim(),displayName:label.join('|').trim()||null,matchesRequested:20};});
      try{const created=await postJson('/api/match-intelligence/benchmarks',{name:data.get('name'),region:data.get('region'),actId:data.get('actId'),leaderboardSnapshotDate:data.get('snapshotDate'),dataAccessStatus:form.elements.permission.checked?'OWNER_REPORTED_PERMISSION':'UNCONFIRMED',members});benchmarkSelectedId=created.cohortId;benchmarkResult=null;benchmarkMessage='Cohort saved. Import coverage is derived from this account’s stored matches.';await loadBenchmarks();renderList();}
      catch(error){benchmarkMessage=String(error.message||error);renderList();}
    });
  }
  function renderDetail(view) {
    const pane=content.querySelector('[data-mi-detail]'); if(!pane)return;
    if (!view || view.loadError) { pane.innerHTML=`<p class="mi-empty" role="alert">${esc(view?.loadError||'Match details unavailable.')}</p>`; return; }
    const m=view.match, p=playerFor(view), rounds=Array.isArray(m.rounds)?m.rounds:[], score=Object.fromEntries((m.teams||[]).map(t=>[t.teamId,t.roundsWon]));
    const own=score[view.perspective.teamId], opponent=Object.entries(score).find(([team])=>team!==view.perspective.teamId)?.[1];
    const roundRow=(m.playerRounds||[]).find(r=>r.identity?.stableKey===view.perspective.identity);
    const tabs=[['overview','Overview'],['rounds','Rounds'],['timeline','Timeline'],['economy','Economy'],['team','Team analysis'],['coaching','Coaching'],['review','Expert review']];
    let body='';
    if(activeTab==='overview') {
      const roster=(m.participants||[]).map(x=>`<tr><td>${esc(x.identity?.observedRiotId||'Player identity unavailable')}</td><td>Team ${esc(x.teamId)}</td><td>${esc(x.agent||'Not reported')}</td><td>${x.totals?`${val(x.totals.kills)}/${val(x.totals.deaths)}/${val(x.totals.assists)}`:'Not reported'}</td><td>${val(x.totals?.acs)}</td></tr>`).join('');
      body=`<div class="mi-match-hero"><div><h3>${esc(m.mapName||'Map not reported')} · ${esc(m.modeName||'Mode not reported')}</h3><p>${esc(view.perspective.riotId)} · ${esc(p?.agent||'Agent unavailable')} · Team ${esc(view.perspective.teamId)} · ${esc(m.startedAt?new Date(m.startedAt).toLocaleString():'Timestamp not reported')}</p><p>${esc(duration(m.durationMillis))} · ${m.overtime===null?'Overtime unknown':m.overtime?'Overtime':'Regulation'} · ${esc(view.quality)} · ${esc(view.provenance?.captureMethod||'Capture method not reported')}</p></div><strong>${val(own)} – ${val(opponent)}<small>Selected team perspective</small></strong></div><p class="mi-coverage">Coverage: ${Object.entries(m.availability||{}).map(([k,v])=>`${esc(k)} ${v?'observed':'unavailable'}`).join(' · ')||'Not reported'} · ${rounds.length} rounds · ${m.events?.length||0} events · ${m.positions?.length||0} discrete position snapshots</p><h4>Team rosters and agents</h4><div class="mi-table-wrap"><table><thead><tr><th>Player</th><th>Team</th><th>Agent</th><th>K/D/A</th><th>ACS</th></tr></thead><tbody>${roster||'<tr><td colspan="5">Roster not reported.</td></tr>'}</tbody></table></div>`;
    } else if(activeTab==='rounds') {
      const roundRecords=(roundRow?m.playerRounds.filter(r=>r.identity?.stableKey===view.perspective.identity):[]);
      let reviewReport=analysisCache.get(m.providerMatchId);
      if(roundMode==='review'&&!reviewReport) {
        reviewReport={loading:true}; analysisCache.set(m.providerMatchId,reviewReport);
        getJson('/api/match-intelligence/matches/'+encodeURIComponent(m.providerMatchId)+'/analysis').then(data=>{analysisCache.set(m.providerMatchId,data);if(selected?.providerMatchId===m.providerMatchId&&activeTab==='rounds'&&roundMode==='review')renderDetail(view);}).catch(error=>{analysisCache.set(m.providerMatchId,{loadError:String(error.message||error)});if(selected?.providerMatchId===m.providerMatchId&&activeTab==='rounds'&&roundMode==='review')renderDetail(view);});
      }
      const reviewFindings=reviewReport?.findings||[];
      const visible=filterRounds(rounds,view.perspective.teamId,roundMode,roundRecords,m.events||[],view.perspective.identity,reviewFindings);
      const selected=visible.find(r=>r.roundNumber===selectedRound)||visible[0]; if(selected&&!visible.some(r=>r.roundNumber===selectedRound))selectedRound=selected.roundNumber;
      const options=[['all','All rounds'],['wins','Team wins'],['losses','Team losses'],['pistols','Pistol rounds'],['overtime','Overtime'],['attack','Attack'],['defense','Defense'],['opening-kill','Player opening kills'],['opening-death','Player opening deaths'],['multi-kill','Player multi-kills (2+)'],['objective','Objective rounds'],['plant','Plant rounds'],['defuse','Defuse rounds'],['detonation','Detonation rounds'],['review','Rounds with review findings']];
      const record=roundRecords.find(r=>r.roundNumber===selected?.roundNumber), events=(m.events||[]).filter(e=>e.roundNumber===selected?.roundNumber),positions=(m.positions||[]).filter(e=>e.roundNumber===selected?.roundNumber);
      const positionEvents=groupPositionEvents(events);
      const selectedPositions=filterEventPositions(positions,selectedPositionEventRef);
      const selectedPositionEvent=positionEvents.find(event=>event.sourceRef===selectedPositionEventRef);
      const catalogResult=resolveMap(m,mapCatalog);
      const projectedPositions=catalogResult.map?projectPositions(selectedPositions,catalogResult.map):[];
      const plottedPositions=projectedPositions.filter(point=>point.placement==='plotted');
      const outsidePositions=projectedPositions.filter(point=>point.placement==='outside').length;
      const mapOverlay=catalogResult.map?`<figure class="mi-map-figure"><div class="mi-map-canvas"><img src="${esc(catalogResult.map.asset)}" width="${catalogResult.map.width}" height="${catalogResult.map.height}" alt=""><div class="mi-map-pins" aria-hidden="true">${plottedPositions.map(point=>`<span class="mi-map-pin mi-map-pin-${esc(String(point.position.snapshotType||'unknown').toLowerCase().replace(/[^a-z0-9]+/g,'-'))}" style="left:${(point.x*100).toFixed(5)}%;top:${(point.y*100).toFixed(5)}%" title="${esc(point.position.identity?.observedRiotId||'Unknown player')} · ${esc(point.position.snapshotType||'Snapshot')}"></span>`).join('')}</div></div><div class="mi-map-legend" aria-label="Map marker key"><span><i class="mi-map-pin mi-map-pin-player-at-event"></i>Player snapshot</span><span><i class="mi-map-pin mi-map-pin-opponent-at-event"></i>Opponent snapshot</span><span><i class="mi-map-pin mi-map-pin-plant-site-event"></i>Plant site</span><span><i class="mi-map-pin mi-map-pin-defuse-site-event"></i>Defuse site</span></div><figcaption>${esc(catalogResult.map.name)} · Riot catalog ${esc(mapCatalog.catalogVersion||'verified release')} · ${plottedPositions.length} plotted${outsidePositions?` · ${outsidePositions} valid source point(s) outside map extent`:''}. Discrete event snapshots only; no paths or positions are inferred.</figcaption></figure>`:'<p class="mi-map-unavailable">'+esc(catalogResult.reason)+'</p>';
      const eventRows=events.map(e=>`<tr><td>${esc(e.roundTimeRaw===null?'Not reported':e.roundTimeRaw+' source units')}</td><td>${esc(e.category)}</td><td>${esc(e.actor?.observedRiotId||'Unknown')}</td><td>${esc(e.target?.observedRiotId||'Not applicable')}</td><td>${esc(e.weaponName||e.rawSourceType||'Not reported')}</td></tr>`).join('');
       body=`<div class="mi-round-toolbar"><div class="mi-round-strip" role="group" aria-label="Select round">${rounds.map(r=>`<button type="button" data-mi-round="${r.roundNumber}" aria-pressed="${r.roundNumber===selectedRound}">R${r.roundNumber}</button>`).join('')}</div><label>Filter <select data-mi-round-filter>${options.map(([key,label])=>`<option value="${key}"${key===roundMode?' selected':''}>${label}</option>`).join('')}</select></label></div>${roundMode==='review'&&reviewReport?.loading?'<p class="mi-empty" role="status">Loading evidence-linked review findings…</p>':''}${roundMode==='review'&&reviewReport?.loadError?`<p class="mi-empty" role="alert">${esc(reviewReport.loadError)}</p>`:''}<p class="mi-coverage">Opening filters require a unique, timed credited kill. Multi-kill and objective filters use reported round/event data. Buy categories are unavailable unless the source explicitly classifies them.</p><div class="mi-round-cards">${visible.map(r=>`<button type="button" data-mi-round="${r.roundNumber}" aria-pressed="${r.roundNumber===selectedRound}"><strong>Round ${r.roundNumber}</strong><span>${r.winningTeamId===view.perspective.teamId?'Win':'Loss'} · ${esc(r.rawWinMethod||'Result unknown')}</span></button>`).join('')}</div>${selected?`<div class="mi-round-detail"><h4>Round ${selected.roundNumber} · ${selected.winningTeamId===view.perspective.teamId?'Team win':'Team loss'}</h4><p>Player K/D/A ${record?[record.kills,record.deaths,record.assists].map(val).join('/'):'Not reported'} · Damage ${val(record?.damage)} · side ${val(record?.side)} · score ${val(record?.score)}</p><p>Recorded loadout ${val(record?.loadoutValue)} · spent ${val(record?.spentCredits)} · remaining credits ${val(record?.remainingCredits)}</p><p>Spike evidence: plant ${selected.plantEvidence?'reported':'not reported'} · defuse ${selected.defuseEvidence?'reported':'not reported'} · detonation time ${val(selected.detonationTimeRaw)} source units</p><h4>Round events</h4><div class="mi-table-wrap"><table><thead><tr><th>Time (source units)</th><th>Type</th><th>Actor</th><th>Target</th><th>Weapon / source</th></tr></thead><tbody>${eventRows||'<tr><td colspan="5">No events reported for this round.</td></tr>'}</tbody></table></div><h4>Inspect positions by event</h4><label class="mi-position-event-label">Event<select data-mi-position-event aria-label="Filter position snapshots by event"><option value="">All recorded snapshots in this round</option>${positionEvents.map(event=>`<option value="${esc(event.sourceRef)}"${event.sourceRef===selectedPositionEventRef?' selected':''}>${esc(event.roundTimeRaw===null?'Time unavailable':event.roundTimeRaw+' source units')} · ${esc(event.category||'Unknown')} · ${esc(event.actor?.observedRiotId||'Unknown')} → ${esc(event.target?.observedRiotId||'No target')}</option>`).join('')}</select></label><p>${selectedPositionEvent?`Showing snapshots linked to ${esc(selectedPositionEvent.category||'this event')} at ${esc(selectedPositionEvent.roundTimeRaw===null?'an unreported time':selectedPositionEvent.roundTimeRaw+' source units')}.`:'Snapshots are discrete source coordinates, not movement paths.'} ${selectedPositionEventRef&&!selectedPositions.length?'No recorded positions were linked to this event.':''}</p>${mapOverlay}<div class="mi-table-wrap"><table><thead><tr><th>Player</th><th>Snapshot</th><th>X (raw)</th><th>Y (raw)</th><th>Coordinate space</th><th>Map placement</th></tr></thead><tbody>${selectedPositions.map((x,index)=>{const placement=projectedPositions[index]?.placement||'unavailable';return `<tr><td>${esc(x.identity?.observedRiotId||'Unknown')}</td><td>${esc(x.snapshotType)}</td><td>${val(x.x)}</td><td>${val(x.y)}</td><td>${esc(x.coordinateSpace||'Unspecified')}</td><td>${placement==='plotted'?'Plotted':placement==='outside'?'Outside map extent':'Unavailable'}</td></tr>`}).join('')||'<tr><td colspan="6">No position snapshots are reported for this selection.</td></tr>'}</tbody></table></div></div>`:'<p class="mi-empty">No rounds match the selected filter.</p>'}`;
    } else if(activeTab==='timeline') {
      const events=[...(m.events||[])].sort((a,b)=>a.roundNumber-b.roundNumber||((finite(a.roundTimeRaw)??0)-(finite(b.roundTimeRaw)??0)));
      body=`<p>Events are sorted by round then reported raw time. Source time units are not independently verified.</p><div class="mi-table-wrap"><table><thead><tr><th>Round · time</th><th>Class</th><th>Actor</th><th>Target</th><th>Weapon / event</th><th>Assists</th></tr></thead><tbody>${events.map(e=>`<tr class="${e.category==='CREDITED_PLAYER_KILL'?'':'mi-special-event'}"><td>${e.roundNumber} · ${val(e.roundTimeRaw)} source units</td><td>${esc(e.category||'Unknown')}</td><td>${esc(e.actor?.observedRiotId||'Unknown')}</td><td>${esc(e.target?.observedRiotId||'Unknown')}</td><td>${esc(e.weaponName||e.rawSourceType||'Not reported')}</td><td>${Array.isArray(e.assistants)?e.assistants.length:'Not reported'}</td></tr>`).join('')||'<tr><td colspan="6">Event timeline was not captured.</td></tr>'}</tbody></table></div><p>Position snapshots: ${(m.positions||[]).length} reported. No continuous paths are inferred.</p>`;
    } else if(activeTab==='economy') {
      const playerRows=(m.playerRounds||[]).filter(r=>r.identity?.stableKey===view.perspective.identity);
      body=`<p>Only recorded values are shown; no force, bonus or buy category is inferred in M4.</p>${equipmentChart(m,view.perspective.teamId)}<h4>Selected-player recorded economy</h4><div class="mi-table-wrap"><table><thead><tr><th>Round</th><th>Side</th><th>Loadout value</th><th>Spent</th><th>Remaining</th><th>Outcome</th></tr></thead><tbody>${playerRows.map(r=>`<tr><td>${r.roundNumber}</td><td>${val(r.side)}</td><td>${val(r.loadoutValue)}</td><td>${val(r.spentCredits)}</td><td>${val(r.remainingCredits)}</td><td>${rounds.find(x=>x.roundNumber===r.roundNumber)?.winningTeamId===view.perspective.teamId?'Win':'Loss'}</td></tr>`).join('')||'<tr><td colspan="6">Player economy rows were not captured.</td></tr>'}</tbody></table></div><p>Totals sum recorded player loadout values only when every rostered player in both teams has a value for that round. Missing rows remain unplotted. Buy categories and conversion by buy category remain unavailable unless explicitly classified by the source.</p>`;
    } else if(activeTab==='team') {
      const teamRounds=(m.rounds||[]), ownWins=teamRounds.filter(r=>r.winningTeamId===view.perspective.teamId).length;
      const attack=(m.playerRounds||[]).filter(r=>r.identity?.stableKey===view.perspective.identity&&String(r.side||'').toUpperCase()==='ATTACK'),defense=(m.playerRounds||[]).filter(r=>r.identity?.stableKey===view.perspective.identity&&String(r.side||'').toUpperCase()==='DEFENSE');const wonRound=new Map(teamRounds.map(r=>[r.roundNumber,r.winningTeamId===view.perspective.teamId]));
      body=`<div class="mi-overview-grid"><article><span>Team round results</span><strong>${ownWins} / ${teamRounds.length}</strong><small>Observed wins / rounds</small></article><article><span>Player on attack</span><strong>${attack.length||'Not reported'}</strong><small>${attack.length?attack.filter(r=>r.teamId===view.perspective.teamId).length+' rows':'Side coverage unavailable'}</small></article><article><span>Player on defense</span><strong>${defense.length||'Not reported'}</strong><small>${defense.length?defense.filter(r=>r.teamId===view.perspective.teamId).length+' rows':'Side coverage unavailable'}</small></article><article><span>Opening conversion</span><strong>See Coaching</strong><small>Evidence-linked match report</small></article></div><p>Teamwork is not scored from aggregate K/D. The Coaching tab links supported opening, reciprocal trade-like and objective findings to their source events.</p>`;
    } else if(activeTab==='coaching') {
      const report=analysisCache.get(m.providerMatchId);
      if(!report) {
        body=`<div class="mi-empty" role="status"><h4>Loading evidence-linked analysis…</h4><p>Only deterministic findings tied to captured events and round data are shown. Unavailable evidence stays unknown.</p></div>`;
        getJson('/api/match-intelligence/matches/'+encodeURIComponent(m.providerMatchId)+'/analysis').then(data=>{analysisCache.set(m.providerMatchId,data);if(selected?.providerMatchId===m.providerMatchId&&activeTab==='coaching')renderDetail(view);}).catch(error=>{analysisCache.set(m.providerMatchId,{loadError:String(error.message||error)});if(selected?.providerMatchId===m.providerMatchId&&activeTab==='coaching')renderDetail(view);});
      } else if(report.loadError) { body=`<div class="mi-empty" role="alert">${esc(report.loadError)}</div>`; }
      else {
        const metrics=(report.metrics||[]).map(x=>`<article><span>${esc(x.label)}</span><strong>${x.value===null?'Not available':esc(Number(x.value).toFixed(2))}</strong><small>${esc(x.numerator??'—')} / ${esc(x.denominator??'—')} ${esc(x.unit||'')} · ${esc(x.availability||'')}</small></article>`).join('');
        const findings=(report.findings||[]).map(x=>`<article class="mi-finding"><div><span class="mi-finding-category">${esc(x.category)}</span>${x.roundNumber?`<span>Round ${esc(x.roundNumber)}</span>`:''}</div><h4>${esc(x.title)}</h4><p>${esc(x.whatHappened)}</p><p>${esc(x.whyItMayMatter)}</p><small>${esc(x.confidence)} confidence · ${esc(x.evidenceType)} · events ${esc((x.sourceEventIds||[]).join(', ')||'none')}</small>${x.reviewQuestion?`<p><strong>Review:</strong> ${esc(x.reviewQuestion)}</p>`:''}</article>`).join('');
        body=`<p class="mi-analysis-version">${esc(report.analysisVersion)} · economy rules ${esc(report.economyRulesetVersion)} · deterministic evidence-linked rules, not a skill score.</p><div class="mi-overview-grid">${metrics}</div><h4>Round findings</h4><div class="mi-findings">${findings||'<p class="mi-empty">No qualifying findings.</p>'}</div><h4>Unavailable analyses</h4><ul>${(report.unavailableAnalyses||[]).map(x=>`<li>${esc(x)}</li>`).join('')}</ul>`;
      }
    } else {
      let annotations=annotationCache.get(m.providerMatchId);
      if(annotations===undefined) {
        annotationCache.set(m.providerMatchId,null);
        body='<p class="mi-empty" role="status">Loading your private expert annotations…</p>';
        getJson('/api/match-intelligence/matches/'+encodeURIComponent(m.providerMatchId)+'/annotations').then(rows=>{annotationCache.set(m.providerMatchId,rows);if(selected?.providerMatchId===m.providerMatchId&&activeTab==='review')renderDetail(view);}).catch(error=>{annotationCache.set(m.providerMatchId,{loadError:String(error.message||error)});if(selected?.providerMatchId===m.providerMatchId&&activeTab==='review')renderDetail(view);});
      } else if(annotations?.loadError) body=`<p class="mi-empty" role="alert">${esc(annotations.loadError)}</p>`;
      else {
        annotations=annotations||[];
        const events=(m.events||[]).map(e=>`<option value="${esc(e.eventId)}" data-round="${e.roundNumber}">R${e.roundNumber} · ${esc(e.category)} · ${esc(e.actor?.observedRiotId||'unknown actor')} → ${esc(e.target?.observedRiotId||'no target')}</option>`).join('');
        const cards=annotations.map(a=>`<article class="mi-annotation"><strong>R${a.roundNumber} · ${esc(a.judgment)} · ${a.status} v${a.revision}</strong><p>${esc(a.reason)}</p>${a.betterAlternative?`<p><b>Alternative:</b> ${esc(a.betterAlternative)}</p>`:''}<small>${a.confidence}% confidence · ${a.insufficientEvidence?'insufficient evidence marked':'evidence reviewed'} · ${esc(a.guidelineVersion)} · private to ${esc(a.annotator)}</small><label>Correct reason<textarea data-mi-reason="${esc(a.annotationId)}" rows="2" maxlength="4000">${esc(a.reason)}</textarea></label><button type="button" data-mi-revise="${esc(a.annotationId)}" data-revision="${a.revision}">Append correction</button>${a.status==='ACTIVE'?` <button type="button" data-mi-withdraw="${esc(a.annotationId)}" data-revision="${a.revision}">Withdraw</button>`:''}</article>`).join('');
        body=`<p>Reviewer judgments are private hypotheses, not model truth. Corrections and withdrawals append immutable revisions. HTTPS VOD references are stored only and never fetched.</p><form class="mi-annotation-form" data-mi-annotation-form><label>Round<select name="roundNumber">${rounds.map(r=>`<option value="${r.roundNumber}">Round ${r.roundNumber}</option>`).join('')}</select></label><label>Evidence event (optional)<select name="eventId"><option value="">No event selected</option>${events}</select></label><label>Tactical judgment<select name="judgment"><option>POSITIONING</option><option>OPENING</option><option>TRADE</option><option>UTILITY</option><option>ECONOMY</option><option>OBJECTIVE</option><option>TEAMPLAY</option><option>OTHER</option><option>INSUFFICIENT_EVIDENCE</option></select></label><label>Reason<textarea name="reason" rows="3" maxlength="4000" required></textarea></label><label>Better alternative (optional)<textarea name="betterAlternative" rows="2" maxlength="4000"></textarea></label><label>Confidence 0–100<input type="number" name="confidence" min="0" max="100" value="50"></label><label>Permitted VOD link (HTTPS only)<input type="url" name="vodUrl" maxlength="1200"></label><label>Disagreement group (optional)<input name="disagreementGroup" maxlength="100"></label><label class="mi-annotation-check"><input type="checkbox" name="insufficientEvidence"> Evidence is insufficient</label><button type="submit">Save private review</button><p data-mi-review-status role="status" aria-live="polite"></p></form><h4>Your expert annotations</h4><div class="mi-annotations">${cards||'<p class="mi-empty">No private annotations yet.</p>'}</div>`;
      }
    }
    if(activeTab==='coaching') {
      const evaluation=baselineCache.get(m.providerMatchId);
      const evalBody=evaluation?.loadError?`<p role="alert">${esc(evaluation.loadError)}</p>`:evaluation?.status==='INSUFFICIENT_EVIDENCE'?`<p><strong>Insufficient evidence.</strong> ${esc(evaluation.limitations.at(-1))}</p>`:evaluation?.status==='EVALUATED_BASELINE'?`<p><strong>Offline evaluation only · ${esc(evaluation.modelVersion)}</strong> · ${evaluation.matchCount} matches (${evaluation.trainMatchCount} train / ${evaluation.testMatchCount} held out) · ${evaluation.testRows} held-out rounds · accuracy ${Number(evaluation.accuracy).toFixed(3)} · Brier ${Number(evaluation.brierScore).toFixed(3)} · false-positive rate ${Number(evaluation.falsePositiveRate).toFixed(3)}.</p>`:'<p>No prediction model is activated. Evaluation is optional and uses only owner-authorized imports.</p>';
      body+=`<section class="mi-model-evaluation"><h4>Pre-round outcome baseline</h4><p>CPU logistic-regression research baseline; pre-round score, round index and reported side only. Outcome prediction does not identify a tactical error.</p>${evalBody}<button type="button" data-mi-evaluate>Evaluate my imported matches</button></section>`;
      if(analysisCache.get(m.providerMatchId)&&!analysisCache.get(m.providerMatchId).loadError)body+=`<section class="mi-model-evaluation"><h4>Evidence-grounded coach brief</h4><p>Copy verified match facts, deterministic findings, source references and availability limits for optional external explanation. No AI service is called.</p><button type="button" data-mi-copy-coach>Copy safe coach brief</button><span data-mi-copy-status role="status" aria-live="polite"></span></section>`;
    }
    const source=view.provenance||{};
    pane.innerHTML=`<header class="mi-detail-heading"><div><p class="mi-kicker">${esc(view.quality)} · ${esc(m.providerMatchId)}</p><h3>${esc(m.mapName||'Match')} · ${esc(m.modeName||'Mode')}</h3></div><p>Captured ${esc(source.capturedAt?new Date(source.capturedAt).toLocaleString():'time unavailable')}</p></header><nav class="mi-tabs" role="tablist" aria-label="Match detail">${tabs.map(([id,label])=>`<button type="button" role="tab" id="miTab-${id}" aria-controls="miPanel" aria-selected="${id===activeTab}" tabindex="${id===activeTab?0:-1}" data-mi-tab="${id}">${label}</button>`).join('')}</nav><section class="mi-tab-panel" id="miPanel" role="tabpanel" aria-labelledby="miTab-${activeTab}" tabindex="0">${body}</section>`;
    pane.querySelectorAll('[data-mi-tab]').forEach((b,index,all)=>{b.addEventListener('click',()=>{activeTab=b.dataset.miTab;renderDetail(view);});b.addEventListener('keydown',e=>{if(!['ArrowLeft','ArrowRight','Home','End'].includes(e.key))return;e.preventDefault();const next=e.key==='Home'?0:e.key==='End'?all.length-1:(index+(e.key==='ArrowRight'?1:all.length-1))%all.length;all[next].focus();all[next].click();});});
    pane.querySelectorAll('[data-mi-round]').forEach(b=>b.addEventListener('click',()=>{selectedRound=Number(b.dataset.miRound);selectedPositionEventRef='';renderDetail(view);}));
    pane.querySelector('[data-mi-position-event]')?.addEventListener('change',e=>{selectedPositionEventRef=e.target.value;renderDetail(view);});
    pane.querySelector('[data-mi-annotation-form]')?.addEventListener('submit',async e=>{
      e.preventDefault();const form=e.currentTarget,d=new FormData(form),button=form.querySelector('button[type=submit]'),status=form.querySelector('[data-mi-review-status]');button.disabled=true;status.textContent='Saving private review…';
      const event=(m.events||[]).find(x=>x.eventId===d.get('eventId'));
      const annotation={roundNumber:Number(d.get('roundNumber')),eventId:event?.eventId||null,eventTimeRaw:event?.roundTimeRaw??null,judgment:String(d.get('judgment')),reason:String(d.get('reason')),betterAlternative:String(d.get('betterAlternative')||'')||null,insufficientEvidence:form.elements.insufficientEvidence.checked,confidence:Number(d.get('confidence')),vodUrl:String(d.get('vodUrl')||'')||null,disagreementGroup:String(d.get('disagreementGroup')||'')||null};
      try{await postJson('/api/match-intelligence/matches/'+encodeURIComponent(m.providerMatchId)+'/annotations',annotation);annotationCache.delete(m.providerMatchId);renderDetail(view);}catch(error){status.textContent=String(error.message||error);button.disabled=false;}
    });
    const reviewForm=pane.querySelector('[data-mi-annotation-form]');
    reviewForm?.elements.roundNumber.addEventListener('change',()=>{const eventSelect=reviewForm.elements.eventId;for(const option of eventSelect.options){if(!option.value)continue;option.hidden=option.dataset.round!==reviewForm.elements.roundNumber.value;if(option.hidden&&option.selected)eventSelect.value='';}});
    pane.querySelectorAll('[data-mi-revise]').forEach(button=>button.addEventListener('click',async()=>{const annotation=(annotationCache.get(m.providerMatchId)||[]).find(a=>a.annotationId===button.dataset.miRevise),reason=pane.querySelector(`[data-mi-reason="${CSS.escape(button.dataset.miRevise)}"]`)?.value;if(!annotation||!reason?.trim())return;button.disabled=true;try{await mutateJson('/api/match-intelligence/annotations/'+encodeURIComponent(annotation.annotationId),'PUT',{expectedRevision:Number(button.dataset.revision),annotation:{roundNumber:annotation.roundNumber,eventId:annotation.eventId,eventTimeRaw:annotation.eventTimeRaw,judgment:annotation.judgment,reason,betterAlternative:annotation.betterAlternative,insufficientEvidence:annotation.insufficientEvidence,confidence:annotation.confidence,vodUrl:annotation.vodUrl,disagreementGroup:annotation.disagreementGroup}});annotationCache.delete(m.providerMatchId);renderDetail(view);}catch(error){button.disabled=false;alert(String(error.message||error));}}));
    pane.querySelectorAll('[data-mi-withdraw]').forEach(button=>button.addEventListener('click',async()=>{button.disabled=true;try{await mutateJson('/api/match-intelligence/annotations/'+encodeURIComponent(button.dataset.miWithdraw)+'?expectedRevision='+encodeURIComponent(button.dataset.revision),'DELETE');annotationCache.delete(m.providerMatchId);renderDetail(view);}catch(error){button.disabled=false;alert(String(error.message||error));}}));
    pane.querySelector('[data-mi-evaluate]')?.addEventListener('click',async event=>{const button=event.currentTarget;button.disabled=true;baselineCache.set(m.providerMatchId,{loading:true});try{baselineCache.set(m.providerMatchId,await getJson('/api/match-intelligence/players/'+encodeURIComponent(view.perspective.riotId)+'/baseline-evaluation'));}catch(error){baselineCache.set(m.providerMatchId,{loadError:String(error.message||error)});}if(selected?.providerMatchId===m.providerMatchId&&activeTab==='coaching')renderDetail(view);});
    pane.querySelector('[data-mi-copy-coach]')?.addEventListener('click',async event=>{const report=analysisCache.get(m.providerMatchId),status=pane.querySelector('[data-mi-copy-status]');const brief={briefVersion:'evidence-grounded-coach-brief-v1',instructions:'Explain only supplied facts and deterministic findings. Do not infer unseen movement, utility use, voice communication, intent, or decision quality. Present unverified tactical judgments as hypotheses. State missing evidence plainly.',match:{providerMatchId:m.providerMatchId,map:m.mapName,mode:m.modeName,act:m.actId,startedAt:m.startedAt,quality:view.quality,availability:m.availability,rounds:m.rounds,selectedPlayer:view.perspective.riotId,selectedTeam:view.perspective.teamId},analysis:report,benchmark:benchmarkResult||null};try{await navigator.clipboard.writeText(JSON.stringify(brief,null,2));status.textContent='Safe coach brief copied. Review it before sharing with an external service.';}catch{status.textContent='Clipboard access is unavailable in this browser. No data was sent.';}});
    pane.querySelector('[data-mi-round-filter]')?.addEventListener('change',e=>{roundMode=e.target.value;selectedRound=null;selectedPositionEventRef='';renderDetail(view);});
  }
  root.querySelector('[data-mi-refresh]').addEventListener('click',()=>{selected=null;detailCache=new Map();analysisCache=new Map();load();});
  load();
})();
