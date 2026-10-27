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
