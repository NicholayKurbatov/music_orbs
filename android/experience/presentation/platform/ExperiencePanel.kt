package musicorbs.experience.presentation.platform

import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import musicorbs.audio.domain.SourceKind
import musicorbs.experience.domain.MaterialId
import musicorbs.experience.domain.PaletteId
import musicorbs.experience.domain.PaletteStop
import musicorbs.experience.domain.ProfileLimits
import musicorbs.experience.domain.Quality
import musicorbs.experience.domain.SceneSettings
import musicorbs.experience.domain.ShapeId
import java.util.Locale

/** UI отображает подтверждённые settings; применение, ошибки и сохранение принадлежат Experience. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicOrbsScreen(
    scene: WebView,
    settings: SceneSettings,
    status: String,
    sourceKind: SourceKind?,
    playing: Boolean,
    onSettings: (SceneSettings) -> Unit,
    onReset: () -> Unit,
    onPick: () -> Unit,
    onCapture: () -> Unit,
    onMicrophone: () -> Unit,
    onPauseResume: () -> Unit,
    onStop: () -> Unit,
    settingsEnabled: Boolean = true,
) {
    var panelVisible by rememberSaveable { mutableStateOf(false) }
    MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFFD0B3FF), background = Color(0xFF05050B), surface = Color(0xFF16141F))) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            key(scene) { AndroidView(factory = { scene }, modifier = Modifier.fillMaxSize()) }
            Column(Modifier.align(Alignment.TopCenter).fillMaxWidth().statusBarsPadding().padding(16.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("MUSIC ORBS", style = MaterialTheme.typography.labelLarge, color = Color.White)
                    Button(onClick = { panelVisible = true }) { Text("Настройки") }
                }
                if (sourceKind == SourceKind.MICROPHONE) {
                    Surface(color = Color(0xFF60243B), shape = MaterialTheme.shapes.small) {
                        Row(Modifier.padding(start = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("● Микрофон", color = Color.White, modifier = Modifier.semantics { contentDescription = "Выбран микрофон" })
                            TextButton(onClick = onStop) { Text("Выключить", color = Color.White) }
                        }
                    }
                }
            }
            if (status.isNotBlank()) {
                Surface(
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(16.dp),
                    color = Color(0xDE191723), shape = MaterialTheme.shapes.medium,
                ) {
                    Text(status, modifier = Modifier.padding(14.dp).semantics { liveRegion = LiveRegionMode.Polite }, color = Color.White, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        if (panelVisible) {
            // ModalBottomSheet использует отдельное модальное окно и scrim: касания не доходят WebView.
            ModalBottomSheet(onDismissRequest = { panelVisible = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
                Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 24.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Ваша скульптура", style = MaterialTheme.typography.titleLarge)
                        TextButton(onClick = { panelVisible = false }) { Text("Готово") }
                    }
                    Text("Потяните форму. Отпустите и наблюдайте.", style = MaterialTheme.typography.bodySmall)
                    if (status.isNotBlank()) {
                        Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.small) {
                            Text(status, modifier = Modifier.padding(12.dp).semantics { liveRegion = LiveRegionMode.Polite }, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Choice("Форма", settings.shapeId, ShapeId.entries, ::shapeLabel, enabled = settingsEnabled) { shape ->
                        val palette = when (shape) { ShapeId.ORB -> PaletteId.WHITE; ShapeId.RIBBON -> PaletteId.PINK; ShapeId.CLOUD -> PaletteId.LILAC; ShapeId.PIXEL_CLOUD -> PaletteId.SPECTRUM }
                        val material = when (shape) { ShapeId.CLOUD -> MaterialId.SOFT; ShapeId.PIXEL_CLOUD -> MaterialId.PIXELS; else -> MaterialId.DOTS }
                        onSettings(settings.copy(shapeId = shape, materialId = material, paletteId = palette, paletteStops = ProfileLimits.PALETTES.getValue(palette)))
                    }
                    Choice("Материал", settings.materialId, MaterialId.entries, ::materialLabel, enabled = settingsEnabled) { onSettings(settings.copy(materialId = it)) }
                    Choice("Палитра", settings.paletteId, PaletteId.entries, ::paletteLabel, enabled = settingsEnabled) { palette ->
                        val stops = if (palette == PaletteId.CUSTOM) listOf(PaletteStop(0.0, settings.colorHex), PaletteStop(1.0, settings.colorHex)) else ProfileLimits.PALETTES.getValue(palette)
                        onSettings(settings.copy(paletteId = palette, paletteStops = stops))
                    }
                    if (settings.paletteId == PaletteId.CUSTOM) CustomColor(settings, onSettings, enabled = settingsEnabled)
                    SettingSlider("Скорость", settings.speed, 0f..2f, enabled = settingsEnabled) { onSettings(settings.copy(speed = it)) }
                    SettingSlider("Амплитуда", settings.amplitude, 0f..1f, enabled = settingsEnabled) { onSettings(settings.copy(amplitude = it)) }
                    SettingSlider("Чувствительность касания", settings.touchSensitivity, 0f..3f, enabled = settingsEnabled) { onSettings(settings.copy(touchSensitivity = it)) }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Синхронизация с музыкой")
                        Switch(checked = settings.musicSync, onCheckedChange = { onSettings(settings.copy(musicSync = it)) }, enabled = settingsEnabled, modifier = Modifier.semantics { contentDescription = "Синхронизация с музыкой" })
                    }
                    Choice("Плотность", settings.quality, Quality.entries, ::qualityLabel, enabled = settingsEnabled) { onSettings(settings.copy(quality = it)) }
                    Choice("Частота кадров", settings.targetFps, listOf(60, 30), { "$it FPS" }, enabled = settingsEnabled) { onSettings(settings.copy(targetFps = it)) }
                    OutlinedButton(onClick = onReset, enabled = settingsEnabled, modifier = Modifier.fillMaxWidth()) { Text("Сбросить активную форму") }
                    Text("Звук", style = MaterialTheme.typography.titleMedium)
                    Text(sourceLabel(sourceKind), style = MaterialTheme.typography.bodySmall)
                    Button(onClick = onPick, modifier = Modifier.fillMaxWidth()) { Text("Выбрать аудиофайл") }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = onPauseResume, enabled = sourceKind == SourceKind.LOCAL_FILE, modifier = Modifier.weight(1f)) { Text(if (playing) "Пауза" else "Продолжить") }
                        OutlinedButton(onClick = onStop, enabled = sourceKind != null, modifier = Modifier.weight(1f)) { Text("Стоп") }
                    }
                    OutlinedButton(onClick = onCapture, modifier = Modifier.fillMaxWidth()) { Text("Захват внешнего воспроизведения") }
                    Text("Потребуется системное согласие. Доступность зависит от источника звука.", style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = onMicrophone, modifier = Modifier.fillMaxWidth()) { Text("Выбрать микрофон") }
                }
            }
        }
    }
}

@Composable
private fun <T> Choice(label: String, value: T, values: List<T>, name: (T) -> String, enabled: Boolean = true, onSelect: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Box {
            OutlinedButton(onClick = { expanded = true }, enabled = enabled, modifier = Modifier.fillMaxWidth().semantics { contentDescription = "$label: ${name(value)}" }) { Text(name(value)) }
            DropdownMenu(expanded = expanded && enabled, onDismissRequest = { expanded = false }) {
                values.forEach { option -> DropdownMenuItem(text = { Text(name(option)) }, enabled = enabled, onClick = { expanded = false; onSelect(option) }) }
            }
        }
    }
}

@Composable
private fun SettingSlider(label: String, value: Double, range: ClosedFloatingPointRange<Float>, enabled: Boolean = true, onCommit: (Double) -> Unit) {
    var draft by remember(value, enabled) { mutableStateOf(value.toFloat()) }
    Column {
        Text("$label · ${String.format(Locale.ROOT, "%.2f", draft)}", style = MaterialTheme.typography.labelMedium)
        Slider(value = draft, onValueChange = { draft = it }, onValueChangeFinished = { if (enabled) onCommit(draft.toDouble()) }, enabled = enabled, valueRange = range, modifier = Modifier.semantics { contentDescription = label })
    }
}

@Composable
private fun CustomColor(settings: SceneSettings, onSettings: (SceneSettings) -> Unit, enabled: Boolean = true) {
    var draft by remember(settings.colorHex) { mutableStateOf(settings.colorHex) }
    val valid = draft.matches(Regex("#[0-9A-Fa-f]{6}"))
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(value = draft, onValueChange = { draft = it.take(7).uppercase(Locale.ROOT) }, label = { Text("Цвет #RRGGBB") }, singleLine = true, enabled = enabled, isError = !valid, modifier = Modifier.fillMaxWidth())
        TextButton(onClick = { onSettings(settings.copy(colorHex = draft, paletteId = PaletteId.CUSTOM, paletteStops = listOf(PaletteStop(0.0, draft), PaletteStop(1.0, draft)))) }, enabled = enabled && valid, modifier = Modifier.fillMaxWidth()) { Text("Применить цвет") }
    }
}

private fun shapeLabel(value: ShapeId) = when (value) { ShapeId.ORB -> "Сфера из частиц"; ShapeId.RIBBON -> "Лента из частиц"; ShapeId.CLOUD -> "Облако из частиц"; ShapeId.PIXEL_CLOUD -> "Пиксельное облако" }
private fun materialLabel(value: MaterialId) = when (value) { MaterialId.DOTS -> "Точки"; MaterialId.PIXELS -> "Пиксели"; MaterialId.SOFT -> "Мягкие частицы" }
private fun paletteLabel(value: PaletteId) = when (value) { PaletteId.WHITE -> "Белая"; PaletteId.PINK -> "Розовая"; PaletteId.LILAC -> "Лиловая"; PaletteId.SPECTRUM -> "Многоцветная"; PaletteId.CUSTOM -> "Свой цвет" }
private fun qualityLabel(value: Quality) = when (value) { Quality.LOW -> "Низкая · 4 000"; Quality.MEDIUM -> "Средняя · 10 000"; Quality.HIGH -> "Высокая · 20 000" }
private fun sourceLabel(value: SourceKind?) = when (value) { SourceKind.LOCAL_FILE -> "Локальный аудиофайл"; SourceKind.PLAYBACK_CAPTURE -> "Внешнее воспроизведение"; SourceKind.MICROPHONE -> "Выбран микрофон"; null -> "Источник не выбран · автономное движение" }
