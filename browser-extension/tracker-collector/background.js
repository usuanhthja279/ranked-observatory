const allowedOrigins = new Set(['http://127.0.0.1:8090', 'http://localhost:8090', 'http://127.0.0.1:8091', 'http://localhost:8091']);
const validPlayer = player => typeof player === 'string' && player.length <= 120 && /^[^\u0000-\u001f#]+#[^\u0000-\u001f#]+$/.test(player);
let startPending = false;
async function job() { return (await chrome.storage.session.get('collectorJob')).collectorJob; }
async function sendApp(j, update) {
  try { await chrome.tabs.sendMessage(j.appTabId, { type: 'APP_UPDATE', update: { ...update, jobId: j.id } }); } catch {}
}
chrome.runtime.onMessage.addListener((message, sender, reply) => {
  (async () => {
    if (sender.id !== chrome.runtime.id) throw new Error('Unexpected sender.');
    if (message?.type === 'START') {
      if (startPending) throw new Error('A collection is already starting.');
      startPending = true;
      try {
      const url = new URL(sender.tab?.url || '');
      if (!allowedOrigins.has(url.origin) || url.pathname !== '/players') throw new Error('Open a local Observatory player profile.');
      const player = url.searchParams.get('player')?.trim();
      if (!validPlayer(player)) throw new Error('The player profile must contain a valid Riot ID.');
      if (await job()) throw new Error('A collection is already running.');
      const j = { id: crypto.randomUUID(), appTabId: sender.tab.id, player, startedAt: Date.now() };
      await chrome.storage.session.set({ collectorJob: j });
      await sendApp(j, { type: 'started' });
      try {
        const runner = await chrome.tabs.create({ url: chrome.runtime.getURL('runner.html'), active: false });
        j.runnerTabId = runner.id;
        await chrome.storage.session.set({ collectorJob: j });
      } catch (error) {
        await sendApp(j, { type: 'finished', message: 'Collector could not open: ' + error.message, captures: [] });
        await chrome.storage.session.remove('collectorJob');
        throw error;
      }
      return { ok: true, jobId: j.id };
      } finally { startPending = false; }
    }
    if (message?.type === 'RUNNER_UPDATE') {
      const j = await job();
      if (!j || message.jobId !== j.id || sender.tab?.id !== j.runnerTabId) throw new Error('Unknown collector session.');
      await sendApp(j, message.update);
      if (message.update?.type === 'finished') await chrome.storage.session.remove('collectorJob');
      return { ok: true };
    }
    throw new Error('Unsupported message.');
  })().then(reply, error => reply({ ok: false, error: error.message }));
  return true;
});
chrome.webRequest.onCompleted.addListener(details => {
  if (![403, 429].includes(details.statusCode)) return;
  (async () => {
    const j = await job();
    if (!j || j.trackerTabId !== details.tabId) return;
    const host = new URL(details.url).hostname;
    if (host !== 'tracker.gg' && host !== 'api.tracker.gg') return;
    j.blockedStatus = details.statusCode;
    await chrome.storage.session.set({ collectorJob: j });
    await sendApp(j, { type: 'progress', message: 'Tracker returned HTTP ' + details.statusCode + '. Collection stopped.' });
    try { await chrome.tabs.sendMessage(j.trackerTabId, { type: 'STOP' }); } catch {}
    try { await chrome.tabs.sendMessage(j.runnerTabId, { type: 'STOP' }); } catch {}
    chrome.runtime.sendMessage({ type: 'COLLECTOR_BLOCKED', jobId: j.id, status: details.statusCode }).catch(() => {});
  })();
}, { urls: ['https://tracker.gg/*', 'https://api.tracker.gg/*'], types: ['main_frame', 'xmlhttprequest'] });
chrome.tabs.onRemoved.addListener(tabId => {
  (async () => {
    const j = await job();
    if (!j || (tabId !== j.runnerTabId && tabId !== j.appTabId)) return;
    if (j.trackerTabId) try { await chrome.tabs.remove(j.trackerTabId); } catch {}
    if (tabId === j.runnerTabId) await sendApp(j, { type: 'finished', message: 'Collector tab was closed.', captures: [] });
    await chrome.storage.session.remove('collectorJob');
  })();
});
