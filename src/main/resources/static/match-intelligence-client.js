(() => {
  'use strict';
  const fail = message => { throw new Error(message); };
  const same = (a, b) => String(a || '').trim().toLocaleLowerCase() === String(b || '').trim().toLocaleLowerCase();

  function validateCollectorExport(value, requestedPlayer) {
    if (!value || typeof value !== 'object' || Array.isArray(value) || value.schemaVersion !== 1 || value.source !== 'tracker.gg' ||
        !same(value.player, requestedPlayer) || !Array.isArray(value.matches) || value.matches.length < 1 || value.matches.length > 5) {
      fail('Collector import must contain one to five responses for the selected player.');
    }
    const bytes = new TextEncoder().encode(JSON.stringify(value)).byteLength;
    if (bytes > 25 * 1024 * 1024) fail('Collector import exceeds the 25 MiB upload limit.');
    const seen = new Set();
    for (const item of value.matches) {
      if (!item || item.validation !== 'passed' || !/^[0-9a-f-]{36}$/i.test(item.matchId || '') || seen.has(item.matchId.toLowerCase())) {
        fail('Collector import contains an invalid or duplicate match response.');
      }
      seen.add(item.matchId.toLowerCase());
      const data = item.response?.data;
      const playerPresent = Array.isArray(data?.segments) && data.segments.some(segment => segment?.type === 'player-summary' &&
        typeof segment.attributes?.platformUserIdentifier === 'string' && same(segment.attributes.platformUserIdentifier, value.player));
      if (String(data?.attributes?.id || '').toLowerCase() !== item.matchId.toLowerCase() || !playerPresent) {
        fail('Collector response identity does not match the selected player and match.');
      }
    }
    return value;
  }

  async function uploadCollectorExport(value, requestedPlayer, fetcher = fetch) {
    validateCollectorExport(value, requestedPlayer);
    const csrfResponse = await fetcher('/api/leaderboard/csrf', { credentials: 'same-origin' });
    if (!csrfResponse.ok) fail('Sign in to the Observatory before saving collected matches.');
    const csrf = await csrfResponse.json();
    if (typeof csrf?.header !== 'string' || typeof csrf?.token !== 'string') fail('Could not prepare the secure match import.');
    const response = await fetcher('/api/match-intelligence/imports', {
      method: 'POST', credentials: 'same-origin',
      headers: { 'Content-Type': 'application/json', [csrf.header]: csrf.token },
      body: JSON.stringify(value)
    });
    if (!response.ok) {
      let message = 'The match import was rejected.';
      try { message = (await response.json()).message || message; } catch { }
      fail(String(message).slice(0, 300));
    }
    return response.json();
  }

  const api = { validateCollectorExport, uploadCollectorExport };
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
  if (typeof window !== 'undefined') window.MatchIntelligenceClient = api;
})();
