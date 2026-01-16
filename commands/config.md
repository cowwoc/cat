---
name: cat:config
description: Interactive wizard to customize your CAT adventure settings
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
╔═════════════════════════════════════════════════════════════╗
║                                                             ║
║            ⚙️  ADVENTURE SETTINGS                           ║
║                                                             ║
╠═════════════════════════════════════════════════════════════╣
║                                                             ║
║  🎮 GAME MODE                                               ║
║  ┌────────────────────────────────────────────────────┐     ║
║  │  {yoloMode ? "⚡ YOLO" : "🛡️ Interactive"}         │     ║
║  └────────────────────────────────────────────────────┘     ║
║                                                             ║
║  🧠 CONTEXT LIMITS                                          ║
║  ┌────────────────────────────────────────────────────┐     ║
║  │  Window:  {contextLimit} tokens                    │     ║
║  │  Target:  {targetContextUsage}% before split       │     ║
║  └────────────────────────────────────────────────────┘     ║
║                                                             ║
║  ⚔️ PLAY STYLE                                              ║
║  ┌────────────────────────────────────────────────────┐     ║
║  │  Approach:    {approach || "balanced"}             │     ║
║  │  Reviews:     {stakeholderReview || "high-risk"}   │     ║
║  │  Refactoring: {refactoring || "opportunistic"}     │     ║
║  └────────────────────────────────────────────────────┘     ║
║                                                             ║
║  🧹 CLEANUP                                                 ║
║  ┌────────────────────────────────────────────────────┐     ║
║  │  Auto-cleanup: {autoCleanupWorktrees ? "On":"Off"} │     ║
║  └────────────────────────────────────────────────────┘     ║
║                                                             ║
║  📊 VERSION GATES                                           ║
║  ┌────────────────────────────────────────────────────┐     ║
║  │  Configure entry/exit conditions for versions      │     ║
║  └────────────────────────────────────────────────────┘     ║
║                                                             ║
╚═════════════════════════════════════════════════════════════╝
```

</step>

<step name="main-menu">

**Present main menu using AskUserQuestion:**

- header: "Settings"
- question: "What would you like to configure?"
- options:
  - label: "🎮 Game Mode"
    description: "How CAT handles approvals"
  - label: "🧠 Context Limits"
    description: "Token thresholds"
  - label: "⚔️ Play Style"
    description: "Development approach"
  - label: "🧹 Cleanup"
    description: "Worktree management"
  - label: "📊 Version Gates"
    description: "Entry/exit conditions for versions"

If user selects "Other" and types "done", "exit", or "back", proceed to exit step.

</step>

<step name="game-mode">

**🎮 Game Mode selection:**

Display:
```
╔═════════════════════════════════════════════════════════════╗
║                                                             ║
║                   🎮 CHOOSE YOUR MODE                       ║
║                                                             ║
╠═════════════════════════════════════════════════════════════╣
║                                                             ║
║  🛡️ INTERACTIVE                                             ║
║  ─────────────────────────────────────────────────────────  ║
║  CAT pauses at key moments for your approval.               ║
║  You review changes before they merge to main.              ║
║  Best for: Learning CAT, important projects                 ║
║                                                             ║
║  ⚡ YOLO                                                    ║
║  ─────────────────────────────────────────────────────────  ║
║  CAT runs autonomously without stopping.                    ║
║  Tasks complete and merge automatically.                    ║
║  Best for: Trusted workflows, batch processing              ║
║                                                             ║
╚═════════════════════════════════════════════════════════════╝
```

AskUserQuestion:
- header: "Mode"
- question: "Select your game mode:"
- options:
  - label: "🛡️ Interactive (Recommended)"
    description: "Approval gates before merging"
  - label: "⚡ YOLO"
    description: "Autonomous execution, no gates"
  - label: "← Back"
    description: "Return to main menu"

Map selection: Interactive → `yoloMode: false`, YOLO → `yoloMode: true`

</step>

<step name="context-limits">

**🧠 Context Limits selection:**

Display current settings, then AskUserQuestion:
- header: "Context"
- question: "What would you like to adjust?"
- options:
  - label: "Context window size"
    description: "Total tokens available"
  - label: "Target usage threshold"
    description: "When to trigger decomposition"
  - label: "← Back"
    description: "Return to main menu"

**For context limit:**
- "200,000 tokens - Claude Opus (Recommended)"
- "128,000 tokens - Claude Sonnet"
- "Custom value"

**For target usage:**
- "30% - Conservative, lots of headroom"
- "40% - Balanced (Recommended)"
- "50% - Aggressive, maximize task size"

</step>

<step name="play-style">

**⚔️ Play Style selection:**

AskUserQuestion:
- header: "Style"
- question: "Which setting would you like to adjust?"
- options:
  - label: "Approach"
    description: "Risk tolerance level"
  - label: "Reviews"
    description: "When to request stakeholder review"
  - label: "Refactoring"
    description: "Code cleanup behavior"
  - label: "← Back"
    description: "Return to main menu"

**Approach options:**
- "🛡️ Conservative" - Minimal changes, thorough testing
- "⚖️ Balanced (Recommended)" - Pragmatic tradeoffs
- "⚔️ Aggressive" - Comprehensive improvements

**Review options:**
- "Always - Every task gets reviewed"
- "High-risk only (Recommended)" - Cross-module or risky changes
- "Never - I'll request when needed"

**Refactoring options:**
- "Avoid - Only fix what's explicitly broken"
- "Opportunistic (Recommended)" - Clean adjacent code naturally
- "Eager - Proactively improve quality"

</step>

<step name="cleanup">

**🧹 Cleanup selection:**

AskUserQuestion:
- header: "Cleanup"
- question: "Worktree cleanup behavior:"
- options:
  - label: "🧹 Auto-cleanup (Recommended)"
    description: "Remove after task completion"
  - label: "📦 Keep"
    description: "Preserve for manual inspection"
  - label: "← Back"
    description: "Return to main menu"

Map: Auto-cleanup → `autoCleanupWorktrees: true`, Keep → `autoCleanupWorktrees: false`

</step>

<step name="version-gates">

**📊 Version Gates configuration:**

Display current gate overview:
```
╔═════════════════════════════════════════════════════════════╗
║                                                             ║
║                   📊 VERSION GATES                          ║
║                                                             ║
╠═════════════════════════════════════════════════════════════╣
║                                                             ║
║  Gates control when work can start and when it's done.      ║
║  Each version can have entry (start) and exit (done) gates. ║
║                                                             ║
║  Major gates are inherited by all minor versions.           ║
║                                                             ║
╚═════════════════════════════════════════════════════════════╝
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
┌─────────────────────────────────────────────────────────────┐
│  📊 Gates for v{version}                                    │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  ENTRY (when can work start?):                              │
│  • {condition 1}                                            │
│  • {condition 2}                                            │
│                                                             │
│  EXIT (when is it done?):                                   │
│  • {condition 1}                                            │
│  • {condition 2}                                            │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

If no gates section exists, display:
```
┌─────────────────────────────────────────────────────────────┐
│  ⚠️ No gates configured for v{version}                      │
│                                                             │
│  Default behavior applies:                                  │
│  • Entry: Previous version must complete                    │
│  • Exit: All tasks must complete                            │
│                                                             │
└─────────────────────────────────────────────────────────────┘
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
  - "No prerequisites" - clear all entry conditions
  - "Custom condition" - freeform text

If "Specific task(s) complete":
- Ask: "Which task(s)? (e.g., 0.5-design-review, comma-separated)"

If "Specific version(s) complete":
- Ask: "Which version(s)? (e.g., 0.3, 0.4, comma-separated)"

If "Custom condition":
- Ask: "Describe the custom entry condition:"

**Step 4b: Edit exit gate**

Use AskUserQuestion:
- header: "Exit Gate"
- question: "Select exit conditions (current: {current conditions}):"
- multiSelect: true
- options:
  - "All tasks complete" - every task in version done
  - "Specific task(s) complete" - only named tasks required
  - "Tests passing" - test suite must pass
  - "Code review complete" - review sign-off
  - "Manual sign-off" - explicit approval
  - "No exit criteria" - clear all exit conditions
  - "Custom condition" - freeform text

If "Specific task(s) complete":
- Ask: "Which task(s)? (comma-separated)"

If "Custom condition":
- Ask: "Describe the custom exit condition:"

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
┌─────────────────────────────────────────────────────────────┐
│  ✓ Gates updated for v{version}                             │
│                                                             │
│  Entry: {summary of entry conditions}                       │
│  Exit:  {summary of exit conditions}                        │
│                                                             │
└─────────────────────────────────────────────────────────────┘
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
┌─────────────────────────────────────────────────────────────┐
│  ✓ Setting updated                                          │
│                                                             │
│    {setting}: {oldValue} → {newValue}                       │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

**After confirming**: Return to the **parent menu** and re-display its options.

Examples:
- Changed "Refactoring" → return to Play Style menu (Approach, Reviews, Refactoring, ← Back)
- Changed "Context window size" → return to Context Limits menu
- Changed "Game Mode" → return to main menu (no parent submenu)
- Changed "Cleanup" → return to main menu (no parent submenu)

</step>

<step name="exit">

**Exit screen:**

If changes were made:
```
╔═════════════════════════════════════════════════════════════╗
║                                                             ║
║             ✨ CONFIGURATION SAVED                          ║
║                                                             ║
╠═════════════════════════════════════════════════════════════╣
║                                                             ║
║  Changes applied:                                           ║
║  • {setting1}: {old} → {new}                                ║
║  • {setting2}: {old} → {new}                                ║
║                                                             ║
║  Your adventure continues with new settings!                ║
║                                                             ║
╚═════════════════════════════════════════════════════════════╝
```

If no changes:
```
┌─────────────────────────────────────────────────────────────┐
│  No changes made. Settings unchanged.                       │
└─────────────────────────────────────────────────────────────┘
```

</step>

</process>

<configuration_reference>

| Setting | Type | Default | Description |
|---------|------|---------|-------------|
| `yoloMode` | boolean | false | Skip approval gates |
| `contextLimit` | number | 200000 | Context window size |
| `targetContextUsage` | number | 40 | Decomposition threshold (%) |
| `approach` | string | "balanced" | Risk tolerance |
| `stakeholderReview` | string | "high-risk-only" | Review frequency |
| `refactoring` | string | "opportunistic" | Cleanup behavior |
| `autoCleanupWorktrees` | boolean | true | Auto-remove worktrees |

</configuration_reference>

<success_criteria>

- [ ] Current configuration displayed in adventure theme
- [ ] User navigated wizard successfully
- [ ] Settings updated in cat-config.json using safe jq pattern
- [ ] Version gates viewable and editable via wizard
- [ ] Gate changes saved to version PLAN.md files
- [ ] Changes confirmed with before/after values

</success_criteria>
