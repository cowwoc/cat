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

<process>

<step name="read-config">

**Read current configuration:**

```bash
cat .claude/cat/cat-config.json
```

If file doesn't exist, inform user to run `/cat:init` first.

</step>

<step name="box-rendering">

**Use centralized box rendering scripts for all displays.**

LLMs cannot reliably calculate character-level padding for Unicode text (M142).
All boxes MUST be rendered using the scripts in `${CLAUDE_PLUGIN_ROOT}/scripts/`.

**Available config box scripts:**

```bash
# config-box.sh - Renders all /cat:config boxes
"${CLAUDE_PLUGIN_ROOT}/scripts/config-box.sh" BOX_TYPE [ARGS...]

# Box types:
#   settings CONTEXT_LIMIT TARGET_USAGE TRUST VERIFY CURIOSITY PATIENCE AUTO_REMOVE
#   behavior TRUST VERIFY CURIOSITY PATIENCE
#   trust CURRENT_LEVEL
#   verify CURRENT_LEVEL
#   curiosity CURRENT_LEVEL
#   patience CURRENT_LEVEL
#   version-gates
#   gates-for VERSION ENTRY_CONDITIONS EXIT_CONDITIONS
#   no-gates VERSION
#   gates-updated VERSION ENTRY_SUMMARY EXIT_SUMMARY
#   setting-updated SETTING OLD_VALUE NEW_VALUE
#   saved CHANGES...
#   no-changes
```

**Workflow (MANDATORY):**
1. Run the box script with output to temp file: `> /tmp/config-box.txt`
2. Use the **Read tool** (NOT bash cat) to read `/tmp/config-box.txt`
3. **Output the box contents as text in your response** (tool output alone is NOT visible to users)

**Anti-pattern (M140):**
```bash
# ❌ WRONG - cat output only appears in tool result, not visible to user
cat /tmp/config-box.txt
# Then immediately calling AskUserQuestion

# ✅ CORRECT - Read tool, then output text
Use Read tool on /tmp/config-box.txt
Then in your response, output the box contents before calling AskUserQuestion
```

**Anti-pattern (M149): NEVER manually type box characters.**
```
# ❌ WRONG - manually typing box in text output
╭─── ⚙️ SETTINGS ───────────────────────────────────╮
│  Some setting value                               │  ← LLM cannot align these
╰───────────────────────────────────────────────────╯

# ✅ CORRECT - use config-box.sh script, then Read and output
"${CLAUDE_PLUGIN_ROOT}/scripts/config-box.sh" settings ... > /tmp/config-box.txt
# Then Read tool on /tmp/config-box.txt
```
Why: LLMs miscalculate Unicode character widths (emojis, special chars). ALWAYS use scripts.

</step>

<step name="display-settings">

**MANDATORY (M130/A021) - Display-Before-Prompt Protocol:**

BLOCKING REQUIREMENT: You MUST output a visual display box BEFORE calling AskUserQuestion.

**Verification sequence:**
1. Have I output a settings/info box in THIS step? If NO → output box first
2. Only AFTER box is displayed → call AskUserQuestion
3. If you find yourself about to call AskUserQuestion without a preceding box → STOP

**Why this matters:** Users need visual context before making choices. Jumping directly to
prompts without display creates confusion and poor UX.

**Display settings screen:**

Render settings box using script:

```bash
"${CLAUDE_PLUGIN_ROOT}/scripts/config-box.sh" settings {trust} {verify} {curiosity} {patience} {autoRemove} > /tmp/config-box.txt
```

Then use Read tool on `/tmp/config-box.txt` and output contents VERBATIM.

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
  - label: "📊 Version Gates"
    description: "Entry/exit conditions for versions"

If user selects "Other" and types "done", "exit", or "back", proceed to exit step.

**Note:** Context limits are fixed and not configurable. See agent-architecture.md § Context Limit Constants.

</step>


<step name="cat-behavior">

**🐱 CAT Behavior selection:**

**MANDATORY (M137) - Display behavior summary BEFORE prompting:**

Render behavior box using script:

```bash
"${CLAUDE_PLUGIN_ROOT}/scripts/config-box.sh" behavior {trust} {verify} {curiosity} {patience} > /tmp/config-box.txt
```

Then use Read tool on `/tmp/config-box.txt` and output contents VERBATIM.

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

Render trust box using script:

```bash
"${CLAUDE_PLUGIN_ROOT}/scripts/config-box.sh" trust {current_trust} > /tmp/config-box.txt
```

Then use Read tool on `/tmp/config-box.txt` and output contents VERBATIM.

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

Render verify box using script:

```bash
"${CLAUDE_PLUGIN_ROOT}/scripts/config-box.sh" verify {current_verify} > /tmp/config-box.txt
```

Then use Read tool on `/tmp/config-box.txt` and output contents VERBATIM.

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

Render curiosity box using script:

```bash
"${CLAUDE_PLUGIN_ROOT}/scripts/config-box.sh" curiosity {current_curiosity} > /tmp/config-box.txt
```

Then use Read tool on `/tmp/config-box.txt` and output contents VERBATIM.

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

Render patience box using script:

```bash
"${CLAUDE_PLUGIN_ROOT}/scripts/config-box.sh" patience {current_patience} > /tmp/config-box.txt
```

Then use Read tool on `/tmp/config-box.txt` and output contents VERBATIM.

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

Render terminal-width box using script:

```bash
"${CLAUDE_PLUGIN_ROOT}/scripts/config-box.sh" terminal-width {current_terminalWidth} > /tmp/config-box.txt
```

Then use Read tool on `/tmp/config-box.txt` and output contents VERBATIM.

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

<step name="version-gates">

**📊 Version Gates configuration:**

Render gate overview using script:

```bash
"${CLAUDE_PLUGIN_ROOT}/scripts/config-box.sh" version-gates > /tmp/config-box.txt
```

Then use Read tool on `/tmp/config-box.txt` and output contents VERBATIM.

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

Extract the `## Gates` section and render using script:

```bash
# If gates exist (pipe-separate multiple conditions)
"${CLAUDE_PLUGIN_ROOT}/scripts/config-box.sh" gates-for {version} "{entry_conditions}" "{exit_conditions}" > /tmp/config-box.txt
```

If no gates section exists:

```bash
"${CLAUDE_PLUGIN_ROOT}/scripts/config-box.sh" no-gates {version} > /tmp/config-box.txt
```

Then use Read tool on `/tmp/config-box.txt` and output contents VERBATIM.

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

Render confirmation using script:

```bash
"${CLAUDE_PLUGIN_ROOT}/scripts/config-box.sh" gates-updated {version} "{entry_summary}" "{exit_summary}" > /tmp/config-box.txt
```

Then use Read tool on `/tmp/config-box.txt` and output contents VERBATIM.

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

Render confirmation using script:

```bash
"${CLAUDE_PLUGIN_ROOT}/scripts/config-box.sh" setting-updated "{setting}" "{oldValue}" "{newValue}" > /tmp/config-box.txt
```

Then use Read tool on `/tmp/config-box.txt` and output contents VERBATIM.

**After confirming**: Return to the **parent menu** and re-display its options.

Examples:
- Changed "Trust" → return to CAT Behavior menu
- Changed "Context window size" → return to Context Limits menu
- Changed "Cleanup" → return to Cleanup/Gates menu

</step>

<step name="exit">

**Exit screen:**

If changes were made, render using script:

```bash
"${CLAUDE_PLUGIN_ROOT}/scripts/config-box.sh" saved "{change1}" "{change2}" ... > /tmp/config-box.txt
```

If no changes:

```bash
"${CLAUDE_PLUGIN_ROOT}/scripts/config-box.sh" no-changes > /tmp/config-box.txt
```

Then use Read tool on `/tmp/config-box.txt` and output contents VERBATIM.

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

</configuration_reference>

<success_criteria>

- [ ] Current configuration displayed
- [ ] User navigated wizard successfully
- [ ] Settings updated in cat-config.json using safe jq pattern
- [ ] Version gates viewable and editable via wizard
- [ ] Gate changes saved to version PLAN.md files
- [ ] Changes confirmed with before/after values

</success_criteria>
