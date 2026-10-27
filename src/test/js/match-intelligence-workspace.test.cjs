'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const { summarize, filterRounds } = require('../../main/resources/static/match-intelligence-workspace.js');
function detail(id, {score=13, enemy=8, mode='Competitive', kills=10, agent='Jett', map='Ascent', side='Attack'}={}) {
  const identity={stableKey:'player-key',observedRiotId:'Player#APAC'};
  return { perspective:{identity:'player-key',riotId:'Player#APAC',teamId:'A'}, match:{providerMatchId:id,modeName:mode,mapName:map,teams:[{teamId:'A',roundsWon:score},{teamId:'B',roundsWon:enemy}],participants:[{identity,agent,totals:{kills,deaths:5,assists:3,acs:240,adr:150,firstKills:2,firstDeaths:1}}],rounds:[{roundNumber:1,winningTeamId:'A'},{roundNumber:13,winningTeamId:'B'}],playerRounds:[{identity,roundNumber:1,side,loadoutValue:800,spentCredits:500,remainingCredits:100},{identity,roundNumber:13,side:'Defense',loadoutValue:null,spentCredits:null,remainingCredits:null}]}};
}
test('overview aggregates observed data and keeps unknown outcomes out of losses', () => {
  const matches=[{providerMatchId:'one'},{providerMatchId:'two'}];
  const details=new Map([['one',detail('one')],['two',detail('two',{score:null,enemy:null,mode:'Deathmatch',kills:4,agent:'Sova',map:'Bind'})]]);
  const actual=summarize(matches,details);
  assert.equal(actual.sample,2); assert.equal(actual.wins,1); assert.equal(actual.losses,0); assert.equal(actual.unknownOutcomes,1);
  assert.equal(actual.kills,14); assert.equal(actual.averageAcs,240); assert.equal(actual.pistolRounds,2); assert.equal(actual.pistolWins,1);
  assert.equal(actual.economyRows,2); assert.equal(actual.averageSpend,500);
  assert.deepEqual(actual.maps,[['Ascent',1],['Bind',1]]); assert.deepEqual(actual.agents,[['Jett',1],['Sova',1]]);
});
test('round filters use selected team, regulation pistols, overtime and reported side', () => {
  const rounds=[{roundNumber:1,winningTeamId:'A',overtimeByRoundPosition:false},{roundNumber:2,winningTeamId:'B',overtimeByRoundPosition:false},{roundNumber:25,winningTeamId:'A',overtimeByRoundPosition:true}];
  const playerRounds=[{roundNumber:1,side:'attack'},{roundNumber:2,side:'Defense'},{roundNumber:25,side:'Attack'}];
  assert.deepEqual(filterRounds(rounds,'A','wins',playerRounds).map(r=>r.roundNumber),[1,25]);
  assert.deepEqual(filterRounds(rounds,'A','losses',playerRounds).map(r=>r.roundNumber),[2]);
  assert.deepEqual(filterRounds(rounds,'A','pistols',playerRounds).map(r=>r.roundNumber),[1]);
  assert.deepEqual(filterRounds(rounds,'A','overtime',playerRounds).map(r=>r.roundNumber),[25]);
  assert.deepEqual(filterRounds(rounds,'A','attack',playerRounds).map(r=>r.roundNumber),[1,25]);
  assert.deepEqual(filterRounds(rounds,'A','defense',playerRounds).map(r=>r.roundNumber),[2]);
});

