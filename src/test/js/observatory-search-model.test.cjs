'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const model = require('../../main/resources/static/observatory-search-model.js');

test('search returns bounded player, country and region matches by visible labels and identifiers', () => {
  const players = [{display_name:'Sage#EUW',player_key:'Sage#EUW'}, {display_name:'Sage#NA',player_key:'Sage#NA'}];
  const countries = [{country:'de',players:906}, {country:'unknown',players:20}];
  const regions = [{region:'eu',players:1200}, {region:'ap',players:900}];
  const result = model.searchEntities('europe', {players, countries, regions, countryNames:{de:'Germany'}, regionNames:{eu:'Europe'}});
  assert.deepEqual(result.regions.map(row=>row.region), ['eu']);
  assert.deepEqual(result.players, []);
  assert.deepEqual(model.searchEntities('germany', {countries, countryNames:{de:'Germany'}}).countries.map(row=>row.country), ['de']);
  assert.deepEqual(model.searchEntities('japan', {countries:[{country:'jp'}]}).countries.map(row=>row.country), ['jp']);
  assert.deepEqual(model.searchEntities('sage#euw', {players}).players.map(row=>row.player_key), ['Sage#EUW']);
});

test('short queries are bounded out and recent searches remain unique and capped', () => {
  assert.deepEqual(model.searchEntities('x'), {players:[], countries:[], regions:[]});
  const history = Array.from({length:5}, (_, i) => ({href:`/${i}`,label:String(i)}));
  const next = model.rememberSearch(history, {href:'/2',label:'Updated'});
  assert.equal(next.length, 5);
  assert.equal(next[0].label, 'Updated');
  assert.equal(next.filter(item=>item.href==='/2').length, 1);
  const replaced = model.rememberSearch([{kind:'country',label:'Japan',href:'/countries?region=all'},{kind:'region',label:'Asia-Pacific',href:'/regions/ap'}], {kind:'country',label:'Japan',href:'/countries?region=ap'});
  assert.equal(replaced.filter(item=>item.label==='Japan').length, 1);
  assert.equal(replaced[0].href, '/countries?region=ap');
});
