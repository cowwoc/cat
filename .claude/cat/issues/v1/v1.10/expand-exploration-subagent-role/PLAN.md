# Plan: expand-exploration-subagent-role

## Goal
Expand the exploration subagent to handle preparation (task size analysis, worktree creation) and verification
(confirming findings before implementation), hiding noisy tool calls from the user and presenting clean progress
summaries.

## Requirements

| ID | Requirement | Priority | Acceptance Criteria |
|----|-------------|----------|---------------------|
| REQ-001 | *Define requirement* | must-have | *How to verify* |


## Satisfies
- None (infrastructure/workflow improvement)

## Current State
Main agent performs task size analysis, worktree creation, and post-exploration verification directly, exposing
Bash/Read/Write tool calls to users. After exploration subagent returns, main agent reads source files to verify
findings (violating M088/M147).

## Target State
Exploration subagent handles three phases internally:
1. **Preparation:** Read PLAN.md, analyze task size, create worktree
2. **Exploration:** Search codebase, find relevant code, check duplicates (existing)
3. **Verification:** Verify findings, run preliminary tests, confirm state

Returns structured JSON that main agent formats for clean display.

## Refactor Approach Outlines

### Conservative
Minimal change: add verification to exploration subagent, keep preparation in main agent.
- **Risk:** LOW
- **Tradeoff:** Preparation tool calls still visible to user

### Balanced
Move preparation AND verification into exploration subagent, return structured JSON.
- **Risk:** MEDIUM
- **Tradeoff:** Larger prompt for exploration subagent

### Aggressive
Full restructure: create new "environment-setup" subagent type separate from exploration.
- **Risk:** HIGH
- **Tradeoff:** More complexity, new subagent type to maintain

## Selected Approach
Balanced

## Detailed Refactor

### Risk Assessment
- **Risk Level:** MEDIUM
- **Breaking Changes:** None - internal workflow change
- **Mitigation:** Existing tests, incremental changes

### Files to Modify
- `plugin/commands/work.md` - Delegate preparation steps to exploration subagent
- `plugin/skills/spawn-subagent/SKILL.md` - Document expanded exploration subagent role
- `plugin/.claude/cat/references/agent-architecture.md` - Update architecture documentation

### Acceptance Criteria
- [ ] Main agent no longer shows Bash/Read calls for task size analysis
- [ ] Main agent no longer reads source files after exploration returns (M088/M147)
- [ ] Exploration subagent returns structured JSON with preparation + findings
- [ ] Main agent presents clean formatted progress summaries
- [ ] work.md documents the expanded exploration subagent workflow

### Requirements Traceability

| Requirement | Covered By | Status |
|-------------|------------|--------|
| REQ-001 | *Step N* | pending |


### Execution Steps
1. **Update spawn-subagent SKILL.md:**
   - Add "Expanded Exploration Subagent" section
   - Document preparation + verification phases
   - Define structured JSON return format
   - Verify: Documentation is clear and complete

2. **Update work.md prepare steps:**
   - Modify steps 2-7 to spawn exploration subagent instead of inline execution
   - Define exploration subagent prompt with preparation instructions
   - Main agent only formats and displays returned JSON
   - Verify: Progress output is clean (no Bash/Read calls visible)

3. **Update agent-architecture.md:**
   - Document expanded exploration subagent responsibilities
   - Clarify main agent boundaries (no source file reads)
   - Verify: Documentation consistent with implementation
