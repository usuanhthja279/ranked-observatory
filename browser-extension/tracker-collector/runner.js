(async () => {
  const status = document.getElementById('status');
  const sleep = ms => new Promise(resolve => setTimeout(resolve, ms));
  let debuggerAttached = false;
  const pendingResponses = new Map(), bodies = new Map();
  let stopped = false, trackerTabId = null, trackerWindowId = null, currentJob = null, lastPageId = null;
  chrome.runtime.onMessage.addListener(message => {
    if (message?.type === 'COLLECTOR_BLOCKED' && message.jobId === currentJob?.id) stopped = true;
  });
  chrome.debugger.onEvent.addListener((source, method, params) => {
    if (source.tabId !== trackerTabId) return;
    if (method === 'Network.responseReceived') {
      try {
        const u = new URL(params.response.url);
        const match = u.hostname === 'api.tracker.gg' && u.protocol === 'https:' && u.pathname.match(/^\/api\/v2\/valorant\/standard\/matches\/([0-9a-f-]{36})$/i);
        if (match && params.response.status === 200 && /json/i.test(params.response.mimeType || '')) pendingResponses.set(params.requestId, match[1].toLowerCase());
      } catch {}
    }
    if (method === 'Network.loadingFailed') pendingResponses.delete(params.requestId);
    if (method === 'Network.loadingFinished' && pendingResponses.has(params.requestId)) {
      const id = pendingResponses.get(params.requestId); pendingResponses.delete(params.requestId);
      if (params.encodedDataLength > 5 * 1024 * 1024) return;
      const promise = chrome.debugger.sendCommand({tabId:trackerTabId}, 'Network.getResponseBody', {requestId:params.requestId}).then(r => {
        if (r.base64Encoded || r.body.length > 5 * 1024 * 1024) throw new Error('Unsupported response body.');
        return JSON.parse(r.body);
      });
      promise.catch(() => {}); bodies.set(id, promise);
    }
  });
  async function getJob() { return (await chrome.storage.session.get('collectorJob')).collectorJob; }
  async function check() {
    const latest = await getJob();
    if (!latest || latest.id !== currentJob.id || latest.blockedStatus || stopped) {
      stopped = true;
      throw new Error(latest?.blockedStatus ? 'Tracker returned HTTP ' + latest.blockedStatus + '.' : 'Collection stopped.');
    }
  }
  async function update(type, message, extra = {}) {
    status.textContent = message;
    const response = await chrome.runtime.sendMessage({ type: 'RUNNER_UPDATE', jobId: currentJob.id, update: { type, message, ...extra } });
    if (!response?.ok) throw new Error(response?.error || 'Cannot update the Observatory.');
  }
  async function navigate(url) {
    await check();
    if (lastPageId) {
      await chrome.tabs.update(trackerTabId, { url: 'about:blank' });
      const blankUntil = Date.now() + 10000;
      let blankReady = false;
      while (Date.now() < blankUntil) {
        await check();
        const tab = await chrome.tabs.get(trackerTabId);
        if (tab.url === 'about:blank' && tab.status === 'complete') { blankReady = true; break; }
        await sleep(100);
      }
      if (!blankReady) throw new Error('Could not reset the Tracker page.');
    }
    await chrome.tabs.update(trackerTabId, { url });
    const until = Date.now() + 30000;
    while (Date.now() < until) {
      await check();
      try {
        const page = await chrome.tabs.sendMessage(trackerTabId, { type: 'PING' });
        if (page?.pageInstanceId && page.pageInstanceId !== lastPageId &&
            new URL(page.url).origin === 'https://tracker.gg' &&
            new URL(page.url).pathname === new URL(url).pathname) {
          lastPageId = page.pageInstanceId;
          return;
        }
      } catch {}
      await sleep(200);
    }
    throw new Error('Tracker page did not finish loading.');
  }
  async function requestCapture(index, type = 'COLLECT_ONE') {
    const until = Date.now() + 15000;
    while (Date.now() < until) {
      await check();
      try { return await chrome.tabs.sendMessage(trackerTabId, { type, player: currentJob.player, index }); }
      catch (error) {
        if (!String(error.message).includes('Receiving end does not exist')) throw error;
        await sleep(250);
      }
    }
    throw new Error('Tracker page helper did not become available.');
  }
  let captures = [], failures = [], seen = new Set();
  try {
    for (let n = 0; n < 30; n++) {
      currentJob = await getJob();
      if (currentJob?.runnerTabId) break;
      await sleep(100);
    }
    if (!currentJob?.runnerTabId) throw new Error('Collector session was not initialized.');
    const collectorWindow = await chrome.windows.create({ url: 'about:blank', type: 'popup', focused: false, width: 680, height: 760 });
    trackerWindowId = collectorWindow.id;
    const tab = collectorWindow.tabs?.[0];
    if (!tab?.id) throw new Error('Tracker collector window could not be created.');
    trackerTabId = tab.id;
    currentJob.trackerTabId = tab.id;
    await chrome.storage.session.set({ collectorJob: currentJob });
    const profile = 'https://tracker.gg/valorant/profile/riot/' + encodeURIComponent(currentJob.player) + '/overview';
    await update('progress', 'Opening Tracker profile for ' + currentJob.player + '.');
    try {
      await chrome.debugger.attach({tabId:trackerTabId}, '1.3'); debuggerAttached = true;
      await chrome.debugger.sendCommand({tabId:trackerTabId}, 'Network.enable', {});
    } catch { if (debuggerAttached) { try { await chrome.debugger.detach({tabId:trackerTabId}); } catch {} } debuggerAttached = false; await update('progress', 'Response capture unavailable; using visible round tables.'); }
    await navigate(profile);
    for (let index = 0; index < 5 && captures.length < 5; index++) {
      await check();
      if (index) await sleep(3000);
      if (index) {
        const reset = await chrome.tabs.sendMessage(trackerTabId, { type: 'CLOSE_MATCH' });
        if (!reset?.closed) await navigate(profile);
      }
      await update('progress', 'Reading recent match ' + (index + 1) + ' (captured ' + captures.length + ' of 5).');
      let response;
      if (debuggerAttached) {
        response = await requestCapture(index, 'OPEN_ONE');
        if (response?.matchId) {
          const id = response.matchId.toLowerCase();
          for (let n = 0; n < 40 && !bodies.has(id); n++) { await check(); await sleep(100); }
          if (bodies.has(id)) {
            try {
              const analysis = window.TrackerNetworkAnalysis.analyze(await bodies.get(id), id, currentJob.player);
              await check();
              bodies.delete(id);
              if (!seen.has(id)) { seen.add(id); captures.push({networkAnalysis:analysis}); await update('progress', 'Validated response for ' + captures.length + ' of 5 matches.', {networkAnalysis:analysis, failures}); }
              continue;
            } catch (error) { bodies.delete(id); await update('progress', 'Response could not be validated; using visible round tables.'); }
          }
          response = await chrome.tabs.sendMessage(trackerTabId, {type:'CAPTURE_CURRENT', player:currentJob.player, matchId:id});
        }
      } else response = await requestCapture(index);
      await check();
      if (!response) throw new Error('Tracker page returned no capture.');
      if (response.done) break;
      if (response.error) {
        if (/BLOCKED:|profile does not match|Recent match list/i.test(response.error)) throw new Error(response.error);
        failures.push({ index: index + 1, message: response.error });
        await update('progress', 'Match ' + (index + 1) + ' could not be captured; continuing.', { failures });
        continue;
      }
      try {
        const parsed = window.MatchAnalyzer.validate(response.capture);
        if (parsed.player.riotId.trim().toLocaleLowerCase() !== currentJob.player.toLocaleLowerCase()) throw new Error('Player does not match.');
        if (seen.has(parsed.match.id)) continue;
        seen.add(parsed.match.id);
        captures.push(response.capture);
        await update('progress', 'Validated ' + captures.length + ' of 5 matches.', { capture: response.capture, failures });
      } catch (error) {
        failures.push({ index: index + 1, message: error.message });
        await update('progress', 'Match ' + (index + 1) + ' failed validation; continuing.', { failures });
      }
    }
    await update('finished', 'Finished: ' + captures.length + ' validated match(es), ' + failures.length + ' issue(s).', { captures: captures.filter(c => !c.networkAnalysis), failures });
  } catch (error) {
    if (currentJob?.id) {
      try { await update('finished', 'Collection stopped: ' + error.message, { captures: captures.filter(c => !c.networkAnalysis), failures }); }
      catch { status.textContent = 'Collection stopped: ' + error.message; }
    } else status.textContent = 'Collection could not start: ' + error.message;
  } finally {
    if (debuggerAttached && trackerTabId) { try { await chrome.debugger.detach({tabId:trackerTabId}); } catch {} }
    if (trackerWindowId) { try { await chrome.windows.remove(trackerWindowId); } catch {} }
    else if (trackerTabId) { try { await chrome.tabs.remove(trackerTabId); } catch {} }
  }
})();
