import assert from 'node:assert/strict';
import { AndroidDevice, appId, inspect, wait } from './android-device.mjs';

if (process.argv.includes('--help')) {
  console.log('node scripts/android-settings-smoke.mjs [--serial emulator-5554] [--remaining]');
  console.log('Все изменения проверяются native UI taps/swipes/text → живой snapshot и native store. CDP configure используется только для cleanup baseline.');
  process.exit(0);
}
const device = new AndroidDevice('native-settings');
const remaining = process.argv.includes('--remaining');
device.report.scope = remaining ? 'remaining-settings-custom-color-sliders-sync-fps-quality' : 'full-settings';
let cdp, baseline;
const captures = [];
const geometry = scene => [...scene.shapes].sort((a, b) => a.shapeId.localeCompare(b.shapeId));
const palette = colors => colors.map((colorHex, index) => ({ offset: index / (colors.length - 1), colorHex }));
const presets = [
  { shape: 'orb', label: 'Сфера из частиц', material: 'dots', palette: 'white', colors: ['#FFFFFF', '#FFFFFF'] },
  { shape: 'ribbon', label: 'Лента из частиц', material: 'dots', palette: 'pink', colors: ['#FF7BBF', '#FFD6EC'] },
  { shape: 'cloud', label: 'Облако из частиц', material: 'soft', palette: 'lilac', colors: ['#9875FF', '#E1C7FF'] },
  { shape: 'pixel-cloud', label: 'Пиксельное облако', material: 'pixels', palette: 'spectrum', colors: ['#FF306E', '#FFBE32', '#33E8CA', '#6585FF', '#D855FF'] },
];
const read = () => cdp.evaluate(inspect);
async function until(predicate, description, timeout = 12000) {
  const end = Date.now() + timeout;
  while (Date.now() < end) { const value = await read(); if (predicate(value.snapshot)) return value; await wait(200); }
  throw new Error(`Не подтверждено: ${description}`);
}
async function closePanel() {
  for (let attempt = 0; attempt < 3; attempt++) {
    device.shell('input', 'keyevent', '4'); await wait(300);
    if (device.nodes().some(node => node.text === 'Настройки')) return;
  }
  throw new Error('Панель не закрылась.');
}
async function choose(label, option) {
  await device.click(`${label}: `, { prefix: true, scroll: true });
  await device.click(option);
}
async function checkSaved(scene) {
  const deadline = Date.now() + 12000;
  while (Date.now() < deadline) {
    try {
      const raw = device.command(['exec-out', 'run-as', appId, 'cat', 'files/session.json']);
      assert(Buffer.byteLength(raw) <= 262144);
      const saved = JSON.parse(raw);
      assert.deepEqual(saved.settings, scene.settings); return saved;
    } catch { /* Debounce/atomic commit ещё выполняется. */ }
    await wait(200);
  }
  throw new Error('Native store не подтвердил settings.');
}
async function change(id, action, predicate, details) {
  const before = (await read()).snapshot;
  await device.openPanel(); await action(before.settings);
  device.write(`${id}-panel.xml`, device.xml());
  await closePanel();
  const after = (await until(predicate, id)).snapshot;
  assert.deepEqual(geometry(after), geometry(before), `${id}: касания панели изменили скульптуру`);
  const saved = await checkSaved(after); assert.deepEqual(geometry(saved), geometry(after));
  device.write(`${id}.json`, { before: before.settings, after: after.settings, persistedRevision: saved.revision, fieldsUnchanged: true });
  device.step(id, details || 'Native UI → подтверждённые settings/store; поля всех форм сохранены.');
  return after;
}

let failure;
try {
  await device.start(); cdp = await device.cdp();
  await until(scene => scene?.shapes?.length > 0, 'ready scene'); await wait(600);
  const initial = await read(); assert.equal(initial.motion.sourceSessionId, 'none', 'Сначала явно остановите аудио для UI-проверки.');
  assert(initial.canvas.height > 0 && initial.glError === 0);
  baseline = initial.snapshot; device.write('baseline.json', baseline);
  if (!remaining) {
  await change('quality-medium', () => choose('Плотность', 'Средняя · 10 000'), s => s.settings.quality === 'medium');
  await change('fps60', () => choose('Частота кадров', '60 FPS'), s => s.settings.targetFps === 60);
  for (const preset of presets) {
    const value = await change(`preset-${preset.shape}`, () => choose('Форма', preset.label), scene => scene.settings.shapeId === preset.shape);
    assert.equal(value.settings.materialId, preset.material); assert.equal(value.settings.paletteId, preset.palette);
    assert.deepEqual(value.settings.paletteStops, palette(preset.colors));
    await wait(500); device.screenshot(`preset-${preset.shape}`); device.write(`preset-${preset.shape}-parameters.json`, { settings: value.settings, shapes: value.shapes.map(s => ({ shapeId: s.shapeId, controls: s.controls.length, seed: s.seed })) });
    captures.push({ ...preset, settings: value.settings });
  }
  for (const [id, label] of [['dots', 'Точки'], ['soft', 'Мягкие частицы'], ['pixels', 'Пиксели']]) {
    await change(`material-${id}`, () => choose('Материал', label), scene => scene.settings.materialId === id);
  }
  for (const [id, label, colors] of [
    ['white', 'Белая', presets[0].colors], ['pink', 'Розовая', presets[1].colors],
    ['lilac', 'Лиловая', presets[2].colors], ['spectrum', 'Многоцветная', presets[3].colors],
  ]) {
    const value = await change(`palette-${id}`, () => choose('Палитра', label), scene => scene.settings.paletteId === id);
    assert.deepEqual(value.settings.paletteStops, palette(colors));
  }
  }
  await change('palette-custom', () => choose('Палитра', 'Свой цвет'), scene => scene.settings.paletteId === 'custom');
  const colorHex = '#3366CC';
  const custom = await change('custom-color', async settings => {
    let edit;
    for (let attempt = 0; attempt < 8 && !edit; attempt++) {
      const nodes = device.nodes();
      edit = nodes.find(node => (node.class === 'android.widget.EditText' || node.text === settings.colorHex) && node.bounds[3] - node.bounds[1] > 80);
      if (!edit) { device.scroll(); await wait(200); }
    }
    assert(edit, 'Поле HEX не найдено'); device.tap(edit); await wait(200);
    device.shell('input', 'keyevent', 'KEYCODE_MOVE_END');
    device.shell('input', 'keyevent', ...Array(8).fill('KEYCODE_DEL'));
    device.shell('input', 'text', `'${colorHex}'`);
    device.write('custom-color-typed.xml', device.xml()); device.screenshot('custom-color-typed');
    // IME уменьшает панель: свайп должен оставаться внутри её ScrollView, выше клавиатуры.
    let apply;
    for (let attempt = 0; attempt < 6; attempt++) {
      const nodes = device.nodes();
      const viewport = nodes.find(node => node.class === 'android.widget.ScrollView');
      assert(viewport, 'Нет видимой области панели');
      apply = nodes.find(node => node.text === 'Применить цвет' && node.bounds[3] <= viewport.bounds[3]);
      if (apply) break;
      const [left, top, right, bottom] = viewport.bounds;
      const x = Math.round(left + (right - left) * 0.77);
      device.shell('input', 'swipe', `${x}`, `${Math.round(top + (bottom - top) * 0.8)}`, `${x}`, `${Math.round(top + (bottom - top) * 0.3)}`, '350');
      await wait(200);
    }
    assert(apply, 'Кнопка применения цвета недоступна при открытой клавиатуре');
    device.write('custom-color-before-apply.xml', device.xml()); device.screenshot('custom-color-before-apply');
    device.tap(apply); await wait(250);
  }, scene => scene.settings.colorHex === colorHex && scene.settings.paletteId === 'custom');
  assert.deepEqual(custom.settings.paletteStops, palette([colorHex, colorHex])); device.screenshot('custom-color');
  for (const [key, label, maximum] of [['speed', 'Скорость', 2], ['amplitude', 'Амплитуда', 1], ['touchSensitivity', 'Чувствительность касания', 3]]) {
    let old, target;
    const value = await change(`slider-${key}`, async settings => {
      old = settings[key]; const fraction = old / maximum < 0.5 ? 0.8 : 0.2; target = fraction * maximum;
      const slider = await device.find(label, { scroll: true });
      const [left, top, right, bottom] = slider.bounds;
      device.shell('input', 'tap', `${Math.round(left + (right - left) * fraction)}`, `${Math.round((top + bottom) / 2)}`);
    }, scene => Number.isFinite(scene.settings[key]) && Math.abs(scene.settings[key] - old) > maximum * 0.08);
    assert(value.settings[key] >= 0 && value.settings[key] <= maximum);
    assert(Math.abs(value.settings[key] - target) < maximum * 0.25, `${key}: slider координата не соответствует диапазону`);
  }
  const syncBefore = (await read()).snapshot.settings.musicSync;
  await change('music-sync', () => device.click('Синхронизация с музыкой', { scroll: true }), scene => scene.settings.musicSync !== syncBefore);
  await change('fps30', () => choose('Частота кадров', '30 FPS'), scene => scene.settings.targetFps === 30);
  await change('quality-high', () => choose('Плотность', 'Высокая · 20 000'), scene => scene.settings.quality === 'high');
  device.step('panel-isolation', 'Во всех UI-действиях seed, controls и revisions всех четырёх форм совпали с состоянием до открытия панели.');
} catch (error) {
  failure = error;
  try { device.screenshot('action-failure'); device.write('action-failure.xml', device.xml()); } catch { }
}
finally {
  if (baseline && cdp) {
    try {
      await cdp.evaluate(`window.musicOrbs.command(${JSON.stringify({ type: 'configure', settings: baseline.settings })})`);
      await checkSaved({ settings: baseline.settings }); cdp.close(); cdp = null;
      device.shell('am', 'force-stop', appId); await device.start();
      const refreshed = await device.cdp(); refreshed.close();
      device.step('cleanup-baseline', 'Настройки восстановлены служебной командой и перезапуском native UI; этот шаг не считается UI-проверкой.');
    } catch (cleanup) { failure ||= cleanup; }
  }
  cdp?.close();
  const references = { orb: 'REF-04, 0–3,6 с', ribbon: 'REF-01, 0–2 с; REF-03, 2–4 с', cloud: 'Отдельного эталона нет; продуктовое предложение', 'pixel-cloud': 'REF-02, 3–5 с' };
  device.write('README.md', `# Нативные настройки и кадры для G4\n\n${device.report.startedAt}. Android Emulator API ${device.report.device.sdk}, ${device.report.device.model}.\nКоманда: \`node scripts/android-settings-smoke.mjs --serial ${device.serial}${remaining ? " --remaining" : ""}\`.\nГраницы: ${device.report.scope}.\nUI-проверка: ${failure ? 'не завершена; причина в report.json' : 'пройдена'}. [Полный отчёт](report.json).\n\nФормы выбраны через нативную панель; кадры сняты после её закрытия.\nИзменения проверены по живому snapshot и native store, поля всех форм сравниваются до/после каждого действия.\nСлужебное восстановление baseline через CDP исключено из доказательства UI.\n\n| Пресет | Кадр и параметры | Настройки | Референс |\n|---|---|---|---|\n${captures.map(p => `| ${p.label} | [PNG](preset-${p.shape}.png), [JSON](preset-${p.shape}-parameters.json) | ${p.settings.quality}, ${p.settings.targetFps} FPS, ${p.settings.materialId}, ${p.settings.paletteId}, speed ${p.settings.speed}, amplitude ${p.settings.amplitude}, touch ${p.settings.touchSensitivity}, sync ${p.settings.musicSync} | ${references[p.shape]} |`).join('\n')}\n\n[Реестр референсов](../../../../docs/references.md). Кадры — статические артефакты, они не подтверждают плавность движения.\nЧеловеческое решение FT-28/29/G4: **pending**. Физический телефон, нагрев, батарея и слышимый звук хоста не проверяются этим прогоном.\n`);
  device.finish(failure);
}
