'use strict';
(function (root, factory) {
  const model = factory();
  if (typeof module === 'object' && module.exports) module.exports = model;
  if (root) root.ObservatorySearchModel = model;
})(typeof globalThis !== 'undefined' ? globalThis : this, function () {
  function searchEntities(query, {players = [], countries = [], regions = [], countryNames = {}, regionNames = {}} = {}) {
    const needle = String(query || '').trim().toLocaleLowerCase();
    if (needle.length < 2) return {players: [], countries: [], regions: []};
    const countryLabel = code => countryNames[code] || (code === 'unknown' ? 'Unknown country' : code === 'kr' ? 'South Korea' : (() => { try { return new Intl.DisplayNames(['en'], {type:'region'}).of(String(code).toUpperCase()) || code; } catch { return code; } })());
    const includes = (...values) => values.some(value => String(value ?? '').toLocaleLowerCase().includes(needle));
    return {
      players: (players || []).filter(item => includes(item.display_name, item.player_key)).slice(0, 6),
      countries: (countries || []).filter(item => includes(countryLabel(item.country), item.country)).slice(0, 4),
      regions: (regions || []).filter(item => includes(regionNames[item.region] || item.region, item.region)).slice(0, 4)
    };
  }
  function rememberSearch(history, item, limit = 5) {
    const identity = value => value?.kind && value?.label ? `${value.kind}|${value.label.toLocaleLowerCase()}` : value?.href;
    return [item, ...(Array.isArray(history) ? history : []).filter(old => identity(old) !== identity(item))].slice(0, limit);
  }
  return {searchEntities, rememberSearch};
});
