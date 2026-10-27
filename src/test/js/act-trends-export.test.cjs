'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const buildCsv = require('../../main/resources/static/act-trends-export.js');

test('trend CSV preserves columns and RFC-style quoting for commas, quotes and newlines', () => {
  const csv = buildCsv(['Act', 'Series', 'Value'], [['V26: ACT V', 'South Korea, AP', 12], ['V26: ACT IV', '"Quoted"\ncountry', null]]);
  assert.ok(csv.startsWith('\ufeff"Act","Series","Value"\r\n'));
  assert.match(csv, /"South Korea, AP","12"/);
  assert.match(csv, /"""Quoted""\ncountry",""/);
});

test('trend CSV neutralizes spreadsheet formulas in server-provided text fields', () => {
  const csv = buildCsv(['Country'], [['=HYPERLINK("https://example.invalid")'], [' +SUM(A1:A2)']]);
  assert.match(csv, /"'=HYPERLINK\(""https:\/\/example\.invalid""\)"/);
  assert.match(csv, /"' \+SUM\(A1:A2\)"/);
});

test('entering Trends scope does not override CSV availability owned by the trend loader', () => {
  const fs = require('node:fs');
  const vm = require('node:vm');
  const source = fs.readFileSync(require.resolve('../../main/resources/static/leaderboard.js'), 'utf8');
  const match = source.match(/function setTrendsScope\(\)\{[\s\S]*?\n\}/);
  assert.ok(match, 'Trends scope function is present');

  const elements = new Map();
  const $ = id => {
    if (!elements.has(id)) elements.set(id, {
      classList: { add() {}, remove() {}, toggle() {} },
      appendChild() {},
      querySelector() { return {}; }
    });
    return elements.get(id);
  };
  const context = { $, document: { title: '' } };
  $('actTrendExport').disabled = false; // loader enabled export after returning rows

  vm.runInNewContext(`${match[0]}\nsetTrendsScope();`, context);

  assert.equal($('actTrendExport').disabled, false);
});
