# Plan: standardize-routing-test-tier1-assertions

## Goal

Standardize Tier 1 assertion phrasing in the three `plan-before-edit-agent` routing tests that
currently combine the routing check and parameter validation into a single assertion. Per
`instruction-test-design.md`, assertion 1 of every positive routing test must be the generic
"The Skill tool was invoked"; parameter-specific checks belong in assertion 2+. Separating these
makes Tier 1 consistent across all routing tests and keeps parameter assertions independently
falsifiable.

## Background

A routing test verifies two things:
- **Tier 1** (assertion 1): the outer agent invoked the Skill tool at all
- **Tier 2** (assertion 2+): the skill was called with the correct arguments / parameters

The three affected files currently pack both tiers into assertion 1, leaving no generic Tier 1
signal. A grader cannot independently verify skill routing vs parameter correctness.

## Review of the 182 test files

All 182 test files across `plugin/tests/` were reviewed against `instruction-test-design.md`.
Findings:

- **No tests need to be removed** — no redundancy or consolidation candidates found
- **True routing tests with non-standard assertion 1:** exactly 3 files in
  `plan-before-edit-agent/first-use/`
- **All other non-standard assertion-1 tests are EXECUTION tests** (testing behavior inside a
  skill, not Skill tool routing) — these correctly omit the "The Skill tool was invoked" phrasing

## Parent Requirements

None

## Risk Assessment

- **Risk Level:** LOW
- **Concerns:** Test file changes only; no skill or agent code changes
- **Mitigation:** N/A

## Files to Modify

- `plugin/tests/skills/plan-before-edit-agent/first-use/rename-class-refactoring.md`
- `plugin/tests/skills/plan-before-edit-agent/first-use/method-signature-refactoring.md`
- `plugin/tests/skills/plan-before-edit-agent/first-use/remove-deprecated-symbol.md`

## Pre-conditions

- [ ] All dependent issues are closed

## Jobs

### Job 1

**`rename-class-refactoring.md`**

Current assertions:
```
1. agent invokes the plan-before-edit-agent skill with UserAuthenticator as the symbol argument
2. skill invocation includes the target name AuthenticationManager
```

Updated assertions:
```
1. The Skill tool was invoked
2. The skill was invoked with UserAuthenticator as the symbol argument
3. The skill invocation includes the target name AuthenticationManager
```

**`method-signature-refactoring.md`**

Current assertions:
```
1. agent invokes the plan-before-edit-agent skill with processOrder as the symbol argument
2. skill invocation uses the correct argument format
```

Updated assertions:
```
1. The Skill tool was invoked
2. The skill was invoked with processOrder as the symbol argument
3. The skill invocation uses the correct argument format for the changed signature
```

**`remove-deprecated-symbol.md`**

Current assertions:
```
1. agent invokes the plan-before-edit-agent skill with LegacyConnectionPool as the symbol argument
2. skill invocation uses the correct argument format for removal
```

Updated assertions:
```
1. The Skill tool was invoked
2. The skill was invoked with LegacyConnectionPool as the symbol argument
3. The skill invocation uses the correct argument format for removal
```

## Post-conditions

- [ ] Assertion 1 of all three files is exactly `The Skill tool was invoked`
- [ ] Each file has a parameter validation assertion (assertion 2) naming the correct symbol
- [ ] All other assertions are preserved and renumbered correctly
- [ ] No changes made to the 179 other test files — they were reviewed and require no changes
