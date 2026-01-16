---
name: cat:config
description: Interactive wizard to customize your CAT settings
model: haiku
context: fork
allowed-tools:
  - Bash
  - Read
  - Write
  - AskUserQuestion
---

<objective>

Interactive configuration wizard to customize CAT settings. Displays current configuration and guides
users through modifying their preferences.

</objective>

<emoji_reference>

**MANDATORY (M210): Use these exact emojis for each setting:**

| Setting | Emoji | Display |
|---------|-------|---------|
| Trust | 🤝 | 🤝 Trust |
| Verify | ✅ | ✅ Verify |
| Curiosity | 🔍 | 🔍 Curiosity |
| Patience | ⏳ | ⏳ Patience |

**Copy-paste from this table when constructing displays manually.**

</emoji_reference>

<process>

<step name="check-precomputed">

**MANDATORY: Check for pre-computed display**

Look in the conversation context for "PRE-COMPUTED CONFIG DISPLAY".

**If found:**
- Store the exact box text from the context
- Continue to "read-config" step

**If NOT found:**
- STOP immediately
- Output error: "ERROR: Pre-computed display not available. Handler config_handler.py may have failed."
- Do NOT attempt to construct boxes manually
- This is a fail-fast requirement per skill-builder methodology

</step>

<step name="read-config">

**Read current configuration:**

```bash
cat .claude/cat/cat-config.json
```

If file doesn't exist, inform user to run `/cat:init` first.

</step>

<step name="display-settings">

**MANDATORY (M130/A021) - Display-Before-Prompt Protocol:**

BLOCKING REQUIREMENT: You MUST output a visual display box BEFORE calling AskUserQuestion.

**Output the PRE-COMPUTED CONFIG DISPLAY from context**

Copy the exact box from the "PRE-COMPUTED CONFIG DISPLAY" context. Do NOT recompute or modify alignment.

**Why pre-computed:** Agents miscalculate emoji widths, causing misaligned box borders.
The handler config_handler.py calculates correct widths before the skill starts.

</step>

<step name="main-menu">

**CHECKPOINT (M132): Verify settings box was displayed in previous step. If not, STOP and output it now.**

**Present main menu using AskUserQuestion:**

Show current values in descriptions using data from read-config step.

- header: "Settings"
- question: "What would you like to configure?"
- options:
  - label: "🐱 CAT Behavior"
    description: "Currently: {trust} · {verify} · {curiosity} · {patience}"
  - label: "🧹 Cleanup"
    description: "Currently: {autoRemoveWorktrees ? 'Auto-remove' : 'Keep'}"
  - label: "📏 Display Width"
    description: "Currently: {terminalWidth || 120} characters"
  - label: "🔀 Completion Workflow"
    description: "Currently: {completionWorkflow || 'merge'}"
  - label: "📊 Version Gates"
    description: "Entry/exit conditions for versions"

If user selects "Other" and types "done", "exit", or "back", proceed to exit step.

**Note:** Context limits are fixed and not configurable. See agent-architecture.md § Context Limit Constants.

</step>


<step name="cat-behavior">

**🐱 CAT Behavior selection:**

**MANDATORY (M137) - Display behavior summary BEFORE prompting:**

```
╭─── 🐱 CAT BEHAVIOR ────────────────────────────────────────────────╮
│                                                                    │
│  🤝 Trust: {trust}                                                 │
│  ✅ Verify: {verify}                                               │
│  🔍 Curiosity: {curiosity}                                         │
│  ⏳ Patience: {patience}                                           │
╰────────────────────────────────────────────────────────────────────╯
```

Then AskUserQuestion:
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

Display current setting, then AskUserQuestion:
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

Display current setting, then AskUserQuestion:
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

Display current setting, then AskUserQuestion:
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

Display current setting, then AskUserQuestion:
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
    description: "Return to main menu"


Map: Auto-remove → `autoRemoveWorktrees: true`, Keep → `autoRemoveWorktrees: false`

</step>

<step name="terminal-width">

**📏 Display Width selection:**

AskUserQuestion:
- header: "Display Width"
- question: "What device are you primarily using?"
- options:
  - label: "🖥️ Desktop/Laptop (Recommended)"
    description: "120 characters - optimized for wide monitors"
  - label: "📱 Mobile"
    description: "50 characters - optimized for phones and narrow screens"
  - label: "⚙️ Custom value"
    description: "Enter a specific width (40-200)"
  - label: "← Back"
    description: "Return to main menu"

**Map selections:**
- Desktop/Laptop → `terminalWidth: 120`
- Mobile → `terminalWidth: 50`
- Custom → prompt for value, validate 40-200

**If Custom value selected:**

AskUserQuestion:
- header: "Custom Width"
- question: "Enter terminal width (40-200):"
- options: ["← Back"]

Validate input is a number between 40-200. If invalid, show error and re-prompt.

**Update config with safe jq pattern:**
```bash
jq '.terminalWidth = {value}' .claude/cat/cat-config.json > .claude/cat/cat-config.json.tmp \
  && mv .claude/cat/cat-config.json.tmp .claude/cat/cat-config.json
```

</step>

<step name="completion-workflow">

**🔀 Completion Workflow selection:**

AskUserQuestion:
- header: "Completion Workflow"
- question: "How should completed tasks be integrated? (Current: {completionWorkflow || 'merge'})"
- options:
  - label: "🔀 Merge (Recommended)"
    description: "Merge task branch directly to base branch after approval"
  - label: "📝 Pull Request"
    description: "Create a PR instead of merging directly"
  - label: "← Back"
    description: "Return to main menu"

Map: Merge → `completionWorkflow: "merge"`, Pull Request → `completionWorkflow: "pr"`

**Update config with safe jq pattern:**
```bash
jq '.completionWorkflow = "{value}"' .claude/cat/cat-config.json > .claude/cat/cat-config.json.tmp \
  && mv .claude/cat/cat-config.json.tmp .claude/cat/cat-config.json
```

</step>

<step name="version-gates">

**📊 Version Gates configuration:**

Display gate overview:

```
╭─── 📊 VERSION GATES ───────────────────────────────────────────────╮
│                                                                    │
│  Gates control when work can start (entry) and                     │
│  when a version is considered complete (exit).                     │
╰────────────────────────────────────────────────────────────────────╯
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
cat .claude/cat/issues/v{major}/v{major}.{minor}/PLAN.md 2>/dev/null || \
cat .claude/cat/issues/v{major}/PLAN.md 2>/dev/null
```

Extract the `## Gates` section and display:

```
╭─── 🚧 GATES FOR {version} ─────────────────────────────────────────╮
│                                                                    │
│  Entry: {entry_conditions or "None configured"}                    │
│  Exit: {exit_conditions or "None configured"}                      │
╰────────────────────────────────────────────────────────────────────╯
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
╭─── ✅ GATES UPDATED ───────────────────────────────────────────────╮
│                                                                    │
│  Version: {version}                                                │
│  Entry: {entry_summary}                                            │
│  Exit: {exit_summary}                                              │
╰────────────────────────────────────────────────────────────────────╯
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

Display confirmation:

```
╭─── ✅ SETTING UPDATED ─────────────────────────────────────────────╮
│                                                                    │
│  {setting}: {oldValue} → {newValue}                                │
╰────────────────────────────────────────────────────────────────────╯
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
╭─── ✅ CONFIGURATION SAVED ─────────────────────────────────────────╮
│                                                                    │
│  Changes:                                                          │
│  - {change1}                                                       │
│  - {change2}                                                       │
╰────────────────────────────────────────────────────────────────────╯
```

If no changes:

```
╭─── ℹ️ NO CHANGES ──────────────────────────────────────────────────╮
│                                                                    │
│  Configuration unchanged.                                          │
╰────────────────────────────────────────────────────────────────────╯
```

</step>

</process>

<configuration_reference>

| Setting | Type | Default | Description |
|---------|------|---------|-------------|
| `trust` | string | "medium" | Trust level (controls review and autonomy) |
| `verify` | string | "changed" | What verification runs before commits |
| `curiosity` | string | "low" | Exploration beyond immediate task |
| `patience` | string | "high" | When to act on discoveries |
| `autoRemoveWorktrees` | boolean | true | Auto-remove worktrees |
| `completionWorkflow` | string | "merge" | Task completion behavior (merge or PR) |

**Context Limits:** Fixed values, not configurable. See agent-architecture.md § Context Limit Constants.

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

### Completion Workflow Values
- `merge` — Merge task branch directly to base branch after approval (default).
- `pr` — Create a pull request instead of merging directly.

</configuration_reference>

<success_criteria>

- [ ] Current configuration displayed
- [ ] User navigated wizard successfully
- [ ] Settings updated in cat-config.json using safe jq pattern
- [ ] Version gates viewable and editable via wizard
- [ ] Gate changes saved to version PLAN.md files
- [ ] Changes confirmed with before/after values

</success_criteria>
