# P7: прямые instrumentation-проверки API 35

Полный прогон шести методов на неизменённом целевом APK: **5 PASS, 1 FAIL**.
Дополнительный диагностический повтор gesture: **1 FAIL** с event trace.
Тесты запускаются через `am instrument`, без UTP, удаления целевого приложения
или очистки его данных. Исходные условия — в `environment-before.json`.
Снимок `session-before.json` содержит исходные данные тестовой сцены; после
каждого завершённого класса сохранены точные байты и проверка SHA-256.

| Запуск | Результат | Наблюдение |
| --- | --- | --- |
| `policy-raw.txt` | 2/3 PASS, 1 FAIL; 11,868 с | Аудит обнаружил создание `files/profileInstalled`, 24 байта, от AndroidX ProfileInstaller. Запрет захвата дал нулевой peak. |
| `policy-retry-raw.txt` | 3/3 PASS; 14,8 с | Перед исходным файловым снимком дождались публичного future ProfileVerifier. Исключения файлов из аудита не добавлялись. |
| `main-raw.txt` | 1/2 PASS, 1 FAIL; 97,591 с | Main capture → HOME → возврат прошёл. Mic: индикатор не найден из-за неверного определения открытой прокрученной панели в тестовой фикстуре. |
| `main-retry-raw.txt` | 0/2 PASS, 2 FAIL; 80,228 с | Mic активен, затем общий timeout 5 с; capture: системная кнопка consent не найдена. Эти попытки не считаются прохождением. |
| `main-mic-diagnostic-raw.txt` | 0/1 PASS, 1 FAIL; 75,973 с | HOME принят, Activity перешла в CREATED, сверху Launcher, recording configurations пусты через 1200 мс. Позже timeout snapshot; полного прохождения возврата нет. |
| `final-policy-raw.txt` | 3/3 PASS; 14,441 с | Оба положительных контроля, запрещённый источник и аудит capture/mic/local прошли. |
| `final-main-raw.txt` | 2/2 PASS; 55,569 с | Main mic/capture → HOME → возврат без скрытого перезапуска, с сохранением формы и настроек. |
| `final-gesture-raw.txt` | 0/1 PASS, 1 FAIL; 17,769 с | Второй палец не изменил controls. Первый DOWN после настоящего ACTION_CANCEL не начал новый жест. |
| `gesture-trace-raw.txt` | 0/1 PASS, 1 FAIL; 18,032 с | Диагностический повтор сохранил FAIL и зарегистрировал trusted touchcancel без pointercancel/lostpointercapture для основного пальца. |

В успешном policy-прогоне оба разрешённых контрольных источника дали peak
`0.36621094`. Источник `ALLOW_CAPTURE_BY_NONE` дал peak `0` на 98 304 samples;
его playback head вырос с 65 280 до 164 288, play state — 3. Во время чтения и
после закрытия capture/mic файлов было 52 → 52, local — 53 → 53 с заранее
созданным синтетическим входным WAV. Изменённых файлов и событий записи не было.
В финальном прогоне capture/mic: 47 → 47 файлов, local: 48 → 48, снова без
изменённых файлов и событий записи. Это проверка синтетического AudioTrack на
эмуляторе, а не совместимости плееров.

При первом успешном Main capture измерена energy `0.2595457`, FGS был активен;
после HOME FGS отсутствовал, после возврата наблюдался автономный режим без
источника. Метрики и UUID находятся в raw status, PCM и consent tokens не пишутся.

Исправления относятся только к тестовой фикстуре: ожидание ProfileVerifier до
начала аудита; определение панели по видимым аудиокнопкам; проверка bounds перед
кликом; ограниченный UiAutomator idle timeout с восстановлением; вызов
`ActivityScenario.onActivity` из Main dispatcher. Последнее избегает
`waitForIdleSync` worker-ветки ActivityScenario, мешавшего snapshot во время
непрерывной анимации. Timeout snapshot остался 5 с. Snapshot теперь пишет
stage/token/elapsed/error; готовность дополнительно сверяет восстановленную
сцену с валидным исходным store. Полный финальный прогон использовал эти исправления.

В финальном Main mic был активен настоящий MIC source 1/session 273 с UUID и
индикатором; после HOME Activity CREATED/Launcher, recorders 0 через 1200 мс.
После возврата — source none, autonomous, indicator false, FGS false: 20
наблюдений за 6270 мс. Main capture energy `0.2581007438895314`, FGS true;
после HOME FGS false, после возврата 20 наблюдений за 5011 мс без источника и
индикатора. Settings/shapes совпали с исходными. Амплитуда физического
микрофона и слышимость выхода здесь не проверялись.

В gesture trace первая доверенная pointerdown дала revision 0 → 1. Затем
прошли pointer events второго пальца без изменения controls и его pointerup.
После native ACTION_CANCEL были только touchcancel, а для основного pointer 2
не было pointercancel/lostpointercapture. Следующая доверенная pointerdown
пришла в тот же canvas с теми же координатами и modelView, revision осталась 1.
PointerController слушает pointercancel/lostpointercapture; совместимость этого
пути с реальным Android CANCEL требует исправления и повторной проверки.
Сценарий outside-UP не достигнут и **не считается пройденным**. Диагностические
listeners ограничены 96 событиями, удаляются в finally и не изменяют runtime.

Сохранён и один неуспешный build (`build-profile-warmup.txt`): прямая ссылка на
runtime-only ProfileVerifier не компилировалась. В фикстуре используется только
reflection его публичного API; зависимости и runtime приложения не менялись.

Перед финальными классами записан `session-before-final.json`: settings/shapes
совпали с исходными, native revision выросла 68 → 78 после другого приёмочного
сценария. Новый SHA — `969907c216cd3d8bd7771309f50f80cc4ab573b4af23d2f0bcdad63f2675d397`.
После каждого финального класса и диагностического повторения exact bytes
совпали с новым baseline. SHA целевого APK остался
`8cb87a65cd09bbcc042b1da468d59a2fe33ab79edd8e45b996b580e6570ddf61`;
он не переустанавливался. RECORD_AUDIO восстановлено в исходное granted=false
с прежними flags. Проверка — `after-gesture-trace.json`, итоги — `report.json`.
