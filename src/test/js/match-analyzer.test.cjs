'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const { validate, regulationPistols, filterRounds } = require('../../main/resources/static/match-analyzer.js');

function fixture() {
  const rounds = Array.from({ length: 18 }, (_, index) => ({
    number: index + 1,
    winnerTeam: index < 13 ? 'A' : 'B',
    winMethod: 'ELIMINATION',
    player: { kills: index === 0 ? 0 : index === 1 ? 1 : 0, deaths: index === 0 ? 1 : 0, assists: index === 1 ? 1 : 0, score: 0 },
    teamKills: 2,
    opponentKills: 1
  }));
  rounds[12].player.kills = 1;
  rounds[12].winnerTeam = 'A';
  rounds[13].winnerTeam = 'B';
  rounds[14].winnerTeam = 'B';
  rounds[15].winnerTeam = 'B';
  rounds[16].winnerTeam = 'B';
  rounds[17].winnerTeam = 'B';
  const wins = rounds.filter(round => round.winnerTeam === 'A').length;
  return {
    schemaVersion: 1,
    source: { provider: 'tracker.gg', method: 'rendered-page', url: 'https://tracker.gg/valorant/match/96d890bc-2361-4c12-a3e3-d0ab48795641', capturedAt: '2026-10-27T11:00:00.000Z', parserVersion: 'test', roundsObserved: rounds.length },
    match: { id: '96d890bc-2361-4c12-a3e3-d0ab48795641', mode: 'Competitive', map: 'Corrode', displayedStartedAt: '10/26/26, 4:31 PM', durationSeconds: 1501, teamAScore: wins, teamBScore: rounds.length - wins, overtime: false },
    player: { riotId: 'the chosen one#mØØda', team: 'A', agent: 'Jett', totals: { kills: 2, deaths: 1, assists: 1, acs: 279, adr: 187.8, headshotPercent: 44, kastPercent: 89, firstKills: 1, firstDeaths: 3 } },
    rounds
  };
}

test('validates round coverage and preserves observed zero values', () => {
  const result = validate(fixture());
  assert.equal(result.reconciliation.kills, 2);
  assert.equal(result.rounds[0].player.kills, 0);
  assert.equal(result.economyAvailable, false);
});

test('rejects unsupported schema versions and unexpected fields', () => {
  const wrongVersion = fixture(); wrongVersion.schemaVersion = 2;
  assert.throws(() => validate(wrongVersion), /schema version/);
  const extra = fixture(); extra.rounds[0].hiddenPayload = { private: true };
  assert.throws(() => validate(extra), /unsupported fields/);
});

test('rejects duplicate, missing, or out-of-range rounds', () => {
  const duplicate = fixture(); duplicate.rounds[1].number = 1;
  assert.throws(() => validate(duplicate), /Duplicate round/);
  const missing = fixture(); missing.rounds[2].number = 19;
  assert.throws(() => validate(missing), /missing/);
  const tooMany = fixture(); tooMany.rounds[0].number = 31;
  assert.throws(() => validate(tooMany), /Round number/);
});

test('rejects round KDA or final-score reconciliation failures', () => {
  const kda = fixture(); kda.rounds[0].player.kills = 1;
  assert.throws(() => validate(kda), /K\/D\/A totals/);
  const score = fixture(); score.match.teamAScore++;
  assert.throws(() => validate(score), /final score/);
});

test('supports zero-death matches without inventing an infinite ratio', () => {
  const match = fixture();
  match.rounds.forEach(round => { round.player.deaths = 0; });
  match.player.totals.deaths = 0;
  const result = validate(match);
  assert.equal(result.player.totals.deaths, 0);
});

test('identifies regulation pistol rounds and filters wins/losses from the player team', () => {
  const match = validate(fixture());
  assert.deepEqual(regulationPistols(match), [1, 13]);
  assert.deepEqual(filterRounds(match, 'pistols').map(round => round.number), [1, 13]);
  assert.equal(filterRounds(match, 'wins').length, 13);
  assert.equal(filterRounds(match, 'losses').length, 5);
});

test('refuses a source URL whose match ID does not match the imported record', () => {
  const mismatch = fixture(); mismatch.match.id = 'aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee';
  assert.throws(() => validate(mismatch), /Match ID must match/);
});

test('rejects lookalike source paths and non-Tracker hosts', () => {
  const wrongPath = fixture(); wrongPath.source.url += '-extra';
  assert.throws(() => validate(wrongPath), /Source URL/);
  const wrongHost = fixture(); wrongHost.source.url = wrongHost.source.url.replace('tracker.gg', 'tracker.example');
  assert.throws(() => validate(wrongHost), /Source URL/);
});

test('normalizes verified Tracker round results and retains unknown labels', () => {
  const { normalizeTrackerWinMethod } = require('../../main/resources/static/tracker-match-capture.js');
  assert.equal(normalizeTrackerWinMethod('DETONATE'), 'SPIKE_DETONATED');
  assert.equal(normalizeTrackerWinMethod('ELIMINATION'), 'ELIMINATION');
  assert.equal(normalizeTrackerWinMethod('DEFUSE'), 'DEFUSE');
  assert.equal(normalizeTrackerWinMethod('unfamiliar outcome'), 'UNKNOWN');
  const match = fixture();
  match.rounds[0].winMethod = 'UNKNOWN';
  match.rounds[0].winMethodLabel = 'UNFAMILIAR OUTCOME';
  assert.equal(validate(match).rounds[0].winMethodLabel, 'UNFAMILIAR OUTCOME');
  const invalidRound = fixture(); invalidRound.rounds[0] = null;
  assert.throws(() => validate(invalidRound), /Round must be an object/);
  match.rounds[0].winMethodLabel = 'x'.repeat(81);
  assert.throws(() => validate(match), /result label/i);
});
