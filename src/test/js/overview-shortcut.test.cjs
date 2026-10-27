'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '../../main/resources/static');
const html = fs.readFileSync(path.join(root, 'leaderboard.html'), 'utf8');
const script = fs.readFileSync(path.join(root, 'leaderboard.js'), 'utf8');

test('Overview Watchlist shortcut remains distinct from Global leaderboard routing', () => {
  assert.match(html, /id="overviewWatchlistLink"[^>]*href="\/watchlist"/);
  assert.match(html, /id="overviewGlobalLink"[^>]*href="\/leaderboard\.html\?view=global"/);
  assert.match(script, /const globalLink=\$\('overviewGlobalLink'\)/);
  assert.doesNotMatch(script, /querySelector\('a\.page-link'\)/);
});
