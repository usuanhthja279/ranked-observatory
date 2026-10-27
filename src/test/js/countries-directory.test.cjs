'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const {selectRows, indexRegions} = require('../../main/resources/static/countries-directory.js');

const names = {in: 'India', jp: 'Japan', unknown: 'Unknown country'};
const regionRows = {
  ap: [{country: 'in', players: 40, radiant_confirmed: 2}, {country: 'jp', players: 12, radiant_confirmed: 1}],
  eu: [{country: 'in', players: 3, radiant_confirmed: 0}],
  na: []
};
const worldRows = [{country: 'in', players: 43, radiant_confirmed: 2}, {country: 'jp', players: 12, radiant_confirmed: 1}, {country: 'unknown', players: 8, radiant_confirmed: 0}];

test('country entries link only to regional ladders that contain observations', () => {
  const index = indexRegions(regionRows);
  assert.deepEqual(index.get('in'), [{region: 'ap', players: 40}, {region: 'eu', players: 3}]);
  assert.equal(index.has('unknown'), false);
});

test('directory filters before paging and keeps aggregate counts source-labeled', () => {
  const result = selectRows(worldRows, regionRows, {query: 'IND', page: 9, pageSize: 1, names});
  assert.equal(result.total, 1);
  assert.equal(result.page, 1);
  assert.equal(result.pageRows[0].country, 'in');
  assert.deepEqual(result.pageRows[0].regions.map(item => item.region), ['ap', 'eu']);
});

test('regional scope changes both country presence and observed values', () => {
  const result = selectRows(worldRows, regionRows, {region: 'eu', sort: 'name', names});
  assert.equal(result.total, 1);
  assert.equal(result.pageRows[0].country, 'in');
  assert.equal(result.pageRows[0].players, 3);
  assert.equal(result.pageRows[0].regions.length, 2);
});

test('sort choices order full filtered results deterministically', () => {
  assert.deepEqual(selectRows(worldRows, regionRows, {sort: 'name', names}).rows.map(row => row.country), ['in', 'jp', 'unknown']);
  assert.deepEqual(selectRows(worldRows, regionRows, {sort: 'radiant', names}).rows.map(row => row.country), ['in', 'jp', 'unknown']);
});

test('unknown country follows the first twelve named countries at the bottom of page one', () => {
  const rows = Array.from({length: 13}, (_, index) => ({country: `c${String(index).padStart(2, '0')}`, players: 100 - index, radiant: 0}));
  rows.push({country: 'unknown', players: 1000, radiant: 999});
  const first = selectRows(rows, {}, {sort: 'players', page: 1, pageSize: 12, names});
  const second = selectRows(rows, {}, {sort: 'players', page: 2, pageSize: 12, names});
  assert.equal(first.pageRows.length, 13);
  assert.equal(first.pageRows.at(-1).country, 'unknown');
  assert.ok(first.pageRows.slice(0, 12).every(row => row.country !== 'unknown'));
  assert.equal(second.pageRows.length, 1);
  assert.notEqual(second.pageRows[0].country, 'unknown');
  assert.equal(second.pageRows[0].country, 'c12');
});
