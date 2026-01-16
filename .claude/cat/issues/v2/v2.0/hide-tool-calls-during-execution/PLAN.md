# Plan: hide-tool-calls-during-execution

## Goal
Update CAT workflow instructions to minimize visible tool calls (Read, Bash, Glob) during execution, showing progress indicators instead of raw tool output.

## Satisfies
- REQ: Demo-ready output for video recording and marketing materials

## Current State
Every tool call is visible to user:
```
● Read(.claude/cat/v1/v1.10/horizontal-progress-banner/PLAN.md)
  ⎿  Read 190 lines

● Bash(find .claude/cat/v*/v*.*/ -mindepth 1 ...)
  ⎿  (output)

● Read(~/.config/claude/plugins/cache/...)
  ⎿  Read 199 lines
```

This creates visual noise and makes demos look cluttered.

## Target State
Replace visible tool calls with progress indicators:
```
◐ Analyzing task requirements...
◑ Spawning 6 reviewers...
◒ Collecting results...
```

Tool calls still execute but output is summarized, not shown line-by-line.

## Risk Assessment
- **Risk Level:** MEDIUM
- **Concerns:**
  - Claude Code controls tool call visibility, not CAT
  - May need to work within existing constraints
- **Mitigation:**
  - Use workflow instructions to batch operations
  - Output progress messages before/after tool blocks
  - Minimize redundant reads

## Approach Analysis

### Constraint: Claude Code Tool Visibility
Claude Code automatically displays tool calls to users. CAT cannot directly hide them.

### Viable Approaches

**A: Workflow optimization (reduce tool calls)**
- Batch file reads into fewer operations
- Cache frequently-read files in workflow context
- Use grep-and-read skill for combined operations
- **Outcome:** Fewer visible tool calls, cleaner output

**B: Progress-first messaging**
- Output clear progress message BEFORE tool block
- User sees "Analyzing..." then tool calls fly by
- Output summary AFTER tools complete
- **Outcome:** Tool calls contextualized, less confusing

**C: Encourage --silent workflows (future)**
- Design workflows that minimize intermediate output
- Document "demo mode" execution patterns
- **Outcome:** Cleaner demos with intentional execution

## Files to Modify
- workflows/work.md - Add progress messaging between phases
- skills/stakeholder-review/SKILL.md - Batch reads, add progress output
- skills/token-report/SKILL.md - Minimize bash calls
- references/workflow-output.md (new) - Document output conventions

## Acceptance Criteria
- [ ] Each workflow phase starts with clear progress message
- [ ] File reads batched where possible (1 read vs 5 sequential)
- [ ] Progress indicators use consistent format (◐ ◑ ◒ ◓ or similar)
- [ ] Final output is a summary, not raw tool results
- [ ] Workflows document "demo-friendly" execution patterns

## Execution Steps
1. **Audit current workflows for excessive tool calls**
   - List all Read/Bash calls in stakeholder-review
   - Identify batching opportunities
   - Verify: Document current call count

2. **Add progress messaging to workflows**
   - Insert output statements before tool blocks
   - Use spinner-style unicode characters
   - Verify: Progress messages appear in execution

3. **Batch file operations**
   - Combine sequential reads where possible
   - Use grep-and-read for search+read patterns
   - Verify: Reduced tool call count

4. **Create workflow-output.md reference**
   - Document output conventions for CAT workflows
   - Define progress indicator format
   - Verify: Reference file created and linked
