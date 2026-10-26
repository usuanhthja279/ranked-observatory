const test = require('node:test');
const assert = require('node:assert/strict');
const { createLatestRequest } = require('../../main/resources/static/observatory-lifecycle.js');

test('begin aborts the earlier request and only the latest request remains current', () => {
  const lifecycle = createLatestRequest();
  const first = lifecycle.begin();
  const second = lifecycle.begin();
  assert.equal(first.signal.aborted, true);
  assert.equal(first.isCurrent(), false);
  assert.equal(second.signal.aborted, false);
  assert.equal(second.isCurrent(), true);
});

test('cancel aborts and invalidates the active request', () => {
  const lifecycle = createLatestRequest();
  const request = lifecycle.begin();
  lifecycle.cancel();
  assert.equal(request.signal.aborted, true);
  assert.equal(request.isCurrent(), false);
});

test('a request remains current until superseded or cancelled', () => {
  const lifecycle = createLatestRequest();
  const request = lifecycle.begin();
  assert.equal(request.isCurrent(), true);
});
