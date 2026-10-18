import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';

const sdk = process.env.ANDROID_HOME || path.join(process.env.LOCALAPPDATA, 'Android', 'Sdk');
const adb = path.join(sdk, 'platform-tools', 'adb.exe');
const java = path.join(process.env.JAVA_HOME, 'bin', 'java.exe');
const outputIndex = process.argv.indexOf('--output');
const output = outputIndex < 0 ? 'tests/device/results/p8-update' : process.argv[outputIndex + 1];
fs.mkdirSync(output, { recursive: true });
fs.mkdirSync('build/p8-update', { recursive: true });
const run = a => execFileSync(adb, ['-s', 'emulator-5554', ...a], { windowsHide: true, maxBuffer: 8 * 1024 * 1024 });
const sha = b => crypto.createHash('sha256').update(b).digest('hex');
const pkg = () => { const text = run(['shell', 'dumpsys', 'package', 'musicorbs.app']).toString(); return {
  versionCode: Number(/versionCode=(\d+)/.exec(text)?.[1]), versionName: /versionName=([^\r\n]+)/.exec(text)?.[1].trim() }; };
const cert = file => {
  const text = execFileSync(java, ['-jar', path.join(sdk, 'build-tools/35.0.0/lib/apksigner.jar'), 'verify', '--print-certs', file], { encoding: 'utf8', windowsHide: true });
  return /Signer #1 certificate SHA-256 digest: ([a-f0-9]+)/i.exec(text)?.[1];
};
const apk = 'android/build/outputs/apk/debug/app-debug.apk';
const before = pkg();
const saved = run(['exec-out', 'run-as', 'musicorbs.app', 'cat', 'files/session.json']);
const installedPath = run(['shell', 'pm', 'path', 'musicorbs.app']).toString().trim().replace('package:', '');
run(['pull', installedPath, 'build/p8-update/previous.apk']);
const certificateBefore = cert('build/p8-update/previous.apk');
const certificateAfter = cert(apk);
assert(certificateBefore && certificateBefore === certificateAfter, 'Update signing certificate must match');
run(['shell', 'am', 'force-stop', 'musicorbs.app']);
const install = run(['install', '-r', apk]).toString().trim();
assert(install.includes('Success'));
const after = pkg();
const restored = run(['exec-out', 'run-as', 'musicorbs.app', 'cat', 'files/session.json']);
assert(after.versionCode > before.versionCode, 'versionCode must increase');
assert.equal(after.versionName, '0.2.0-dev');
assert(saved.equals(restored), 'session.json must survive update byte-for-byte');
const report = { performedAt: new Date().toISOString(), result: 'pass', applicationId: 'musicorbs.app',
  before, after, certificateSha256: certificateAfter, apkSha256: sha(fs.readFileSync(apk)),
  sessionSha256Before: sha(saved), sessionSha256After: sha(restored), install,
  uninstall: false, clearData: false, limits: ['Android Emulator API 35; Honor 200 Pro не проверен.'] };
fs.writeFileSync(`${output}/report.json`, JSON.stringify(report, null, 2) + '\n');
console.log(JSON.stringify(report, null, 2));
