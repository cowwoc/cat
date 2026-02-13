# Plan: add-race-condition-review-criteria

## Goal
Add race condition, TOCTOU, and concurrency safety review criteria to the security stakeholder agent.
Currently no stakeholder checks for these vulnerability classes, creating a detection gap.

## Problem
The security stakeholder agent (plugin/agents/stakeholder-security.md) has review criteria for
injection, auth bypasses, data exposure, input validation, resource exhaustion, and crypto weaknesses,
but contains zero mention of race conditions, TOCTOU (CWE-367), atomicity violations, or concurrency
safety. This means stakeholder review cannot catch these vulnerability classes.

## Satisfies
None - infrastructure improvement

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** Adding review criteria may produce false positives on non-concurrent code
- **Mitigation:** Scope criteria to file-system operations, git operations, and shared state access

## Files to Modify
- plugin/agents/stakeholder-security.md - Add race condition/concurrency review criteria

## Acceptance Criteria
- [ ] Security stakeholder has TOCTOU/race condition criteria under High Priority section
- [ ] Criteria cover: TOCTOU (CWE-367), atomicity violations, unprotected shared state, check-then-act patterns
- [ ] Context-appropriate: scoped to file-system ops, git operations, shared state (not general threading)
- [ ] Examples provided showing what to look for in skill/script code
- [ ] Functionality works, tests passing, no regressions

## Execution Steps
1. **Step 1:** Add race condition criteria to security stakeholder
   - File: plugin/agents/stakeholder-security.md
   - Add new subsection under "### High Priority" (after Resource Exhaustion, before Medium Priority)
   - Title: "**Race Conditions / TOCTOU**: Time-of-check-to-time-of-use gaps, check-then-act without pinning, unprotected shared state between operations"
   - This is a single line addition matching the existing format of other High Priority items
2. **Step 2:** Add concurrency-specific review guidance
   - File: plugin/agents/stakeholder-security.md
   - Add new section after "## Context-Specific Security Model" and before "## Review Output Format"
   - Title: "## Concurrency Safety Checks"
   - Content should list specific patterns to look for:
     - Branch/ref operations: `git rev-parse` called multiple times on same ref without pinning
     - File-system TOCTOU: check existence then read/write without atomic operation
     - Lock files: check-then-create without atomic creation (use `O_CREAT|O_EXCL` or equivalent)
     - Shared state: reading config/state files between operations that must be consistent
   - Include note: "These checks are most relevant for bash scripts, git operation skills, and file-based state management. Not applicable to pure documentation changes."
3. **Step 3:** Run existing tests to verify no regressions
   - Run: mvn -f hooks/pom.xml test

## Success Criteria
- [ ] Security stakeholder agent includes race condition/TOCTOU in High Priority criteria
- [ ] Concurrency Safety Checks section provides actionable review patterns
- [ ] Patterns are scoped appropriately (file-system, git, shared state - not general threading)
- [ ] All existing tests pass with no regressions