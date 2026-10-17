import fs from 'node:fs';
import path from 'node:path';
import { execFileSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import assert from 'node:assert/strict';

export const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
export const wait = milliseconds => new Promise(resolve => setTimeout(resolve, milliseconds));
export const appId = 'musicorbs.app';
export const activity = `${appId}/musicorbs.bootstrap.MainActivity`;
const xmlText = value => value.replace(/&quot;/g, '"').replace(/&apos;/g, "'").replace(/&lt;/g, '<').replace(/&gt;/g, '>').replace(/&amp;/g, '&');

export class AndroidDevice {
  constructor(kind) {
    const args = process.argv.slice(2);
    const serialIndex = args.indexOf('--serial');
    this.serial = serialIndex < 0 ? 'emulator-5554' : args[serialIndex + 1];
    assert(/^emulator-\d+$/.test(this.serial), 'Разрешён только Android Emulator.');
    this.sdk = process.env.ANDROID_HOME || process.env.ANDROID_SDK_ROOT || (process.env.LOCALAPPDATA && path.join(process.env.LOCALAPPDATA, 'Android', 'Sdk'));
    assert(this.sdk, 'Укажите ANDROID_HOME.');
    this.adb = path.join(this.sdk, 'platform-tools', process.platform === 'win32' ? 'adb.exe' : 'adb');
    assert(fs.existsSync(this.adb), 'Не найден SDK platform-tools/adb.');
    this.output = path.join(root, 'tests', 'device', 'results', `${kind}-${new Date().toISOString().replace(/[:.]/g, '-')}`);
    fs.mkdirSync(this.output, { recursive: true });
    const size = this.shell('wm', 'size').match(/(\d+)x(\d+)/g)?.at(-1)?.split('x').map(Number) ?? [1080, 2340];
    [this.width, this.height] = size;
    this.report = { schemaVersion: 1, startedAt: new Date().toISOString(), serial: this.serial, result: 'running', steps: [], limits: ['Android Emulator API 35; физическое устройство не проверено.', 'Звук хоста и эстетическая приёмка человеком не подтверждаются.'] };
    this.report.device = { model: this.shell('getprop', 'ro.product.model').trim(), android: this.shell('getprop', 'ro.build.version.release').trim(), sdk: this.shell('getprop', 'ro.build.version.sdk').trim() };
  }
  command(args, { binary = false, input } = {}) {
    return execFileSync(this.adb, ['-s', this.serial, ...args], { encoding: binary ? null : 'utf8', input, timeout: 30000, maxBuffer: 12 * 1024 * 1024, windowsHide: true, stdio: [input == null ? 'ignore' : 'pipe', 'pipe', 'pipe'] });
  }
  shell(...args) { return this.command(['shell', ...args]); }
  write(name, data) { fs.writeFileSync(path.join(this.output, name), typeof data === 'string' || Buffer.isBuffer(data) ? data : `${JSON.stringify(data, null, 2)}\n`); }
  screenshot(name) { this.write(`${name}.png`, this.command(['exec-out', 'screencap', '-p'], { binary: true })); }
  step(id, details, result = 'pass') { this.report.steps.push({ id, result, details }); this.write('report.json', this.report); console.log(`${result === 'pass' ? '✓' : '•'} ${id}: ${details}`); }
  finish(error) {
    this.report.result = error ? 'fail' : 'pass';
    if (error) {
      let text = error instanceof Error ? error.message : String(error);
      if (error?.stdout) text += `\n${String(error.stdout).slice(0, 1500)}`;
      if (error?.stderr) text += `\n${String(error.stderr).slice(0, 1500)}`;
      for (const [value, replacement] of [[this.adb, 'adb'], [this.sdk, '<android-sdk>'], [root, '<project>'], [process.env.USERPROFILE, '<user>']]) if (value) text = text.replaceAll(value, replacement);
      this.report.failure = text;
      try { this.screenshot('failure'); this.write('failure.xml', this.xml()); } catch { }
      console.error(text); process.exitCode = 1;
    }
    this.report.finishedAt = new Date().toISOString(); this.write('report.json', this.report);
    console.log(`Артефакты: ${path.relative(root, this.output).split(path.sep).join('/')}`);
  }
  xml() { this.shell('uiautomator', 'dump', '--compressed', '/sdcard/music-orbs-automation.xml'); return this.command(['exec-out', 'cat', '/sdcard/music-orbs-automation.xml']); }
  nodes() {
    return [...this.xml().matchAll(/<node\b([^>]+)>/g)].map(match => {
      const node = Object.fromEntries([...match[1].matchAll(/([\w-]+)="([^"]*)"/g)].map(pair => [pair[1], xmlText(pair[2])]));
      node.bounds = node.bounds?.match(/^\[(\d+),(\d+)\]\[(\d+),(\d+)\]$/)?.slice(1).map(Number);
      return node;
    }).filter(node => node.enabled !== 'false' && node.bounds && node.bounds[2] > node.bounds[0] && node.bounds[3] > node.bounds[1]);
  }
  scroll(direction = 'down') {
    const [start, end] = direction === 'up' ? [0.35, 0.78] : [0.78, 0.35];
    this.shell('input', 'swipe', `${Math.round(this.width * .77)}`, `${Math.round(this.height * start)}`, `${Math.round(this.width * .77)}`, `${Math.round(this.height * end)}`, '350');
  }
  async find(value, { prefix = false, scroll = false, attempts = scroll ? 8 : 4 } = {}) {
    for (let attempt = 0; attempt < attempts; attempt++) {
      const nodes = this.nodes().filter(node => [node.text, node['content-desc']].some(text => prefix ? text?.startsWith(value) : text === value));
      if (nodes.length) return nodes.at(-1);
      if (scroll) this.scroll(scroll === 'up' ? 'up' : 'down');
      await wait(200);
    }
    throw new Error(`UI element missing: ${value}`);
  }
  tap(node) { const [left, top, right, bottom] = node.bounds; this.shell('input', 'tap', `${Math.round((left + right) / 2)}`, `${Math.round((top + bottom) / 2)}`); }
  async click(value, options) { this.tap(await this.find(value, options)); await wait(250); }
  isPanelOpen() { return this.nodes().some(node => ['Ваша скульптура', 'Выбрать аудиофайл', 'Сбросить активную форму', 'Звук'].includes(node.text) || node['content-desc']?.startsWith('Форма: ')); }
  async openPanel() { if (this.isPanelOpen()) return; await this.click('Настройки'); await this.find('Ваша скульптура'); }
  async closePanel() { this.shell('input', 'keyevent', '4'); await wait(250); }
  async start() { this.shell('input', 'keyevent', 'KEYCODE_WAKEUP'); this.shell('wm', 'dismiss-keyguard'); this.shell('am', 'start', '-W', '-n', activity); await wait(1000); if (this.isPanelOpen()) await this.closePanel(); await this.find('Настройки', { attempts: 8 }); }
  async chooseDocument(filename) {
    // Сначала Recent; затем стандартный системный корень Downloads.
    let nodes = this.nodes();
    let file = nodes.find(node => node.text === filename || node['content-desc'] === filename);
    if (!file) {
      const drawer = nodes.find(node => /Show roots|Показать корни|Открыть.*меню|Open navigation/i.test(node['content-desc']));
      if (drawer) { this.tap(drawer); await wait(300); }
      nodes = this.nodes();
      const downloads = nodes.find(node => /^(Downloads|Загрузки)$/.test(node.text));
      if (downloads) { this.tap(downloads); await wait(350); }
      file = await this.find(filename, { scroll: true, attempts: 5 });
    }
    this.tap(file); await wait(700);
  }
  async cdp() {
    const pids = this.shell('pidof', appId).trim().split(/\s+/);
    const sockets = this.shell('cat', '/proc/net/unix').split(/\r?\n/).map(line => line.trim().split(/\s+/).at(-1)).filter(value => value?.startsWith('@webview_devtools_remote'));
    const socket = sockets.find(value => pids.some(pid => value.endsWith(`_${pid}`)));
    assert(socket, 'Не найден debug WebView socket текущего приложения.');
    this.command(['forward', 'tcp:9223', `localabstract:${socket.slice(1)}`]);
    const targets = await (await fetch('http://127.0.0.1:9223/json', { signal: AbortSignal.timeout(5000) })).json();
    const target = targets.find(value => value.type === 'page' && value.url.startsWith('https://appassets.androidplatform.net/'));
    assert(target, 'Не найдена локальная страница Music Orbs в CDP.');
    const url = new URL(target.webSocketDebuggerUrl); url.hostname = '127.0.0.1'; url.port = '9223';
    return Cdp.open(url.toString());
  }
}

class Cdp {
  constructor(socket) { this.socket = socket; this.sequence = 0; this.pending = new Map(); socket.addEventListener('message', event => { const value = JSON.parse(event.data); this.pending.get(value.id)?.(value); }); }
  static async open(url) {
    const socket = new WebSocket(url);
    await new Promise((resolve, reject) => { socket.addEventListener('open', resolve, { once: true }); socket.addEventListener('error', reject, { once: true }); });
    return new Cdp(socket);
  }
  async evaluate(expression) {
    const id = ++this.sequence;
    const result = await new Promise((resolve, reject) => {
      const timeout = setTimeout(() => { this.pending.delete(id); reject(new Error('CDP evaluate timeout')); }, 8000);
      this.pending.set(id, response => { clearTimeout(timeout); this.pending.delete(id); resolve(response); });
      this.socket.send(JSON.stringify({ id, method: 'Runtime.evaluate', params: { expression, returnByValue: true, awaitPromise: true } }));
    });
    assert(!result.error && !result.result?.exceptionDetails, 'CDP expression failed');
    return result.result.result.value;
  }
  close() { this.socket.close(); }
}

export const inspect = `(async()=>({motion:window.musicOrbs.visualState(),snapshot:await window.musicOrbs.snapshot(),canvas:{width:document.querySelector('canvas').getBoundingClientRect().width,height:document.querySelector('canvas').getBoundingClientRect().height},glError:document.querySelector('canvas').getContext('webgl2').getError(),now:performance.now()}))()`;
