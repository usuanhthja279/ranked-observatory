'use strict';
(function (root, factory) {
  const model = factory();
  if (typeof module === 'object' && module.exports) module.exports = model;
  if (root.document) mount(root, model);
})(typeof globalThis !== 'undefined' ? globalThis : this, function () {
  const REGION_KEYS = ['ap', 'eu', 'na', 'latam', 'br', 'kr'];
  const REGION_LABELS = {ap: 'Asia-Pacific', eu: 'Europe', na: 'North America', latam: 'Latin America', br: 'Brazil', kr: 'South Korea'};
  function indexRegions(byRegion) {
    const index = new Map();
    for (const [region, rows] of Object.entries(byRegion || {})) {
      for (const row of rows || []) {
        const key = row.country || 'unknown';
        if (!index.has(key)) index.set(key, []);
        index.get(key).push({region, players: Number(row.players || 0)});
      }
    }
    for (const values of index.values()) values.sort((a, b) => b.players - a.players || a.region.localeCompare(b.region));
    return index;
  }
  function selectRows(allRows, byRegion, {region = 'all', query = '', sort = 'players', page = 1, pageSize = 12, names = {}, regionNames = REGION_LABELS} = {}) {
    const index = indexRegions(byRegion), needle = String(query).trim().toLocaleLowerCase();
    let rows = (region === 'all' ? allRows || [] : byRegion?.[region] || []).map(row => ({...row, country: row.country || 'unknown', regions: index.get(row.country || 'unknown') || []}));
    rows = rows.filter(row => !needle || `${names[row.country] || row.country} ${row.country}`.toLocaleLowerCase().includes(needle));
    rows.sort((a, b) => {
      if (a.country === 'unknown' || b.country === 'unknown') return a.country === b.country ? 0 : a.country === 'unknown' ? 1 : -1;
      if (sort === 'name') return String(names[a.country] || a.country).localeCompare(String(names[b.country] || b.country));
      const aValue = sort === 'radiant' ? Number(a.radiant_confirmed ?? a.radiant ?? 0) : Number(a.players || 0);
      const bValue = sort === 'radiant' ? Number(b.radiant_confirmed ?? b.radiant ?? 0) : Number(b.players || 0);
      return bValue - aValue || String(names[a.country] || a.country).localeCompare(String(names[b.country] || b.country));
    });
    const unknown = rows.find(row => row.country === 'unknown');
    const namedRows = rows.filter(row => row.country !== 'unknown');
    const size = Math.max(1, pageSize);
    const pages = Math.max(1, Math.ceil(namedRows.length / size)), currentPage = Math.min(pages, Math.max(1, Number(page) || 1));
    const pageRows = namedRows.slice((currentPage - 1) * size, currentPage * size);
    // Keep the unclassified aggregate visible after the first page of named
    // countries; it is useful context but should not displace a named country.
    if (currentPage === 1 && unknown) pageRows.push(unknown);
    return {rows, pageRows, page: currentPage, pages, total: rows.length, hasUnknown: Boolean(unknown), regionNames};
  }
  return {REGION_KEYS, REGION_LABELS, indexRegions, selectRows};
});

function mount(root, model) {
  const params = new URLSearchParams(location.search);
  if (params.get('view') !== 'countries') return;
  document.body.classList.add('country-directory');
  const $ = id => document.getElementById(id);
  const esc = value => String(value ?? '').replace(/[&<>"']/g, char => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[char]));
  const fmt = value => value == null ? '—' : Number(value).toLocaleString();
  const selectedAct = params.get('act') || '';
  let state = {region: params.get('region') || 'all', query: params.get('q') || '', sort: params.get('sort') || 'players', page: Number(params.get('page') || 1)};
  let controller = null, generation = 0, snapshot = null, allRows = [], byRegion = {}, names = {}, regionNames = model.REGION_LABELS;
  $('countriesDirectory').hidden = false;
  $('pageTitle').textContent = 'Countries';
  $('subtitle').textContent = 'Explore reported country labels and observed ranked entries.';
  $('breadcrumbs').innerHTML = '<a class="breadcrumb-link" href="/overview">Overview</a><span class="subtle" aria-hidden="true">›</span><span class="breadcrumb-current" aria-current="page">Countries</span>';
  $('countriesRegion').value = model.REGION_KEYS.includes(state.region) ? state.region : 'all';
  $('countriesSearch').value = state.query;
  $('countriesSort').value = ['players', 'radiant', 'name'].includes(state.sort) ? state.sort : 'players';

  function withAct(path) {
    const [endpoint, query = ''] = path.split('?', 2), values = new URLSearchParams(query);
    if (selectedAct && !values.has('act')) values.set('act', selectedAct);
    return endpoint + '?' + values.toString();
  }
  async function get(path, signal) {
    const response = await fetch('/api/leaderboard/' + withAct(path), {signal, headers: {Accept: 'application/json'}});
    if (!response.ok) throw new Error(`The country explorer could not load its data (HTTP ${response.status}).`);
    return response.json();
  }
  function updateUrl(push = false) {
    const next = new URL(location.href);
    for (const key of ['region', 'q', 'sort', 'page']) next.searchParams.delete(key);
    if (state.region !== 'all') next.searchParams.set('region', state.region);
    if (state.query) next.searchParams.set('q', state.query);
    if (state.sort !== 'players') next.searchParams.set('sort', state.sort);
    if (state.page > 1) next.searchParams.set('page', String(state.page));
    history[push ? 'pushState' : 'replaceState']({countries: true}, '', next.pathname + next.search + next.hash);
  }
  function regionLinks(row) {
    if (row.country === 'unknown') return '<span class="subtle">Country label unknown</span>';
    const values = state.region === 'all' ? row.regions : row.regions.filter(item => item.region === state.region);
    if (!values.length) return '<span class="subtle">No regional ladder entry available</span>';
    return values.map(item => `<a href="/countries/${encodeURIComponent(row.country)}?region=${encodeURIComponent(item.region)}${selectedAct ? '&act=' + encodeURIComponent(selectedAct) : ''}" title="Open ${esc(model.REGION_LABELS[item.region] || item.region)} country view · ${fmt(item.players)} entries">${esc((model.REGION_LABELS[item.region] || item.region))}</a>`).join('');
  }
  function render() {
    const selected = model.selectRows(allRows, byRegion, {...state, names, regionNames});
    const sourceLabel = snapshot?.archiveOnly ? 'Archived Act snapshot' : 'Latest complete snapshot';
    const totalEntries = selected.rows.reduce((sum, row) => sum + Number(row.players || 0), 0);
    const radiantTotal = selected.rows.reduce((sum, row) => sum + Number(row.radiant_confirmed ?? row.radiant ?? 0), 0);
    $('countriesSummary').innerHTML = `<div class="countries-stat"><span>Country labels in view</span><strong>${fmt(selected.total)}</strong></div><div class="countries-stat"><span>Observed leaderboard entries</span><strong>${fmt(totalEntries)}</strong></div><div class="countries-stat"><span>Confirmed Radiant entries</span><strong>${fmt(radiantTotal)}</strong></div>`;
    $('countriesFreshness').textContent = `${sourceLabel}${snapshot?.latestSnapshot?.[0]?.completed_at ? ' · completed ' + new Date(snapshot.latestSnapshot[0].completed_at).toLocaleString() : ''}. Country labels are reported by the source; entries across different regional ladders may include the same account.`;
    $('countriesResults').innerHTML = selected.pageRows.length ? selected.pageRows.map(row => {
      const code = row.country === 'unknown' ? '?' : row.country.toUpperCase();
      const reportedTiers = [['Radiant', row.radiant_confirmed ?? row.radiant], ['Immortal 3', row.immortal3], ['Immortal 2', row.immortal2], ['Immortal 1', row.immortal1]].filter(([, value]) => value != null);
      return `<article class="country-directory-card"><div class="country-directory-name">${row.country === 'unknown' ? '<span class="unknown-symbol" aria-hidden="true">?</span>' : `<img class="flag" src="/flags/${encodeURIComponent(row.country)}.svg" alt="" loading="lazy">`}<span>${esc(names[row.country] || (row.country === 'unknown' ? 'Unknown country' : row.country))}</span><span class="country-directory-code">${esc(code)}</span></div><div class="country-directory-count">${fmt(row.players)}<small>observed entries</small></div><div class="country-directory-tiers">${reportedTiers.map(([label, value]) => `<span>${esc(label)} · ${fmt(value)}</span>`).join('') || '<span>Rank detail unavailable</span>'}</div><div class="country-directory-regions"><span>Regional views</span>${regionLinks(row)}</div></article>`;
    }).join('') : '<div class="countries-empty">No country labels match these filters. Try another name, code or region.</div>';
    const pageStart = selected.page === 1 ? 1 : 13 + (selected.hasUnknown ? 1 : 0) + (selected.page - 2) * 12;
    $('countriesPageLabel').textContent = selected.total ? `Showing ${pageStart}–${pageStart + selected.pageRows.length - 1} of ${fmt(selected.total)}` : '0 countries';
    $('countriesPrevious').disabled = selected.page <= 1;
    $('countriesNext').disabled = selected.page >= selected.pages;
    $('countriesPagination').hidden = selected.pages <= 1;
    $('countriesError').textContent = '';
    updateUrl(false);
  }
  async function load() {
    const current = ++generation;
    controller?.abort(); controller = new AbortController();
    $('countriesError').textContent = '';
    $('countriesResults').innerHTML = '<div class="countries-empty" role="status">Loading country observations…</div>';
    const signal = controller.signal;
    try {
      const [status, worldRows, ...regional] = await Promise.all([
        get('status', signal),
        get('countries?region=all&country=all', signal),
        ...model.REGION_KEYS.map(key => get(`countries?region=${key}&country=all`, signal))
      ]);
      if (current !== generation) return;
      snapshot = status; allRows = worldRows; byRegion = Object.fromEntries(model.REGION_KEYS.map((key, index) => [key, regional[index]]));
      names = status.countries || {}; regionNames = {...model.REGION_LABELS, ...(status.regions || {})};
      $('lastUpdated').textContent = status.latestSnapshot?.[0]?.completed_at ? new Date(status.latestSnapshot[0].completed_at).toLocaleString() : '—';
      if (!model.REGION_KEYS.includes(state.region) && state.region !== 'all') state.region = 'all';
      render();
    } catch (error) {
      if (current !== generation || error.name === 'AbortError') return;
      $('countriesResults').innerHTML = '';
      $('countriesError').textContent = error.message || 'Unable to load country observations.';
      $('countriesSummary').innerHTML = '<div class="countries-stat"><span>Country data</span><strong>Unavailable</strong></div>';
      $('countriesPagination').hidden = true;
    }
  }
  $('countriesSearch').addEventListener('input', () => { state.query = $('countriesSearch').value.trimStart(); state.page = 1; render(); });
  $('countriesRegion').addEventListener('change', () => { state.region = $('countriesRegion').value; state.page = 1; render(); });
  $('countriesSort').addEventListener('change', () => { state.sort = $('countriesSort').value; state.page = 1; render(); });
  $('countriesPrevious').addEventListener('click', () => { state.page--; render(); });
  $('countriesNext').addEventListener('click', () => { state.page++; render(); });
  window.addEventListener('popstate', () => {
    const next = new URLSearchParams(location.search);
    state = {region: next.get('region') || 'all', query: next.get('q') || '', sort: next.get('sort') || 'players', page: Number(next.get('page') || 1)};
    $('countriesRegion').value = state.region; $('countriesSearch').value = state.query; $('countriesSort').value = state.sort;
    render();
  });
  root.loadCountriesDirectory = load;
}
