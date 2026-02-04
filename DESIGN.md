# Sidekick for JetBrains Rider

> **An AI-powered coding companion that seamlessly integrates local LLMs, intelligent navigation, and developer workflow automation into JetBrains Rider.**

---

## Table of Contents

1. [Vision & Philosophy](#vision--philosophy)
2. [Core Feature Pillars](#core-feature-pillars)
3. [Phased Roadmap](#phased-roadmap)
4. [Technical Architecture](#technical-architecture)
5. [Platform Requirements](#platform-requirements)

---

## Vision & Philosophy

**Sidekick** is designed to be an indispensable coding companion within JetBrains Rider, focusing on:

- **Privacy-First AI:** Leverage local LLMs (Ollama, Llama.cpp) for code assistance without sending code to external services
- **Context Awareness:** Deep integration with Rider's code analysis to provide intelligent, project-aware suggestions
- **Developer Flow:** Non-intrusive features that enhance productivity without disrupting coding rhythm
- **Extensibility:** Plugin architecture that allows community-driven feature expansion

---

## Core Feature Pillars

### 🧠 Pillar 1: Local LLM Integration

Private, on-device AI assistance that understands your codebase.

| Feature | Description | Priority |
|---------|-------------|----------|
| **Ollama Bridge** | Seamless connection to local Ollama instances for code chat | P0 |
| **Context Injection** | Automatic injection of file context, project structure, and symbols into prompts | P0 |
| **Streaming Responses** | Real-time token streaming in a docked tool window | P0 |
| **Model Selector** | Quick-switch between installed local models | P1 |
| **Chat History** | Persistent, searchable conversation logs per project | P1 |
| **Prompt Templates** | Customizable prompt templates for common tasks (explain, refactor, test) | P1 |
| **Code Actions** | Inline "Ask Sidekick" actions on selected code | P2 |
| **Multi-File Context** | Include multiple related files in context window | P2 |

### 🎯 Pillar 2: Intelligent Code Generation

AI-powered code writing and transformation tools.

| Feature | Description | Priority |
|---------|-------------|----------|
| **Doc Writer** | Generate XML documentation from method signatures and bodies | P0 |
| **Test Scaffolder** | Create unit test stubs with appropriate assertions | P0 |
| **Commit Message Generator** | Analyze staged changes and generate conventional commits | P1 |
| **Variable Naming Suggestions** | Context-aware naming for variables and methods | P1 |
| **Regex Builder** | Natural language to regex pattern conversion | P2 |
| **Query Helper** | Generate LINQ/SQL from natural language comments | P2 |
| **DTO Generator** | Paste JSON → auto-generate C# records/classes | P2 |
| **Refactoring Explainer** | Generate PR descriptions for complex refactors | P3 |

### 🧭 Pillar 3: Navigation & Productivity

Enhanced code navigation and workflow acceleration.

| Feature | Description | Priority |
|---------|-------------|----------|
| **Jump-to-Test** | Single-keystroke toggle between source and corresponding test file | P0 |
| **Bookmark Workspaces** | Named collections of bookmarks and breakpoints for debugging sessions | P1 |
| **Snippet Pocket** | Multi-item clipboard with persistent snippets | P1 |
| **Recent Files Grid** | Visual "Mission Control" style file browser | P1 |
| **Quick Args** | Context-aware argument suggestions from scope | P2 |
| **Copy as Markdown** | Copy code with syntax highlighting and file path | P2 |
| **PR Focus Mode** | Filter Solution Explorer to only changed files | P2 |
| **Smart Paste** | Detect and auto-transform pasted content (JSON, XML, etc.) | P3 |

### 👁️ Pillar 4: Visual Enhancements

Improved code readability and navigation cues.

| Feature | Description | Priority |
|---------|-------------|----------|
| **Depth-Coded Tabs** | Color tabs by directory depth (namespace) | P1 |
| **Rainbow Scopes** | Subtle background shading for nested scopes | P1 |
| **Method Separators** | Visual divider lines between methods | P1 |
| **File Age Indicator** | Tab/gutter colors based on modification recency | P2 |
| **Git Diff Heatmap** | Gutter indicators for commit frequency | P2 |
| **Log Noise Reducer** | Collapsible log viewer with filtering | P2 |
| **Comment Dimmer** | Reduce opacity of old/stale comments | P3 |
| **Zen Mode** | Distraction-free full-screen editing | P3 |

### 🛡️ Pillar 5: Code Quality & Analysis

Proactive code quality improvements.

| Feature | Description | Priority |
|---------|-------------|----------|
| **Exception Hunter** | Highlight potentially unhandled exceptions | P1 |
| **TODO Tracker** | Deadline-aware TODO management with calendar sync | P1 |
| **Dead Code Cemetery** | Bulk management of unused symbols | P2 |
| **Performance Linter** | Flag common anti-patterns (string concat in loops, etc.) | P2 |
| **Variable Lifecycle** | Visualize mutation and read paths | P3 |
| **Dependency Visualizer** | NuGet package "bloat" analysis | P3 |

### 🎮 Pillar 6: Gamification & Engagement

Make coding more engaging with achievement systems.

| Feature | Description | Priority |
|---------|-------------|----------|
| **Daily Stats Dashboard** | Commits, lines changed, time in IDE metrics | P2 |
| **Code Combo** | Visual effects for sustained typing bursts | P3 |
| **Developer XP** | Skill-based leveling for different task types | P3 |
| **Sound Packs** | Optional mechanical keyboard audio | P3 |

---

## Phased Roadmap

### Phase 0: Foundation (v0.1.0)

**Goal:** Establish plugin skeleton, core infrastructure, and basic Ollama connectivity.

```
Duration: 2-3 weeks
```

#### Deliverables

- [ ] **Plugin Skeleton**
  - IntelliJ Platform plugin structure (Kotlin/Java)
  - Rider-specific SDK integration
  - Basic settings persistence

- [ ] **Ollama Client**
  - HTTP client for Ollama API
  - Connection health monitoring
  - Model enumeration endpoint

- [ ] **Tool Window**
  - Dockable chat panel
  - Message input with submit
  - Basic response rendering

- [ ] **Settings Panel**
  - Ollama URL configuration
  - Model selection
  - Connection test button

#### Technical Milestones

| Milestone | Description | Acceptance Criteria |
|-----------|-------------|---------------------|
| M0.1 | Plugin loads in Rider | No errors on IDE startup |
| M0.2 | Settings panel functional | Ollama URL persists across restarts |
| M0.3 | Basic chat works | Send message → receive response from Ollama |

---

### Phase 1: Core LLM Features (v0.2.0)

**Goal:** Rich AI chat experience with context awareness.

```
Duration: 3-4 weeks
```

#### Deliverables

- [ ] **Streaming Responses**
  - SSE parsing for real-time token display
  - Cancellation support
  - Progress indication

- [ ] **Context Injection**
  - Current file content injection
  - Symbol resolution in context
  - Project structure summary

- [ ] **Code Actions**
  - "Explain this code" action
  - "Refactor this code" action
  - "Write tests for this" action

- [ ] **Prompt Templates**
  - Template editor in settings
  - Variable substitution ({{selection}}, {{file}}, {{language}})
  - Built-in templates for common tasks

- [ ] **Chat History**
  - SQLite persistence per project
  - History browser panel
  - Search functionality

#### Architecture Notes

```
┌────────────────────────────────────────────────────────────┐
│                     Sidekick Plugin                        │
├────────────────────────────────────────────────────────────┤
│  ┌─────────────────┐  ┌─────────────────┐  ┌────────────┐ │
│  │   Tool Window   │  │  Code Actions   │  │  Settings  │ │
│  │   (Chat UI)     │  │  (Intentions)   │  │   Panel    │ │
│  └────────┬────────┘  └────────┬────────┘  └─────┬──────┘ │
│           │                    │                  │        │
│  ┌────────┴────────────────────┴──────────────────┴──────┐ │
│  │                   Service Layer                        │ │
│  │  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐ │ │
│  │  │ ChatService  │  │ContextService│  │ HistoryService│ │ │
│  │  └──────────────┘  └──────────────┘  └──────────────┘ │ │
│  └─────────────────────────┬──────────────────────────────┘ │
│                            │                                │
│  ┌─────────────────────────┴──────────────────────────────┐ │
│  │                    Ollama Client                        │ │
│  │  - HTTP/SSE Communication                               │ │
│  │  - Model Management                                     │ │
│  │  - Connection Pooling                                   │ │
│  └─────────────────────────────────────────────────────────┘ │
└────────────────────────────────────────────────────────────┘
                            │
                            ▼
                    ┌───────────────┐
                    │    Ollama     │
                    │  (localhost)  │
                    └───────────────┘
```

---

### Phase 2: Code Generation (v0.3.0)

**Goal:** Intelligent code generation and transformation tools.

```
Duration: 3-4 weeks
```

#### Deliverables

- [ ] **Documentation Generator**
  - XML doc generation for methods/classes
  - Parameter documentation inference
  - Return value documentation
  - Exception documentation

- [ ] **Test Scaffolder**
  - xUnit/NUnit/MSTest template support
  - Arrange/Act/Assert structure
  - Mock setup generation
  - Edge case suggestions

- [ ] **Commit Message Generator**
  - Git integration for staged changes
  - Conventional Commits format
  - Scope detection from file paths
  - Breaking change detection

- [ ] **Variable Naming Assistant**
  - Context menu for renaming suggestions
  - Multiple alternatives with confidence scores
  - Apply rename via Rider refactoring

- [ ] **JSON → DTO Generator**
  - Clipboard detection for JSON
  - Record/class generation options
  - Nullable reference type support
  - System.Text.Json attribute generation

---

### Phase 3: Navigation & Productivity (v0.4.0)

**Goal:** Enhance code navigation and developer workflow.

```
Duration: 3-4 weeks
```

#### Deliverables

- [ ] **Jump-to-Test**
  - Convention-based test file discovery
  - Configurable naming patterns
  - Create test file if not exists
  - Bi-directional navigation

- [ ] **Bookmark Workspaces**
  - Named workspace creation
  - Bookmark and breakpoint grouping
  - Quick workspace switching
  - Export/import workspaces

- [ ] **Snippet Pocket**
  - Multi-slot clipboard (5-10 slots)
  - Visual snippet preview
  - Keyboard shortcuts for slots
  - Persistence across sessions

- [ ] **Recent Files Grid**
  - Visual file browser
  - Preview on hover
  - Grouping by project/folder
  - Recent search integration

- [ ] **Copy as Markdown**
  - Syntax-highlighted code blocks
  - File path header
  - Line number options
  - GitHub-flavored markdown

---

### Phase 4: Visual Enhancements (v0.5.0)

**Goal:** Improve code readability with visual cues.

```
Duration: 2-3 weeks
```

#### Deliverables

- [ ] **Depth-Coded Tabs**
  - Color gradient based on namespace depth
  - Customizable color palette
  - Toggle per project

- [ ] **Rainbow Scopes**
  - Background tinting for nested blocks
  - Configurable opacity
  - Disable for specific languages

- [ ] **Method Separators**
  - Horizontal lines between methods
  - Style customization (solid, dashed, dotted)
  - Color matching theme

- [ ] **File Age Indicator**
  - Tab color based on last modified
  - Relative time display
  - Git-aware modification tracking

- [ ] **Git Diff Heatmap**
  - Gutter annotation for commit frequency
  - Interactive commit history popup
  - Blame integration

---

### Phase 5: Code Quality (v0.6.0)

**Goal:** Proactive code quality enhancements.

```
Duration: 3-4 weeks
```

#### Deliverables

- [ ] **Exception Hunter**
  - Static analysis for unhandled exceptions
  - Call chain traversal
  - Rider inspection integration
  - Quick-fix suggestions

- [ ] **TODO Tracker**
  - TODO extraction with deadlines
  - Priority levels (TODO, FIXME, HACK, etc.)
  - Dashboard view
  - Desktop notifications for due items

- [ ] **Performance Linter**
  - String concatenation in loops
  - LINQ in hot paths
  - Allocation patterns
  - Async void detection
  - Integration with Rider analysis

- [ ] **Dead Code Cemetery**
  - Aggregate unused symbol view
  - Batch removal operations
  - Safe delete integration
  - Usage trend tracking

---

### Phase 6: Gamification (v0.7.0)

**Goal:** Add engagement and achievement features.

```
Duration: 2-3 weeks
```

#### Deliverables

- [ ] **Daily Stats Dashboard**
  - Commits today/week/month
  - Lines added/removed
  - Time in IDE
  - Most active files
  - Streak tracking

- [ ] **Code Combo**
  - Typing burst detection
  - Visual particle effects
  - Combo multiplier display
  - Personal best tracking

- [ ] **Developer XP**
  - XP for various activities
  - Skill trees (Testing, Refactoring, Documentation)
  - Level progression
  - Achievement badges

---

### Phase 7: Polish & Ecosystem (v1.0.0)

**Goal:** Production-ready release with ecosystem integration.

```
Duration: 4-6 weeks
```

#### Deliverables

- [ ] **Performance Optimization**
  - Lazy initialization
  - Background indexing
  - Memory profiling
  - Startup time optimization

- [ ] **Telemetry (Opt-In)**
  - Anonymous usage analytics
  - Crash reporting
  - Feature usage tracking

- [ ] **Plugin Marketplace**
  - JetBrains Marketplace submission
  - Marketing materials
  - Documentation site
  - Video tutorials

- [ ] **Extensibility API**
  - Public API for third-party extensions
  - Custom prompt template plugins
  - Visual enhancement hooks

- [ ] **Multi-Provider Support**
  - OpenAI API (optional)
  - Anthropic Claude API (optional)
  - Azure OpenAI (optional)
  - Provider abstraction layer

---

## Technical Architecture

### Technology Stack

| Component | Technology | Rationale |
|-----------|------------|-----------|
| **Plugin Framework** | IntelliJ Platform SDK | Native Rider integration |
| **Language** | Kotlin | Modern, null-safe, IDE-preferred |
| **LLM Client** | Ktor HTTP Client | Async, coroutine-based |
| **Persistence** | SQLite (Exposed) | Lightweight, embedded |
| **UI** | JetBrains UI DSL | Consistent IDE look |
| **Markdown** | CommonMark (Flexmark) | Chat response rendering |

### Key Interfaces

```kotlin
// Core service interfaces

interface OllamaService {
    suspend fun listModels(): List<OllamaModel>
    suspend fun chat(request: ChatRequest): Flow<ChatToken>
    suspend fun generate(request: GenerateRequest): Flow<GenerateToken>
    fun getConnectionStatus(): ConnectionStatus
}

interface ContextService {
    fun getCurrentFileContext(): FileContext?
    fun getSelectionContext(): SelectionContext?
    fun getProjectContext(): ProjectContext
    fun getSymbolContext(psiElement: PsiElement): SymbolContext?
}

interface ChatHistoryService {
    suspend fun saveMessage(message: ChatMessage)
    suspend fun getHistory(projectId: String, limit: Int): List<ChatMessage>
    suspend fun searchHistory(query: String): List<ChatMessage>
}

interface PromptTemplateService {
    fun getTemplates(): List<PromptTemplate>
    fun expandTemplate(template: PromptTemplate, context: TemplateContext): String
    fun saveCustomTemplate(template: PromptTemplate)
}
```

### Data Models

```kotlin
// Core data models

data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val options: ChatOptions? = null,
    val context: List<Int>? = null
)

data class ChatMessage(
    val role: MessageRole,
    val content: String,
    val timestamp: Instant = Instant.now()
)

enum class MessageRole { SYSTEM, USER, ASSISTANT }

data class FileContext(
    val filePath: String,
    val language: Language,
    val content: String,
    val cursorPosition: Int?,
    val selection: TextRange?
)

data class PromptTemplate(
    val id: String,
    val name: String,
    val template: String,
    val category: TemplateCategory,
    val isBuiltIn: Boolean
)
```

---

## Platform Requirements

### Minimum Requirements

| Requirement | Version |
|-------------|---------|
| **IntelliJ Platform** | 2024.1+ |
| **JetBrains Rider** | 2024.1+ |
| **JDK** | 17+ |
| **Kotlin** | 1.9+ |

### Optional Dependencies

| Dependency | Purpose |
|------------|---------|
| **Ollama** | Local LLM inference (required for AI features) |
| **Git** | VCS integration features |

### Supported Languages

Initial focus on .NET languages with Rider:

- C# (primary)
- F#
- VB.NET
- C++ (via Rider)

Future expansion:

- All IntelliJ Platform languages (if ported to IntelliJ IDEA)

---

## Success Metrics

### Phase Completion Criteria

| Phase | Key Metrics |
|-------|-------------|
| 0 | Plugin installs without errors, basic chat functional |
| 1 | <200ms streaming latency, 95% context injection accuracy |
| 2 | Doc generation for 90% of method signatures |
| 3 | Jump-to-test works for 95% of conventional test setups |
| 4 | No measurable impact on IDE startup (<100ms) |
| 5 | Exception hunter <5% false positive rate |
| 6 | Daily stats accurate within 5% of actual |
| 7 | Marketplace approval, >100 installs first month |

---

## Appendix A: Feature Priority Matrix

| Priority | Description | Example Features |
|----------|-------------|------------------|
| **P0** | Must-have for MVP | Ollama Bridge, Context Injection, Streaming |
| **P1** | High value, early phases | Doc Writer, Jump-to-Test, Chat History |
| **P2** | Important but not urgent | Regex Builder, Git Heatmap, Stats |
| **P3** | Nice-to-have | Code Combo, Sound Packs, Zen Mode |

---

## Appendix B: Competitor Analysis

| Plugin | Strengths | Weaknesses | Sidekick Differentiation |
|--------|-----------|------------|--------------------------|
| **GitHub Copilot** | Cloud-based, large model | Privacy concerns, cost | Local-first, free |
| **AI Assistant** | JetBrains native | Cloud-dependent | Ollama integration |
| **Codeium** | Free tier | Limited customization | Prompt templates, extensibility |
| **TabNine** | Fast completions | Less conversational | Full chat interface |

---

## Revision History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 0.1.0 | 2026-02-04 | Ryan | Initial design document |
