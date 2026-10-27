(() => {
  'use strict';

  const own = (value, key) => Object.prototype.hasOwnProperty.call(value, key);
  const object = value => value !== null && typeof value === 'object' && !Array.isArray(value);
  const fail = message => { throw new Error(message); };
  const exactKeys = (value, expected, label) => {
    if (!object(value)) fail(`${label} must be an object.`);
    const actual = Object.keys(value).sort();
    const wanted = [...expected].sort();
    if (actual.length !== wanted.length || actual.some((key, i) => key !== wanted[i])) {
      fail(`${label} has missing or unsupported fields.`);
    }
  };
  const integer = (value, label, min = 0, max = 100000) => {
    if (!Number.isInteger(value) || value < min || value > max) fail(`${label} must be an integer from ${min} to ${max}.`);
    return value;
  };
  const number = (value, label, min = 0, max = 100000) => {
    if (typeof value !== 'number' || !Number.isFinite(value) || value < min || value > max) fail(`${label} must be a number from ${min} to ${max}.`);
    return value;
  };

  function validate(raw) {
    exactKeys(raw, ['schemaVersion', 'source', 'match', 'player', 'rounds'], 'Import');
    if (raw.schemaVersion !== 1) fail('Unsupported match capture schema version.');
    exactKeys(raw.source, ['provider', 'method', 'url', 'capturedAt', 'parserVersion', 'roundsObserved'], 'Source');
    if (raw.source.provider !== 'tracker.gg' || raw.source.method !== 'rendered-page') fail('Only rendered Tracker page captures are supported.');
    let sourceUrl;
    try { sourceUrl = new URL(raw.source.url); } catch { fail('Source URL must be a Tracker VALORANT match page.'); }
    const sourceMatch = sourceUrl.pathname.match(/^\/valorant\/match\/([0-9a-f-]{8,64})\/?$/i);
    if (sourceUrl.protocol !== 'https:' || sourceUrl.hostname !== 'tracker.gg' || !sourceMatch) fail('Source URL must be a Tracker VALORANT match page.');
    if (!Number.isFinite(Date.parse(raw.source.capturedAt))) fail('Capture time is missing or invalid.');
    if (typeof raw.source.parserVersion !== 'string' || raw.source.parserVersion.length < 1 || raw.source.parserVersion.length > 80) fail('Parser version is missing or invalid.');
    integer(raw.source.roundsObserved, 'Observed round count', 1, 30);

    exactKeys(raw.match, ['id', 'mode', 'map', 'displayedStartedAt', 'durationSeconds', 'teamAScore', 'teamBScore', 'overtime'], 'Match');
    const id = raw.match.id;
    if (typeof id !== 'string' || !/^[0-9a-f-]{8,64}$/i.test(id) || sourceMatch[1].toLowerCase() !== id.toLowerCase()) fail('Match ID must match the source URL.');
    if (typeof raw.match.mode !== 'string' || raw.match.mode.length < 1 || raw.match.mode.length > 80) fail('Match mode is missing or invalid.');
    if (typeof raw.match.map !== 'string' || raw.match.map.length < 1 || raw.match.map.length > 80) fail('Map name is missing or invalid.');
    if (typeof raw.match.displayedStartedAt !== 'string' || raw.match.displayedStartedAt.length > 120) fail('Displayed start time is invalid.');
    integer(raw.match.durationSeconds, 'Duration', 1, 7200);
    integer(raw.match.teamAScore, 'Team A score', 0, 30);
    integer(raw.match.teamBScore, 'Team B score', 0, 30);
    if (typeof raw.match.overtime !== 'boolean') fail('Overtime state must be explicit.');

    exactKeys(raw.player, ['riotId', 'team', 'agent', 'totals'], 'Player');
    if (typeof raw.player.riotId !== 'string' || !raw.player.riotId.includes('#') || raw.player.riotId.length > 120) fail('Player Riot ID is missing or invalid.');
    if (!['A', 'B'].includes(raw.player.team)) fail('Player team must be A or B.');
    if (typeof raw.player.agent !== 'string' || raw.player.agent.length < 1 || raw.player.agent.length > 40) fail('Player agent is missing or invalid.');
    exactKeys(raw.player.totals, ['kills', 'deaths', 'assists', 'acs', 'adr', 'headshotPercent', 'kastPercent', 'firstKills', 'firstDeaths'], 'Player totals');
    for (const key of ['kills', 'deaths', 'assists', 'firstKills', 'firstDeaths']) integer(raw.player.totals[key], `Player ${key}`, 0, 100);
    integer(raw.player.totals.acs, 'Player ACS', 0, 2000);
    number(raw.player.totals.adr, 'Player ADR', 0, 2000);
    number(raw.player.totals.headshotPercent, 'Player headshot percentage', 0, 100);
    number(raw.player.totals.kastPercent, 'Player KAST percentage', 0, 100);

    if (!Array.isArray(raw.rounds) || raw.rounds.length !== raw.source.roundsObserved || raw.rounds.length > 30) fail('Round list does not match the declared capture coverage.');
    const roundNumbers = new Set();
    const aggregate = { kills: 0, deaths: 0, assists: 0, wins: 0, losses: 0 };
    for (const round of raw.rounds) {
      if (!object(round)) fail('Round must be an object.');
      const roundKeys = ['number', 'winnerTeam', 'winMethod', 'player', 'teamKills', 'opponentKills'];
      if (own(round, 'winMethodLabel')) roundKeys.push('winMethodLabel');
      exactKeys(round, roundKeys, 'Round');
      if (own(round, 'winMethodLabel') && (typeof round.winMethodLabel !== 'string' || !round.winMethodLabel.trim() || round.winMethodLabel.length > 80)) fail('Round result label is invalid.');
      integer(round.number, 'Round number', 1, 30);
      if (roundNumbers.has(round.number)) fail(`Duplicate round ${round.number}.`);
      roundNumbers.add(round.number);
      if (!['A', 'B'].includes(round.winnerTeam)) fail(`Round ${round.number} has no valid winner.`);
      if (!['ELIMINATION', 'DEFUSE', 'SPIKE_DETONATED', 'TIME', 'UNKNOWN'].includes(round.winMethod)) fail(`Round ${round.number} has an unsupported result type.`);
      exactKeys(round.player, ['kills', 'deaths', 'assists', 'score'], `Round ${round.number} player stats`);
      for (const key of ['kills', 'deaths', 'assists']) {
        integer(round.player[key], `Round ${round.number} ${key}`, 0, 20);
        aggregate[key] += round.player[key];
      }
      integer(round.player.score, `Round ${round.number} score`, 0, 2000);
      integer(round.teamKills, `Round ${round.number} team kills`, 0, 20);
      integer(round.opponentKills, `Round ${round.number} opponent kills`, 0, 20);
      if (round.winnerTeam === raw.player.team) aggregate.wins++; else aggregate.losses++;
    }
    for (let n = 1; n <= raw.rounds.length; n++) if (!roundNumbers.has(n)) fail(`Round ${n} is missing.`);
    if (aggregate.kills !== raw.player.totals.kills || aggregate.deaths !== raw.player.totals.deaths || aggregate.assists !== raw.player.totals.assists) {
      fail('Round K/D/A totals do not reconcile with the match summary.');
    }
    const scoreA = raw.rounds.filter(round => round.winnerTeam === 'A').length;
    const scoreB = raw.rounds.length - scoreA;
    if (scoreA !== raw.match.teamAScore || scoreB !== raw.match.teamBScore) fail('Round winners do not reconcile with the final score.');
    if (raw.rounds.length !== raw.match.teamAScore + raw.match.teamBScore) fail('The capture does not contain exactly one result for every scored round.');

    return {
      ...raw,
      reconciliation: { ...aggregate, rounds: raw.rounds.length, scoreA, scoreB },
      economyAvailable: false,
      eventsAvailable: false,
      benchmarkAvailable: false
    };
  }

  function parse(text) {
    let raw;
    try { raw = JSON.parse(text); } catch { fail('The selected file is not valid JSON.'); }
    return validate(raw);
  }

  function regulationPistols(match) {
    const metadata = match?.match || match;
    if (!metadata || !['competitive', 'unrated', 'premier'].includes(String(metadata.mode).toLowerCase())) return [];
    return match.rounds.filter(round => [1, 13].includes(round.number)).map(round => round.number);
  }

  function filterRounds(match, filter = 'all') {
    const pistols = new Set(regulationPistols(match));
    return match.rounds.filter(round => {
      const won = round.winnerTeam === match.player.team;
      if (filter === 'pistols') return pistols.has(round.number);
      if (filter === 'wins') return won;
      if (filter === 'losses') return !won;
      return true;
    });
  }

  const api = { validate, parse, regulationPistols, filterRounds };
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
  if (typeof window !== 'undefined') window.MatchAnalyzer = api;

  if (typeof document === 'undefined') return;
  const params = new URLSearchParams(location.search);
  if (location.pathname !== '/players' || !params.get('player')) return;
  const workspace = document.getElementById('playerProfileWorkspace');
  if (!workspace) return;
  const stylesheet = document.createElement('link');
  stylesheet.rel = 'stylesheet'; stylesheet.href = '/match-analyzer.css?v=3'; document.head.append(stylesheet);
  const section = document.createElement('section');
  section.className = 'match-analyzer'; section.setAttribute('aria-labelledby', 'matchAnalyzerTitle');
  section.innerHTML = `
    <div class="match-analyzer-heading"><div><p class="match-analyzer-kicker">MATCH REVIEW · BROWSER-ASSISTED</p><h2 id="matchAnalyzerTitle">Round-by-round match analyzer</h2><p class="match-analyzer-note">Import a local JSON capture from a Tracker match page. Data stays in your browser and is not uploaded to the Observatory.</p></div></div>
    <div class="match-analyzer-import"><label for="matchAnalyzerFile">Choose a Tracker match capture (.json)</label><input id="matchAnalyzerFile" type="file" accept="application/json,.json"><p id="matchAnalyzerStatus" role="status" aria-live="polite">No file imported. Use the browser collector below to request recent matches.</p><details><summary>How to capture a Tracker match</summary><ol><li>Open the match page on Tracker and use the same browser.</li><li><a href="/tracker-match-capture.js" download="tracker-match-capture.js">Download the browser capture helper</a>, open the downloaded file in a text editor, and copy its contents.</li><li>On the Tracker page, open Developer Tools → Console, paste the helper, and run it. Enter the Riot ID shown in the match scoreboard.</li><li>Import the downloaded JSON here. The helper reads visible scoreboard/round tables and saves only the selected player’s round stats plus team kill totals; it does not call a match API or send the capture to this app.</li></ol></details></div>
    <div class="match-analyzer-collector"><button type="button" id="matchAnalyzerCollect">Collect latest five matches</button><p>Keep the Tracker collector window open and visible. You can use other tabs in the main window.</p><p id="matchAnalyzerCollectStatus" role="status" aria-live="polite">Install the collector in Chrome, Edge or Brave. The default Tracker profile filters determine which recent matches are shown.</p><label id="matchAnalyzerCollectedLabel" hidden>Review a collected match <select id="matchAnalyzerCollected"></select></label><ul id="matchAnalyzerFailures"></ul></div>
    <div id="matchAnalyzerResult"></div>`;
  workspace.append(section);
  const input = section.querySelector('#matchAnalyzerFile');
  const status = section.querySelector('#matchAnalyzerStatus');
  const result = section.querySelector('#matchAnalyzerResult');
  let imported = null, filter = 'all', selectedRound = null;
  const escape = value => String(value ?? '—').replace(/[&<>"']/g, char => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[char]));
  const formatWinMethod = round => round.winMethod === 'UNKNOWN' && round.winMethodLabel ? 'Unknown (Tracker: ' + round.winMethodLabel + ')' : round.winMethod.toLowerCase().replaceAll('_', ' ');
  const pct = value => `${Number(value).toLocaleString()}%`;
  const detail = (match, round) => {
    if (!round) return '';
    const resultText = round.winnerTeam === match.player.team ? 'Team win' : 'Team loss';
    const teamKills = round.teamKills;
    const opponentKills = round.opponentKills;
    return `<p class="match-analyzer-selected-round">Round ${round.number}: ${resultText} by ${escape(formatWinMethod(round))} · player ${round.player.kills}/${round.player.deaths}/${round.player.assists} K/D/A · team eliminations ${teamKills}–${opponentKills}. Team side is not available in this capture.</p>`;
  };
  function render() {
    if (!imported) { result.innerHTML = ''; return; }
    const match = imported, rounds = filterRounds(match, filter), ownTeam = match.player.team;
    const ownScore = ownTeam === 'A' ? match.match.teamAScore : match.match.teamBScore;
    const opposingScore = ownTeam === 'A' ? match.match.teamBScore : match.match.teamAScore;
    const ratio = match.player.totals.deaths ? (match.player.totals.kills / match.player.totals.deaths).toFixed(2) : '—';
    const pistolNums = regulationPistols(match);
    const pistols = match.rounds.filter(round => pistolNums.includes(round.number));
    const pistolWins = pistols.filter(round => round.winnerTeam === ownTeam).length;
    const pistolKda = pistols.reduce((sum, round) => ({ kills: sum.kills + round.player.kills, deaths: sum.deaths + round.player.deaths, assists: sum.assists + round.player.assists }), { kills: 0, deaths: 0, assists: 0 });
    const selected = match.rounds.find(round => round.number === selectedRound);
    result.innerHTML = `
      <header class="match-analyzer-match-header"><div><h3>${escape(match.match.map)} · ${escape(match.match.mode)}</h3><p>${escape(match.player.riotId)} · ${escape(match.player.agent)} · Team ${ownTeam} · ${escape(match.match.displayedStartedAt)}</p><p class="match-analyzer-source"><a href="${escape(match.source.url)}" target="_blank" rel="noopener noreferrer">Source Tracker match ↗</a> · Captured ${escape(new Date(match.source.capturedAt).toLocaleString())} · ${match.reconciliation.rounds}/${match.source.roundsObserved} rounds reconciled</p></div><strong class="match-analyzer-final-score">${ownScore}–${opposingScore}<small>${ownTeam === 'A' ? 'Team A' : 'Team B'} perspective</small></strong></header>
      <div class="match-analyzer-metrics"><div><span>Match K/D/A</span><strong>${match.player.totals.kills}/${match.player.totals.deaths}/${match.player.totals.assists}</strong></div><div><span>K/D ratio</span><strong>${ratio}</strong></div><div><span>ACS · ADR</span><strong>${match.player.totals.acs} · ${match.player.totals.adr}</strong></div><div><span>HS · KAST</span><strong>${pct(match.player.totals.headshotPercent)} · ${pct(match.player.totals.kastPercent)}</strong></div></div>
      <section class="match-analyzer-pistols"><div><h4>Regulation pistol rounds</h4><p>${pistols.length ? `Rounds ${pistolNums.join(' and ')} · team won ${pistolWins} of ${pistols.length} · player ${pistolKda.kills}/${pistolKda.deaths}/${pistolKda.assists} K/D/A` : 'Pistol-round coverage is unavailable for this match.'}</p></div><div class="match-analyzer-unknown"><strong>Not captured yet</strong><span>Buy classification, round sides, opening duel identity, event timeline and Radiant comparison.</span></div></section>
      <div class="match-analyzer-table-heading"><h4>Round detail</h4><label>Show <select id="matchAnalyzerFilter"><option value="all" ${filter==='all'?'selected':''}>All rounds</option><option value="pistols" ${filter==='pistols'?'selected':''}>Pistol rounds</option><option value="wins" ${filter==='wins'?'selected':''}>Team wins</option><option value="losses" ${filter==='losses'?'selected':''}>Team losses</option></select></label></div>
      <div class="match-analyzer-table-wrap"><table><thead><tr><th>Round</th><th>Result</th><th>Player K/D/A</th><th>Score</th><th>Team eliminations</th></tr></thead><tbody>${rounds.map(round => `<tr${selectedRound===round.number?' class="is-selected"':''}><td><button type="button" class="match-analyzer-round" data-match-round="${round.number}" aria-pressed="${selectedRound===round.number}">Round ${round.number}</button></td><td><span class="match-analyzer-result ${round.winnerTeam===ownTeam?'is-win':'is-loss'}">${round.winnerTeam===ownTeam?'Win':'Loss'}</span><small>${escape(formatWinMethod(round))}${pistolNums.includes(round.number)?' · pistol':''}</small></td><td>${round.player.kills}/${round.player.deaths}/${round.player.assists}</td><td>${round.player.score}</td><td>${round.teamKills}–${round.opponentKills}</td></tr>`).join('') || '<tr><td colspan="5">No captured rounds match this filter.</td></tr>'}</tbody></table></div>
      ${detail(match, selected)}
      <p class="match-analyzer-provenance">Source method: rendered Tracker page capture; no match API is used. Zero values are preserved. Economy amounts are intentionally omitted until their labels are verified. This import is temporary and clears when this page is reloaded.</p>`;
    result.querySelector('#matchAnalyzerFilter').addEventListener('change', event => { filter = event.target.value; render(); });
    result.querySelectorAll('[data-match-round]').forEach(button => button.addEventListener('click', () => { selectedRound = Number(button.dataset.matchRound); render(); }));
  }
  const collectButton = section.querySelector('#matchAnalyzerCollect');
  const collectStatus = section.querySelector('#matchAnalyzerCollectStatus');
  const collectedLabel = section.querySelector('#matchAnalyzerCollectedLabel');
  const collectedSelect = section.querySelector('#matchAnalyzerCollected');
  const failuresList = section.querySelector('#matchAnalyzerFailures');
  const collected = [];
  let pendingCollection = false, currentJobId = null, responseTimer = null;
  const collectorOrigins = new Set(['http://127.0.0.1:8090', 'http://localhost:8090', 'http://127.0.0.1:8091', 'http://localhost:8091']);
  if (!collectorOrigins.has(location.origin)) {
    collectButton.disabled = true;
    collectStatus.textContent = 'Open this player page in your local Observatory to use the browser collector.';
  }

  function addCollected(raw) {
    const match = validate(raw);
    const requested = (params.get('player') || '').trim().toLocaleLowerCase();
    if (match.player.riotId.trim().toLocaleLowerCase() !== requested) throw new Error('Collected match belongs to a different player.');
    if (collected.some(item => item.match.id === match.match.id)) return;
    if (collected.length >= 5) throw new Error('A collection can contain at most five matches.');
    collected.push(match);
    const option = document.createElement('option');
    option.value = match.match.id;
    option.textContent = match.match.map + ' · ' + match.match.displayedStartedAt + ' · ' + match.match.teamAScore + '–' + match.match.teamBScore;
    collectedSelect.appendChild(option);
    collectedLabel.hidden = false;
    if (!imported || collected.length === 1) {
      imported = match; filter = 'all'; selectedRound = null; collectedSelect.value = match.match.id; render();
    }
  }
  collectedSelect.addEventListener('change', () => {
    const selected = collected.find(match => match.match.id === collectedSelect.value);
    if (selected) { imported = selected; filter = 'all'; selectedRound = null; render(); }
  });
  collectButton.addEventListener('click', () => {
    if (pendingCollection) return;
    pendingCollection = true; currentJobId = null; collectButton.disabled = true;
    collected.length = 0; collectedSelect.replaceChildren(); collectedLabel.hidden = true; failuresList.replaceChildren();
    collectStatus.textContent = 'Starting the browser collector…';
    clearTimeout(responseTimer);
    responseTimer = setTimeout(() => {
      if (!currentJobId && pendingCollection) {
        pendingCollection = false; collectButton.disabled = false;
        collectStatus.textContent = 'No extension response. Install or enable the Observatory Tracker collector in this browser, then try again.';
      }
    }, 5000);
  });
  window.addEventListener('message', event => {
    if (event.source !== window || event.origin !== location.origin || event.data?.source !== 'observatory-tracker-extension' || !pendingCollection) return;
    const update = event.data;
    if (update.type === 'started') {
      if (typeof update.jobId !== 'string') return;
      currentJobId = update.jobId; clearTimeout(responseTimer);
      collectStatus.textContent = 'Collector started. Keep the Tracker collector window open and visible.';
      return;
    }
    if (update.type === 'error' && !currentJobId) {
      clearTimeout(responseTimer); pendingCollection = false; collectButton.disabled = false;
      collectStatus.textContent = String(update.message || 'Collector could not start.').slice(0, 500);
      return;
    }
    if (!currentJobId || update.jobId !== currentJobId || !['progress', 'finished'].includes(update.type)) return;
    try {
      if (update.capture) addCollected(update.capture);
      if (Array.isArray(update.captures)) update.captures.slice(0, 5).forEach(addCollected);
      if (Array.isArray(update.failures)) {
        failuresList.replaceChildren();
        update.failures.slice(0, 10).forEach(failure => {
          const item = document.createElement('li');
          item.textContent = 'Match ' + String(failure.index || '?') + ': ' + String(failure.message || 'Capture failed').slice(0, 300);
          failuresList.appendChild(item);
        });
      }
      collectStatus.textContent = String(update.message || 'Collector updated.').slice(0, 500);
    } catch (error) {
      collectStatus.textContent = 'A collected match was rejected: ' + (error instanceof Error ? error.message : 'invalid data');
    }
    if (update.type === 'finished') { pendingCollection = false; collectButton.disabled = false; currentJobId = null; }
  });  input.addEventListener('change', async () => {
    const file = input.files?.[0];
    if (!file) return;
    if (file.size > 1024 * 1024) { status.textContent = 'File is too large. Match captures must be 1 MiB or smaller.'; input.value = ''; return; }
    try {
      const match = parse(await file.text());
      const requestedPlayer = (params.get('player') || '').trim().toLocaleLowerCase();
      if (requestedPlayer && requestedPlayer !== match.player.riotId.trim().toLocaleLowerCase()) fail(`This capture is for ${match.player.riotId}; open that player’s profile before importing it.`);
      imported = match; filter = 'all'; selectedRound = null;
      status.textContent = `Validated ${match.reconciliation.rounds} rounds. K/D/A and ${match.reconciliation.scoreA}–${match.reconciliation.scoreB} round outcomes match the summary.`;
      render();
    } catch (error) {
      imported = null; render(); status.textContent = error instanceof Error ? error.message : 'The capture could not be validated.';
    }
  });
})();
