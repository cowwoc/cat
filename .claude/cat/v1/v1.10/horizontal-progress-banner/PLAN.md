# Plan: horizontal-progress-banner

## Problem
The current progress banner in work.md uses a vertical layout that doesn't match the originally
specified design. The implementation diverged from the planned "Style E" horizontal connected
layout to a simpler vertical list.

## Current State
```
▸ Preparing ◆
▹ Executing
▹ Reviewing
▹ Merging

──────────────────────────────────────────────────────────────────
```

## Target State
Horizontal layout with phases connected by lines and circle-based status indicators:
```
🐱 CAT › 2.0-flexible-version-schema
────────────────────────────────────────────────────────────────

● Preparing ────── ◉ Executing ────── ○ Reviewing ────── ○ Merging
                      45K tokens
```

## Satisfies
None - UX improvement task

## Visual Specification

### Symbols
| Symbol | Meaning |
|--------|---------|
| `○` | Pending (empty circle) |
| `●` | Complete (filled circle) |
| `◉` | Current/Active (see alternatives below) |
| `✗` | Failed |

### Current/Active Symbol Alternatives

The following symbols are candidates for indicating the current/active phase.
Implementation should choose one that renders well across terminals.

**Using `◉` (fisheye):**
```
🐱 CAT › 2.0-flexible-version-schema
────────────────────────────────────────────────────────────────

● Preparing ────── ◉ Executing ────── ○ Reviewing ────── ○ Merging
                      45K tokens
```

**Using `⊙` (circled dot):**
```
🐱 CAT › 2.0-flexible-version-schema
────────────────────────────────────────────────────────────────

● Preparing ────── ⊙ Executing ────── ○ Reviewing ────── ○ Merging
                      45K tokens
```

**Using `◐` (half circle):**
```
🐱 CAT › 2.0-flexible-version-schema
────────────────────────────────────────────────────────────────

● Preparing ────── ◐ Executing ────── ○ Reviewing ────── ○ Merging
                      45K tokens
```

**Using `◕` (mostly filled):**
```
🐱 CAT › 2.0-flexible-version-schema
────────────────────────────────────────────────────────────────

● Preparing ────── ◕ Executing ────── ○ Reviewing ────── ○ Merging
                      45K tokens
```

**Using `▶` (play):**
```
🐱 CAT › 2.0-flexible-version-schema
────────────────────────────────────────────────────────────────

● Preparing ────── ▶ Executing ────── ○ Reviewing ────── ○ Merging
                      45K tokens
```

**Using `►` (pointer):**
```
🐱 CAT › 2.0-flexible-version-schema
────────────────────────────────────────────────────────────────

● Preparing ────── ► Executing ────── ○ Reviewing ────── ○ Merging
                      45K tokens
```

### State Examples

**Starting state:**
```
🐱 CAT › 2.0-flexible-version-schema
────────────────────────────────────────────────────────────────

○ Preparing ────── ○ Executing ────── ○ Reviewing ────── ○ Merging
```

**During Preparing:**
```
🐱 CAT › 2.0-flexible-version-schema
────────────────────────────────────────────────────────────────

◉ Preparing ────── ○ Executing ────── ○ Reviewing ────── ○ Merging
```

**During Executing:**
```
🐱 CAT › 2.0-flexible-version-schema
────────────────────────────────────────────────────────────────

● Preparing ────── ◉ Executing ────── ○ Reviewing ────── ○ Merging
                      45K tokens
```

**During Reviewing:**
```
🐱 CAT › 2.0-flexible-version-schema
────────────────────────────────────────────────────────────────

● Preparing ────── ● Executing ────── ◉ Reviewing ────── ○ Merging
                      75K · 3 commits
```

**Complete:**
```
🐱 CAT › 2.0-flexible-version-schema ✓
────────────────────────────────────────────────────────────────

● Preparing ────── ● Executing ────── ● Reviewing ────── ● Merging
                      75K · 3 commits    approved          → main
```

**Failed:**
```
🐱 CAT › 2.0-flexible-version-schema ✗
────────────────────────────────────────────────────────────────

● Preparing ────── ● Executing ────── ✗ Reviewing ────── ○ Merging
                      75K · 3 commits    BLOCKED: security concern
```

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** Unicode symbol rendering varies across terminals
- **Mitigation:** Test on common terminals (iTerm2, VS Code, Windows Terminal); choose symbols with wide support

## Files to Modify
- `plugin/commands/work.md` - Update `<progress_output>` section with new horizontal layout
- `plugin/scripts/work-progress.sh` - Update rendering functions for horizontal format
- `plugin/.claude/cat/workflows/work.md` - Sync progress display documentation

## Acceptance Criteria
- [ ] Header shows cat emoji and task name: `🐱 CAT › {task-name}`
- [ ] Phases displayed horizontally connected with `──────` lines
- [ ] Pending phases show empty circle `○`
- [ ] Completed phases show filled circle `●`
- [ ] Current/active phase shows distinct symbol (◉ or chosen alternative)
- [ ] Failed phases show `✗`
- [ ] Metrics appear below relevant phase (tokens, commits, status)
- [ ] Final success shows `✓` in header
- [ ] Final failure shows `✗` in header

## Execution Steps
1. **Step 1:** Update work-progress.sh to render horizontal layout
   - Files: plugin/scripts/work-progress.sh
   - Verify: Script outputs horizontal format when invoked

2. **Step 2:** Update work.md `<progress_output>` section
   - Files: plugin/commands/work.md
   - Verify: Documentation matches new visual specification

3. **Step 3:** Update workflow reference
   - Files: plugin/.claude/cat/workflows/work.md
   - Verify: Workflow documentation consistent with new format

4. **Step 4:** Test rendering across terminal types
   - Verify: Symbols render correctly in VS Code terminal, iTerm2
