(() => {
  const databaseName = 'observatory-tracker-collector';
  const storeName = 'checkpoints';
  const maxAgeMs = 30 * 24 * 60 * 60 * 1000;

  function openDatabase() {
    return new Promise((resolve, reject) => {
      const request = indexedDB.open(databaseName, 1);
      request.onupgradeneeded = () => {
        const database = request.result;
        if (!database.objectStoreNames.contains(storeName)) database.createObjectStore(storeName, { keyPath: 'key' });
      };
      request.onsuccess = () => resolve(request.result);
      request.onerror = () => reject(request.error || new Error('Could not open the local collection checkpoint store.'));
    });
  }

  function keyFor(player, targetMatches) {
    const normalized = String(player || '').trim().normalize('NFKC').toLocaleLowerCase('en-US');
    return `${normalized}\u0000${Number(targetMatches)}`;
  }

  async function read(key) {
    const database = await openDatabase();
    try {
      return await new Promise((resolve, reject) => {
        const transaction = database.transaction(storeName, 'readonly');
        const request = transaction.objectStore(storeName).get(key);
        request.onsuccess = () => resolve(request.result || null);
        request.onerror = () => reject(request.error || new Error('Could not read the local collection checkpoint.'));
      });
    } finally { database.close(); }
  }

  async function write(checkpoint) {
    const database = await openDatabase();
    try {
      await new Promise((resolve, reject) => {
        const transaction = database.transaction(storeName, 'readwrite');
        transaction.objectStore(storeName).put(checkpoint);
        transaction.oncomplete = resolve;
        transaction.onerror = () => reject(transaction.error || new Error('Could not save the local collection checkpoint.'));
        transaction.onabort = () => reject(transaction.error || new Error('Local collection checkpoint save was interrupted.'));
      });
    } finally { database.close(); }
  }

  async function remove(key) {
    const database = await openDatabase();
    try {
      await new Promise((resolve, reject) => {
        const transaction = database.transaction(storeName, 'readwrite');
        transaction.objectStore(storeName).delete(key);
        transaction.oncomplete = resolve;
        transaction.onerror = () => reject(transaction.error || new Error('Could not remove the local collection checkpoint.'));
      });
    } finally { database.close(); }
  }

  globalThis.TrackerCollectorCheckpoint = Object.freeze({
    async load(player, targetMatches) {
      const key = keyFor(player, targetMatches);
      const checkpoint = await read(key);
      if (!checkpoint) return null;
      if (checkpoint.schemaVersion !== 1 || checkpoint.key !== key || Date.now() - Number(checkpoint.updatedAt || 0) > maxAgeMs) {
        await remove(key);
        return null;
      }
      return checkpoint;
    },
    save(value) {
      const player = String(value?.player || '').trim();
      const targetMatches = Number(value?.targetMatches);
      if (!player || ![5, 10, 20].includes(targetMatches)) return Promise.reject(new Error('Invalid collection checkpoint identity.'));
      const checkpoint = {
        schemaVersion: 1,
        key: keyFor(player, targetMatches),
        player,
        targetMatches,
        updatedAt: Date.now(),
        nextIndex: Math.min(targetMatches, Math.max(0, Number(value.nextIndex) || 0)),
        stage: value.stage === 'FINALIZING' ? 'FINALIZING' : 'COLLECTING',
        captures: Array.isArray(value.captures) ? value.captures.slice(0, targetMatches) : [],
        responses: Array.isArray(value.responses) ? value.responses.slice(0, targetMatches) : [],
        failures: Array.isArray(value.failures) ? value.failures.slice(0, targetMatches * 2) : []
      };
      return write(checkpoint);
    },
    clear(player, targetMatches) { return remove(keyFor(player, targetMatches)); }
  });
})();
