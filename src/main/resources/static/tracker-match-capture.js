/* Browser-assisted capture helper for the visible Tracker VALORANT match page.
 * Paste into the Tracker page's developer console. It reads rendered UI only,
 * clicks the visible round selector, and downloads a local JSON file.
 */
function normalizeTrackerWinMethod(label) {
  const normalized = String(label).toUpperCase().replace(/[^A-Z]+/g, '_').replace(/^_|_$/g, '');
  const methods = { ELIMINATION: 'ELIMINATION', DEFUSE: 'DEFUSE', DETONATE: 'SPIKE_DETONATED', SPIKE_DETONATED: 'SPIKE_DETONATED', TIME: 'TIME' };
  return Object.prototype.hasOwnProperty.call(methods, normalized) ? methods[normalized] : 'UNKNOWN';
}

async function captureTrackerMatch(playerInput, sourceUrl = location.href, isCancelled = () => false) {
  const sleep = ms => new Promise(resolve => setTimeout(resolve, ms));
  const readNumber = value => {
    if (value === null || value === undefined || String(value).trim() === '') throw new Error('A required numeric value is missing.');
    const parsed = Number(String(value).replace(/[,%+\s]/g, ''));
    if (!Number.isFinite(parsed)) throw new Error(`Could not read numeric value: ${value}`);
    return parsed;
  };
  const readCell = cell => cell?.querySelector('.value')?.innerText?.trim() ?? cell?.innerText?.trim() ?? '';
  const decodeRiotId = href => {
    const encoded = href?.split('/valorant/profile/riot/')[1]?.split('?')[0];
    if (!encoded) return '';
    try { return decodeURIComponent(encoded); } catch { return ''; }
  };
  {
    const source = new URL(sourceUrl);
    if (source.origin !== 'https://tracker.gg') throw new Error('Match source must be tracker.gg.');
    sourceUrl = source.href;
    const matchId = source.pathname.match(/^\/valorant\/match\/([0-9a-f-]{8,64})\/?$/i)?.[1];
    if (!matchId) throw new Error('Open a Tracker VALORANT match page before running this helper.');
    const mode = document.querySelector('.vm-header-info .trn-match-drawer__header-label')?.innerText?.trim();
    const map = document.querySelector('.vm-header-info .trn-match-drawer__header-value')?.innerText?.trim();
    const scoreBlocks = [...document.querySelectorAll('.vm-header-score > .trn-match-drawer__header-block')];
    const scoreA = readNumber(scoreBlocks[0]?.querySelector('.trn-match-drawer__header-value')?.innerText);
    const scoreB = readNumber(scoreBlocks[2]?.querySelector('.trn-match-drawer__header-value')?.innerText);
    const headerText = document.querySelector('.vm-header')?.innerText || '';
    const startMatch = headerText.match(/\b\d{1,2}\/\d{1,2}\/\d{2,4},?\s+\d{1,2}:\d{2}\s*[AP]M\b/i);
    const durationMatch = headerText.match(/\b(?:(\d+)m\s*)?(\d+)s\b/i);
    if (!mode || !map || !Number.isFinite(scoreA) || !Number.isFinite(scoreB) || !durationMatch) throw new Error('The rendered match header changed or is incomplete. No capture was created.');
    const displayedStartedAt = startMatch?.[0] || '';
    const durationSeconds = Number(durationMatch[1] || 0) * 60 + Number(durationMatch[2]);

    const tab = label => [...document.querySelectorAll('.trn-match-drawer__tabs-tab')].find(node => node.innerText.trim() === label);
    const scoreboardTab = tab('Scoreboard'), roundsTab = tab('Rounds');
    if (!scoreboardTab || !roundsTab) throw new Error('Expected Scoreboard and Rounds tabs were not found.');
    scoreboardTab.click();
    await sleep(250);
    if (!playerInput) throw new Error('Capture cancelled.');
    const requestedId = playerInput.trim().toLocaleLowerCase();
    const scoreTable = [...document.querySelectorAll('.st-valorant-team-Red,.st-valorant-team-Blue')].find(table =>
      [...table.querySelectorAll('.st-content__item a.info[href*="/valorant/profile/riot/"]')].some(link => decodeRiotId(link.getAttribute('href')).toLocaleLowerCase() === requestedId));
    const scoreRow = scoreTable && [...scoreTable.querySelectorAll('.st-content__item')].find(row => {
      const link = row.querySelector('a.info[href*="/valorant/profile/riot/"]');
      return decodeRiotId(link?.getAttribute('href')).toLocaleLowerCase() === requestedId;
    });
    if (!scoreTable || !scoreRow) throw new Error('That Riot ID was not found in the visible scoreboard.');
    const headerCells = [...scoreTable.querySelector('.st-header').children].map(cell => cell.innerText.trim());
    const rowCells = [...scoreRow.querySelectorAll(':scope > .st-content__item-value')];
    const stats = Object.fromEntries(headerCells.map((label, index) => [label, readCell(rowCells[index])]));
    const playerAgent = scoreRow.querySelector('img[alt]')?.alt;
    const riotId = decodeRiotId(scoreRow.querySelector('a.info')?.getAttribute('href'));
    const playerTeam = scoreTable.querySelector('.st-header')?.innerText.match(/Team\s+([AB])/i)?.[1]?.toUpperCase();
    if (!playerAgent || !playerTeam || !stats.K || !stats.D || !stats.A || !stats.ACS || !stats.ADR || !stats['HS%'] || !stats.KAST || !stats.FK || !stats.FD) throw new Error('The player summary columns did not match the expected Tracker layout.');

    roundsTab.click();
    await sleep(250);
    const roundStrip = document.querySelector('.vmr-header')?.previousElementSibling;
    const roundButtons = [...(roundStrip?.children || [])];
    if (roundButtons.length < 1 || roundButtons.length > 30) throw new Error('The rendered round selector has an unsupported size.');
    const rounds = [];
    const tables = () => [...document.querySelectorAll('.st-valorant-team-Red,.st-valorant-team-Blue')];
    for (let index = 0; index < roundButtons.length; index++) {
      if (isCancelled()) throw new Error('Capture stopped.');
      roundButtons[index].click();
      let heading = '';
      for (let attempt = 0; attempt < 20; attempt++) {
        await sleep(100);
        heading = document.querySelector('.vmr-header-left')?.innerText || '';
        if (heading.match(new RegExp(`ROUND\\s+${index + 1}\\b`, 'i'))) break;
      }
      if (!heading.match(new RegExp(`ROUND\\s+${index + 1}\\b`, 'i'))) throw new Error(`Round ${index + 1} did not render.`);
      const outcome = heading.match(/TEAM\s+([AB])\s+(.+?)\s+WIN/i);
      if (!outcome) throw new Error(`Round ${index + 1} result did not render. Capture stopped without downloading a file.`);
      const winMethodLabel = outcome[2].trim();
      if (!winMethodLabel || winMethodLabel.length > 80) throw new Error('Round result label is invalid.');
      const winMethod = normalizeTrackerWinMethod(winMethodLabel);
      const teamStats = new Map();
      let playerRow = null;
      for (const table of tables()) {
        const team = table.querySelector('.st-header')?.innerText.match(/Team\s+([AB])/i)?.[1]?.toUpperCase();
        if (!team) throw new Error(`Round ${index + 1} team label is missing.`);
        let kills = 0;
        const rows = [...table.querySelectorAll('.st-content__item')].filter(row => row.querySelector('a.info[href*="/valorant/profile/riot/"]'));
        for (const row of rows) {
          const link = row.querySelector('a.info[href*="/valorant/profile/riot/"]');
          const id = decodeRiotId(link?.getAttribute('href'));
          const cells = [...row.querySelectorAll(':scope > .st-content__item-value')];
          if (cells.length < 5) throw new Error(`Round ${index + 1} row columns changed.`);
          kills += readNumber(readCell(cells[2]));
          if (id.toLocaleLowerCase() === requestedId) playerRow = { team, cells };
        }
        teamStats.set(team, kills);
      }
      if (!playerRow) throw new Error(`Selected player is absent from round ${index + 1}.`);
      const ownCells = playerRow.cells;
      const ownKda = [2, 3, 4].map(position => readNumber(readCell(ownCells[position])));
      const isA = playerTeam === 'A';
      rounds.push({
        number: index + 1,
        winnerTeam: outcome[1].toUpperCase(),
        winMethod,
        ...(winMethod === 'UNKNOWN' ? { winMethodLabel } : {}),
        player: { kills: ownKda[0], deaths: ownKda[1], assists: ownKda[2], score: readNumber(readCell(ownCells[1])) },
        teamKills: teamStats.get(isA ? 'A' : 'B'),
        opponentKills: teamStats.get(isA ? 'B' : 'A')
      });
    }
    const payload = {
      schemaVersion: 1,
      source: { provider: 'tracker.gg', method: 'rendered-page', url: sourceUrl, capturedAt: new Date().toISOString(), parserVersion: 'tracker-rendered-v1', roundsObserved: rounds.length },
      match: { id: matchId, mode, map, displayedStartedAt, durationSeconds, teamAScore: scoreA, teamBScore: scoreB, overtime: rounds.length > 24 },
      player: {
        riotId, team: playerTeam, agent: playerAgent,
        totals: { kills: readNumber(stats.K), deaths: readNumber(stats.D), assists: readNumber(stats.A), acs: readNumber(stats.ACS), adr: readNumber(stats.ADR), headshotPercent: readNumber(stats['HS%']), kastPercent: readNumber(stats.KAST), firstKills: readNumber(stats.FK), firstDeaths: readNumber(stats.FD) }
      },
      rounds
    };
    return payload;
  }
}
if (typeof module !== 'undefined' && module.exports) module.exports = { captureTrackerMatch, normalizeTrackerWinMethod };
if (typeof window !== 'undefined') {
  globalThis.TrackerMatchCapture = { capture: captureTrackerMatch };
  if (!globalThis.__trackerCollectorMode) {
    (async () => {
      try {
        const player = window.prompt('Enter the exact Riot ID from the scoreboard (name#tag):');
        if (!player) return;
        const payload = await captureTrackerMatch(player, location.href);
        const blob = new Blob([JSON.stringify(payload, null, 2)], { type: 'application/json' });
        const link = document.createElement('a'); link.href = URL.createObjectURL(blob);
        link.download = `tracker-match-${payload.match.id}-${payload.player.team}.json`; link.click();
        setTimeout(() => URL.revokeObjectURL(link.href), 30000);
      } catch (error) { window.alert(error instanceof Error ? error.message : 'Tracker capture failed.'); }
    })();
  }
}
