# Plan: migrate-to-silent-preprocessing

## Current State
Skills use OUTPUT TEMPLATEs injected by PreToolUse handlers. The LLM reads templates with placeholders and manually
constructs output, leading to errors (M246, M256, M257, M288, M298).

## Target State  
Skills use `!`command`` preprocessing syntax to generate output silently. Commands execute during skill expansion,
before the LLM sees content. Output is guaranteed correct with no LLM manipulation.

## Satisfies
- Pre-Demo Polish goal: "Command output displays correctly aligned"

## Risk Assessment
- **Risk Level:** MEDIUM
- **Breaking Changes:** Skills must be invoked differently (pass args to preprocessing commands)
- **Mitigation:** Incremental migration by area, test each before moving to next

## Sub-issues

### 1. update-skill-builder-docs
Update skill-builder documentation to teach the new `!`command`` mechanism instead of OUTPUT TEMPLATE + handler pattern.

**Files:**
- plugin/skills/skill-builder/SKILL.md
- plugin/skills/skill-builder/workflow-output.md
- plugin/concepts/agent-architecture.md (if references handlers)

### 2. migrate-progress-banners
Migrate the progress banner system (most error-prone) to use silent preprocessing.

**Files:**
- plugin/hooks/skill_handlers/work_handler.py (remove banner construction from OUTPUT TEMPLATE)
- .claude/commands/cat-banner/ (the new skill with `!`command``)
- plugin/commands/work.md (update to use /cat-banner)

### 3. migrate-status-displays
Migrate status command displays to silent preprocessing.

**Files:**
- plugin/hooks/skill_handlers/status_handler.py
- plugin/commands/status.md
- New status display skill with preprocessing

### 4. migrate-remaining-handlers
Migrate remaining handlers: add, help, cleanup, config, init, token-report, render-diff, stakeholder.

**Files:**
- All remaining *_handler.py files
- Corresponding command/skill .md files

## Acceptance Criteria
- [ ] skill-builder docs updated to teach new mechanism
- [ ] Progress banners use silent preprocessing (no more M298-type errors)
- [ ] Status displays use silent preprocessing
- [ ] All handlers migrated or marked as not-applicable
- [ ] No OUTPUT TEMPLATE sections that require LLM manipulation remain
