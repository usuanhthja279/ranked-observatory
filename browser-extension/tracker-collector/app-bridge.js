(() => {
  const allowed = ['http://127.0.0.1:8090', 'http://localhost:8090', 'http://127.0.0.1:8091', 'http://localhost:8091'];
  if (!allowed.includes(location.origin) || !new URLSearchParams(location.search).has('player')) return;
  const send = data => window.postMessage({ source: 'observatory-tracker-extension', ...data }, location.origin);
  document.addEventListener('click', event => {
    if (!event.isTrusted || !event.target.closest('#matchAnalyzerCollect')) return;
    const targetMatches = Number(document.getElementById('matchAnalyzerTarget')?.value || 5);
    chrome.runtime.sendMessage({ type: 'START', targetMatches }, response => {
      if (chrome.runtime.lastError) send({ type: 'error', message: 'Extension could not start: ' + chrome.runtime.lastError.message });
      else if (!response || !response.ok) send({ type: 'error', message: response?.error || 'Collector could not start.' });
      else send({ type: 'started', jobId: response.jobId });
    });
  });
  chrome.runtime.onMessage.addListener((message, sender) => {
    if (sender.id !== chrome.runtime.id || message?.type !== 'APP_UPDATE') return;
    send(message.update);
  });
  window.addEventListener('message', event => {
    if (event.source !== window || event.origin !== location.origin || event.data?.source !== 'observatory-match-import-result') return;
    chrome.runtime.sendMessage({ type: 'APP_IMPORT_RESULT', jobId: event.data.jobId, batchNumber: event.data.batchNumber, result: event.data.result });
  });
})();
