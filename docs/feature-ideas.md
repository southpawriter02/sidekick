# Sidekick Feature Ideas — 20 Enhancements, Security Fixes & UX Improvements

> Brainstormed from a full review of the source tree, `DESIGN.md`, design specs (v0.1–v1.0), changelogs, and the existing KI.

---

## 🧠 Feature Enhancements

### 1. **Inline Diff Preview for Agent Edits**

COMPLETED

The agent system can propose code changes, but there's no visual diff preview before application. Add a side-by-side or unified diff panel (like Rider's built-in diff viewer) that lets the user inspect, cherry-pick hunks, and approve/reject individual changes before they're applied.

- **Why now:** The agent executor (`AgentExecutor.kt`) already has tool-call results — surface them visually.
- **Effort:** Medium

---

### 2. **Conversation Branching & Forking**

Allow users to "fork" a conversation at any message to explore an alternative line of reasoning without losing the original thread. Visualize branches as a tree/timeline sidebar.

- **Why now:** `ChatController` tracks `conversationHistory` linearly — extend to a tree structure.
- **Effort:** High

---

### 3. **Prompt Template Marketplace (Local)**

The `PromptTemplateService` supports custom templates, but discovering and sharing them is manual. Add a local template gallery where users can import/export `.sidekick-prompt` files, with preview, tags, and categories.

- **Why now:** Templates are in `prompts/` but have no discovery UX.
- **Effort:** Medium

---

### 4. **Smart Context Budget Indicator**

The `ContextBuilder` silently truncates when context is too large. Add a live "token budget" bar in the chat UI showing how much of the model's context window is consumed by file content, selection, project summary, and codebase search results — let the user toggle sections on/off.

- **Why now:** `ContextBuilder.build()` assembles sections but the user has zero visibility into what was included or dropped.
- **Effort:** Medium

---

### 5. **Multi-File Drag-and-Drop Context**

Allow dragging files from the Project Explorer directly into the chat input to attach them as context. Currently multi-file context is programmatic only.

- **Why now:** `DESIGN.md` lists "Multi-File Context" as P2 — this is a natural UX for it.
- **Effort:** Medium

---

### 6. **Git-Aware Prompt Enrichment**

Automatically inject the current branch name, recent commit messages, and staged diff summary into the system prompt so the LLM understands what you're working on. Toggle-able in settings.

- **Why now:** The `visual/git/` package already has Git integration for heatmaps — extend to the prompt pipeline.
- **Effort:** Low

---

### 7. **"Explain This Error" One-Click Action**

When Rider's analysis produces a warning or error, add a gutter icon that sends the error message, surrounding code, and fix suggestions to the LLM for a contextual explanation.

- **Why now:** `generation/errorexplain/` exists but is not wired to Rider's error gutter annotations.
- **Effort:** Medium

---

### 8. **Session-Aware Workspace Snapshots**

Extend Bookmark Workspaces (`navigation/workspaces/`) with automatic snapshots: save the current set of open files, cursor positions, and breakpoints whenever the user switches workspaces, and restore them on re-entry.

- **Why now:** Workspaces save bookmarks but not the full session state.
- **Effort:** Medium

---

### 9. **Streaming Code Highlighting in Chat Bubbles**

The `MessageBubble` renders markdown, but code blocks have no syntax highlighting until the stream completes. Implement incremental syntax highlighting using Rider's language grammars so code is colored as it appears token by token.

- **Why now:** This is a noticeable UX gap — large code blocks are uncolored during streaming.
- **Effort:** High

---

### 10. **Agent Task Dashboard**

Build a persistent "Task History" panel (like a mini CI dashboard) showing past agent tasks, their status (✅/❌), duration, tools invoked, and self-correction attempts. Clicking a task replays the conversation that triggered it.

- **Why now:** `AgentExecutor.getTaskHistory()` and `TaskEvent` already exist — they just lack UI.
- **Effort:** Medium

---

## 🛡️ Security Improvements

### 11. **Rate-Limited LLM Requests**

COMPLETED

There is no rate limiting on chat or agent requests. A tight loop (e.g., agent self-correction retries) could saturate the local LLM. Add configurable per-minute request caps with exponential back-off.

- **Why now:** The self-correction system (`agent/correction/`) can retry indefinitely.
- **Effort:** Low

---

### 12. **Prompt Injection Detection**

Add a lightweight scanner that checks user input for common prompt injection patterns (e.g., "Ignore previous instructions", "You are now…") **before** sending to the LLM. Log as `SecurityEventType` and optionally warn the user.

- **Why now:** `CommandSandbox.checkForInjection()` handles shell injection but not LLM prompt injection.
- **Effort:** Low

---

### 13. **Scoped File Access per Agent Task**

COMPLETED

Currently `SecurityConfig.restrictedPaths` is global. Introduce per-task file access scoping where the agent can only read/write files within the project root or explicitly approved directories for the duration of a single task.

- **Why now:** A malicious model response could instruct the agent to read `~/.ssh/` — per-task scoping limits blast radius.
- **Effort:** Medium

---

### 14. **Sensitive Data Redaction in Context**

Before injecting file content or project context into the prompt, scan for and redact patterns matching API keys, connection strings, JWT tokens, and passwords. Use regex-based heuristics plus `.gitignore`/`.env` awareness.

- **Why now:** `ContextBuilder` sends raw file content — no PII/secret filtering exists.
- **Effort:** Medium

---

### 15. **Audit Log Export**

The `CommandSandbox` maintains an in-memory `eventLog`, but it's not persisted or exportable. Add JSON/CSV export of the security audit log, and optionally write to a project-level `.sidekick/audit.log` file with rotation.

- **Why now:** Compliance-conscious users need persistent audit trails.
- **Effort:** Low

---

## 🎨 UX Fixes & Quality of Life

### 16. **Keyboard-First Chat Navigation**

The chat panel lacks keyboard shortcuts for common actions: `Ctrl+Enter` to send, `Esc` to cancel, `↑` to edit last message, `Ctrl+L` to clear. Add a shortcuts cheat-sheet popup (`?` key).

- **Why now:** Power users in Rider are keyboard-centric — the chat UI currently requires mouse clicks.
- **Effort:** Low

---

### 17. **Provider Health Toast Notifications**

`ChatController.updateConnectionStatus()` polls providers silently. When a provider goes offline or comes back online, show a non-intrusive toast notification (IntelliJ's `Notifications.Bus`) instead of only updating the status bar widget.

- **Why now:** Users report confusion when the LLM silently disconnects mid-conversation.
- **Effort:** Low

---

### 18. **Chat Export to Markdown**

COMPLETED

Add an "Export Conversation" action that saves the entire chat thread (with code blocks, context, and timestamps) as a well-formatted Markdown file. Useful for documentation, PR descriptions, or sharing with teammates.

- **Why now:** Chat history exists in memory but there's no user-facing export path.
- **Effort:** Low

---

### 19. **Model Download Progress Indicator**

When the user selects a model that isn't yet pulled locally, `ModelSelectorWidget` should show a download progress bar with ETA and allow cancellation, rather than silently failing or hanging.

- **Why now:** `ModelSelectorWidget.kt` lists available models but doesn't handle the "not yet downloaded" case gracefully.
- **Effort:** Medium

---

### 20. **Collapsible Context Sections in Chat**

COMPLETED

When the system prompt includes large context blocks (file content, search results, project summary), render them as collapsible `<details>` sections in the chat bubble so the conversation doesn't become unwieldy.

- **Why now:** `ContextBuilder` can inject hundreds of lines of context — users have reported the chat becoming hard to scroll through.
- **Effort:** Low
