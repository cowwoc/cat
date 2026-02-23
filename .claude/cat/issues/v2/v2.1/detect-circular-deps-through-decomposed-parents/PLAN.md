# Plan: detect-circular-deps-through-decomposed-parents

## Problem

When all issues in a minor version are blocked due to circular dependencies mediated through
decomposed parent issues, work-prepare returns NO_ISSUES without detecting or reporting the
circular dependency. The existing cycle detection in WorkPrepare only checks direct dependency
references between issues, but does not account for the implicit dependency chain created when:

1. Issue A depends on decomposed parent B
2. Decomposed parent B is skipped (has open sub-issues)
3. B's only remaining open sub-issue C depends on A (or on another issue that depends on A)

This creates an unresolvable deadlock that is invisible to both the agent and the user.

**Concrete example:** `ci-build-jlink-bundle` depends on `migrate-python-to-java` (a decomposed
parent). `migrate-python-to-java`'s only open sub-issue is `add-java-build-to-ci` (also a
decomposed parent), whose sub-issues include `ci-build-jlink-bundle`. This creates a cycle through
the decomposed parent structure that the current DFS-based cycle detector does not traverse.

## Satisfies

None (infrastructure bugfix)

## Reproduction Code

```
# Run work-prepare on v2.1 where:
# - migrate-python-to-java is decomposed parent (skipped), only open sub-issue is add-java-build-to-ci
# - add-java-build-to-ci is decomposed parent (skipped), sub-issues depend on migrate-python-to-java
# - All other v2.1 issues depend on migrate-python-to-java or ci-build-jlink-bundle
# Result: NO_ISSUES with no circular_dependencies field
# Expected: NO_ISSUES with circular_dependencies detected and user offered resolution options
```

## Expected vs Actual

- **Expected:** work-prepare detects the circular dependency through decomposed parents and returns
  it in the `circular_dependencies` field, allowing the work skill to surface resolution options to
  the user (e.g., break the cycle by removing a dependency, close the parent manually, etc.)
- **Actual:** work-prepare returns NO_ISSUES with empty `circular_dependencies`, leaving the user
  with no actionable information about why no issues are available

## Root Cause

The `findCircularDependencies` method in WorkPrepare builds a dependency graph from explicit
`Dependencies:` fields in STATE.md files. When issue A depends on decomposed parent B, the graph
records A→B. But B is skipped during issue selection (it's a decomposed parent), so the graph
doesn't traverse B's sub-issues to find that one of them (C) depends back on A. The cycle
A→B→(implicit sub-issue C)→A is never detected.

## Risk Assessment

- **Risk Level:** MEDIUM
- **Regression Risk:** Changes to cycle detection could produce false positives
- **Mitigation:** Comprehensive tests for decomposed parent cycle detection, including negative
  tests for non-circular decomposed parents

## Files to Modify

- `client/src/main/java/io/github/cowwoc/cat/hooks/util/WorkPrepare.java` - Enhance
  `findCircularDependencies` to traverse decomposed parent sub-issue relationships
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/WorkPrepareTest.java` - Add tests for
  cycles through decomposed parents

## Test Cases

- [ ] Cycle through single decomposed parent: A depends on decomposed B, B's sub-issue depends on A
- [ ] Cycle through nested decomposed parents: A→B(decomposed)→C(decomposed sub-issue)→A
- [ ] No false positive: A depends on decomposed B, B's sub-issues do NOT depend on A
- [ ] Mixed direct and decomposed cycles detected together
- [ ] Existing direct cycle tests still pass

## Pre-conditions

- [ ] All dependent issues are closed

## Execution Steps

1. **Step 1:** Write failing tests for cycle detection through decomposed parents
   - Files: `WorkPrepareTest.java`
2. **Step 2:** Enhance `findCircularDependencies` to build an augmented dependency graph that
   includes implicit edges from decomposed parents to their sub-issues and vice versa
   - Files: `WorkPrepare.java`
3. **Step 3:** Run all tests to verify fix and no regressions
4. **Step 4:** Rebuild jlink bundle and verify work-prepare output includes circular_dependencies

## Post-conditions

- [ ] Bug fixed: circular dependencies through decomposed parents are detected
- [ ] Regression test added for decomposed parent cycles
- [ ] No new issues: existing cycle detection tests still pass
- [ ] E2E: Run work-prepare against v2.1 and verify circular_dependencies field is populated with
  the migrate-python-to-java cycle
