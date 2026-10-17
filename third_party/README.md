# Third-party inventory

Реестр включённых runtime-зависимостей, sampling-фрагмента и build tooling:
[runtime](runtime.md). Полные LICENSE/NOTICE сохранены рядом.
Установлен инструмент разработки OpenSpec; его сгенерированные skills находятся
в `.agents/skills/`. Происхождение и лицензия: [OpenSpec](openspec.md),
[MIT notice](OpenSpec-LICENSE).
План и проверенные upstream commits: [docs/reuse.md](../docs/reuse.md).

При включении каждого фрагмента добавить:
- upstream repository, commit и исходный путь;
- локальный путь и список изменений;
- исходный LICENSE и copyright notice;
- способ обновления и контрактные проверки.

LICENSE в корне относится к самому проекту и не заменяет notices зависимостей.
