---
name: update-preferences
description: Update adventure style preferences for the project
---

# Update Preferences Skill

**Purpose**: Modify development style preferences that shape how CAT makes autonomous decisions.

## When to Use

- Want to change from conservative to aggressive approach mid-project
- Need to adjust stakeholder review frequency
- Want more/less opportunistic refactoring

## Workflow

### 1. Display Current Preferences

```bash
# Read current preferences
cat .claude/cat/cat-config.json | jq '.adventureMode.preferences'
```

Display:
```
╔═══════════════════════════════════════════════════════════════════╗
║  🎮 CURRENT ADVENTURE STYLE                                       ║
╠═══════════════════════════════════════════════════════════════════╣
║                                                                   ║
║  Approach:          [current value]                               ║
║  Stakeholder Review: [current value]                              ║
║  Refactoring:       [current value]                               ║
║                                                                   ║
╚═══════════════════════════════════════════════════════════════════╝
```

### 2. Ask What to Change

AskUserQuestion: header="Update", question="Which preference to update?", options=[
  "Development approach",
  "Stakeholder review frequency",
  "Refactoring appetite",
  "All preferences"
]

### 3. Present Options Based on Selection

**If approach:**
AskUserQuestion: header="Approach", question="Development approach?", options=[
  "🛡️ Conservative - minimal changes, thorough testing, avoid risk",
  "⚖️ Balanced - pragmatic tradeoffs, reasonable coverage",
  "⚔️ Aggressive - comprehensive improvements, move fast, refactor freely"
]

**If stakeholder review:**
AskUserQuestion: header="Review", question="When should CAT trigger stakeholder review?", options=[
  "Always before merging",
  "Only for high-risk or cross-module changes",
  "Never - I'll request when needed"
]

**If refactoring:**
AskUserQuestion: header="Refactoring", question="Refactoring appetite?", options=[
  "Avoid - only fix what's broken",
  "Opportunistic - clean up adjacent code when natural",
  "Eager - improve code quality proactively"
]

### 4. Update Configuration

Update `.claude/cat/cat-config.json`:
```bash
# Use jq to update the specific preference
jq '.adventureMode.preferences.approach = "aggressive"' .claude/cat/cat-config.json > tmp.json
mv tmp.json .claude/cat/cat-config.json
```

Update `PROJECT.md` User Preferences section to match.

### 5. Confirm Change

Display:
```
╔═══════════════════════════════════════════════════════════════════╗
║  ✨ PREFERENCES UPDATED                                           ║
╠═══════════════════════════════════════════════════════════════════╣
║                                                                   ║
║  [preference]: [old value] → [new value]                          ║
║                                                                   ║
║  This will affect how CAT makes decisions going forward.          ║
║                                                                   ║
╚═══════════════════════════════════════════════════════════════════╝
```

## Preference Values Reference

| Preference | Values | Effect |
|------------|--------|--------|
| **approach** | conservative, balanced, aggressive | Influences approach recommendations at forks |
| **stakeholderReview** | always, high-risk-only, never | Controls when multi-perspective review triggers |
| **refactoring** | avoid, opportunistic, eager | Determines cleanup behavior on adjacent code |

## Success Criteria

- [ ] Current preferences displayed
- [ ] User selected preference(s) to change
- [ ] cat-config.json updated
- [ ] PROJECT.md updated
- [ ] Confirmation shown with old → new values
