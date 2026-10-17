# Исправление нулевой высоты canvas

2026-10-17, AOSP API 35 x86_64, профиль Pixel 5, Android WebView 124.
Это проверка эмулятора; визуальное решение человека и производительность
телефона не подтверждаются.

Первый [G2](../g2-2026-10-17T19-29-03-309Z/report.json) показал пустую сцену.
При CDP-диагностике `innerHeight=851`, CSS-высота canvas и его rect равнялись
нулю, хотя WebGL2 drawing buffer составлял 786×1702, программа shader была
связана, `gl.getError()=0`, RAF выполнялся. `100dvh` поддерживался синтаксически,
но WebView после размещения в Compose AndroidView вычислял нулевую высоту.

Установка CSS-размеров из фактического viewport восстановила изображение:
[кадр после live-проверки](live-css-fix.png), [CDP](cdp-live-fix.json).
После этого исправление внесено в bootstrap: fixed canvas и явные CSS px
при resize, отдельно от GPU backing buffer. Камера учитывает узкий portrait;
обычные HTML header/hint скрыты в native режиме, где их предоставляет Compose.

Повторно собран и установлен APK с `index-D2qlhTjZ.js` и `index-BBqcT9Hu.css`.
[Новый кадр](fresh-build.png) показывает Orb без ручных CDP-изменений.
CSS canvas 393×851, buffer 786×1702, WebGL error 0.
[Первый CDP](fresh-build-cdp.json) снят до завершения restore и содержит исходное
поле; для окончательного состояния используется [settled CDP](settled-cdp.json).

После реального `adb input swipe` Orb изменился с revision 14 / 1 control
до revision 31 / 2 controls. Сравнение [снимка до install-r](saved-before-install.json)
и [подтверждённого сохранения после жеста](saved-after-drag.json) проверено
через deepEqual: первый прежний control сохранён точно, новая деформация
добавлена отдельно, revision сессии выросла с 3 до 4.
[Кадр после жеста](after-drag.png).

Browser smoke дополнен проверкой нулевой CSS-высоты и portrait resize:
inline-размер canvas сохраняет размеры viewport даже при внешнем правиле
`height:0px`. Полный повтор G2 со сменой форм, quality, reset и corruption
выполняется отдельным сценарием; этот отчёт его не заменяет.
