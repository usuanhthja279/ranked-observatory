'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const { summarize, filterRounds, filterEventPositions, groupPositionEvents, equipmentByRound, equipmentChart, resolveMap, projectPositions } = require('../../main/resources/static/match-intelligence-workspace.js');
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

test('round evidence filters identify unique player openings, multi-kills, objectives and review findings', () => {
  const rounds=[
    {roundNumber:1,winningTeamId:'A'},
    {roundNumber:2,winningTeamId:'B'},
    {roundNumber:3,winningTeamId:'A'},
    {roundNumber:4,winningTeamId:'B'}
  ];
  const playerRounds=[
    {roundNumber:1,kills:2}, {roundNumber:2,kills:1},
    {roundNumber:3,kills:0}, {roundNumber:4,kills:3}
  ];
  const event=(roundNumber,category,roundTimeRaw,actor,target)=>({roundNumber,category,roundTimeRaw,actor:actor&&{stableKey:actor},target:target&&{stableKey:target}});
  const events=[
    event(1,'CREDITED_PLAYER_KILL',100,'player-key','opponent'),
    event(1,'CREDITED_PLAYER_KILL',200,'teammate','opponent-2'),
    event(2,'CREDITED_PLAYER_KILL',75,'enemy','player-key'),
    event(3,'CREDITED_PLAYER_KILL',50,'enemy','player-key'),
    event(3,'CREDITED_PLAYER_KILL',50,'teammate','enemy-2'),
    event(4,'PLANT',300,'teammate',null)
  ];
  const findings=[{roundNumber:2,title:'Evidence-linked review'}];
  const filter=(mode)=>filterRounds(rounds,'A',mode,playerRounds,events,'player-key',findings).map(round=>round.roundNumber);
  assert.deepEqual(filter('opening-kill'),[1]);
  assert.deepEqual(filter('opening-death'),[2]);
  assert.deepEqual(filter('multi-kill'),[1,4]);
  assert.deepEqual(filter('objective'),[4]);
  assert.deepEqual(filter('plant'),[4]); // only explicitly classified PLANT events count
  assert.deepEqual(filter('review'),[2]);
  assert.deepEqual(filterRounds(rounds,'A','opening-death',playerRounds,[
    event(3,'CREDITED_PLAYER_KILL',50,'enemy','player-key'),
    event(3,'CREDITED_PLAYER_KILL',50,'teammate','enemy-2')
  ],'player-key').map(round=>round.roundNumber),[]); // tied earliest events are not assigned as an opening
});

test('team equipment series sums only complete recorded player loadouts and preserves missing rounds', () => {
  const identity=key=>({stableKey:key});
  const match={
    participants:[{teamId:'A',identity:identity('a1')},{teamId:'A',identity:identity('a2')},{teamId:'B',identity:identity('b1')},{teamId:'B',identity:identity('b2')}],
    rounds:[{roundNumber:1},{roundNumber:2}],
    playerRounds:[
      {roundNumber:1,identity:identity('a1'),loadoutValue:4000},{roundNumber:1,identity:identity('a2'),loadoutValue:3500},
      {roundNumber:1,identity:identity('b1'),loadoutValue:4200},{roundNumber:1,identity:identity('b2'),loadoutValue:3300},
      {roundNumber:2,identity:identity('a1'),loadoutValue:5000},{roundNumber:2,identity:identity('a2'),loadoutValue:null},
      {roundNumber:2,identity:identity('b1'),loadoutValue:5000},{roundNumber:2,identity:identity('b2'),loadoutValue:5000}
    ]
  };
  assert.deepEqual(equipmentByRound(match),[
    {roundNumber:1,teams:{A:7500,B:7500}},
    {roundNumber:2,teams:{A:null,B:10000}}
  ]);
  const chart=equipmentChart(match,'A');
  assert.match(chart,/<svg viewBox=/);
  assert.match(chart,/role="img" aria-label="Recorded equipment value by round for both teams"/);
  assert.match(chart,/Round 1, Team A: 7,500 recorded equipment value/);
  assert.match(chart,/Round 1, Team B: 7,500 recorded equipment value/);
  assert.doesNotMatch(chart,/Round 2, Team B:/); // one incomplete roster leaves the comparison blank
  assert.match(chart,/Incomplete team rounds are left blank/);
  assert.deepEqual(equipmentByRound({...match,participants:match.participants.slice(0,2)}),[]);
});


test('event position inspection returns only the exact source reference and preserves all-round view', () => {
  const positions=[{sourceRef:'kill-1',x:10,y:20},{sourceRef:'plant-1',x:30,y:40},{sourceRef:'kill-2',x:50,y:60}];
  assert.deepEqual(filterEventPositions(positions,'kill-1'),[positions[0]]);
  assert.deepEqual(filterEventPositions(positions,'missing-event'),[]);
  assert.deepEqual(filterEventPositions(positions,''),positions);
});

test('position event choices deduplicate paired kill/death rows by exact source reference', () => {
  const death={sourceRef:'event-1',category:'PLAYER_DEATH'},kill={sourceRef:'event-1',category:'CREDITED_PLAYER_KILL'},plant={sourceRef:'event-2',category:'SPIKE_PLANT'},unlinked={category:'UNKNOWN'};
  assert.deepEqual(groupPositionEvents([death,kill,plant,unlinked]),[kill,plant]);
});

const verifiedMap={name:'Test map',mapId:'map-1',asset:'/maps/map-1.png',width:1024,height:1024,validFrom:'2026-10-13',xMultiplier:0.01,yMultiplier:-0.01,xScalarToAdd:0.5,yScalarToAdd:0.5};
const mapMatch=overrides=>({mapId:'MAP-1',startedAt:'2026-10-20T00:00:00Z',mapTransform:{xMultiplier:0.01,yMultiplier:-0.01,xScalarToAdd:0.5,yScalarToAdd:0.5},...overrides});
test('map overlay requires exact map ID, a valid match date and matching catalog transform',()=>{
  const catalog={validThrough:'2026-10-26',maps:[verifiedMap]};
  assert.equal(resolveMap(mapMatch({}),catalog).map,verifiedMap);
  assert.match(resolveMap(mapMatch({startedAt:'2026-10-12T23:59:59Z'}),catalog).reason,/predates/);
  assert.match(resolveMap(mapMatch({startedAt:'2026-10-27T00:00:00Z'}),catalog).reason,/newer than the verified map catalog/);
  assert.match(resolveMap(mapMatch({}),{maps:[verifiedMap]}).reason,/validity window/);
  assert.match(resolveMap(mapMatch({mapId:'other'}),catalog).reason,/map ID/);
  assert.match(resolveMap(mapMatch({mapTransform:{...verifiedMap,xMultiplier:0.02}}),catalog).reason,/transform/);
  assert.match(resolveMap(mapMatch({startedAt:null}),catalog).reason,/date/);
  assert.match(resolveMap(mapMatch({mapTransform:null}),catalog).reason,/transform/);
});
test('position projection plots only supported coordinates and reports outside or unavailable points',()=>{
  const points=projectPositions([
    {x:0,y:0,coordinateSpace:'TRACKER_WORLD_RAW'},
    {x:50,y:0,coordinateSpace:'TRACKER_WORLD_RAW'},
    {x:50.00000001,y:0,coordinateSpace:'TRACKER_WORLD_RAW'},
    {x:50.00001,y:0,coordinateSpace:'TRACKER_WORLD_RAW'},
    {x:0,y:100,coordinateSpace:'OTHER'},
    {x:NaN,y:0,coordinateSpace:'TRACKER_WORLD_RAW'},
    {x:0,y:50,coordinateSpace:'TRACKER_WORLD_RAW'}
  ],verifiedMap);
  assert.deepEqual(points.map(point=>point.placement),['plotted','plotted','plotted','outside','unavailable','unavailable','plotted']);
  assert.deepEqual([points[0].x,points[0].y],[0.5,0.5]);
  assert.deepEqual([points[1].x,points[1].y],[1,0.5]);
  assert.deepEqual([points[6].x,points[6].y],[0.5,0]);
});

test('Riot tactical map manifest assets match their recorded hashes and dimensions',()=>{
  const staticRoot=path.resolve(__dirname,'../../main/resources/static');
  const catalog=JSON.parse(fs.readFileSync(path.join(staticRoot,'maps/map-manifest-13.08.json'),'utf8'));
  assert.equal(catalog.maps.length,13);
  assert.match(catalog.sourceUrl,/valorant\.dyn\.riotcdn\.net/);
  assert.match(catalog.licenseNoticeUrl,/support-developer\.riotgames\.com/);
  for(const map of catalog.maps){
    const image=fs.readFileSync(path.join(staticRoot,map.asset.replace(/^\//,'')));
    assert.deepEqual([...image.subarray(0,8)],[137,80,78,71,13,10,26,10],map.name+' PNG signature');
    assert.equal(image.readUInt32BE(16),map.width,map.name+' width');
    assert.equal(image.readUInt32BE(20),map.height,map.name+' height');
    assert.equal(crypto.createHash('sha256').update(image).digest('hex'),map.sha256,map.name+' SHA-256');
  }
});
