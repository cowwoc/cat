# Plan: lazy-load-skills-and-memory

## Goal
Reduce upfront context token usage by implementing lazy-loading for skills and memory files. Load only essential resources on session start; defer specialized skills and context-specific memory files until actually needed.

## Parent Requirements
None (infrastructure optimization not directly tied to v2.1 demo polish objectives)

## Context
Current behavior loads all 60+ cat: skills (~2.8k tokens) and all memory files (~6.1k tokens) on every session start, regardless of actual usage. Analysis shows typical sessions use <5% of loaded skills, wasting ~4-5k tokens (2.5% of 200k budget) unnecessarily.

## Approaches

### A: Lazy-Load Skills Only
- **Risk:** LOW
- **Scope:** 3-5 files (minimal - skill loading only)
- **Description:** Defer specialized skills, keep memory files unchanged. Simpler implementation, ~2.6k token savings.

### B: Lazy-Load Skills and Memory Files (Recommended)
- **Risk:** MEDIUM
- **Scope:** 6-10 files (moderate - both loading systems)
- **Description:** Defer both skills and context-specific memory files. Full optimization, ~4-5k token savings.

## Risk Assessment
- **Risk Level:** MEDIUM
- **Concerns:** 
  - Skill loading infrastructure changes could break skill invocation if not properly tested
  - Memory file deferral might cause missing context for certain operations
  - Need to ensure deferred resources load correctly when triggered
- **Mitigation:** 
  - Comprehensive testing of skill invocation paths
  - Clear fallback behavior if deferred loading fails
  - Incremental rollout (skills first, then memory files)

## Files to Modify
Skills loading:
- Identify skill loader (likely in client/ or plugin/)
- Update to distinguish core vs. specialized skills
- Implement on-demand loading trigger

Memory files loading:
- plugin/rules/license-header.md - mark for file-creation trigger
- plugin/rules/configuration-reads.md - mark for worktree-context trigger  
- plugin/rules/backwards-compatibility.md - mark for migration-edit trigger
- Update memory file loader to support deferred loading

Configuration:
- Add lazy-loading configuration (enable/disable, which files to defer)

## Pre-conditions
- [ ] All dependent issues are closed

## Jobs

### Job 1: Implement lazy-loading for skills
- Identify core entry-point skills (help, status, add, work, optimize-execution)
- Update skill loader to load only core skills on session start
- Implement on-demand loading when specialized skill invoked
- Add caching to avoid re-loading same skill multiple times
  - Files: skill loader infrastructure

### Job 2: Implement lazy-loading for memory files
- Mark context-specific memory files for deferred loading:
  - license-header.md → load on Write/Edit of new file
  - configuration-reads.md → load in worktree context
  - backwards-compatibility.md → load when editing plugin/migrations/
- Update memory file loader to support trigger-based loading
- Implement trigger detection and dynamic loading
  - Files: memory file loader, hook system

### Job 3: Add configuration and testing
- Add lazy-loading settings to config.json schema
- Add tests verifying:
  - Core skills load on session start
  - Specialized skills load on first invocation
  - Memory files load when triggered
  - Token usage reduced by expected amount
  - Files: config schema, test files

## Post-conditions
- [ ] Core skills (help, status, add, work, optimize-execution) load on session start
- [ ] Specialized skills load on first invocation only
- [ ] Context-specific memory files load on-demand when triggered
- [ ] All tests pass
- [ ] No skill invocation regressions
- [ ] E2E: Session-start context reduced by ~40% (verified via /context output comparison before/after)
