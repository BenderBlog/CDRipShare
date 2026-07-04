# Cover Editor Window Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move cover editing controls out of the main window into a single independent Compose Desktop window while keeping cover settings live and shared.

**Architecture:** Keep `MainViewModel` as the single source of truth for cover file, generated preview, background mode, and custom color. Add window visibility state in `Main.kt`, pass an `onOpenCoverEditor` callback into `App`, and render a second `Window` containing a new `CoverEditorWindow` composable when requested. Reuse the existing color picker and background selector logic by moving the main panel toward a lightweight overview and adding a dedicated editor content composable.

**Tech Stack:** Kotlin/JVM 17, Compose Desktop 1.7.3, Material 3, existing Skia-backed `ImageBitmap` preview flow.

---

## File Structure

| File | Responsibility |
| --- | --- |
| `src/main/kotlin/io/github/benderblog/cdripshare/Main.kt` | Owns top-level main window and optional cover editor window lifecycle. |
| `src/main/kotlin/io/github/benderblog/cdripshare/ui/App.kt` | Main app layout; passes the "open cover editor" action to the cover overview panel; exposes `pickCoverImage(...)` for both windows. |
| `src/main/kotlin/io/github/benderblog/cdripshare/ui/ImagePickerPanel.kt` | Becomes the lightweight main-window cover overview and hosts reusable cover editing content/controls. |
| `src/main/kotlin/io/github/benderblog/cdripshare/ui/CoverEditorWindow.kt` | New window content wrapper that binds `MainViewModel` to `CoverEditorContent`. |
| `src/main/kotlin/io/github/benderblog/cdripshare/ui/AppPreview.kt` | Updates preview calls for the new `App` signature. |

---

### Task 1: Slim the Main-Window Cover Panel

**Files:**
- Modify: `src/main/kotlin/io/github/benderblog/cdripshare/ui/ImagePickerPanel.kt`
- Modify: `src/main/kotlin/io/github/benderblog/cdripshare/ui/App.kt`
- Modify: `src/main/kotlin/io/github/benderblog/cdripshare/ui/AppPreview.kt`

- [ ] **Step 1: Change `ImagePickerPanel` to an overview-only API**

In `ImagePickerPanel.kt`, replace the current `ImagePickerPanel` signature with:

```kotlin
@Composable
fun ImagePickerPanel(
    imageFile: File?,
    coverPreview: ImageBitmap?,
    onEdit: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
)
```

Inside the composable, keep the existing `bitmap` decoding block but remove all background-mode and custom-color UI from the main panel. The body should keep the source thumbnail, file name, dimensions, generated 16:9 preview, and an edit/view button:

```kotlin
Column(
    modifier = modifier.fillMaxWidth().fillMaxHeight().verticalScroll(rememberScrollState()),
    horizontalAlignment = Alignment.CenterHorizontally
) {
    Text("封面图片", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(bottom = 8.dp))

    Surface(
        modifier = Modifier
            .size(140.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable { onEdit() },
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        if (bitmap != null) {
            Image(bitmap = bitmap, contentDescription = "原始封面", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Box(contentAlignment = Alignment.Center) {
                Text("点击编辑封面", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            }
        }
    }

    Spacer(Modifier.height(6.dp))
    Text(
        imageFile?.name ?: "未选择",
        style = MaterialTheme.typography.bodySmall,
        color = if (imageFile != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
    if (bitmap != null) {
        Text("${bitmap.width} × ${bitmap.height}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }

    if (coverPreview != null) {
        Spacer(Modifier.height(12.dp))
        Text("封面预览", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp))
        Spacer(Modifier.height(2.dp))
        Surface(
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(6.dp)),
            shape = RoundedCornerShape(6.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Image(bitmap = coverPreview, contentDescription = "封面预览 (1920×1080)", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        }
    }

    Spacer(Modifier.height(10.dp))
    OutlinedButton(
        onClick = onEdit,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(if (enabled) "编辑封面" else "查看封面", style = MaterialTheme.typography.labelMedium)
    }
}
```

- [ ] **Step 2: Remove now-unused parameters from the main `ImagePickerPanel` call**

In `App.kt`, change the app signature:

```kotlin
@Composable
fun App(
    viewModel: MainViewModel,
    onOpenCoverEditor: () -> Unit = {},
)
```

Then replace the current `ImagePickerPanel(...)` call with:

```kotlin
ImagePickerPanel(
    imageFile = viewModel.imageFile.value,
    coverPreview = viewModel.coverPreview.value,
    onEdit = onOpenCoverEditor,
    enabled = !isWorking,
    modifier = Modifier.weight(0.3f).fillMaxHeight()
)
```

Remove the now-unused `BackgroundMode` import from `App.kt`.

- [ ] **Step 3: Update previews to compile with the new signature**

In `AppPreview.kt`, no behavior change is required because `onOpenCoverEditor` has a default value. Verify each preview still calls:

```kotlin
App(viewModel = vm)
```

- [ ] **Step 4: Run compilation for this slice**

Run:

```bash
./gradlew compileKotlin
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit Task 1**

```bash
git add src/main/kotlin/io/github/benderblog/cdripshare/ui/ImagePickerPanel.kt \
        src/main/kotlin/io/github/benderblog/cdripshare/ui/App.kt \
        src/main/kotlin/io/github/benderblog/cdripshare/ui/AppPreview.kt
git commit -m "refactor: slim cover overview panel"
```

---

### Task 2: Add Reusable Cover Editor Content

**Files:**
- Modify: `src/main/kotlin/io/github/benderblog/cdripshare/ui/ImagePickerPanel.kt`

- [ ] **Step 1: Add `CoverEditorContent` in `ImagePickerPanel.kt`**

Add this public composable above `PaletteColorPickerDialog(...)` so it can reuse the existing private `BgModeSelector`, `PaletteColorPickerDialog`, and `parseHexColor` helpers:

```kotlin
@Composable
fun CoverEditorContent(
    imageFile: File?,
    coverPreview: ImageBitmap?,
    onSelect: () -> Unit,
    enabled: Boolean,
    bgMode: BackgroundMode,
    onBgModeChange: (BackgroundMode) -> Unit,
    customColorHex: String,
    onCustomColorChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val bitmap = remember(imageFile?.absolutePath) {
        imageFile?.takeIf { it.exists() }?.let {
            try {
                org.jetbrains.skia.Image.makeFromEncoded(it.readBytes()).toComposeImageBitmap()
            } catch (_: Exception) {
                null
            }
        }
    }

    var showColorDialog by remember { mutableStateOf(false) }

    Row(
        modifier = modifier.fillMaxSize().padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("封面预览", style = MaterialTheme.typography.titleMedium)
            Surface(
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp)),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                if (coverPreview != null) {
                    Image(bitmap = coverPreview, contentDescription = "封面预览 (1920×1080)", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                } else {
                    Box(contentAlignment = Alignment.Center) {
                        Text("选择封面后显示 1920×1080 预览", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                    }
                }
            }

            Text("原始图片", style = MaterialTheme.typography.titleSmall)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(
                    modifier = Modifier.size(96.dp).clip(RoundedCornerShape(8.dp)),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    if (bitmap != null) {
                        Image(bitmap = bitmap, contentDescription = "原始封面", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    } else {
                        Box(contentAlignment = Alignment.Center) {
                            Text("未选择", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(imageFile?.name ?: "未选择封面图片", style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (bitmap != null) {
                        Text("${bitmap.width} × ${bitmap.height}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        Column(
            modifier = Modifier.width(220.dp).fillMaxHeight().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("设置", style = MaterialTheme.typography.titleMedium)
            Button(onClick = onSelect, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
                Text(if (imageFile == null) "选择封面" else "更换封面")
            }

            BgModeSelector(
                bgMode = bgMode,
                onBgModeChange = { mode ->
                    if (mode == BackgroundMode.Custom) showColorDialog = true
                    else onBgModeChange(mode)
                },
                enabled = enabled
            )

            if (bgMode == BackgroundMode.Custom) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("当前: #${customColorHex.removePrefix("#")}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(6.dp))
                    Surface(modifier = Modifier.size(18.dp).clip(RoundedCornerShape(4.dp)), color = parseHexColor(customColorHex)) {}
                }
                OutlinedButton(
                    onClick = { showColorDialog = true },
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("选择颜色")
                }
            }

            if (!enabled) {
                Text("合成中只能查看封面预览", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    if (showColorDialog) {
        PaletteColorPickerDialog(
            initialHex = customColorHex.removePrefix("#"),
            onConfirm = { hex ->
                onCustomColorChange(hex)
                onBgModeChange(BackgroundMode.Custom)
                showColorDialog = false
            },
            onDismiss = { showColorDialog = false }
        )
    }
}
```

- [ ] **Step 2: Run compilation for editor content**

Run:

```bash
./gradlew compileKotlin
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit Task 2**

```bash
git add src/main/kotlin/io/github/benderblog/cdripshare/ui/ImagePickerPanel.kt
git commit -m "feat: add cover editor content"
```

---

### Task 3: Add the Cover Editor Window Wrapper

**Files:**
- Create: `src/main/kotlin/io/github/benderblog/cdripshare/ui/CoverEditorWindow.kt`
- Modify: `src/main/kotlin/io/github/benderblog/cdripshare/ui/App.kt`

- [ ] **Step 1: Create `CoverEditorWindow.kt`**

Create `src/main/kotlin/io/github/benderblog/cdripshare/ui/CoverEditorWindow.kt`:

```kotlin
package io.github.benderblog.cdripshare.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import io.github.benderblog.cdripshare.model.BackgroundMode
import io.github.benderblog.cdripshare.model.Phase
import io.github.benderblog.cdripshare.viewmodel.MainViewModel

@Composable
fun CoverEditorWindow(
    viewModel: MainViewModel,
    onSelectImage: () -> Unit,
) {
    val state = viewModel.appState.value
    val isWorking = state.phase == Phase.Working

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            CoverEditorContent(
                imageFile = viewModel.imageFile.value,
                coverPreview = viewModel.coverPreview.value,
                onSelect = onSelectImage,
                enabled = !isWorking,
                bgMode = viewModel.bgMode.value,
                onBgModeChange = remember {
                    { mode: BackgroundMode ->
                        viewModel.bgMode.value = mode
                        viewModel.onBgModeChanged()
                    }
                },
                customColorHex = viewModel.customColorHex,
                onCustomColorChange = remember {
                    { hex: String ->
                        viewModel.customColorHex = hex
                        viewModel.onBgModeChanged()
                    }
                }
            )
        }
    }
}
```

- [ ] **Step 2: Expose a shared cover image picker**

In `App.kt`, replace the private `pickImageFile` function with this public package-level helper:

```kotlin
fun pickCoverImage(viewModel: MainViewModel) {
    nativeFileDialog("选择封面图片", FileDialog.LOAD, filter = imageFilter)?.let { viewModel.setImageFile(it) }
}
```

If no remaining code calls `pickImageFile`, remove the old private function completely.

- [ ] **Step 3: Run compilation for the new wrapper**

Run:

```bash
./gradlew compileKotlin
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit Task 3**

```bash
git add src/main/kotlin/io/github/benderblog/cdripshare/ui/CoverEditorWindow.kt \
        src/main/kotlin/io/github/benderblog/cdripshare/ui/App.kt
git commit -m "feat: add cover editor window content"
```

---

### Task 4: Wire the Independent Window in `Main.kt`

**Files:**
- Modify: `src/main/kotlin/io/github/benderblog/cdripshare/Main.kt`

- [ ] **Step 1: Add Compose runtime and window imports**

Update imports in `Main.kt` to include:

```kotlin
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.github.benderblog.cdripshare.ui.CoverEditorWindow
import io.github.benderblog.cdripshare.ui.pickCoverImage
```

- [ ] **Step 2: Remember the shared view model and cover window state**

Replace the top of `main()` with:

```kotlin
fun main() = application {
    val state = rememberWindowState(width = 1000.dp, height = 750.dp)
    val coverEditorState = rememberWindowState(width = 760.dp, height = 560.dp)
    val viewModel = remember { MainViewModel() }
    var showCoverEditor by remember { mutableStateOf(false) }
```

- [ ] **Step 3: Pass the open-editor callback into `App`**

Replace:

```kotlin
App(viewModel)
```

with:

```kotlin
App(
    viewModel = viewModel,
    onOpenCoverEditor = { showCoverEditor = true }
)
```

- [ ] **Step 4: Render the second window when requested**

After the main `Window { ... }` block, add:

```kotlin
if (showCoverEditor) {
    Window(
        onCloseRequest = { showCoverEditor = false },
        state = coverEditorState,
        title = "编辑封面"
    ) {
        window.minimumSize = java.awt.Dimension(680, 480)
        CoverEditorWindow(
            viewModel = viewModel,
            onSelectImage = { pickCoverImage(viewModel) }
        )
    }
}
```

This guarantees there is at most one cover editor window because the second `Window` only exists while `showCoverEditor` is true.

- [ ] **Step 5: Run compilation**

Run:

```bash
./gradlew compileKotlin
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit Task 4**

```bash
git add src/main/kotlin/io/github/benderblog/cdripshare/Main.kt
git commit -m "feat: wire cover editor window"
```

---

### Task 5: Final Verification

**Files:**
- Verify only; no intended source changes.

- [ ] **Step 1: Run full build**

Run:

```bash
./gradlew build
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 2: Manual desktop verification**

Run the app:

```bash
./gradlew run
```

Verify:

- Main window opens normally.
- Main cover panel shows only cover state and the edit/view button.
- Clicking `编辑封面` opens a separate `编辑封面` window.
- Clicking `编辑封面` again while the window is open does not create another editor window.
- Closing the editor window does not clear selected cover file, background mode, custom color, or preview.
- Selecting/changing cover image in the editor updates the main cover panel.
- Changing background mode in the editor updates the main 16:9 preview.
- Selecting a custom color in the editor updates the main 16:9 preview.
- During synthesis, editor controls are disabled but the editor window can still show the preview.

- [ ] **Step 3: Check git state**

Run:

```bash
git status --short
```

Expected: no uncommitted files, unless manual app execution generated local ignored files.

---

## Plan Self-Review

- Spec coverage: Task 1 implements the lighter main cover area; Task 2 implements live editor controls without draft state; Task 3 binds editor controls to `MainViewModel`; Task 4 implements the independent single editor window; Task 5 covers compile, build, and manual verification.
- Placeholder scan: no incomplete placeholder steps or generic "handle later" instructions remain.
- Type consistency: `ImagePickerPanel`, `CoverEditorContent`, `CoverEditorWindow`, `pickCoverImage`, and `showCoverEditor` names are consistent across tasks.
