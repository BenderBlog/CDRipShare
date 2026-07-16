# 封面背景色可选 + 自动色后处理 实现方案

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 封面生成时支持用户从 8 种背景色模式中选择（1 自动 + 7 固定预设），自动模式对提取的颜色做去饱和+压暗后处理

**Architecture:** 新增 `BackgroundMode` 枚举模型（仿 `AudioOutputMode` 模式），在 `CoverImageGenerator` 中增加 HSL 颜色工具函数和 `adjustBackgroundColor` 后处理逻辑，`MainViewModel` 持有 `bgMode` 状态并透传，`ImagePickerPanel` 中增加下拉选择器

**Tech Stack:** Kotlin, Jetpack Compose Desktop, Skia (org.jetbrains.skia)

---

## File Structure

| 文件 | 操作 | 职责 |
|------|------|------|
| `src/main/kotlin/.../model/BackgroundMode.kt` | 新建 | 背景色模式枚举（1 Auto + 7 固定色） |
| `src/main/kotlin/.../util/CoverImageGenerator.kt` | 修改 | 增加 HSL 工具、`adjustBackgroundColor`、`generate` 接受 `BackgroundMode` |
| `src/main/kotlin/.../viewmodel/MainViewModel.kt` | 修改 | 增加 `bgMode` 状态，透传到 `CoverImageGenerator.generate` |
| `src/main/kotlin/.../ui/ImagePickerPanel.kt` | 修改 | 增加背景色模式选择 UI |
| `src/main/kotlin/.../ui/App.kt` | 修改 | 传入 bgMode 参数到 ImagePickerPanel |

---

### Task 1: 新建 BackgroundMode 模型

**Files:**
- Create: `src/main/kotlin/io/github/benderblog/cdripshare/model/BackgroundMode.kt`

- [ ] **Step 1: 创建 BackgroundMode.kt**

```kotlin
package io.github.benderblog.cdripshare.model

enum class BackgroundMode(val label: String) {
    Auto("自动提取"),
    DarkBluePurple("深蓝紫"),
    Navy("藏蓝"),
    GitHubDark("GitHub 暗"),
    DarkGray("深灰"),
    DarkGreen("墨绿"),
    DarkBrown("深棕"),
    ClassicBlack("经典黑");

    /** 固定色模式下返回对应的 ARGB 颜色值；Auto 模式返回 0（标记使用自动提取） */
    val colorHex: Int
        get() = when (this) {
            Auto           -> 0
            DarkBluePurple -> 0xFF1E2130L.toInt()
            Navy           -> 0xFF0F1A2EL.toInt()
            GitHubDark     -> 0xFF0D1117L.toInt()
            DarkGray       -> 0xFF2C2C2CL.toInt()
            DarkGreen      -> 0xFF1B2A1EL.toInt()
            DarkBrown      -> 0xFF2A1F1AL.toInt()
            ClassicBlack   -> 0xFF111111L.toInt()
        }
}
```

- [ ] **Step 2: 验证编译**

Run: `./gradlew compileKotlin 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add src/main/kotlin/io/github/benderblog/cdripshare/model/BackgroundMode.kt
git commit -m "feat: add BackgroundMode model for cover background color selection"
```

---

### Task 2: 修改 CoverImageGenerator - 添加 HSL 工具 + 颜色后处理 + 模式参数

**Files:**
- Modify: `src/main/kotlin/io/github/benderblog/cdripshare/util/CoverImageGenerator.kt`

- [ ] **Step 1: 修改 generate() 签名并增加 import**

在文件顶部增加 import：

```kotlin
import io.github.benderblog.cdripshare.model.BackgroundMode
import kotlin.math.max
import kotlin.math.min
```

将 `generate` 方法签名改为：

```kotlin
fun generate(sourceImageFile: File, outputFile: File, bgMode: BackgroundMode = BackgroundMode.Auto): File {
```

- [ ] **Step 2: 添加 HSL ↔ RGB 转换工具方法**

在 `CoverImageGenerator` 对象体内，常量定义区域之后、`generate()` 之前，添加：

```kotlin
    /**
     * ARGB → HSL 分量数组 [hue=0..360, saturation=0..1, lightness=0..1]
     */
    private fun rgbToHsl(argb: Int): FloatArray {
        val r = ((argb shr 16) and 0xFF) / 255f
        val g = ((argb shr 8) and 0xFF) / 255f
        val b = (argb and 0xFF) / 255f

        val maxC = max(max(r, g), b)
        val minC = min(min(r, g), b)
        val delta = maxC - minC

        val l = (maxC + minC) / 2f
        val s = if (delta == 0f) 0f else delta / (1f - kotlin.math.abs(2f * l - 1f))
        val h = when {
            delta == 0f -> 0f
            maxC == r   -> 60f * (((g - b) / delta) % 6f)
            maxC == g   -> 60f * (((b - r) / delta) + 2f)
            else        -> 60f * (((r - g) / delta) + 4f)
        }

        return floatArrayOf(if (h < 0) h + 360f else h, s.coerceIn(0f, 1f), l.coerceIn(0f, 1f))
    }

    /**
     * HSL → ARGB (alpha 固定 0xFF)
     */
    private fun hslToRgb(h: Float, s: Float, l: Float): Int {
        val c = (1f - kotlin.math.abs(2f * l - 1f)) * s
        val x = c * (1f - kotlin.math.abs((h / 60f) % 2f - 1f))
        val m = l - c / 2f

        val (r1, g1, b1) = when {
            h < 60   -> Triple(c, x, 0f)
            h < 120  -> Triple(x, c, 0f)
            h < 180  -> Triple(0f, c, x)
            h < 240  -> Triple(0f, x, c)
            h < 300  -> Triple(x, 0f, c)
            else     -> Triple(c, 0f, x)
        }

        val r = ((r1 + m) * 255f + 0.5f).toInt().coerceIn(0, 255)
        val g = ((g1 + m) * 255f + 0.5f).toInt().coerceIn(0, 255)
        val b = ((b1 + m) * 255f + 0.5f).toInt().coerceIn(0, 255)

        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    /**
     * 对提取的主色做后处理：保留色相，饱和度降到 30%，亮度压到 15%~25% 之间
     */
    private fun adjustBackgroundColor(argb: Int): Int {
        val hsl = rgbToHsl(argb)
        val sat = (hsl[1] * 0.3f).coerceIn(0f, 1f)
        val light = (hsl[2] * 0.5f).coerceIn(0.10f, 0.25f)
        return hslToRgb(hsl[0], sat, light)
    }
```

- [ ] **Step 3: 修改 generate() 中 bgColor 的获取逻辑**

将：

```kotlin
val bgColor = extractDominantColor(image)
```

替换为：

```kotlin
val bgColor = when (bgMode) {
    BackgroundMode.Auto -> adjustBackgroundColor(extractDominantColor(image))
    else -> bgMode.colorHex or (0xFF shl 24)
}
```

- [ ] **Step 4: 验证编译**

Run: `./gradlew compileKotlin 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/io/github/benderblog/cdripshare/util/CoverImageGenerator.kt
git commit -m "feat: add HSL color adjustment and BackgroundMode support to CoverImageGenerator"
```

---

### Task 3: 修改 MainViewModel - 增加 bgMode 状态并透传

**Files:**
- Modify: `src/main/kotlin/io/github/benderblog/cdripshare/viewmodel/MainViewModel.kt`

- [ ] **Step 1: 增加 bgMode 状态和 import**

在 import 区域添加：

```kotlin
import io.github.benderblog.cdripshare.model.BackgroundMode
```

在类成员 `videoCodec` 之后添加：

```kotlin
val bgMode = mutableStateOf(BackgroundMode.Auto)
```

- [ ] **Step 2: 透传 bgMode 到 CoverImageGenerator.generate() 两处调用**

`generateCoverPreview()` 中将：

```kotlin
CoverImageGenerator.generate(file, tempCover)
```

改为：

```kotlin
CoverImageGenerator.generate(file, tempCover, bgMode.value)
```

`startSynthesis()` 中将：

```kotlin
CoverImageGenerator.generate(imageFile.value!!, coverFile)
```

改为：

```kotlin
CoverImageGenerator.generate(imageFile.value!!, coverFile, bgMode.value)
```

- [ ] **Step 3: 验证编译**

Run: `./gradlew compileKotlin 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add src/main/kotlin/io/github/benderblog/cdripshare/viewmodel/MainViewModel.kt
git commit -m "feat: add bgMode state and pass-through to CoverImageGenerator"
```

---

### Task 4: 修改 UI - ImagePickerPanel 增加选择器 + App.kt 传参

**Files:**
- Modify: `src/main/kotlin/io/github/benderblog/cdripshare/ui/ImagePickerPanel.kt`
- Modify: `src/main/kotlin/io/github/benderblog/cdripshare/ui/App.kt`

- [ ] **Step 1: 修改 ImagePickerPanel 函数签名和实现**

在文件顶部 import 区域增加：

```kotlin
import io.github.benderblog.cdripshare.model.BackgroundMode
```

修改函数签名，增加 `bgMode` 和 `onBgModeChange` 参数：

```kotlin
@Composable
fun ImagePickerPanel(
    imageFile: File?,
    coverPreview: ImageBitmap?,
    onSelect: () -> Unit,
    enabled: Boolean,
    bgMode: BackgroundMode,
    onBgModeChange: (BackgroundMode) -> Unit,
    modifier: Modifier = Modifier,
) {
```

在函数体内，封面预览区域之后、最后的闭合花括号之前，增加背景色选择器：

```kotlin
        // 背景色选择
        Spacer(Modifier.height(8.dp))
        Spacer(Modifier.weight(1f))
        Spacer(Modifier.height(4.dp))
        BgModeSelector(
            bgMode = bgMode,
            onBgModeChange = onBgModeChange,
            enabled = enabled
        )
```

在文件末尾（`ImagePickerPanel` 函数之后）添加独立 composable：

```kotlin
@Composable
private fun BgModeSelector(
    bgMode: BackgroundMode,
    onBgModeChange: (BackgroundMode) -> Unit,
    enabled: Boolean,
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            "背景:",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(4.dp))
        Box {
            OutlinedButton(
                onClick = { expanded = true },
                enabled = enabled,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    bgMode.label,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
                Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(14.dp))
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                BackgroundMode.entries.forEach { mode ->
                    DropdownMenuItem(
                        text = { Text(mode.label, style = MaterialTheme.typography.labelSmall) },
                        onClick = { onBgModeChange(mode); expanded = false }
                    )
                }
            }
        }
    }
}
```

需要的额外 import（检查是否已有，缺失则补）：

```kotlin
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
```

- [ ] **Step 2: 修改 App.kt 中 ImagePickerPanel 的调用**

找到 `App.kt` 中 `ImagePickerPanel(` 调用处，增加两个参数：

```kotlin
ImagePickerPanel(
    imageFile = viewModel.imageFile.value,
    coverPreview = viewModel.coverPreview.value,
    onSelect = { pickImageFile(viewModel) },
    enabled = !isWorking,
    bgMode = viewModel.bgMode.value,
    onBgModeChange = { viewModel.bgMode.value = it },
    modifier = Modifier.weight(0.3f).fillMaxHeight()
)
```

- [ ] **Step 3: 验证编译**

Run: `./gradlew compileKotlin 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add src/main/kotlin/io/github/benderblog/cdripshare/ui/ImagePickerPanel.kt \
        src/main/kotlin/io/github/benderblog/cdripshare/ui/App.kt
git commit -m "feat: add background color mode selector to ImagePickerPanel"
```

---

## Self-Review

**1. Spec coverage:**
- ✅ 7 个固定色 + 1 自动 = 8 个选项 → Task 1 `BackgroundMode` 枚举
- ✅ 自动提取颜色后降低饱和度+压暗 → Task 2 `adjustBackgroundColor()`
- ✅ 用户可选固定背景色 → Task 4 UI 下拉选择器
- ✅ 透传至生成逻辑 → Task 3 `MainViewModel` 传递 `bgMode`

**2. Placeholder scan:** No TBD/TODO, all code is concrete.

**3. Type consistency:**
- 8 个 `BackgroundMode` 枚举值全局一致
- `bgMode: BackgroundMode` 参数签名在三处一致
- `onBgModeChange: (BackgroundMode) -> Unit` 回调类型匹配
