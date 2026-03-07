# Plan: fix-validation-claim-hook-false-positive

## Problem

The `VALIDATION CLAIM WITHOUT EVIDENCE DETECTED` hook fires a false-positive after
`cat:verify-implementation-agent` returns its verification results to the main agent. The hook is
designed to prevent the main agent from making unsubstantiated claims about implementation completeness,
but it incorrectly triggers when the main agent references or summarizes legitimate verify-implementation
output. This forces the main agent to invoke `cat:verify-implementation-agent` a second time, wasting
~249K cost-weighted tokens (~849K raw tokens) per `/cat:work` session.

## Parent Requirements

None — reliability fix.

## Reproduction

```
1. Run /cat:work on any issue
2. Implementation subagent completes and commits changes
3. Main agent invokes cat:verify-implementation-agent
4. Verify-implementation returns COMPLETE/PARTIAL/INCOMPLETE report
5. Main agent processes results and states (e.g.) "Verification complete: 3/3 criteria passed"
6. Hook fires: VALIDATION CLAIM WITHOUT EVIDENCE DETECTED
7. Main agent invokes cat:verify-implementation-agent again (duplicate call)
```

## Expected vs Actual
- **Expected:** Hook does not fire after main agent references verify-implementation-agent output;
  verify-implementation-agent is called exactly once per confirm phase
- **Actual:** Hook fires false-positive when main agent summarizes verify-implementation output, causing
  a duplicate ~849K raw token invocation

## Root Cause

The hook pattern-matches on phrases like "criteria passed", "verification complete", "all criteria met",
or similar language in the main agent's response without checking whether a verify-implementation-agent
invocation preceded the statement. Verify-implementation output contains exactly this language, and when
the main agent summarizes it, the hook's regex or keyword matching triggers incorrectly.

The fix requires either:
A. Adding a "verify-implementation just ran" exemption flag/context to the hook detection logic
B. Narrowing the hook's pattern matching so it only triggers on unsubstantiated claims (without
   preceding verification evidence)
C. Having verify-implementation-agent mark its output in a way the hook recognizes as evidence

## Risk Assessment
- **Risk Level:** MEDIUM
- **Regression Risk:** Over-relaxing the hook could allow real unsubstantiated claims to pass
- **Mitigation:** Add a test case that confirms the hook still fires for genuine unsubstantiated claims;
  confirm hook does not fire after legitimate verify-implementation execution

## Files to Modify

First, identify the hook implementation:
```bash
grep -r "VALIDATION CLAIM WITHOUT EVIDENCE" /workspace/plugin/ --include="*.java" --include="*.sh" -l
grep -r "VALIDATION CLAIM WITHOUT EVIDENCE" /workspace/.claude/ -l
```

Expected files (to be confirmed by investigation):
- `plugin/hooks/` — the hook handler that detects validation claims (exact file TBD)
- Possibly `plugin/agents/` — agent instructions that define when to claim validation
- Possibly `plugin/skills/work-with-issue-agent/first-use.md` — if the verify phase triggers the hook

## Test Cases
- [ ] Original bug: run /cat:work confirm phase, verify verify-implementation-agent is called exactly once
- [ ] Regression: make an unsubstantiated claim without verify-implementation; confirm hook still fires
- [ ] Edge case: partial verification result (PARTIAL status) does not trigger false-positive

## Pre-conditions
- [ ] All dependent issues are closed

## Sub-Agent Waves

### Wave 1
- Locate the hook source file by searching for "VALIDATION CLAIM WITHOUT EVIDENCE":
  ```bash
  grep -r "VALIDATION CLAIM WITHOUT EVIDENCE" /workspace/plugin/ /workspace/.claude/ -l
  ```
  Read the hook implementation to understand the detection logic (pattern matching, conditions, context).
  - Files: hook source file(s) identified by grep

### Wave 2
- Implement the fix in the hook to exempt verify-implementation-agent output from the false-positive
  trigger. Approach: add a condition that checks whether verify-implementation-agent was invoked in the
  recent tool call history, OR narrow the pattern to require absence of preceding verification evidence.
  - Files: hook source file identified in Wave 1
- If the hook is in Java: also add a unit test verifying (a) false-positive no longer fires after
  verify-implementation output, (b) genuine unsubstantiated claims still trigger the hook.
  - Files: corresponding test file in `client/src/test/`

### Wave 3
- Run `mvn -f client/pom.xml test` to verify all tests pass.
  - Files: none (validation only)
- Update `STATE.md` to reflect implementation complete.
  - Files: issue `STATE.md`

## Post-conditions
- [ ] Hook no longer fires false-positive after verify-implementation-agent output
- [ ] Hook still fires correctly for genuine unsubstantiated validation claims
- [ ] All existing hook tests pass (`mvn -f client/pom.xml test`)
- [ ] E2E: Run `/cat:work` on an issue through the confirm phase; verify verify-implementation-agent
  appears exactly once in the session tool call log
