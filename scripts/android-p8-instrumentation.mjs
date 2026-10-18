import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import { spawn, execFileSync } from 'node:child_process';

const args = process.argv.slice(2);
const value = (flag, fallback) => args.includes(flag) ? args[args.indexOf(flag) + 1] : fallback;
const serial = value('--serial', 'emulator-5554');
if (!/^emulator-\d+$/.test(serial)) throw Error('Этот runner предназначен только для эмулятора; FT-36 выполняется отдельно.');
const sdk = process.env.ANDROID_HOME || path.join(process.env.LOCALAPPDATA, 'Android', 'Sdk');
const adb = path.join(sdk, 'platform-tools', 'adb.exe');
const output = value('--output', `tests/device/results/p8-native-${new Date().toISOString().replace(/[:.]/g, '-')}`);
fs.mkdirSync(output, { recursive: true });
const run = (a, input) => execFileSync(adb, ['-s', serial, ...a], { encoding: input ? null : 'utf8', input, windowsHide: true, timeout: 30000, maxBuffer: 8 * 1024 * 1024 });
const hash = b => crypto.createHash('sha256').update(b).digest('hex');
const classes = value('--class', 'musicorbs.platform.audio.VisualizerTest');
const saved = Buffer.from(run(['exec-out', 'run-as', 'musicorbs.app', 'cat', 'files/session.json']));
const permission = /android.permission.RECORD_AUDIO: granted=(true|false), flags=\[([^\]]*)\]/.exec(run(['shell', 'dumpsys', 'package', 'musicorbs.app']));
const report = { startedAt: new Date().toISOString(), serial, classes, result: 'running',
  apkSha256: hash(fs.readFileSync('android/build/outputs/apk/debug/app-debug.apk')),
  sessionSha256Before: hash(saved), initialRecordAudioGranted: permission?.[1] === 'true',
  limits: ['Android Emulator API 35; Honor 200 Pro/Яндекс Музыка не проверены.', 'Только scalar metrics; waveform/FFT/PCM не записываются.'] };
let log = '';
try {
  const child = spawn(adb, ['-s', serial, 'shell', 'am', 'instrument', '-w', '-r', '-e', 'class', classes, 'musicorbs.app.test/androidx.test.runner.AndroidJUnitRunner'], { windowsHide: true });
  const stream = fs.createWriteStream(path.join(output, 'instrumentation.txt'));
  const append = chunk => { const text = chunk.toString(); log += text; stream.write(text); process.stdout.write(text); };
  child.stdout.on('data', append); child.stderr.on('data', append);
  await new Promise((resolve, reject) => { child.on('error', reject); child.on('exit', code => code === 0 ? resolve() : reject(Error(`adb exit ${code}`))); });
  await new Promise(resolve => stream.end(resolve));
  fs.writeFileSync(path.join(output, 'instrumentation.txt'), log.replace(/[ \t]+$/gm, '').trimEnd() + '\n');
  report.result = /OK \(\d+ tests?\)/.test(log) && !/FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed/.test(log) ? 'pass' : 'fail';
  report.tests = Number(/OK \((\d+) tests?\)/.exec(log)?.[1] || /Tests run: (\d+)/.exec(log)?.[1] || 0);
  report.evidence = [...log.matchAll(/P7_AUDIO_EVIDENCE (\{[^\r\n]+\})/g)].map(m => JSON.parse(m[1]));
} catch (error) { report.result = 'fail'; report.failure = String(error.message); }
finally {
  run(['shell', 'am', 'force-stop', 'musicorbs.app']);
  const after = Buffer.from(run(['exec-out', 'run-as', 'musicorbs.app', 'cat', 'files/session.json']));
  report.sessionPreservedByTests = saved.equals(after);
  if (!saved.equals(after)) run(['shell', 'run-as', 'musicorbs.app', 'sh', '-c', "'cat > files/session.json'"], saved);
  if (permission?.[1] === 'false') run(['shell', 'pm', 'revoke', 'musicorbs.app', 'android.permission.RECORD_AUDIO']);
  report.sessionSha256After = hash(Buffer.from(run(['exec-out', 'run-as', 'musicorbs.app', 'cat', 'files/session.json'])));
  report.finishedAt = new Date().toISOString();
  fs.writeFileSync(path.join(output, 'report.json'), `${JSON.stringify(report, null, 2)}\n`);
  console.log(`Результат: ${report.result}; отчёт: ${output}/report.json`);
}
if (report.result !== 'pass' || !report.sessionPreservedByTests) process.exitCode = 1;
