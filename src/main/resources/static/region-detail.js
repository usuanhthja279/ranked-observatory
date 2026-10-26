'use strict';
(() => {
  const tabs = [...document.querySelectorAll('[data-region-tab]')];
  const nav = document.getElementById('regionExperienceTabs');
  const overview = document.getElementById('regionOverviewPanel');
  const distribution = document.getElementById('regionDistributionPanel');
  const historicalDisclosure = document.getElementById('historicalTrendDisclosure');
  if (!tabs.length || !nav || !overview || !distribution) return;

  const tabKeys = tabs.map(tab => tab.dataset.regionTab);
  const $ = id => document.getElementById(id);
  const esc = value => String(value ?? '—').replace(/[&<>"']/g, char => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[char]));
  const fmt = value => value == null ? '—' : Number(value).toLocaleString();
  let isRegionView = false;
  let isArchiveView = false;
  const panels = {
    overview: overview,
    standings: $('playerSection'),
    countries: $('countrySection'),
    'rank-distribution': distribution,
    movement: $('scopeSection'),
    'historical-trends': $('timelineSection')
  };
  tabs.forEach(tab => {
    tab.setAttribute('role', 'tab');
    tab.setAttribute('aria-controls', panels[tab.dataset.regionTab]?.id || 'regionOverviewPanel');
  });
  Object.entries(panels).forEach(([key, panel]) => {
    if (panel) {
      panel.setAttribute('role', 'tabpanel');
      panel.setAttribute('aria-labelledby', tabs.find(tab => tab.dataset.regionTab === key)?.id || 'regionOverviewTab');
      panel.tabIndex = 0;
    }
  });

  function setVisible(id, visible) {
    const element = $(id);
    if (element) element.classList.toggle('hidden', !visible);
  }

  function setTab(key, {focus = false, updateUrl = false} = {}) {
    if (!tabKeys.includes(key)) key = 'overview';
    $('trendLayout')?.classList.toggle('region-tab-layout', isRegionView);
    tabs.forEach(tab => {
      const selected = tab.dataset.regionTab === key;
      tab.setAttribute('aria-selected', String(selected));
      tab.tabIndex = selected ? 0 : -1;
    });

    overview.hidden = key !== 'overview';
    distribution.hidden = key !== 'rank-distribution';
    setVisible('currentSection', false);
    setVisible('playerSection', key === 'standings');
    setVisible('selectedPlayerSection', key === 'standings' && Boolean(document.getElementById('selectedPlayerDetails')?.textContent.trim()));
    setVisible('countrySection', key === 'countries');
    setVisible('countryPreview', key === 'countries' && Boolean($('previewTitle')?.textContent.trim()));
    setVisible('scopeSection', key === 'movement');
    setVisible('timelineSection', key === 'historical-trends');
    setVisible('trendLayout', key === 'movement' || key === 'historical-trends');
    if (historicalDisclosure) {
      historicalDisclosure.hidden = isRegionView && key !== 'historical-trends';
      if (isRegionView && key === 'historical-trends') historicalDisclosure.open = true;
    }
    if (key === 'movement' && isArchiveView) {
      $('changeWindowLabel').textContent = 'Movement is unavailable for a single archived snapshot.';
      $('movers').innerHTML = '<p class="empty-state">Archived Acts contain one end-of-Act standings snapshot; there are no within-Act RR changes to compare.</p>';
    }
    const playerSection = $('playerSection');
    const countrySection = $('countrySection');
    if (key === 'standings' && playerSection) playerSection.open = true;
    if (key === 'countries' && countrySection) countrySection.open = true;

    if (focus) tabs.find(tab => tab.dataset.regionTab === key)?.focus();
    if (updateUrl && isRegionView) {
      const next = `#${key}`;
      if (location.hash !== next) history.pushState({regionTab: key}, '', location.pathname + location.search + next);
    }
  }

  function paintOverview(detail) {
    const summary = detail.summary;
    const boundary = detail.boundary;
    const actLabel = detail.archive ? 'Archived act snapshot' : 'Latest complete regional snapshot';
    const cards = [
      ['Players', summary?.players],
      [summary?.radiant_confirmed == null ? 'Radiant players' : 'Confirmed Radiant players', summary?.radiant_confirmed ?? summary?.radiant],
      ['Top regional rank', summary?.top_rank == null ? null : `#${fmt(summary.top_rank)}`],
      ['Top player RR', summary?.top_rr],
      ['Observed Radiant boundary', boundary == null ? null : `${fmt(boundary)} RR`]
    ];
    $('regionOverviewMetrics').innerHTML = cards.map(([label, value]) => `<article class="region-metric"><span>${esc(label)}</span><strong>${esc(value == null ? 'Unavailable' : typeof value === 'number' ? fmt(value) : value)}</strong></article>`).join('');
    $('regionOverviewNote').textContent = `${actLabel} · ${summary?.carried_forward == null ? 'Carry-forward total unavailable' : `${fmt(summary.carried_forward)} carried-forward players`} · ${summary?.uncertain == null ? 'uncertainty total unavailable' : `${fmt(summary.uncertain)} uncertain records`}. The observed Radiant boundary uses fresh, unambiguous Radiant observations; it is not an official promotion requirement.${boundary == null ? ' No boundary is available for this snapshot.' : ''}`;
    $('regionOverviewTopPlayer').textContent = summary?.top_player || 'No verified top player in this snapshot.';

    const rows = [
      [summary?.radiant_confirmed == null ? 'Radiant' : 'Confirmed Radiant', summary?.radiant_confirmed ?? summary?.radiant],
      ['Immortal 3', summary?.immortal3],
      ['Immortal 2', summary?.immortal2],
      ['Immortal 1', summary?.immortal1]
    ].filter(([, value]) => value != null);
    const peak = Math.max(1, ...rows.map(([, value]) => Number(value)));
    const total = rows.reduce((sum, [, value]) => sum + Number(value), 0);
    $('regionDistributionRows').innerHTML = rows.length ? rows.map(([label, value]) => {
      const count = Number(value);
      const share = total ? count / total * 100 : 0;
      return `<div class="region-rank-row"><div class="region-rank-label"><strong>${esc(label)}</strong><span>${fmt(count)} · ${share.toFixed(1)}%</span></div><div class="region-rank-track" role="img" aria-label="${esc(label)}: ${fmt(count)} players, ${share.toFixed(1)} percent of listed Radiant and Immortal tiers"><span style="width:${Math.max(0, Math.min(100, count / peak * 100))}%"></span></div></div>`;
    }).join('') : '<p class="subtle">Rank distribution is unavailable for this Act.</p>';
    $('regionDistributionNote').textContent = `${actLabel}. Counts are observed source tiers; the percentages use only the listed Radiant and Immortal groups. Unknown-country estimates are not included.`;
  }

  function onRegionData(event) {
    const detail = event.detail || {};
    isRegionView = detail.view === 'region' && detail.region !== 'all';
    isArchiveView = Boolean(detail.archive);
    nav.hidden = !isRegionView;
    if ($('regionTabHint')) $('regionTabHint').hidden = !isRegionView;
    if (!isRegionView) {
      $('trendLayout')?.classList.toggle('region-tab-layout', false);
      if (historicalDisclosure) historicalDisclosure.hidden = false;
      return;
    }
    paintOverview(detail);
    setTab(location.hash.slice(1) || 'overview');
  }

  tabs.forEach((tab, index) => {
    tab.addEventListener('click', () => setTab(tab.dataset.regionTab, {updateUrl: true}));
    tab.addEventListener('keydown', event => {
      const nextIndex = event.key === 'ArrowRight' ? (index + 1) % tabs.length : event.key === 'ArrowLeft' ? (index + tabs.length - 1) % tabs.length : event.key === 'Home' ? 0 : event.key === 'End' ? tabs.length - 1 : -1;
      if (nextIndex < 0) return;
      event.preventDefault();
      const next = tabs[nextIndex];
      setTab(next.dataset.regionTab, {focus: true, updateUrl: true});
    });
  });
  window.addEventListener('popstate', () => setTab(location.hash.slice(1), {updateUrl: false}));
  window.addEventListener('hashchange', () => setTab(location.hash.slice(1), {updateUrl: false}));
  window.addEventListener('observatory:region-data', onRegionData);
  if (globalThis.observatoryRegionData) onRegionData({detail: globalThis.observatoryRegionData});
})();
