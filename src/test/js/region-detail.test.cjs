'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');

const source = fs.readFileSync(path.join(__dirname, '../../main/resources/static/region-detail.js'), 'utf8');

class Element {
  constructor(id, key = '') {
    this.id = id;
    this.dataset = key ? {regionTab: key} : {};
    this.attributes = {};
    this.listeners = {};
    this.hidden = false;
    this.open = false;
    this.tabIndex = 0;
    this.textContent = '';
    this.innerHTML = '';
    this.classList = {contains: name => Boolean(this.classNames?.has(name)), toggle: (name, on) => {this.classNames ||= new Set(); on ? this.classNames.add(name) : this.classNames.delete(name);}};
  }
  setAttribute(name, value) { this.attributes[name] = String(value); }
  addEventListener(name, listener) { this.listeners[name] = listener; }
  focus() { this.focused = true; }
  click() { this.listeners.click?.({currentTarget: this}); }
  key(name) { this.listeners.keydown?.({key: name, preventDefault() {}}); }
}

function setup(hash = '') {
  const keys = ['overview','standings','countries','rank-distribution','movement','historical-trends'];
  const tabs = keys.map((key, index) => new Element(`tab-${key}`, key));
  const ids = ['regionExperienceTabs','regionOverviewPanel','regionDistributionPanel','historicalTrendDisclosure','currentSection','playerSection','selectedPlayerSection','countrySection','countryPreview','scopeSection','timelineSection','trendLayout','previewTitle','selectedPlayerDetails','regionOverviewMetrics','regionOverviewNote','regionOverviewTopPlayer','regionDistributionRows','regionDistributionNote','changeWindowLabel','movers'];
  const elements = new Map(ids.map(id => [id, new Element(id)]));
  elements.get('regionExperienceTabs').hidden = true;
  elements.get('historicalTrendDisclosure').hidden = false;
  const handlers = {};
  const location = {pathname:'/regions/ap', search:'?act=season-x', hash};
  const history = {pushState(_state, _title, url) {this.lastUrl = url; location.hash = url.slice(url.indexOf('#'));}};
  class CustomEvent {constructor(type, options) {this.type = type; this.detail = options.detail;}}
  const context = {document: {querySelectorAll: () => tabs, getElementById: id => elements.get(id) || null}, window: {addEventListener: (name, cb) => handlers[name] = cb}, location, history, CustomEvent, globalThis: {}};
  vm.runInNewContext(source, context);
  const publish = detail => handlers['observatory:region-data']({detail});
  return {tabs,elements,handlers,location,history,publish};
}

test('region detail renders observed metrics and six keyboard-accessible tabs', () => {
  const page = setup();
  page.publish({view:'region',region:'ap',summary:{players:15024,radiant:500,radiant_confirmed:498,top_rank:1,top_rr:1057,top_player:'Example#TAG',carried_forward:215,uncertain:3,immortal3:2970,immortal2:4263,immortal1:7291},boundary:612});
  assert.equal(page.elements.get('regionExperienceTabs').hidden,false);
  assert.equal(page.elements.get('regionOverviewPanel').hidden,false);
  assert.match(page.elements.get('regionOverviewMetrics').innerHTML,/15,024/);
  assert.match(page.elements.get('regionOverviewMetrics').innerHTML,/Confirmed Radiant players/);
  assert.match(page.elements.get('regionOverviewMetrics').innerHTML,/612 RR/);
  assert.match(page.elements.get('regionOverviewNote').textContent,/not an official promotion requirement/);
  assert.match(page.elements.get('regionDistributionRows').innerHTML,/Immortal 3/);
  assert.match(page.elements.get('regionDistributionRows').innerHTML,/Confirmed Radiant/);
  assert.equal(page.tabs.length,6);
  page.tabs[0].key('ArrowRight');
  assert.equal(page.tabs[1].focused,true);
  assert.equal(page.tabs[1].attributes['aria-selected'],'true');
  assert.equal(page.elements.get('playerSection').classList.contains('hidden'),false);
  assert.equal(page.history.lastUrl,'/regions/ap?act=season-x#standings');
});

test('region tabs switch scoped panels and open archived Act trends only on request', () => {
  const page = setup();
  page.publish({view:'region',region:'eu',summary:{players:12,radiant:2,immortal3:3,immortal2:4,immortal1:3},boundary:null,archive:true});
  page.tabs[2].click();
  assert.equal(page.elements.get('countrySection').classList.contains('hidden'),false);
  assert.equal(page.elements.get('playerSection').classList.contains('hidden'),true);
  page.tabs[4].click();
  assert.equal(page.elements.get('trendLayout').classList.contains('hidden'),false);
  assert.equal(page.elements.get('scopeSection').classList.contains('hidden'),false);
  assert.equal(page.elements.get('timelineSection').classList.contains('hidden'),true);
  assert.match(page.elements.get('movers').innerHTML,/one end-of-Act standings snapshot/);
  page.tabs[5].click();
  assert.equal(page.elements.get('timelineSection').classList.contains('hidden'),false);
  assert.equal(page.elements.get('historicalTrendDisclosure').hidden,false);
  assert.equal(page.elements.get('historicalTrendDisclosure').open,true);
  assert.match(page.elements.get('regionOverviewNote').textContent,/Archived act snapshot/);
  assert.match(page.elements.get('regionOverviewMetrics').innerHTML,/Observed Radiant boundary/);
  assert.match(page.elements.get('regionOverviewMetrics').innerHTML,/Unavailable/);
});
