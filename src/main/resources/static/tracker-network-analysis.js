(() => {
  'use strict';
  const value = (segment, name) => segment?.stats?.[name]?.value;
  const text = x => typeof x === 'string' ? x : '';
  function analyze(body, matchId, player) {
    const data = body?.data;
    if (!data || text(data.attributes?.id).toLowerCase() !== matchId.toLowerCase() || !Array.isArray(data.segments) || data.segments.length > 20000) throw new Error('Invalid match response or match ID.');
    const key = player.trim().toLocaleLowerCase();
    const types = type => data.segments.filter(s => s && s.type === type);
    const summary = types('player-summary').find(s => text(s.attributes?.platformUserIdentifier).toLocaleLowerCase() === key);
    const own = types('player-round').filter(s => text(s.attributes?.platformUserIdentifier).toLocaleLowerCase() === key);
    const results = types('round-summary');
    const expected = data.metadata?.rounds;
    if (!summary || !Number.isInteger(expected) || expected < 1 || expected > 30 || own.length !== expected || results.length !== expected) throw new Error('Response lacks complete selected-player round coverage.');
    const rounds = [];
    for (let n = 1; n <= expected; n++) {
      const pr = own.filter(s => s.attributes?.round === n), rr = results.filter(s => s.attributes?.round === n);
      if (pr.length !== 1 || rr.length !== 1) throw new Error('Duplicate or missing round.');
      const s = pr[0], team = s.metadata?.teamId, winner = value(rr[0], 'winningTeam');
      if (!['Blue','Red'].includes(team) || !['Blue','Red'].includes(winner)) throw new Error('Round team or winner unavailable.');
      const read = name => { const v = value(s, name); if (!Number.isInteger(v) || v < 0 || v > 10000) throw new Error('Invalid round ' + name); return v; };
      rounds.push({ number:n, won:team === winner, side:['attacker','defender'].includes(s.metadata?.teamSide) ? s.metadata.teamSide : null, kills:read('kills'), deaths:read('deaths'), assists:read('assists'), damage:read('damage'), loadout:Number.isFinite(value(s,'loadoutValue')) ? value(s,'loadoutValue') : null });
    }
    for (const name of ['kills','deaths','assists']) { const total=value(summary,name); if (!Number.isInteger(total) || rounds.reduce((a,r)=>a+r[name],0)!==total) throw new Error('Round ' + name + ' does not reconcile with match summary.'); }
    const groups = (list) => ({ rounds:list.length, wins:list.filter(r=>r.won).length, damage:list.reduce((a,r)=>a+r.damage,0), kills:list.reduce((a,r)=>a+r.kills,0), deaths:list.reduce((a,r)=>a+r.deaths,0), assists:list.reduce((a,r)=>a+r.assists,0) });
    return { matchId, player, map:text(data.metadata?.mapName).slice(0,80), agent:text(summary.metadata?.agentName).slice(0,80), rounds, overall:groups(rounds), pistol:groups(rounds.filter(r=>r.number===1||r.number===13)), attack:groups(rounds.filter(r=>r.side==='attacker')), defense:groups(rounds.filter(r=>r.side==='defender')), eventSegments:types('player-round-kills').length, extended:data.metadata?.extendedDataIncluded === true };
  }
  if (typeof module !== 'undefined' && module.exports) module.exports = { analyze };
  if (typeof globalThis !== 'undefined') globalThis.TrackerNetworkAnalysis = { analyze };
})();
