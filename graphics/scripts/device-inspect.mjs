// Только локальный CDP, открытый adb forward; PCM и permission tokens не читаются.
const endpoint = process.env.MUSIC_ORBS_CDP_URL || 'http://127.0.0.1:9223';
const targets = await (await fetch(`${endpoint}/json`)).json();
const target = targets.find(item => item.type === 'page' && item.url.startsWith('https://appassets.androidplatform.net/'));
if (!target) throw new Error('Локальная страница Music Orbs не найдена.');
const socket = new WebSocket(target.webSocketDebuggerUrl);
await new Promise((resolve, reject) => { socket.addEventListener('open', resolve, { once: true }); socket.addEventListener('error', reject, { once: true }); });
const expression = process.argv[2] || `(async () => {
 const canvas=document.querySelector('canvas'), gl=canvas?.getContext('webgl2'), rect=canvas?.getBoundingClientRect(), style=canvas?getComputedStyle(canvas):null;
 return { visibility:document.visibilityState, hidden:document.hidden, viewport:[innerWidth,innerHeight,devicePixelRatio], canvas:canvas?{width:canvas.width,height:canvas.height,rect:rect?.toJSON(),display:style?.display,cssWidth:style?.width,cssHeight:style?.height}:null,
 gl:gl?{lost:gl.isContextLost(),error:gl.getError(),drawingBuffer:[gl.drawingBufferWidth,gl.drawingBufferHeight],viewport:Array.from(gl.getParameter(gl.VIEWPORT)),version:gl.getParameter(gl.VERSION)}:null,
 notice:document.getElementById('notice')?.textContent,mode:document.getElementById('mode')?.textContent, motion:window.musicOrbs?.visualState?.(),snapshot:await window.musicOrbs?.snapshot?.()};
})()`;
try {
  const response = await new Promise((resolve, reject) => {
    const timeout = setTimeout(() => reject(new Error('CDP timeout')), 5000);
    socket.addEventListener('message', event => { const message = JSON.parse(event.data); if (message.id === 1) { clearTimeout(timeout); resolve(message); } });
    socket.send(JSON.stringify({ id: 1, method: 'Runtime.evaluate', params: { expression, returnByValue: true, awaitPromise: true } }));
  });
  console.log(JSON.stringify(response, null, 2));
} finally { socket.close(); }
