# OpenSpec: инструмент планирования

- Upstream: https://github.com/Fission-AI/OpenSpec.
- Поставка: npm `@fission-ai/openspec@1.14.3`, глобальная установка CLI;
  пакет не является runtime dependency приложения.
- Git tag `v1.14.3`: commit `c794978f4f8845ce59693373c9dd882ee776588c`,
  проверен через `git ls-remote` 2026-10-17.
- npm integrity: `sha512-ALFW1d5bSm6t27Kmcle93SDFuqUc8fLwy5ZdBfIiNffo0Yk9AEOJxw6xDqBv38eMM7l7TeOZBB/7C08luxYGfg==`.
- Исходные шаблоны: `src/core/templates/workflows/` и
  `src/core/templates/skill-templates.ts`; в npm —
  `dist/core/templates/workflows/*.js` и `dist/core/templates/skill-templates.js`.
- Целевые файлы: `.agents/skills/openspec-{propose,explore,apply-change,update-change,sync-specs,archive-change}/SKILL.md`
  и marker `.agents/skills/.openspec-target`, сгенерированы
  `openspec init --tools codex --language Russian --profile core`.
- Изменения: generated skills не редактировались; конфигурация проекта
  `openspec/config.yaml` дополнена русским контекстом и правилами артефактов.
- Copyright (c) 2024 OpenSpec Contributors; MIT, точная копия
  license notice из установленного пакета: [OpenSpec-LICENSE](OpenSpec-LICENSE).
- Обновление: выбрать версию CLI, выполнить `openspec update`, просмотреть diff,
  выполнить strict validation. Проектные требования остаются в docs и change artifacts.

Подготовленные proposal/design/specs/tasks являются документацией Music Orbs.
CLI и generated skills не означают переиспользование renderer/DSP/capture модулей
из [плана reuse](../docs/reuse.md).
