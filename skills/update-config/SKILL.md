---
name: cat:update-config
description: Interactive wizard to customize your CAT adventure settings
---

# Update Config Wizard

**Purpose**: Customize how CAT guides your development adventure.

## Workflow

### 1. Display Current Configuration

```bash
cat .claude/cat/cat-config.json
```

Display the adventure settings screen:

```
    ╔══════════════════════════════════════════════════════════════╗
    ║                                                              ║
    ║             ⚙️  ADVENTURE SETTINGS                            ║
    ║                                                              ║
    ╠══════════════════════════════════════════════════════════════╣
    ║                                                              ║
    ║   🎮 GAME MODE                                                ║
    ║   ┌────────────────────────────────────────────────────┐     ║
    ║   │  {yoloMode ? "⚡ YOLO" : "🛡️ Interactive"}          │     ║
    ║   └────────────────────────────────────────────────────┘     ║
    ║                                                              ║
    ║   🧠 CONTEXT LIMITS                                           ║
    ║   ┌────────────────────────────────────────────────────┐     ║
    ║   │  Window:  {contextLimit} tokens                    │     ║
    ║   │  Target:  {targetContextUsage * 100}% before split │     ║
    ║   └────────────────────────────────────────────────────┘     ║
    ║                                                              ║
    ║   ⚔️ PLAY STYLE                                               ║
    ║   ┌────────────────────────────────────────────────────┐     ║
    ║   │  Approach:    {approach || "balanced"}             │     ║
    ║   │  Reviews:     {stakeholderReview || "high-risk"}   │     ║
    ║   │  Refactoring: {refactoring || "opportunistic"}     │     ║
    ║   └────────────────────────────────────────────────────┘     ║
    ║                                                              ║
    ║   🧹 CLEANUP                                                  ║
    ║   ┌────────────────────────────────────────────────────┐     ║
    ║   │  Auto-cleanup: {autoCleanupWorktrees ? "On" : "Off"}│    ║
    ║   └────────────────────────────────────────────────────┘     ║
    ║                                                              ║
    ╚══════════════════════════════════════════════════════════════╝
```

### 2. Main Menu

Use AskUserQuestion:
- header: "Settings"
- question: "What would you like to configure?"
- options:
  - "🎮 Game Mode - How CAT handles approvals"
  - "🧠 Context Limits - Token thresholds"
  - "⚔️ Play Style - Development approach"
  - "🧹 Cleanup - Worktree management"

### 3. Handle Each Selection

#### 🎮 Game Mode

Display:
```
    ╔══════════════════════════════════════════════════════════════╗
    ║                                                              ║
    ║                    🎮 CHOOSE YOUR MODE                        ║
    ║                                                              ║
    ╠══════════════════════════════════════════════════════════════╣
    ║                                                              ║
    ║   🛡️ INTERACTIVE                                              ║
    ║   ─────────────────────────────────────────────────────────  ║
    ║   CAT pauses at key moments for your approval.               ║
    ║   You review changes before they merge to main.              ║
    ║   Best for: Learning CAT, important projects                 ║
    ║                                                              ║
    ║   ⚡ YOLO                                                      ║
    ║   ─────────────────────────────────────────────────────────  ║
    ║   CAT runs autonomously without stopping.                    ║
    ║   Tasks complete and merge automatically.                    ║
    ║   Best for: Trusted workflows, batch processing              ║
    ║                                                              ║
    ╚══════════════════════════════════════════════════════════════╝
```

AskUserQuestion:
- header: "Mode"
- question: "Select your game mode:"
- options:
  - "🛡️ Interactive - Approval gates before merging (Recommended)"
  - "⚡ YOLO - Autonomous execution, no gates"

Map: Interactive → `yoloMode: false`, YOLO → `yoloMode: true`

#### 🧠 Context Limits

Display:
```
    ╔══════════════════════════════════════════════════════════════╗
    ║                                                              ║
    ║                  🧠 CONTEXT MANAGEMENT                        ║
    ║                                                              ║
    ╠══════════════════════════════════════════════════════════════╣
    ║                                                              ║
    ║   Context Window                                             ║
    ║   ─────────────────────────────────────────────────────────  ║
    ║   Total tokens available for each agent.                     ║
    ║   Current: {contextLimit} tokens                             ║
    ║                                                              ║
    ║   Target Usage                                               ║
    ║   ─────────────────────────────────────────────────────────  ║
    ║   When to trigger task decomposition.                        ║
    ║   Current: {targetContextUsage * 100}%                       ║
    ║                                                              ║
    ╚══════════════════════════════════════════════════════════════╝
```

AskUserQuestion:
- header: "Context"
- question: "What would you like to adjust?"
- options:
  - "Context window size"
  - "Target usage threshold"
  - "← Back to main menu"

**For context limit:**
AskUserQuestion:
- header: "Window"
- question: "Select context window size:"
- options:
  - "200,000 tokens - Claude Opus (Recommended)"
  - "128,000 tokens - Claude Sonnet"
  - "Custom value"

**For target usage:**
AskUserQuestion:
- header: "Threshold"
- question: "When should CAT split large tasks?"
- options:
  - "30% - Conservative, lots of headroom"
  - "40% - Balanced (Recommended)"
  - "50% - Aggressive, maximize task size"

#### ⚔️ Play Style

Display:
```
    ╔══════════════════════════════════════════════════════════════╗
    ║                                                              ║
    ║                   ⚔️ CHOOSE YOUR STYLE                        ║
    ║                                                              ║
    ╠══════════════════════════════════════════════════════════════╣
    ║                                                              ║
    ║   Your play style shapes how CAT makes decisions             ║
    ║   when multiple paths are available.                         ║
    ║                                                              ║
    ║   Current loadout:                                           ║
    ║   ┌────────────────────────────────────────────────────┐     ║
    ║   │  🗡️ Approach:    {approach}                        │     ║
    ║   │  👁️ Reviews:     {stakeholderReview}               │     ║
    ║   │  🔧 Refactoring: {refactoring}                     │     ║
    ║   └────────────────────────────────────────────────────┘     ║
    ║                                                              ║
    ╚══════════════════════════════════════════════════════════════╝
```

AskUserQuestion:
- header: "Style"
- question: "Which aspect to customize?"
- options:
  - "🗡️ Approach - Risk tolerance"
  - "👁️ Reviews - Quality gates"
  - "🔧 Refactoring - Code cleanup"
  - "← Back to main menu"

**Approach options:**
```
    ╔══════════════════════════════════════════════════════════════╗
    ║                                                              ║
    ║                    🗡️ DEVELOPMENT APPROACH                    ║
    ║                                                              ║
    ╠══════════════════════════════════════════════════════════════╣
    ║                                                              ║
    ║   🛡️ CONSERVATIVE                                             ║
    ║   Minimal changes. Thorough testing. Avoid risk.             ║
    ║   "Measure twice, cut once."                                 ║
    ║                                                              ║
    ║   ⚖️ BALANCED                                                 ║
    ║   Pragmatic tradeoffs. Reasonable coverage.                  ║
    ║   "Get it right, keep it moving."                            ║
    ║                                                              ║
    ║   ⚔️ AGGRESSIVE                                               ║
    ║   Comprehensive improvements. Move fast.                     ║
    ║   "Fortune favors the bold."                                 ║
    ║                                                              ║
    ╚══════════════════════════════════════════════════════════════╝
```

AskUserQuestion:
- header: "Approach"
- question: "Choose your approach:"
- options:
  - "🛡️ Conservative"
  - "⚖️ Balanced (Recommended)"
  - "⚔️ Aggressive"

**Review options:**
AskUserQuestion:
- header: "Reviews"
- question: "When should stakeholders review?"
- options:
  - "Always - Every task gets reviewed"
  - "High-risk only - Cross-module or risky changes (Recommended)"
  - "Never - I'll request when needed"

**Refactoring options:**
AskUserQuestion:
- header: "Refactor"
- question: "How aggressively should CAT clean up code?"
- options:
  - "Avoid - Only fix what's explicitly broken"
  - "Opportunistic - Clean adjacent code naturally (Recommended)"
  - "Eager - Proactively improve quality"

#### 🧹 Cleanup

Display:
```
    ╔══════════════════════════════════════════════════════════════╗
    ║                                                              ║
    ║                    🧹 WORKTREE CLEANUP                        ║
    ║                                                              ║
    ╠══════════════════════════════════════════════════════════════╣
    ║                                                              ║
    ║   After completing a task, CAT can automatically             ║
    ║   remove the worktree and branch.                            ║
    ║                                                              ║
    ║   Current: {autoCleanupWorktrees ? "Auto-cleanup ON" : "Manual cleanup"}
    ║                                                              ║
    ╚══════════════════════════════════════════════════════════════╝
```

AskUserQuestion:
- header: "Cleanup"
- question: "Worktree cleanup behavior:"
- options:
  - "🧹 Auto-cleanup - Remove after task completion (Recommended)"
  - "📦 Keep - Preserve for manual inspection"

### 4. Update Configuration

```bash
# Safe jq update pattern
jq '.settingName = "newValue"' .claude/cat/cat-config.json > .claude/cat/cat-config.json.tmp \
  && mv .claude/cat/cat-config.json.tmp .claude/cat/cat-config.json
```

### 5. Confirm Change

```
    ┌──────────────────────────────────────────────────────────┐
    │  ✓ Setting updated                                       │
    │                                                          │
    │    {setting}: {oldValue} → {newValue}                    │
    │                                                          │
    └──────────────────────────────────────────────────────────┘
```

AskUserQuestion:
- header: "Continue"
- question: "Configure another setting?"
- options:
  - "Yes - Back to settings menu"
  - "Done - Save and exit"

### 6. Exit Screen

```
    ╔══════════════════════════════════════════════════════════════╗
    ║                                                              ║
    ║              ✨ CONFIGURATION SAVED                           ║
    ║                                                              ║
    ╠══════════════════════════════════════════════════════════════╣
    ║                                                              ║
    ║   Changes applied:                                           ║
    ║   • {setting1}: {old} → {new}                                ║
    ║   • {setting2}: {old} → {new}                                ║
    ║                                                              ║
    ║   Your adventure continues with new settings!                ║
    ║                                                              ║
    ╚══════════════════════════════════════════════════════════════╝
```

If no changes:
```
    ┌──────────────────────────────────────────────────────────┐
    │  No changes made. Settings unchanged.                    │
    └──────────────────────────────────────────────────────────┘
```

## Configuration Reference

| Setting | Type | Default | Description |
|---------|------|---------|-------------|
| `yoloMode` | boolean | false | Skip approval gates |
| `contextLimit` | number | 200000 | Context window size |
| `targetContextUsage` | number | 0.4 | Decomposition threshold |
| `approach` | string | "balanced" | Risk tolerance |
| `stakeholderReview` | string | "high-risk-only" | Review frequency |
| `refactoring` | string | "opportunistic" | Cleanup behavior |
| `autoCleanupWorktrees` | boolean | true | Auto-remove worktrees |

## Success Criteria

- [ ] Current configuration displayed in adventure theme
- [ ] User navigated wizard successfully
- [ ] Settings updated in cat-config.json
- [ ] Changes confirmed with before/after values
