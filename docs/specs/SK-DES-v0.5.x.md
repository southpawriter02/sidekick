# Sidekick v0.5.x – Visual Enhancements Phase

> **Phase Goal:** Improved code readability with visual cues and navigation aids  
> **Building On:** v0.4.x Navigation & Productivity

---

## Version Overview

| Version | Focus | Key Deliverables |
|---------|-------|------------------|
| v0.5.1 | Depth-Coded Tabs | Color tabs by namespace/directory depth |
| v0.5.2 | Rainbow Scopes | Background tinting for nested blocks |
| v0.5.3 | Method Separators | Visual divider lines between methods |
| v0.5.4 | File Age Indicator | Tab colors based on modification time |
| v0.5.5 | Git Diff Heatmap | Gutter indicators for commit frequency |

---

## v0.5.1 — Depth-Coded Tabs

### v0.5.1a — DepthTabModels

**Goal:** Data structures for tab coloring based on file depth.

#### DepthTabModels.kt

```kotlin
package com.sidekick.visual.tabs

import java.awt.Color

/**
 * Configuration for depth-coded tabs.
 */
data class DepthTabConfig(
    val enabled: Boolean = true,
    val colorPalette: ColorPalette = ColorPalette.DEFAULT,
    val maxDepth: Int = 6,
    val baseDirectory: String? = null
)

/**
 * Color palette for depth coding.
 */
data class ColorPalette(
    val colors: List<Color>,
    val name: String
) {
    fun colorForDepth(depth: Int): Color {
        return colors[depth.coerceIn(0, colors.lastIndex)]
    }

    companion object {
        val DEFAULT = ColorPalette(
            name = "Ocean",
            colors = listOf(
                Color(66, 133, 244),   // Depth 0 - Blue
                Color(52, 168, 83),    // Depth 1 - Green
                Color(251, 188, 4),    // Depth 2 - Yellow
                Color(234, 67, 53),    // Depth 3 - Red
                Color(156, 39, 176),   // Depth 4 - Purple
                Color(0, 150, 136),    // Depth 5 - Teal
                Color(255, 87, 34)     // Depth 6+ - Orange
            )
        )

        val RAINBOW = ColorPalette(
            name = "Rainbow",
            colors = listOf(
                Color(255, 0, 0),      // Red
                Color(255, 127, 0),    // Orange
                Color(255, 255, 0),    // Yellow
                Color(0, 255, 0),      // Green
                Color(0, 0, 255),      // Blue
                Color(75, 0, 130),     // Indigo
                Color(148, 0, 211)     // Violet
            )
        )

        val MONOCHROME = ColorPalette(
            name = "Monochrome",
            colors = (0..6).map { Color(50 + it * 30, 50 + it * 30, 50 + it * 30) }
        )

        val ALL = listOf(DEFAULT, RAINBOW, MONOCHROME)
    }
}

/**
 * File depth analysis result.
 */
data class FileDepthInfo(
    val filePath: String,
    val depth: Int,
    val namespace: String?,
    val color: Color
)
```

---

### v0.5.1b — DepthTabService

**Goal:** Service to calculate file depth and assign colors.

#### DepthTabService.kt

```kotlin
package com.sidekick.visual.tabs

import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import java.awt.Color

@Service(Service.Level.PROJECT)
@State(name = "SidekickDepthTabs", storages = [Storage("sidekick-tabs.xml")])
class DepthTabService(private val project: Project) : PersistentStateComponent<DepthTabService.State> {

    data class State(var config: DepthTabConfig = DepthTabConfig())
    private var state = State()

    companion object {
        fun getInstance(project: Project): DepthTabService {
            return project.getService(DepthTabService::class.java)
        }
    }

    override fun getState() = state
    override fun loadState(state: State) { this.state = state }

    /**
     * Calculates depth info for a file.
     */
    fun getDepthInfo(file: VirtualFile): FileDepthInfo {
        val basePath = state.config.baseDirectory ?: project.basePath ?: ""
        val relativePath = file.path.removePrefix(basePath).trim('/')
        val depth = relativePath.count { it == '/' }
        val namespace = extractNamespace(file)
        val color = state.config.colorPalette.colorForDepth(depth)

        return FileDepthInfo(
            filePath = file.path,
            depth = depth,
            namespace = namespace,
            color = color
        )
    }

    /**
     * Gets the tab color for a file.
     */
    fun getTabColor(file: VirtualFile): Color? {
        if (!state.config.enabled) return null
        return getDepthInfo(file).color
    }

    /**
     * Updates configuration.
     */
    fun updateConfig(config: DepthTabConfig) {
        state.config = config
    }

    private fun extractNamespace(file: VirtualFile): String? {
        return when (file.extension) {
            "cs" -> extractCSharpNamespace(file)
            "kt", "java" -> extractJavaPackage(file)
            else -> null
        }
    }

    private fun extractCSharpNamespace(file: VirtualFile): String? {
        return try {
            val content = String(file.contentsToByteArray())
            Regex("""namespace\s+([\w.]+)""").find(content)?.groupValues?.get(1)
        } catch (e: Exception) { null }
    }

    private fun extractJavaPackage(file: VirtualFile): String? {
        return try {
            val content = String(file.contentsToByteArray())
            Regex("""package\s+([\w.]+)""").find(content)?.groupValues?.get(1)
        } catch (e: Exception) { null }
    }
}
```

---

### v0.5.1c — DepthTabDecorator

**Goal:** Editor tab decorator that applies depth-based colors.

#### DepthTabDecorator.kt

```kotlin
package com.sidekick.visual.tabs

import com.intellij.openapi.fileEditor.impl.EditorTabColorProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import java.awt.Color

/**
 * Provides depth-based tab coloring.
 */
class DepthTabColorProvider : EditorTabColorProvider {

    override fun getEditorTabColor(project: Project, file: VirtualFile): Color? {
        val service = DepthTabService.getInstance(project)
        return service.getTabColor(file)
    }
}
```

#### plugin.xml

```xml
<extensions defaultExtensionNs="com.intellij">
    <editorTabColorProvider 
        implementation="com.sidekick.visual.tabs.DepthTabColorProvider"/>
</extensions>
```

---

## v0.5.2 — Rainbow Scopes

### v0.5.2a — RainbowScopeModels

**Goal:** Data structures for scope-based background highlighting.

#### RainbowScopeModels.kt

```kotlin
package com.sidekick.visual.scopes

import java.awt.Color

/**
 * Configuration for rainbow scope highlighting.
 */
data class RainbowScopeConfig(
    val enabled: Boolean = true,
    val opacity: Float = 0.05f,
    val maxNestingLevel: Int = 5,
    val colorScheme: ScopeColorScheme = ScopeColorScheme.WARM,
    val excludedLanguages: Set<String> = emptySet()
)

/**
 * Color schemes for scope highlighting.
 */
enum class ScopeColorScheme(val colors: List<Color>) {
    WARM(listOf(
        Color(255, 200, 200),  // Level 1 - Light Red
        Color(255, 220, 180),  // Level 2 - Light Orange
        Color(255, 255, 180),  // Level 3 - Light Yellow
        Color(220, 255, 180),  // Level 4 - Light Lime
        Color(180, 255, 200)   // Level 5 - Light Green
    )),
    COOL(listOf(
        Color(180, 220, 255),  // Level 1 - Light Blue
        Color(200, 180, 255),  // Level 2 - Light Purple
        Color(180, 255, 255),  // Level 3 - Light Cyan
        Color(220, 200, 255),  // Level 4 - Light Lavender
        Color(180, 200, 220)   // Level 5 - Light Slate
    )),
    MONOCHROME(listOf(
        Color(240, 240, 240),
        Color(230, 230, 230),
        Color(220, 220, 220),
        Color(210, 210, 210),
        Color(200, 200, 200)
    ));

    fun colorForLevel(level: Int, opacity: Float): Color {
        val base = colors[level.coerceIn(0, colors.lastIndex)]
        return Color(base.red, base.green, base.blue, (opacity * 255).toInt())
    }
}

/**
 * A detected scope in code.
 */
data class CodeScope(
    val startOffset: Int,
    val endOffset: Int,
    val nestingLevel: Int,
    val scopeType: ScopeType
)

/**
 * Types of code scopes.
 */
enum class ScopeType {
    CLASS, METHOD, BLOCK, LAMBDA, LOOP, CONDITIONAL, TRY_CATCH
}
```

---

### v0.5.2b — RainbowScopeService

**Goal:** Service to detect and highlight nested scopes.

#### RainbowScopeService.kt

```kotlin
package com.sidekick.visual.scopes

import com.intellij.openapi.components.*
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil

@Service(Service.Level.PROJECT)
@State(name = "SidekickRainbowScopes", storages = [Storage("sidekick-scopes.xml")])
class RainbowScopeService(private val project: Project) : PersistentStateComponent<RainbowScopeService.State> {

    data class State(var config: RainbowScopeConfig = RainbowScopeConfig())
    private var state = State()

    companion object {
        fun getInstance(project: Project): RainbowScopeService {
            return project.getService(RainbowScopeService::class.java)
        }
    }

    override fun getState() = state
    override fun loadState(state: State) { this.state = state }

    /**
     * Detects scopes in the given file.
     */
    fun detectScopes(psiFile: PsiFile): List<CodeScope> {
        if (!state.config.enabled) return emptyList()
        if (state.config.excludedLanguages.contains(psiFile.language.id)) return emptyList()

        val scopes = mutableListOf<CodeScope>()
        detectScopesRecursive(psiFile, 0, scopes)
        return scopes
    }

    /**
     * Gets the scope at a given offset.
     */
    fun getScopeAtOffset(psiFile: PsiFile, offset: Int): CodeScope? {
        return detectScopes(psiFile).find { scope ->
            offset in scope.startOffset..scope.endOffset
        }
    }

    private fun detectScopesRecursive(element: PsiElement, level: Int, scopes: MutableList<CodeScope>) {
        if (level > state.config.maxNestingLevel) return

        val scopeType = detectScopeType(element)
        if (scopeType != null) {
            scopes.add(CodeScope(
                startOffset = element.textRange.startOffset,
                endOffset = element.textRange.endOffset,
                nestingLevel = level,
                scopeType = scopeType
            ))
        }

        element.children.forEach { child ->
            detectScopesRecursive(child, if (scopeType != null) level + 1 else level, scopes)
        }
    }

    private fun detectScopeType(element: PsiElement): ScopeType? {
        val elementType = element.node?.elementType?.toString() ?: return null
        return when {
            elementType.contains("CLASS") -> ScopeType.CLASS
            elementType.contains("METHOD") || elementType.contains("FUN") -> ScopeType.METHOD
            elementType.contains("LAMBDA") -> ScopeType.LAMBDA
            elementType.contains("FOR") || elementType.contains("WHILE") -> ScopeType.LOOP
            elementType.contains("IF") || elementType.contains("WHEN") -> ScopeType.CONDITIONAL
            elementType.contains("TRY") -> ScopeType.TRY_CATCH
            elementType.contains("BLOCK") -> ScopeType.BLOCK
            else -> null
        }
    }
}
```

---

### v0.5.2c — RainbowScopeHighlighter

**Goal:** Editor highlighter that renders scope backgrounds.

#### RainbowScopeHighlighter.kt

```kotlin
package com.sidekick.visual.scopes

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.markup.*
import com.intellij.psi.PsiFile

/**
 * Applies rainbow scope highlighting to an editor.
 */
class RainbowScopeHighlighter(private val editor: Editor, private val psiFile: PsiFile) {

    private val markupModel = editor.markupModel
    private val highlighters = mutableListOf<RangeHighlighter>()

    fun apply() {
        clear()
        
        val project = psiFile.project
        val service = RainbowScopeService.getInstance(project)
        val config = service.state.config
        
        if (!config.enabled) return

        val scopes = service.detectScopes(psiFile)
        
        scopes.forEach { scope ->
            val color = config.colorScheme.colorForLevel(scope.nestingLevel, config.opacity)
            
            val attributes = TextAttributes().apply {
                backgroundColor = color
            }
            
            val highlighter = markupModel.addRangeHighlighter(
                scope.startOffset,
                scope.endOffset,
                HighlighterLayer.FIRST - scope.nestingLevel,
                attributes,
                HighlighterTargetArea.EXACT_RANGE
            )
            
            highlighters.add(highlighter)
        }
    }

    fun clear() {
        highlighters.forEach { markupModel.removeHighlighter(it) }
        highlighters.clear()
    }
}
```

---

## v0.5.3 — Method Separators

### v0.5.3a — MethodSeparatorModels

**Goal:** Data structures for method separator rendering.

#### MethodSeparatorModels.kt

```kotlin
package com.sidekick.visual.separators

import java.awt.Color

/**
 * Configuration for method separators.
 */
data class MethodSeparatorConfig(
    val enabled: Boolean = true,
    val style: SeparatorStyle = SeparatorStyle.SOLID,
    val color: Color = Color(128, 128, 128, 80),
    val thickness: Int = 1,
    val padding: Int = 4,
    val showBeforeClass: Boolean = true,
    val showBeforeMethod: Boolean = true
)

/**
 * Separator line styles.
 */
enum class SeparatorStyle(val dashPattern: FloatArray?) {
    SOLID(null),
    DASHED(floatArrayOf(5f, 5f)),
    DOTTED(floatArrayOf(2f, 2f)),
    DOUBLE(null);

    val displayName: String get() = name.lowercase().replaceFirstChar { it.uppercase() }
}

/**
 * A detected code boundary.
 */
data class CodeBoundary(
    val line: Int,
    val boundaryType: BoundaryType,
    val name: String?
)

/**
 * Types of code boundaries.
 */
enum class BoundaryType {
    CLASS_START, METHOD_START, PROPERTY_GROUP, REGION
}
```

---

### v0.5.3b — MethodSeparatorService

**Goal:** Service to detect method boundaries.

#### MethodSeparatorService.kt

```kotlin
package com.sidekick.visual.separators

import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project
import com.intellij.psi.*

@Service(Service.Level.PROJECT)
@State(name = "SidekickSeparators", storages = [Storage("sidekick-separators.xml")])
class MethodSeparatorService(private val project: Project) : PersistentStateComponent<MethodSeparatorService.State> {

    data class State(var config: MethodSeparatorConfig = MethodSeparatorConfig())
    private var state = State()

    companion object {
        fun getInstance(project: Project): MethodSeparatorService {
            return project.getService(MethodSeparatorService::class.java)
        }
    }

    override fun getState() = state
    override fun loadState(state: State) { this.state = state }

    /**
     * Detects code boundaries in a file.
     */
    fun detectBoundaries(psiFile: PsiFile): List<CodeBoundary> {
        if (!state.config.enabled) return emptyList()

        val boundaries = mutableListOf<CodeBoundary>()
        
        psiFile.accept(object : PsiRecursiveElementVisitor() {
            override fun visitElement(element: PsiElement) {
                val boundary = detectBoundary(element, psiFile)
                if (boundary != null) boundaries.add(boundary)
                super.visitElement(element)
            }
        })

        return boundaries.sortedBy { it.line }
    }

    private fun detectBoundary(element: PsiElement, file: PsiFile): CodeBoundary? {
        val elementType = element.node?.elementType?.toString() ?: return null
        val line = file.viewProvider.document?.getLineNumber(element.textRange.startOffset) ?: return null

        return when {
            state.config.showBeforeClass && elementType.contains("CLASS") -> {
                CodeBoundary(line, BoundaryType.CLASS_START, element.text.take(50))
            }
            state.config.showBeforeMethod && (elementType.contains("METHOD") || elementType.contains("FUN")) -> {
                CodeBoundary(line, BoundaryType.METHOD_START, extractMethodName(element))
            }
            else -> null
        }
    }

    private fun extractMethodName(element: PsiElement): String? {
        return element.children.find { it.node?.elementType?.toString()?.contains("IDENTIFIER") == true }?.text
    }
}
```

---

### v0.5.3c — MethodSeparatorRenderer

**Goal:** Line painter for separator rendering.

#### MethodSeparatorRenderer.kt

```kotlin
package com.sidekick.visual.separators

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorLinePainter
import com.intellij.openapi.editor.LineExtensionInfo
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import java.awt.*

/**
 * Renders method separator lines.
 */
class MethodSeparatorLinePainter : EditorLinePainter() {

    override fun getLineExtensions(
        project: Project,
        file: VirtualFile,
        lineNumber: Int
    ): Collection<LineExtensionInfo>? {
        return null // Use custom painting instead
    }
}

/**
 * Custom editor component for separator rendering.
 */
class SeparatorPainter(private val editor: Editor) {

    fun paint(g: Graphics2D, psiFile: com.intellij.psi.PsiFile) {
        val service = MethodSeparatorService.getInstance(psiFile.project)
        val config = service.state.config
        
        if (!config.enabled) return

        val boundaries = service.detectBoundaries(psiFile)
        val lineHeight = editor.lineHeight

        g.color = config.color
        g.stroke = when (config.style) {
            SeparatorStyle.SOLID -> BasicStroke(config.thickness.toFloat())
            SeparatorStyle.DASHED -> BasicStroke(
                config.thickness.toFloat(),
                BasicStroke.CAP_BUTT,
                BasicStroke.JOIN_MITER,
                10f,
                config.style.dashPattern,
                0f
            )
            SeparatorStyle.DOTTED -> BasicStroke(
                config.thickness.toFloat(),
                BasicStroke.CAP_ROUND,
                BasicStroke.JOIN_ROUND,
                10f,
                config.style.dashPattern,
                0f
            )
            SeparatorStyle.DOUBLE -> BasicStroke(config.thickness.toFloat())
        }

        boundaries.forEach { boundary ->
            val y = editor.logicalPositionToXY(
                com.intellij.openapi.editor.LogicalPosition(boundary.line, 0)
            ).y - config.padding
            
            g.drawLine(0, y, editor.component.width, y)
            
            if (config.style == SeparatorStyle.DOUBLE) {
                g.drawLine(0, y + 3, editor.component.width, y + 3)
            }
        }
    }
}
```

---

## v0.5.4 — File Age Indicator

### v0.5.4a — FileAgeModels

**Goal:** Data structures for file age visualization.

#### FileAgeModels.kt

```kotlin
package com.sidekick.visual.age

import java.awt.Color
import java.time.Duration
import java.time.Instant

/**
 * Configuration for file age indicators.
 */
data class FileAgeConfig(
    val enabled: Boolean = true,
    val colorMode: AgeColorMode = AgeColorMode.GRADIENT,
    val thresholds: AgeThresholds = AgeThresholds.DEFAULT,
    val showInGutter: Boolean = false,
    val showInTab: Boolean = true
)

/**
 * Age coloring modes.
 */
enum class AgeColorMode {
    GRADIENT,    // Smooth gradient from green to red
    THRESHOLD,   // Discrete colors at thresholds
    HEAT        // Heat map style (blue to red)
}

/**
 * Age thresholds for coloring.
 */
data class AgeThresholds(
    val fresh: Duration = Duration.ofHours(1),
    val recent: Duration = Duration.ofDays(1),
    val stale: Duration = Duration.ofDays(7),
    val old: Duration = Duration.ofDays(30)
) {
    companion object {
        val DEFAULT = AgeThresholds()
        
        val AGGRESSIVE = AgeThresholds(
            fresh = Duration.ofMinutes(30),
            recent = Duration.ofHours(4),
            stale = Duration.ofDays(1),
            old = Duration.ofDays(7)
        )
    }
}

/**
 * File age information.
 */
data class FileAgeInfo(
    val filePath: String,
    val lastModified: Instant,
    val age: Duration,
    val ageCategory: AgeCategory,
    val color: Color
) {
    val relativeTime: String get() = formatRelativeTime(age)
    
    private fun formatRelativeTime(duration: Duration): String {
        return when {
            duration.toMinutes() < 1 -> "just now"
            duration.toMinutes() < 60 -> "${duration.toMinutes()}m ago"
            duration.toHours() < 24 -> "${duration.toHours()}h ago"
            duration.toDays() < 7 -> "${duration.toDays()}d ago"
            duration.toDays() < 30 -> "${duration.toDays() / 7}w ago"
            else -> "${duration.toDays() / 30}mo ago"
        }
    }
}

/**
 * Age categories.
 */
enum class AgeCategory(val color: Color) {
    FRESH(Color(76, 175, 80)),       // Green
    RECENT(Color(139, 195, 74)),     // Light Green
    STALE(Color(255, 193, 7)),       // Amber
    OLD(Color(255, 152, 0)),         // Orange
    ANCIENT(Color(244, 67, 54))      // Red
}
```

---

### v0.5.4b — FileAgeService

**Goal:** Service to track and visualize file ages.

#### FileAgeService.kt

```kotlin
package com.sidekick.visual.age

import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import java.awt.Color
import java.time.Duration
import java.time.Instant

@Service(Service.Level.PROJECT)
@State(name = "SidekickFileAge", storages = [Storage("sidekick-age.xml")])
class FileAgeService(private val project: Project) : PersistentStateComponent<FileAgeService.State> {

    data class State(var config: FileAgeConfig = FileAgeConfig())
    private var state = State()

    companion object {
        fun getInstance(project: Project): FileAgeService {
            return project.getService(FileAgeService::class.java)
        }
    }

    override fun getState() = state
    override fun loadState(state: State) { this.state = state }

    /**
     * Gets age info for a file.
     */
    fun getAgeInfo(file: VirtualFile): FileAgeInfo {
        val modTime = Instant.ofEpochMilli(file.timeStamp)
        val age = Duration.between(modTime, Instant.now())
        val category = categorize(age)
        val color = getColor(age, category)

        return FileAgeInfo(
            filePath = file.path,
            lastModified = modTime,
            age = age,
            ageCategory = category,
            color = color
        )
    }

    /**
     * Gets tab color based on file age.
     */
    fun getTabColor(file: VirtualFile): Color? {
        if (!state.config.enabled || !state.config.showInTab) return null
        return getAgeInfo(file).color
    }

    private fun categorize(age: Duration): AgeCategory {
        val thresholds = state.config.thresholds
        return when {
            age < thresholds.fresh -> AgeCategory.FRESH
            age < thresholds.recent -> AgeCategory.RECENT
            age < thresholds.stale -> AgeCategory.STALE
            age < thresholds.old -> AgeCategory.OLD
            else -> AgeCategory.ANCIENT
        }
    }

    private fun getColor(age: Duration, category: AgeCategory): Color {
        return when (state.config.colorMode) {
            AgeColorMode.THRESHOLD -> category.color
            AgeColorMode.GRADIENT -> computeGradient(age)
            AgeColorMode.HEAT -> computeHeatColor(age)
        }
    }

    private fun computeGradient(age: Duration): Color {
        val ratio = (age.toHours().toFloat() / 720f).coerceIn(0f, 1f) // 30 days max
        val r = (76 + (244 - 76) * ratio).toInt()
        val g = (175 - (175 - 67) * ratio).toInt()
        val b = (80 - (80 - 54) * ratio).toInt()
        return Color(r, g, b)
    }

    private fun computeHeatColor(age: Duration): Color {
        val ratio = (age.toHours().toFloat() / 720f).coerceIn(0f, 1f)
        return if (ratio < 0.5f) {
            val t = ratio * 2
            Color((255 * t).toInt(), (255 * t).toInt(), 255)
        } else {
            val t = (ratio - 0.5f) * 2
            Color(255, (255 * (1 - t)).toInt(), (255 * (1 - t)).toInt())
        }
    }
}
```

---

## v0.5.5 — Git Diff Heatmap

### v0.5.5a — GitHeatmapModels

**Goal:** Data structures for Git-based change visualization.

#### GitHeatmapModels.kt

```kotlin
package com.sidekick.visual.git

import java.awt.Color
import java.time.Instant

/**
 * Configuration for Git heatmap.
 */
data class GitHeatmapConfig(
    val enabled: Boolean = true,
    val showInGutter: Boolean = true,
    val colorScheme: HeatmapColorScheme = HeatmapColorScheme.FIRE,
    val metricType: HeatmapMetric = HeatmapMetric.COMMIT_COUNT
)

/**
 * What metric to visualize.
 */
enum class HeatmapMetric {
    COMMIT_COUNT,      // How often this line is changed
    LAST_CHANGED,      // When was this line last changed
    AUTHOR_COUNT,      // How many authors touched this
    CHURN_RATE        // Changes per time period
}

/**
 * Color schemes for the heatmap.
 */
enum class HeatmapColorScheme(val colors: List<Color>) {
    FIRE(listOf(
        Color(255, 255, 200),  // Cold - Light Yellow
        Color(255, 200, 100),  // Warm - Orange
        Color(255, 100, 50),   // Hot - Red-Orange
        Color(200, 50, 50)     // Very Hot - Dark Red
    )),
    PLASMA(listOf(
        Color(13, 8, 135),
        Color(126, 3, 168),
        Color(203, 71, 120),
        Color(248, 149, 64)
    )),
    VIRIDIS(listOf(
        Color(68, 1, 84),
        Color(59, 82, 139),
        Color(33, 145, 140),
        Color(94, 201, 98)
    ));

    fun colorForIntensity(intensity: Float): Color {
        val index = (intensity * (colors.size - 1)).toInt().coerceIn(0, colors.lastIndex)
        return colors[index]
    }
}

/**
 * Line-level Git statistics.
 */
data class LineGitStats(
    val lineNumber: Int,
    val commitCount: Int,
    val lastCommitDate: Instant?,
    val lastAuthor: String?,
    val authorCount: Int
) {
    val intensity: Float get() = (commitCount.toFloat() / 20f).coerceIn(0f, 1f)
}

/**
 * File-level Git statistics.
 */
data class FileGitStats(
    val filePath: String,
    val totalCommits: Int,
    val lineStats: Map<Int, LineGitStats>,
    val hotspotLines: List<Int>
)
```

---

### v0.5.5b — GitHeatmapService

**Goal:** Service to compute Git-based heatmap data.

#### GitHeatmapService.kt

```kotlin
package com.sidekick.visual.git

import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import git4idea.commands.Git
import git4idea.commands.GitCommand
import git4idea.commands.GitLineHandler
import git4idea.repo.GitRepositoryManager
import java.time.Instant

@Service(Service.Level.PROJECT)
@State(name = "SidekickGitHeatmap", storages = [Storage("sidekick-heatmap.xml")])
class GitHeatmapService(private val project: Project) : PersistentStateComponent<GitHeatmapService.State> {

    data class State(var config: GitHeatmapConfig = GitHeatmapConfig())
    private var state = State()
    
    private val cache = mutableMapOf<String, FileGitStats>()

    companion object {
        fun getInstance(project: Project): GitHeatmapService {
            return project.getService(GitHeatmapService::class.java)
        }
    }

    override fun getState() = state
    override fun loadState(state: State) { this.state = state }

    /**
     * Gets Git stats for a file.
     */
    fun getFileStats(file: VirtualFile): FileGitStats? {
        if (!state.config.enabled) return null
        
        return cache.getOrPut(file.path) {
            computeFileStats(file)
        }
    }

    /**
     * Gets line-level stats.
     */
    fun getLineStats(file: VirtualFile, line: Int): LineGitStats? {
        return getFileStats(file)?.lineStats?.get(line)
    }

    /**
     * Invalidates cache for a file.
     */
    fun invalidate(file: VirtualFile) {
        cache.remove(file.path)
    }

    private fun computeFileStats(file: VirtualFile): FileGitStats {
        val lineStats = mutableMapOf<Int, LineGitStats>()
        
        // Run git blame to get line-level info
        val blameOutput = runGitBlame(file)
        
        blameOutput.forEachIndexed { index, blame ->
            lineStats[index + 1] = parseBlameInfo(index + 1, blame)
        }
        
        // Compute hotspots (most changed lines)
        val hotspots = lineStats.entries
            .sortedByDescending { it.value.commitCount }
            .take(10)
            .map { it.key }

        return FileGitStats(
            filePath = file.path,
            totalCommits = lineStats.values.sumOf { it.commitCount },
            lineStats = lineStats,
            hotspotLines = hotspots
        )
    }

    private fun runGitBlame(file: VirtualFile): List<String> {
        try {
            val repo = GitRepositoryManager.getInstance(project)
                .getRepositoryForFile(file) ?: return emptyList()
            
            val handler = GitLineHandler(project, repo.root, GitCommand.BLAME)
            handler.addParameters("-l", file.path)
            
            val result = Git.getInstance().runCommand(handler)
            return if (result.success()) result.output else emptyList()
        } catch (e: Exception) {
            return emptyList()
        }
    }

    private fun parseBlameInfo(line: Int, blame: String): LineGitStats {
        // Parse git blame output format: hash (author date ...) content
        val parts = blame.split(Regex("\\s+"), limit = 4)
        val author = parts.getOrNull(1)?.removeSurrounding("(", ")")
        
        return LineGitStats(
            lineNumber = line,
            commitCount = 1, // Would need log analysis for count
            lastCommitDate = null,
            lastAuthor = author,
            authorCount = 1
        )
    }
}
```

---

### v0.5.5c — GitHeatmapGutterRenderer

**Goal:** Gutter icon renderer for heatmap display.

#### GitHeatmapGutterRenderer.kt

```kotlin
package com.sidekick.visual.git

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.markup.*
import com.intellij.openapi.vfs.VirtualFile
import java.awt.*
import javax.swing.Icon

/**
 * Renders Git heatmap in the editor gutter.
 */
class GitHeatmapGutterRenderer(
    private val file: VirtualFile,
    private val lineStats: LineGitStats,
    private val config: GitHeatmapConfig
) : GutterIconRenderer() {

    override fun getIcon(): Icon = HeatmapIcon(lineStats, config)

    override fun getTooltipText(): String {
        return buildString {
            appendLine("Line ${lineStats.lineNumber}")
            appendLine("Commits: ${lineStats.commitCount}")
            lineStats.lastAuthor?.let { appendLine("Last author: $it") }
            lineStats.lastCommitDate?.let { appendLine("Last changed: $it") }
        }
    }

    override fun equals(other: Any?): Boolean {
        return other is GitHeatmapGutterRenderer && 
               other.lineStats.lineNumber == lineStats.lineNumber
    }

    override fun hashCode(): Int = lineStats.lineNumber
}

/**
 * Icon that displays heatmap color.
 */
class HeatmapIcon(
    private val stats: LineGitStats,
    private val config: GitHeatmapConfig
) : Icon {
    
    override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) {
        val g2d = g as Graphics2D
        g2d.color = config.colorScheme.colorForIntensity(stats.intensity)
        g2d.fillRect(x, y, iconWidth, iconHeight)
    }

    override fun getIconWidth() = 8
    override fun getIconHeight() = 12
}

/**
 * Applies heatmap highlighting to an editor.
 */
class GitHeatmapHighlighter(private val editor: Editor, private val file: VirtualFile) {

    private val highlighters = mutableListOf<RangeHighlighter>()

    fun apply() {
        clear()
        
        val project = editor.project ?: return
        val service = GitHeatmapService.getInstance(project)
        val stats = service.getFileStats(file) ?: return
        val config = service.state.config

        if (!config.enabled || !config.showInGutter) return

        stats.lineStats.forEach { (line, lineStats) ->
            val offset = editor.document.getLineStartOffset(line - 1)
            
            val highlighter = editor.markupModel.addRangeHighlighter(
                offset,
                offset,
                HighlighterLayer.FIRST,
                null,
                HighlighterTargetArea.LINES_IN_RANGE
            )
            
            highlighter.gutterIconRenderer = GitHeatmapGutterRenderer(file, lineStats, config)
            highlighters.add(highlighter)
        }
    }

    fun clear() {
        highlighters.forEach { editor.markupModel.removeHighlighter(it) }
        highlighters.clear()
    }
}
```

---

## plugin.xml Additions

```xml
<extensions defaultExtensionNs="com.intellij">
    <!-- Visual Enhancement Services (v0.5.x) -->
    <projectService serviceImplementation="com.sidekick.visual.tabs.DepthTabService"/>
    <projectService serviceImplementation="com.sidekick.visual.scopes.RainbowScopeService"/>
    <projectService serviceImplementation="com.sidekick.visual.separators.MethodSeparatorService"/>
    <projectService serviceImplementation="com.sidekick.visual.age.FileAgeService"/>
    <projectService serviceImplementation="com.sidekick.visual.git.GitHeatmapService"/>
    
    <!-- Tab Color Provider -->
    <editorTabColorProvider implementation="com.sidekick.visual.tabs.DepthTabColorProvider"/>
</extensions>

<actions>
    <!-- Toggle Visual Enhancements -->
    <action id="Sidekick.ToggleDepthTabs"
            class="com.sidekick.visual.tabs.ToggleDepthTabsAction"
            text="Toggle Depth-Coded Tabs">
        <add-to-group group-id="Sidekick.ActionGroup"/>
    </action>
    
    <action id="Sidekick.ToggleRainbowScopes"
            class="com.sidekick.visual.scopes.ToggleRainbowScopesAction"
            text="Toggle Rainbow Scopes">
        <add-to-group group-id="Sidekick.ActionGroup"/>
    </action>
    
    <action id="Sidekick.ToggleMethodSeparators"
            class="com.sidekick.visual.separators.ToggleMethodSeparatorsAction"
            text="Toggle Method Separators">
        <add-to-group group-id="Sidekick.ActionGroup"/>
    </action>
    
    <action id="Sidekick.ToggleGitHeatmap"
            class="com.sidekick.visual.git.ToggleGitHeatmapAction"
            text="Toggle Git Heatmap">
        <add-to-group group-id="Sidekick.ActionGroup"/>
    </action>
</actions>
```

---

## Settings Panel Additions

### VisualSettingsConfigurable.kt

```kotlin
package com.sidekick.visual

import com.intellij.openapi.options.Configurable
import com.intellij.ui.dsl.builder.*
import javax.swing.JComponent

class VisualSettingsConfigurable : Configurable {
    
    override fun getDisplayName() = "Sidekick Visual"

    override fun createComponent(): JComponent = panel {
        group("Depth-Coded Tabs") {
            row { checkBox("Enable depth-coded tabs") }
            row("Color palette:") { comboBox(listOf("Ocean", "Rainbow", "Monochrome")) }
        }
        
        group("Rainbow Scopes") {
            row { checkBox("Enable rainbow scopes") }
            row("Opacity:") { slider(0, 100, 10, 25) }
            row("Color scheme:") { comboBox(listOf("Warm", "Cool", "Monochrome")) }
        }
        
        group("Method Separators") {
            row { checkBox("Enable method separators") }
            row("Style:") { comboBox(listOf("Solid", "Dashed", "Dotted", "Double")) }
        }
        
        group("File Age") {
            row { checkBox("Enable file age indicator") }
            row("Color mode:") { comboBox(listOf("Gradient", "Threshold", "Heat")) }
        }
        
        group("Git Heatmap") {
            row { checkBox("Enable Git heatmap") }
            row("Metric:") { comboBox(listOf("Commit count", "Last changed")) }
            row("Color scheme:") { comboBox(listOf("Fire", "Plasma", "Viridis")) }
        }
    }

    override fun isModified() = false
    override fun apply() {}
}
```

---

## Verification Plan

### Automated Tests

```bash
# Run all v0.5.x tests
./gradlew test --tests "com.sidekick.visual.*"
```

### Manual Verification

| Version | Step | Expected Result |
|---------|------|-----------------|
| v0.5.1 | Open files at different depths | Tabs show different colors |
| v0.5.2 | View nested code blocks | Background colors per nesting level |
| v0.5.3 | View file with multiple methods | Separator lines visible |
| v0.5.4 | Open old and new files | Tab colors reflect age |
| v0.5.5 | Open Git-tracked file | Gutter shows heatmap colors |

---

## Success Metrics

| Metric | Target |
|--------|--------|
| Startup impact | <100ms additional |
| Memory overhead | <5MB per project |
| Render performance | <16ms per frame |
| Theme compatibility | Works with all JetBrains themes |

---

## Revision History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 0.5.0 | 2026-02-04 | Ryan | Initial v0.5.x design specification |
