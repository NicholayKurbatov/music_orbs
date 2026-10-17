export async function connectLocalCdp(endpoint) {
  const url = new URL(endpoint);
  if (url.protocol !== 'http:' || !['localhost', '127.0.0.1', '[::1]'].includes(url.hostname) || url.username || url.password || url.search) throw new Error('CDP разрешён только через локальный adb forward.');
  const targets = await (await fetch(new URL('/json', url), { signal: AbortSignal.timeout(5000) })).json();
  const target = targets.find(item => item.type === 'page' && item.url.startsWith('https://appassets.androidplatform.net/'));
  if (!target) throw new Error('Локальная страница Music Orbs не найдена.');
  const socketUrl = new URL(target.webSocketDebuggerUrl);
  if (socketUrl.protocol !== 'ws:' || !['localhost', '127.0.0.1', '[::1]'].includes(socketUrl.hostname)) throw new Error('CDP WebSocket должен быть локальным.');
  const socket = new WebSocket(socketUrl);
  await new Promise((resolve, reject) => {
    const timer = setTimeout(() => { socket.close(); reject(new Error('CDP connect timeout.')); }, 5000);
    socket.addEventListener('open', () => { clearTimeout(timer); resolve(); }, { once: true });
    socket.addEventListener('error', () => { clearTimeout(timer); reject(new Error('CDP connection failed.')); }, { once: true });
  });
  let sequence = 0, closed = false;
  const pending = new Map();
  const listeners = new Set();
  socket.addEventListener('message', event => {
    let message; try { message = JSON.parse(event.data); } catch { return; }
    if (message.id !== undefined) {
      const waiter = pending.get(message.id); if (!waiter) return;
      pending.delete(message.id); clearTimeout(waiter.timer);
      if (message.error) waiter.reject(new Error(`CDP: ${message.error.message}`)); else waiter.resolve(message.result);
    } else { for (const listener of listeners) listener(message); }
  });
  const rejectPending = () => { closed = true; for (const waiter of pending.values()) { clearTimeout(waiter.timer); waiter.reject(new Error('CDP закрыт.')); } pending.clear(); };
  socket.addEventListener('close', rejectPending);
  const call = (method, params = {}) => new Promise((resolve, reject) => {
    if (closed || pending.size >= 16) { reject(new Error('CDP недоступен или очередь заполнена.')); return; }
    const id = ++sequence;
    const timer = setTimeout(() => { pending.delete(id); reject(new Error(`CDP timeout: ${method}`)); }, 10000);
    pending.set(id, { resolve, reject, timer });
    try { socket.send(JSON.stringify({ id, method, params })); }
    catch (error) { pending.delete(id); clearTimeout(timer); reject(error); }
  });
  return {
    target: { title: target.title, url: target.url }, call,
    onEvent(listener) { listeners.add(listener); return () => listeners.delete(listener); },
    async evaluate(expression) {
      const result = await call('Runtime.evaluate', { expression, returnByValue: true, awaitPromise: true });
      if (result.exceptionDetails) throw new Error(result.exceptionDetails.exception?.description?.split('\n')[0] || result.exceptionDetails.text);
      return result.result.value;
    },
    close() { if (closed) return; rejectPending(); listeners.clear(); socket.close(); },
  };
}
