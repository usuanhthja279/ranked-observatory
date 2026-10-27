const test = require('node:test');
const assert = require('node:assert/strict');
const { createLatestRequest, runWhenDisclosureOpens } = require('../../main/resources/static/observatory-lifecycle.js');

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

test('collapsed optional panels do no work until opened, then initialize once', () => {
  const listeners = new Set();
  const disclosure = {
    open: false,
    addEventListener: (type, listener) => type === 'toggle' && listeners.add(listener),
    removeEventListener: (type, listener) => type === 'toggle' && listeners.delete(listener),
    toggle() { this.open = !this.open; for (const listener of [...listeners]) listener(); }
  };
  let calls = 0;
  runWhenDisclosureOpens(disclosure, () => calls++);
  assert.equal(calls, 0);
  assert.equal(listeners.size, 1);
  disclosure.toggle();
  assert.equal(calls, 1);
  assert.equal(listeners.size, 0);
  disclosure.toggle();
  assert.equal(calls, 1);
});

test('visible pages can eagerly initialize disclosure content', () => {
  let calls = 0;
  runWhenDisclosureOpens({ open: false }, () => calls++, true);
  assert.equal(calls, 1);
});
