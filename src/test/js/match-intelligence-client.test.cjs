'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const { validateCollectorExport, uploadCollectorExport } = require('../../main/resources/static/match-intelligence-client.js');

const player = 'Sample#TAG';
function entry(id = '11111111-2222-4333-8444-555555555555') {
  return { matchId:id, validation:'passed', response:{ data:{ attributes:{ id }, segments:[
    { type:'player-summary', attributes:{ platformUserIdentifier:player } }
  ] } } };
}
function exportBody(matches=[entry()]) {
  return { schemaVersion:1, source:'tracker.gg', player, exportedAt:'2026-10-28T00:00:00Z', requestedMatches:matches.length,
    responseCount:matches.length, complete:true, failures:[], matches };
}

test('collector transfer accepts a bounded matching response batch', () => {
  assert.equal(validateCollectorExport(exportBody(), player).matches.length, 1);
});

test('collector transfer rejects another player, duplicates and batches above five', () => {
  assert.throws(() => validateCollectorExport(exportBody(), 'Other#TAG'), /selected player/);
  assert.throws(() => validateCollectorExport(exportBody([entry(), entry()]), player), /duplicate/);
  assert.throws(() => validateCollectorExport(exportBody(Array.from({length:6},(_,i)=>entry(`11111111-2222-4333-8444-${String(i).padStart(12,'0')}`))), player), /one to five/);
});

test('collector transfer uses same-origin CSRF and authenticated import request', async () => {
  const calls=[];
  const fetcher=async (url, options={}) => {
    calls.push({url,options});
    if (url === '/api/leaderboard/csrf') return {ok:true,json:async()=>({header:'X-CSRF-TOKEN',token:'synthetic-token'})};
    return {ok:true,json:async()=>({jobId:'synthetic-job',acceptedMatches:1,partialMatches:0,rejectedMatches:0})};
  };
  const receipt=await uploadCollectorExport(exportBody(),player,fetcher);
  assert.equal(receipt.jobId,'synthetic-job');
  assert.equal(calls.length,2);
  assert.equal(calls[0].options.credentials,'same-origin');
  assert.equal(calls[1].options.credentials,'same-origin');
  assert.equal(calls[1].options.headers['X-CSRF-TOKEN'],'synthetic-token');
  assert.equal(JSON.parse(calls[1].options.body).matches.length,1);
});

test('collector transfer never posts when CSRF preparation fails', async () => {
  const calls=[];
  await assert.rejects(uploadCollectorExport(exportBody(),player,async url=>{calls.push(url);return {ok:false};}),/Sign in/);
  assert.deepEqual(calls,['/api/leaderboard/csrf']);
});
