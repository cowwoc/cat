---
name: cat:config
description: Interactive wizard to customize your CAT adventure settings
model: haiku
context: fork
allowed-tools:
  - Bash
  - Read
  - Write
  - AskUserQuestion
---

<objective>

Interactive configuration wizard to customize CAT settings. Displays current configuration in adventure
style and guides users through modifying their preferences.

</objective>

<process>

<step name="read-config">

**Read current configuration:**

```bash
cat .claude/cat/cat-config.json
```

If file doesn't exist, inform user to run `/cat:init` first.

</step>

<step name="display-settings">

**Display adventure settings screen:**

**IMPORTANT: Output styled text DIRECTLY - do NOT use Bash tool for rendering.**

```
╭─── ⚙️ ADVENTURE SETTINGS ─────────────────────────────────╮
│                                                            │
│  🧠 CONTEXT LIMITS                                         │
│     Window:  {contextLimit} tokens                         │
│     Target:  {targetContextUsage}% before split            │
│                                                            │
│  🐱 CAT BEHAVIOR                                           │
│     Trust:     {trust || "medium"}                         │
│     Verify:    {verify || "changed"}                       │
│     Curiosity: {curiosity || "low"}                        │
│     Patience:  {patience || "high"}                        │
│                                                            │
│  🧹 CLEANUP                                                │
│     Auto-remove: {autoRemoveWorktrees ? "On" : "Off"}      │
│                                                            │
│  📊 VERSION GATES                                          │
│     Configure entry/exit conditions for versions           │
│                                                            │
╰────────────────────────────────────────────────────────────╯
```

</step>

<step name="main-menu">

**Present main menu using AskUserQuestion:**

Show current values in descriptions using data from read-config step.

- header: "Settings"
- question: "What would you like to configure?"
- options:
  - label: "🧠 Context Limits"
    description: "Currently: {contextLimit}k / {targetContextUsage}%"
  - label: "🐱 CAT Behavior"
    description: "Currently: {trust} · {verify} · {curiosity} · {patience}"
  - label: "🧹 Cleanup / 📊 Gates"
    description: "Currently: {autoRemoveWorktrees ? 'Auto-remove' : 'Keep'}"

If user selects "Other" and types "done", "exit", or "back", proceed to exit step.

</step>

<step name="context-limits">

**🧠 Context Limits selection:**

Display current settings, then AskUserQuestion:
- header: "Context"
- question: "What would you like to adjust?"
- options (show current values in descriptions):
  - label: "Context window size"
    description: "Currently: {contextLimit} tokens"
  - label: "Target usage threshold"
    description: "Currently: {targetContextUsage}%"
  - label: "← Back"
    description: "Return to main menu"

**For context limit** (prefix ONLY the option matching current contextLimit with "✅ "):
- "200,000 tokens - Claude Opus (Recommended)"
- "128,000 tokens - Claude Sonnet"
- "Custom value"

**For target usage** (prefix ONLY the option matching current targetContextUsage with "✅ "):
- "30% - Conservative, lots of headroom"
- "40% - Balanced (Recommended)"
- "50% - Aggressive, maximize task size"

</step>

<step name="cat-behavior">

**🐱 CAT Behavior selection:**

AskUserQuestion:
- header: "Behavior"
- question: "Which setting would you like to adjust?"
- options (show current values in descriptions):
  - label: "🤝 Trust"
    description: "Currently: {trust || 'medium'}"
  - label: "✅ Verify"
    description: "Currently: {verify || 'changed'}"
  - label: "🔍 Curiosity"
    description: "Currently: {curiosity || 'low'}"
  - label: "⏳ Patience"
    description: "Currently: {patience || 'high'}"
  - label: "← Back"
    description: "Return to main menu"

</step>

<step name="trust">

**🤝 Trust — How much you trust CAT to make decisions**

Display (add "(current)" after the level name if it matches current config):
```
╭─── 🤝 TRUST LEVEL ────────────────────────────────────────╮
│  How much freedom does CAT have to roam?                   │
├────────────────────────────────────────────────────────────┤
│                                                            │
│  🐱─┈  LOW {trust == 'low' ? '(current)' : ''}             │
│     Low trust. CAT presents options frequently:            │
│     where to place code, which approach to take.           │
│     ✦ Best for: Learning, strong preferences               │
│                                                            │
│  🐱─ ─ ┈  MEDIUM {trust == 'medium' ? '(current)' : ''}    │
│     Moderate trust. CAT handles routine decisions          │
│     but presents options for meaningful trade-offs.        │
│     ✦ Best for: Balanced control and efficiency            │
│                                                            │
│  🐱─ ─ ─ ─ ┈  HIGH {trust == 'high' ? '(current)' : ''}    │
│     Full autonomy. CAT runs without stopping.              │
│     Skips stakeholder review. Tasks auto-merge.            │
│     ✦ Best for: Trusted workflows, batch processing        │
│                                                            │
╰────────────────────────────────────────────────────────────╯
```

AskUserQuestion:
- header: "Trust"
- question: "How much do you trust CAT to make decisions? (Current: {trust || 'medium'})"
- options:
  - label: "Medium (Recommended)"
    description: "Auto-fixes review issues, presents meaningful choices"
  - label: "Low"
    description: "Asks before fixing review issues, presents options frequently"
  - label: "High"
    description: "Full autonomy, skips review, auto-merges"
  - label: "← Back"
    description: "Return to behavior menu"

Map: Low → `trust: "low"`, Medium → `trust: "medium"`, High → `trust: "high"`

</step>

<step name="verify">

**✅ Verify — What verification CAT runs before committing**

Display (add "(current)" after the level name if it matches current config):
```
╭─── ✅ VERIFICATION LEVEL ─────────────────────────────────╮
│  What does CAT check before commit?                        │
├────────────────────────────────────────────────────────────┤
│                                                            │
│  ⚡ NONE {verify == 'none' ? '(current)' : ''}             │
│     No verification before commit. Fastest iteration       │
│     but won't catch any errors automatically.              │
│     ✦ Best for: Rapid prototyping, manual verification     │
│                                                            │
│  📦 CHANGED {verify == 'changed' ? '(current)' : ''}       │
│     Verify modified file/module only. Catches most         │
│     regressions without verifying the full project.        │
│     ✦ Best for: Most workflows                             │
│                                                            │
│  🔒 ALL {verify == 'all' ? '(current)' : ''}               │
│     Verify the entire project before each commit.          │
│     Slowest but highest confidence.                        │
│     ✦ Best for: Critical code, integration changes         │
│                                                            │
╰────────────────────────────────────────────────────────────╯
```

AskUserQuestion:
- header: "Verify"
- question: "What verification should CAT run? (Current: {verify || 'changed'})"
- options:
  - label: "Changed (Recommended)"
    description: "Verify modified file/module only"
  - label: "None"
    description: "No verification before commit"
  - label: "All"
    description: "Verify entire project"
  - label: "← Back"
    description: "Return to behavior menu"

Map: None → `verify: "none"`, Changed → `verify: "changed"`, All → `verify: "all"`

</step>

<step name="curiosity">

**🔍 Curiosity — How much CAT explores beyond the immediate task**

Display (add "(current)" after the level name if it matches current config):
```
╭─── 🔍 CURIOSITY LEVEL ────────────────────────────────────╮
│  How much does CAT look beyond the task?                   │
├────────────────────────────────────────────────────────────┤
│                                                            │
│  🎯 LOW {curiosity == 'low' ? '(current)' : ''}            │
│     Task-only. Complete exactly what's required,           │
│     nothing more. Don't look for improvements.             │
│     ✦ Best for: Minimal scope, predictable output          │
│                                                            │
│  👀 MEDIUM {curiosity == 'medium' ? '(current)' : ''}      │
│     Opportunistic. Notice obvious issues encountered       │
│     while working (bugs, deprecated syntax).               │
│     ✦ Best for: Balanced thoroughness                      │
│                                                            │
│  🔭 HIGH {curiosity == 'high' ? '(current)' : ''}          │
│     Proactive. Actively examine related code for           │
│     patterns, tech debt, or optimization opportunities.    │
│     ✦ Best for: Comprehensive improvement                  │
│                                                            │
╰────────────────────────────────────────────────────────────╯
```

AskUserQuestion:
- header: "Curiosity"
- question: "How much should CAT explore beyond the task? (Current: {curiosity || 'low'})"
- options:
  - label: "Low (Recommended)"
    description: "Task-only, minimal scope"
  - label: "Medium"
    description: "Notice obvious issues while working"
  - label: "High"
    description: "Actively explore for improvements"
  - label: "← Back"
    description: "Return to behavior menu"

Map: Low → `curiosity: "low"`, Medium → `curiosity: "medium"`, High → `curiosity: "high"`

</step>

<step name="patience">

**⏳ Patience — When CAT acts on discovered opportunities**

Display (add "(current)" after the level name if it matches current config):
```
╭─── ⏳ PATIENCE LEVEL ─────────────────────────────────────╮
│  When does CAT act on what it finds?                       │
├────────────────────────────────────────────────────────────┤
│                                                            │
│  ⚡ LOW {patience == 'low' ? '(current)' : ''}             │
│     Act immediately. Address improvements as part of       │
│     the current task. Scope expands but work is done.      │
│     ✦ Best for: Comprehensive fixes, avoiding tech debt    │
│                                                            │
│  📋 MEDIUM {patience == 'medium' ? '(current)' : ''}       │
│     Defer to current version. Log improvements as          │
│     separate tasks within the current version.             │
│     ✦ Best for: Focused tasks with nearby follow-up        │
│                                                            │
│  📅 HIGH {patience == 'high' ? '(current)' : ''}           │
│     Defer by priority. Schedule improvements to future     │
│     versions based on benefit/cost ratio.                  │
│     ✦ Best for: Surgical tasks, controlled scope           │
│                                                            │
╰────────────────────────────────────────────────────────────╯
```

AskUserQuestion:
- header: "Patience"
- question: "When should CAT act on discovered opportunities? (Current: {patience || 'high'})"
- options:
  - label: "High (Recommended)"
    description: "Defer by priority to future versions"
  - label: "Medium"
    description: "Defer to current version"
  - label: "Low"
    description: "Act immediately, expand scope"
  - label: "← Back"
    description: "Return to behavior menu"

Map: Low → `patience: "low"`, Medium → `patience: "medium"`, High → `patience: "high"`

**Priority-based deferral (when patience is high):**
- High benefit, low cost → Current or next version
- Moderate → Next major version
- Low benefit, high cost → Backlog or distant future

</step>

<step name="cleanup-gates">

**🧹 Cleanup / 📊 Version Gates:**

AskUserQuestion:
- header: "Settings"
- question: "What would you like to configure?"
- options (show current values in descriptions):
  - label: "🧹 Cleanup"
    description: "Currently: {autoRemoveWorktrees ? 'Auto-remove' : 'Keep'}"
  - label: "📊 Version Gates"
    description: "Entry/exit conditions for versions"
  - label: "← Back"
    description: "Return to main menu"

</step>

<step name="cleanup">

**🧹 Cleanup selection:**

AskUserQuestion:
- header: "Cleanup"
- question: "Worktree cleanup behavior: (Current: {autoRemoveWorktrees ? 'Auto-remove' : 'Keep'})"
- options:
  - label: "🧹 Auto-remove (Recommended)"
    description: "Remove after task completion"
  - label: "📦 Keep"
    description: "Preserve for manual inspection"
  - label: "← Back"
    description: "Return to previous menu"


Map: Auto-remove → `autoRemoveWorktrees: true`, Keep → `autoRemoveWorktrees: false`

</step>

<step name="version-gates">

**📊 Version Gates configuration:**

Display current gate overview:
```
╭─── 📊 VERSION GATES ──────────────────────────────────────╮
│                                                            │
│  Gates control when work can start and when it's done.     │
│  Each version can have entry (start) and exit (done)       │
│  gates. Major gates are inherited by all minor versions.   │
│                                                            │
╰────────────────────────────────────────────────────────────╯
```

**Step 1: Select version to configure**

First, scan for available versions:
```bash
ls -1d .claude/cat/v[0-9]*/v[0-9]*.[0-9]* 2>/dev/null | \
  sed 's|.claude/cat/v[0-9]*/v||' | sort -V
```

Determine current minor version from ROADMAP.md (first non-completed).

Use AskUserQuestion:
- header: "Select Version"
- question: "Which version's gates do you want to configure?"
- options:
  - "v{X}.{Y-1} - Previous minor" (if exists)
  - "v{X}.{Y} - Current minor" (highlighted)
  - "v{X}.{Y+1} - Next minor" (if exists)
  - "Enter version number" - Custom input

**If "Enter version number":**

Use AskUserQuestion:
- header: "Version"
- question: "Enter the version number (e.g., 0.5 or just 0 for major):"
- options: ["← Back"]

Parse input to determine if major (single digit) or minor (X.Y format).

**Step 2: Display current gates**

Read the PLAN.md for selected version:
```bash
cat .claude/cat/v{major}/v{major}.{minor}/PLAN.md 2>/dev/null || \
cat .claude/cat/v{major}/PLAN.md 2>/dev/null
```

Extract and display the `## Gates` section:
```
╭─── 📊 Gates for v{version} ───────────────────────────────╮
│                                                            │
│  ENTRY (when can work start?):                             │
│  • {condition 1}                                           │
│  • {condition 2}                                           │
│                                                            │
│  EXIT (when is it done?):                                  │
│  • {condition 1}                                           │
│  • {condition 2}                                           │
│                                                            │
╰────────────────────────────────────────────────────────────╯
```

If no gates section exists, display:
```
╭─── ⚠️ No gates configured for v{version} ─────────────────╮
│                                                            │
│  Default behavior applies:                                 │
│  • Entry: Previous version must complete                   │
│  • Exit: All tasks must complete                           │
│                                                            │
╰────────────────────────────────────────────────────────────╯
```

**Step 3: Choose action**

Use AskUserQuestion:
- header: "Action"
- question: "What would you like to do?"
- options:
  - label: "Edit entry gate"
    description: "Change when work can start"
  - label: "Edit exit gate"
    description: "Change completion criteria"
  - label: "View another version"
    description: "Select a different version"
  - label: "← Back"
    description: "Return to main menu"

**Step 4a: Edit entry gate**

Use AskUserQuestion:
- header: "Entry Gate"
- question: "Select entry conditions (current: {current conditions}):"
- multiSelect: true
- options:
  - "Previous version complete" - sequential dependency
  - "Specific task(s) complete" - named tasks required
  - "Specific version(s) complete" - named versions required
  - "Manual approval required" - explicit sign-off

If "Specific task(s) complete":
- Ask: "Which task(s)? (e.g., 0.5-design-review, comma-separated)"

If "Specific version(s) complete":
- Ask: "Which version(s)? (e.g., 0.3, 0.4, comma-separated)"

**Step 4b: Edit exit gate**

Use AskUserQuestion:
- header: "Exit Gate"
- question: "Select exit conditions (current: {current conditions}):"
- multiSelect: true
- options:
  - "All tasks complete" - every task in version done
  - "Specific task(s) complete" - only named tasks required
  - "Tests passing" - test suite must pass
  - "Manual sign-off" - explicit approval

If "Specific task(s) complete":
- Ask: "Which task(s)? (comma-separated)"

**Step 5: Update PLAN.md**

Read the version's PLAN.md, update the `## Gates` section:

```markdown
## Gates

### Entry
- {condition 1}
- {condition 2}

### Exit
- {condition 1}
- {condition 2}
```

If the PLAN.md doesn't have a `## Gates` section, insert it after `## Focus` or `## Vision`.

Write the updated PLAN.md using the Write tool.

**Step 6: Confirm and loop**

Display confirmation:
```
╭─── ✓ Gates updated for v{version} ────────────────────────╮
│                                                            │
│  Entry: {summary of entry conditions}                      │
│  Exit:  {summary of exit conditions}                       │
│                                                            │
╰────────────────────────────────────────────────────────────╯
```

Return to Step 3 (Choose action) to allow further edits or navigation.

</step>

<step name="update-config">

**Update configuration file:**

```bash
# Safe jq update pattern
jq '.settingName = "newValue"' .claude/cat/cat-config.json > .claude/cat/cat-config.json.tmp \
  && mv .claude/cat/cat-config.json.tmp .claude/cat/cat-config.json
```

</step>

<step name="confirm">

**Confirm change and return to parent menu:**

```
╭─── ✓ Setting updated ─────────────────────────────────────╮
│                                                            │
│  {setting}: {oldValue} → {newValue}                        │
│                                                            │
╰────────────────────────────────────────────────────────────╯
```

**After confirming**: Return to the **parent menu** and re-display its options.

Examples:
- Changed "Trust" → return to CAT Behavior menu
- Changed "Context window size" → return to Context Limits menu
- Changed "Cleanup" → return to Cleanup/Gates menu

</step>

<step name="exit">

**Exit screen:**

If changes were made:
```
╭─── ✨ CONFIGURATION SAVED ────────────────────────────────╮
│                                                            │
│  Changes applied:                                          │
│  • {setting1}: {old} → {new}                               │
│  • {setting2}: {old} → {new}                               │
│                                                            │
│  Your adventure continues with new settings!               │
│                                                            │
╰────────────────────────────────────────────────────────────╯
```

If no changes:
```
╭────────────────────────────────────────────────────────────╮
│  No changes made. Settings unchanged.                      │
╰────────────────────────────────────────────────────────────╯
```

</step>

</process>

<configuration_reference>

| Setting | Type | Default | Description |
|---------|------|---------|-------------|
| `contextLimit` | number | 200000 | Context window size |
| `targetContextUsage` | number | 40 | Decomposition threshold (%) |
| `trust` | string | "medium" | Trust level (controls review and autonomy) |
| `verify` | string | "changed" | What verification runs before commits |
| `curiosity` | string | "low" | Exploration beyond immediate task |
| `patience` | string | "high" | When to act on discoveries |
| `autoRemoveWorktrees` | boolean | true | Auto-remove worktrees |

### Trust Values
- `low` — Asks before fixing review issues. Presents options frequently.
- `medium` — Auto-fixes review issues. Presents meaningful choices.
- `high` — Full autonomy. Skips review. Auto-merges.

### Verify Values
- `none` — No verification before commit.
- `changed` — Verify modified file/module only.
- `all` — Verify entire project.

### Curiosity Values
- `low` — Task-only. Don't explore.
- `medium` — Notice obvious issues while working.
- `high` — Actively explore for improvements.

### Patience Values
- `low` — Act immediately. Expand scope.
- `medium` — Defer to current version.
- `high` — Defer by priority to future versions.

</configuration_reference>

<success_criteria>

- [ ] Current configuration displayed in adventure theme
- [ ] User navigated wizard successfully
- [ ] Settings updated in cat-config.json using safe jq pattern
- [ ] Version gates viewable and editable via wizard
- [ ] Gate changes saved to version PLAN.md files
- [ ] Changes confirmed with before/after values

</success_criteria>
