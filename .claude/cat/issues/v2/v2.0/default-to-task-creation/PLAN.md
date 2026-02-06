# Plan: default-to-task-creation

## Goal
Change CAT's default behavior so that when work is suggested or requested, the main agent creates a task first rather
than working on it directly. This enforces the "plan first, execute via task" pattern.

## Satisfies
- Ensures all work is tracked in the planning structure
- Prevents ad-hoc work that bypasses task tracking
- Improves visibility and auditability of all changes

## Current State
When user requests work (e.g., "fix the bug in X" or "add feature Y"), the main agent may:
- Start working directly without creating a task
- Complete work without it being tracked in STATE.md
- Skip the planning/review workflow

## Target State
When work is suggested or requested:
1. Main agent proposes creating a task via `/cat:add`
2. User confirms or provides task details
3. Task is created with STATE.md and PLAN.md
4. Work is executed via `/cat:work` with proper tracking

**Exception:** Trivial fixes (typos, single-line changes) can be done directly with user confirmation.

## Risk Assessment
- **Risk Level:** MEDIUM
- **Concerns:**
  - May slow down simple requests
  - User friction if every small change requires task creation
- **Mitigation:**
  - Add "trivial fix" exception for small changes
  - Make task creation fast with `/cat:add <description>` shortcut
  - Trust level can influence behavior (high trust = more autonomy)

## Approaches

### A: Session Instructions (Recommended)
- Add instruction to CAT session hook or CLAUDE.md
- Main agent reads instruction at session start
- Instruction says "default to creating tasks for work requests"
- **Risk:** LOW - documentation-level change
- **Scope:** 1-2 files

### B: PreToolUse Hook Enforcement
- Create hook that detects "work-like" tool calls (Edit, Write)
- Hook prompts: "Should this be a tracked task?"
- **Risk:** MEDIUM - may be intrusive
- **Scope:** 1 hook file + configuration

### C: Workflow Document Update
- Update agent-architecture.md with "work request handling" section
- Reference from session startup
- **Risk:** LOW
- **Scope:** 1-2 files

## Files to Modify
- .claude/cat/references/agent-architecture.md - Add "Work Request Handling" section
- .claude/cat/SESSION.md or hooks - Add startup instruction
- plugin/CLAUDE.md - Add behavioral guideline

## Acceptance Criteria
- [ ] When user requests work, agent proposes task creation first
- [ ] Agent explains: "I'll create a task for this so it's tracked properly"
- [ ] User can override with "just do it" for trivial fixes
- [ ] Trust level influences behavior:
  - low: Always ask before any work
  - medium: Propose task for non-trivial work
  - high: Create task automatically, proceed to work
- [ ] Existing `/cat:work` workflow unchanged

## Execution Steps
1. **Document the behavior in agent-architecture.md**
   - Add "Work Request Handling" section
   - Define trivial vs non-trivial work
   - Specify trust-level variations
   - Verify: Section added and clear

2. **Add session instruction**
   - Update startup hook or CLAUDE.md
   - Instruction: "Default to task creation for work requests"
   - Verify: Instruction loaded at session start

3. **Update CLAUDE.md with guideline**
   - Add behavioral rule: "Create task before working"
   - Include exception for trivial fixes
   - Verify: Rule documented

4. **Test behavior**
   - Request "fix bug X" - should propose task
   - Request "fix typo" - should ask if trivial
   - With high trust - should auto-create and proceed
   - Verify: All scenarios work correctly
