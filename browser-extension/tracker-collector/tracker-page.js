(() => {
  let stopped = false;
  const pageInstanceId = crypto.randomUUID();
  const sleep = ms => new Promise(resolve => setTimeout(resolve, ms));
  function check() {
    if (stopped) throw new Error('Collection stopped after an access restriction.');
    const text = (document.body?.innerText || '').slice(0, 5000).toLowerCase();
    if (['verify you are human', 'checking your browser', 'just a moment', 'cloudflare ray', 'private profile', 'profile is private'].some(s => text.includes(s))) {
      throw new Error('BLOCKED: Tracker requires verification or the profile is private.');
    }
  }
  async function waitFor(find, label, timeout = 20000) {
    const until = Date.now() + timeout;
    while (Date.now() < until) {
      check();
      const value = find();
      if (value) return value;
      await sleep(200);
    }
    throw new Error(label + ' did not appear in Tracker’s rendered page.');
  }
  chrome.runtime.onMessage.addListener((message, sender, reply) => {
    if (sender.id !== chrome.runtime.id) return;
    if (message?.type === 'STOP') { stopped = true; reply({ ok: true }); return; }
    if (!sender.url?.startsWith(chrome.runtime.getURL('runner.html'))) return;
    if (message?.type === 'PING') { reply({ pageInstanceId, url: location.href }); return; }
    if (message?.type === 'CLOSE_MATCH') {
      (async () => {
        check();
        if (!document.querySelector('.vm-header')) return { closed: true };
        document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', code: 'Escape', bubbles: true }));
        const until = Date.now() + 2000;
        while (Date.now() < until) {
          check();
          if (!document.querySelector('.vm-header')) return { closed: true };
          await sleep(100);
        }
        return { closed: false };
      })().then(reply, error => reply({ error: error.message, closed: false }));
      return true;
    }
    if (message?.type === 'CAPTURE_CURRENT') {
      (async () => {
        check();
        const link = document.querySelector('.vm-header a[href*="/valorant/match/"]');
        const url = new URL(link?.getAttribute('href'), location.href);
        if (url.origin !== 'https://tracker.gg' || url.pathname.split('/')[3]?.toLowerCase() !== message.matchId) throw new Error('Match changed.');
        return {capture: await globalThis.TrackerMatchCapture.capture(message.player, url.href, () => stopped)};
      })().then(reply, error => reply({error:error.message})); return true;
    }
    if (!['COLLECT_ONE', 'OPEN_ONE'].includes(message?.type)) return;
    (async () => {
      const index = message.index;
      if (!Number.isInteger(index) || index < 0 || index > 4 || typeof message.player !== 'string') throw new Error('Invalid collection request.');
      const displayedId = decodeURIComponent(location.pathname.split('/valorant/profile/riot/')[1]?.split('/')[0] || '');
      if (displayedId.toLocaleLowerCase() !== message.player.toLocaleLowerCase()) throw new Error('Tracker profile does not match the requested player.');
      await waitFor(() => document.querySelector('.v3-match-row'), 'Recent match list');
      const rows = [...document.querySelectorAll('.v3-match-row')];
      if (index >= rows.length) return { done: true, visibleCount: rows.length };
      rows[index].click();
      const link = await waitFor(() => document.querySelector('.vm-header a[href*="/valorant/match/"]'), 'Match detail');
      const url = new URL(link.getAttribute('href'), location.href);
      if (url.origin !== 'https://tracker.gg' || !/^\/valorant\/match\/[0-9a-f-]{36}\/?$/i.test(url.pathname)) throw new Error('Unexpected match link.');
      check();
      if (message.type === 'OPEN_ONE') return { matchId: url.pathname.split('/')[3] };
      const capture = await globalThis.TrackerMatchCapture.capture(message.player, url.href, () => stopped);
      check();
      return { capture };
    })().then(reply, error => reply({ error: error.message }));
    return true;
  });
})();
