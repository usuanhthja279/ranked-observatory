(async () => {
  const status = document.getElementById('status');
  const sleep = ms => new Promise(resolve => setTimeout(resolve, ms));
  let debuggerAttached = false;
  const pendingResponses = new Map(), bodies = new Map();
  let stopped = false, trackerTabId = null, trackerWindowId = null, currentJob = null, lastPageId = null;
  chrome.runtime.onMessage.addListener(message => {
    if (message?.type === 'COLLECTOR_BLOCKED' && message.jobId === currentJob?.id) stopped = true;
    if (message?.type === 'APP_IMPORT_RESULT_TO_RUNNER' && message.jobId === currentJob?.id) {
      const pending = pendingImports.get(message.batchNumber);
      if (pending) { clearTimeout(pending.timer); pendingImports.delete(message.batchNumber); pending.resolve(message.result); }
    }
  });
  const pendingImports = new Map();
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
  async function importBatch(payload, batchNumber) {
    return new Promise(async resolve => {
      const timer = setTimeout(() => { pendingImports.delete(batchNumber); resolve({ ok:false, message:'Observatory import timed out; the JSON download remains available.' }); }, 60000);
      pendingImports.set(batchNumber, { resolve, timer });
      try {
        const result = await chrome.runtime.sendMessage({ type:'RUNNER_UPDATE', jobId:currentJob.id,
          update:{ type:'import', message:'Saving validated response batch ' + batchNumber + ' to your Observatory account.', export:payload, batchNumber } });
        if (!result?.ok) { clearTimeout(timer); pendingImports.delete(batchNumber); resolve({ ok:false, message:result?.error || 'The Observatory could not receive this batch.' }); }
      } catch (error) { clearTimeout(timer); pendingImports.delete(batchNumber); resolve({ ok:false, message:String(error.message || error).slice(0,300) }); }
    });
  }
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
  let captures = [], failures = [], seen = new Set(), responses = [];
  let nextIndex = 0, checkpointEnabled = true, checkpointFailureReported = false, resumeFinalizing = false;
  const restoredUiResults = [];
  function responseExport(matches, issues = failures, boundedBatch = false) {
    return { schemaVersion:1, source:'tracker.gg', player:currentJob.player, exportedAt:new Date().toISOString(),
      requestedMatches:boundedBatch ? matches.length : currentJob.targetMatches, responseCount:matches.length,
      complete:boundedBatch ? matches.length > 0 : matches.length === currentJob.targetMatches && issues.length === 0,
      failures:issues, matches };
  }
  async function saveCheckpoint(stage = 'COLLECTING', index = nextIndex) {
    if (!checkpointEnabled) return;
    nextIndex = Math.min(currentJob.targetMatches, Math.max(0, index));
    try {
      await window.TrackerCollectorCheckpoint.save({ player:currentJob.player, targetMatches:currentJob.targetMatches,
        nextIndex, stage, captures, responses, failures });
    } catch (error) {
      checkpointEnabled = false;
      if (!checkpointFailureReported) {
        checkpointFailureReported = true;
        failures.push({ message:'Local resume checkpoint could not be saved; this run can continue, but an interrupted run may need to start again.' });
        status.textContent = 'Local resume checkpoint unavailable; continuing this collection.';
      }
    }
  }
  async function clearCheckpoint() {
    try { await window.TrackerCollectorCheckpoint.clear(currentJob.player, currentJob.targetMatches); }
    catch { /* A stale local checkpoint is harmless and expires after 30 days. */ }
  }
  async function restoreCheckpoint() {
    const saved = await window.TrackerCollectorCheckpoint.load(currentJob.player, currentJob.targetMatches);
    if (!saved || !Array.isArray(saved.captures) || !Array.isArray(saved.responses) || !Array.isArray(saved.failures)) return false;
    let recoveredRecords = 0, checkpointDataInvalid = false;
    failures = saved.failures.filter(item => item && typeof item.message === 'string').slice(0, currentJob.targetMatches * 2);
    for (const capture of saved.captures.slice(0, currentJob.targetMatches)) {
      if (!capture?.match || !capture?.player) continue;
      try {
        const parsed = window.MatchAnalyzer.validate(capture);
        if (parsed.player.riotId.trim().toLocaleLowerCase() !== currentJob.player.trim().toLocaleLowerCase()) { checkpointDataInvalid = true; continue; }
        if (seen.has(parsed.match.id)) continue;
        seen.add(parsed.match.id); captures.push(capture); restoredUiResults.push({ capture });
        recoveredRecords++;
      } catch { checkpointDataInvalid = true; failures.push({ message:'A saved rendered-page capture failed validation and was discarded.' }); }
    }
    for (const record of saved.responses.slice(0, currentJob.targetMatches)) {
      try {
        const id = String(record.matchId || '').toLowerCase();
        const body = record.response;
        const segments = body?.data?.segments;
        const selected = Array.isArray(segments) && segments.some(s => s?.type === 'player-summary' &&
          typeof s.attributes?.platformUserIdentifier === 'string' &&
          s.attributes.platformUserIdentifier.trim().toLocaleLowerCase() === currentJob.player.trim().toLocaleLowerCase());
        if (!/^[0-9a-f-]{36}$/.test(id) || String(body?.data?.attributes?.id || '').toLowerCase() !== id || !selected || record.validation !== 'passed') { checkpointDataInvalid = true; continue; }
        const analysis = window.TrackerNetworkAnalysis.analyze(body, id, currentJob.player);
        if (!responses.some(item => item.matchId === id)) responses.push({ matchId:id, validation:'passed', response:body });
        if (!seen.has(id)) { seen.add(id); captures.push({ networkAnalysis:analysis }); }
        if (!restoredUiResults.some(item => item.networkAnalysis?.matchId === id)) restoredUiResults.push({ networkAnalysis:analysis });
        recoveredRecords++;
      } catch { checkpointDataInvalid = true; failures.push({ message:'A saved network response failed identity/analysis validation and was discarded.' }); }
    }
    nextIndex = Math.min(currentJob.targetMatches, Math.max(0, Number(saved.nextIndex) || 0));
    resumeFinalizing = saved.stage === 'FINALIZING';
    if (resumeFinalizing) nextIndex = currentJob.targetMatches;
    else if (checkpointDataInvalid) nextIndex = 0;
    if ((saved.captures.length || saved.responses.length) && recoveredRecords === 0 && !resumeFinalizing) return false;
    return captures.length > 0 || resumeFinalizing;
  }
  async function exportResponses() {
    if (!responses.length) return 'No full match responses available for JSON export.';
    const content = JSON.stringify(responseExport(responses), null, 2);
    const url = URL.createObjectURL(new Blob([content], {type:'application/json'}));
    try {
      await update('progress', 'Choose where to save the match response JSON. Use the ignored Observatory build or data folder.');
      await chrome.downloads.download({url, filename:'tracker-match-responses-' + new Date().toISOString().replace(/[:.]/g,'-') + '.json', saveAs:true});
      return 'JSON save started for ' + responses.length + ' response(s).';
    } catch (error) { return 'JSON export failed or was cancelled: ' + String(error.message || error); }
    finally { setTimeout(() => URL.revokeObjectURL(url), 60000); }
  }
  try {
    for (let n = 0; n < 30; n++) {
      currentJob = await getJob();
      if (currentJob?.runnerTabId) break;
      await sleep(100);
    }
    if (!currentJob?.runnerTabId) throw new Error('Collector session was not initialized.');
    let resumed = false;
    try { resumed = await restoreCheckpoint(); }
    catch { failures.push({ message:'A saved local checkpoint could not be read; starting a new collection.' }); }
    if (!resumed) { captures = []; responses = []; failures = []; seen.clear(); nextIndex = 0; resumeFinalizing = false; await saveCheckpoint(); }
    const collectorWindow = await chrome.windows.create({ url: 'about:blank', type: 'popup', focused: false, width: 680, height: 760 });
    trackerWindowId = collectorWindow.id;
    const tab = collectorWindow.tabs?.[0];
    if (!tab?.id) throw new Error('Tracker collector window could not be created.');
    trackerTabId = tab.id;
    currentJob.trackerTabId = tab.id;
    await chrome.storage.session.set({ collectorJob: currentJob });
    const profile = 'https://tracker.gg/valorant/profile/riot/' + encodeURIComponent(currentJob.player) + '/overview';
    await update('progress', (resumed ? (resumeFinalizing ? 'Resuming saved collection finalization for ' : 'Resuming saved collection at match ' + (nextIndex + 1) + ' for ') : 'Opening Tracker profile for ') + currentJob.player + '.');
    if (resumed) for (const result of restoredUiResults) await update('progress', 'Resumed saved collection: restored a validated match.', result);
    try {
      await chrome.debugger.attach({tabId:trackerTabId}, '1.3'); debuggerAttached = true;
      await chrome.debugger.sendCommand({tabId:trackerTabId}, 'Network.enable', {});
    } catch { if (debuggerAttached) { try { await chrome.debugger.detach({tabId:trackerTabId}); } catch {} } debuggerAttached = false; await update('progress', 'Response capture unavailable; using visible round tables.'); }
    await navigate(profile);
    for (let index = nextIndex; index < currentJob.targetMatches && captures.length < currentJob.targetMatches; index++) {
      await check();
      if (index) await sleep(3000);
      if (index) {
        const reset = await chrome.tabs.sendMessage(trackerTabId, { type: 'CLOSE_MATCH' });
        if (!reset?.closed) await navigate(profile);
      }
      await update('progress', 'Reading recent match ' + (index + 1) + ' (captured ' + captures.length + ' of ' + currentJob.targetMatches + ').');
      let response;
      if (debuggerAttached) {
        response = await requestCapture(index, 'OPEN_ONE');
        if (response?.matchId) {
          const id = response.matchId.toLowerCase();
          for (let n = 0; n < 40 && !bodies.has(id); n++) { await check(); await sleep(100); }
          if (bodies.has(id)) {
            try {
              const body = await bodies.get(id);
              const data = body?.data;
              const selected = Array.isArray(data?.segments) && data.segments.some(s => s?.type === 'player-summary' && typeof s.attributes?.platformUserIdentifier === 'string' && s.attributes.platformUserIdentifier.trim().toLocaleLowerCase() === currentJob.player.trim().toLocaleLowerCase());
              if (String(data?.attributes?.id || '').toLowerCase() !== id || !selected) throw new Error('Response identity does not match.');
              if (!responses.some(item => item.matchId === id)) responses.push({ matchId:id, validation:'pending', response:body });
              const record = responses.find(item => item.matchId === id);
              record.validation = 'passed';
              let analysis;
              try { analysis = window.TrackerNetworkAnalysis.analyze(body, id, currentJob.player); }
              catch (error) {
                record.analysisWarning = String(error.message).slice(0,300);
                failures.push({ index:index + 1, message:'The response was captured, but one summary analysis was unavailable: ' + record.analysisWarning });
              }
              await check();
              bodies.delete(id);
              if (!seen.has(id)) {
                seen.add(id); captures.push(analysis ? {networkAnalysis:analysis} : {networkMatchId:id});
                await saveCheckpoint('COLLECTING', index + 1);
                await update('progress', 'Validated response for ' + captures.length + ' of ' + currentJob.targetMatches + ' matches.', {networkAnalysis:analysis, failures});
              } else await saveCheckpoint('COLLECTING', index + 1);
              continue;
            } catch (error) { bodies.delete(id); await update('progress', 'Response could not be validated; using visible round tables.'); }
          }
          response = await chrome.tabs.sendMessage(trackerTabId, {type:'CAPTURE_CURRENT', player:currentJob.player, matchId:id});
        }
      } else response = await requestCapture(index);
      await check();
      if (!response) throw new Error('Tracker page returned no capture.');
      if (response.done) { await saveCheckpoint('FINALIZING', currentJob.targetMatches); break; }
      if (response.error) {
        if (/BLOCKED:|profile does not match|Recent match list/i.test(response.error)) throw new Error(response.error);
        failures.push({ index: index + 1, message: response.error });
        await saveCheckpoint('COLLECTING', index + 1);
        await update('progress', 'Match ' + (index + 1) + ' could not be captured; continuing.', { failures });
        continue;
      }
      try {
        const parsed = window.MatchAnalyzer.validate(response.capture);
        if (parsed.player.riotId.trim().toLocaleLowerCase() !== currentJob.player.toLocaleLowerCase()) throw new Error('Player does not match.');
        if (seen.has(parsed.match.id)) { await saveCheckpoint('COLLECTING', index + 1); continue; }
        seen.add(parsed.match.id);
        captures.push(response.capture);
        await saveCheckpoint('COLLECTING', index + 1);
        await update('progress', 'Validated ' + captures.length + ' of ' + currentJob.targetMatches + ' matches.', { capture: response.capture, failures });
      } catch (error) {
        failures.push({ index: index + 1, message: error.message });
        await saveCheckpoint('COLLECTING', index + 1);
        await update('progress', 'Match ' + (index + 1) + ' failed validation; continuing.', { failures });
      }
    }
    await saveCheckpoint('FINALIZING', currentJob.targetMatches);
    const validResponses = responses.filter(item => item.validation === 'passed');
    const importReceipts = [];
    for (let offset = 0; offset < validResponses.length; offset += 5) {
      const group = validResponses.slice(offset, offset + 5);
      const payload = responseExport(group, [], true);
      const receipt = await importBatch(payload, Math.floor(offset / 5) + 1);
      if (receipt?.ok) importReceipts.push(receipt);
      else failures.push({ index:offset + 1, message:'Account save failed: ' + String(receipt?.message || 'unknown error').slice(0,250) });
    }
    const importStatus = importReceipts.length ? 'Saved ' + importReceipts.reduce((n, item) => n + Number(item.acceptedMatches || 0), 0) + ' validated match(es) to your Observatory account.' : (validResponses.length ? 'Account save unavailable; use the JSON export.' : 'No validated network responses were available for account save.');
    const exportStatus = await exportResponses();
    await update('finished', 'Finished: ' + captures.length + ' of ' + currentJob.targetMatches + ' requested matches captured, ' + failures.length + ' issue(s). ' + importStatus + ' ' + exportStatus, { captures: captures.filter(c => c?.match && c?.player), failures });
    if (!validResponses.length || exportStatus.startsWith('JSON save started')) await clearCheckpoint();
  } catch (error) {
    if (currentJob?.blockedStatus || /BLOCKED:|private profile|requires verification/i.test(String(error.message))) await clearCheckpoint();
    else if (currentJob?.id) await saveCheckpoint();
    if (currentJob?.id) {
      try { const exportStatus = await exportResponses(); await update('finished', 'Collection stopped: ' + error.message + '. ' + exportStatus, { captures: captures.filter(c => c?.match && c?.player), failures }); }
      catch { status.textContent = 'Collection stopped: ' + error.message; }
    } else status.textContent = 'Collection could not start: ' + error.message;
  } finally {
    if (debuggerAttached && trackerTabId) { try { await chrome.debugger.detach({tabId:trackerTabId}); } catch {} }
    if (trackerWindowId) { try { await chrome.windows.remove(trackerWindowId); } catch {} }
    else if (trackerTabId) { try { await chrome.tabs.remove(trackerTabId); } catch {} }
  }
})();
