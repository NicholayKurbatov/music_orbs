import fs from 'node:fs';
import { execFileSync } from 'node:child_process';

const files = [...new Set(execFileSync('git', ['ls-files', '-c', '-o', '--exclude-standard'], { encoding: 'utf8' }).split(/\r?\n/))].filter(f => f && fs.existsSync(f));
const tree = {};
for (const file of files.sort()) {
  let current = tree;
  for (const part of file.split('/')) current = current[part] ??= {};
}
function render(node, prefix = '') {
  return Object.entries(node).flatMap(([name, value], i, entries) => {
    const last = i === entries.length - 1;
    return [prefix + (last ? '\\-- ' : '|-- ') + name + (Object.keys(value).length ? '/' : ''), ...render(value, prefix + (last ? '    ' : '|   '))];
  });
}
const body = `# Дерево проекта и карта модулей

Дерево содержит существующие файлы рабочего проекта. Игнорируемые build/dist,
node_modules, SDK, локальные референсы и Git-метаданные не входят в список.
Реализация начата; фактические проверки и ограничения описаны в [tests.md](tests.md).

- domain — модели, чистые политики и агрегаты, без платформенных импортов.
- application — сценарии и порты; реализации адаптеров создаются в bootstrap.
- infrastructure/platform/presentation — библиотечные, Android, WebView и UI адаптеры.
- graphics — browser harness и локальный bundle для Android WebView.
- core — JVM runner общих Kotlin-контекстов, без зависимости от Android SDK.
- contracts — schema и общие JSON fixtures Kotlin/TypeScript.
- tests — контрактные тесты и планы device/функциональной приёмки.
- third_party — происхождение и лицензии включённых зависимостей/фрагментов.
- openspec — задачи и delta specs текущего изменения; checkbox требует проверки.
- scripts — статический аудит связности и воспроизводимое обновление этого дерева.

Обновление: \`node scripts/update-tree.mjs\`. Проверка: \`node scripts/check-project.mjs\`.

\`\`\`text
MusicOrbs/
${render(tree).join('\n')}
\`\`\`

Локальные видео и производные кадры находятся в игнорируемой references/local/;
они не включаются в assets приложения. Их реестр — [references.md](references.md).
Пакеты и зависимости реализации — [implementation-plan.md](implementation-plan.md).
`;
fs.writeFileSync('docs/tree.md', body, 'utf8');
console.log(`${files.length} files documented`);
