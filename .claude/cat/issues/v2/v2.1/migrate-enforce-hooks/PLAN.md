# Plan: migrate-enforce-hooks

## Metadata
- **Parent:** migrate-python-to-java
- **Wave:** 3 (concurrent with handler subtasks)
- **Estimated Tokens:** 15K

## Goal
Create Java implementations for the 2 remaining Python hooks that have no Java equivalent: `enforce-worktree-isolation.py` and `enforce-status-output.py`. Wire them up in hooks.json.

## Satisfies
None - infrastructure/setup task

## Risk Assessment
- **Risk Level:** MEDIUM
- **Concerns:** These hooks enforce critical safety behavior (worktree isolation, status output)
- **Mitigation:** Test both block and allow paths; compare output to Python

## Files to Create

| Python Source | Java Target | hooks.json Line |
|---------------|-------------|----------------|
| `plugin/hooks/enforce-worktree-isolation.py` | `plugin/hooks/src/io/github/cowwoc/cat/hooks/EnforceWorktreeIsolation.java` | ~132 |
| `plugin/hooks/enforce-status-output.py` | `plugin/hooks/src/io/github/cowwoc/cat/hooks/EnforceStatusOutput.java` | ~196 |

## Files to Modify
- `plugin/hooks/hooks.json` - Replace 2 Python commands with java_runner.sh calls

## Dependencies
- java-core-hooks (entry points and core infrastructure must be wired up)

## Execution Steps
1. **Read enforce-worktree-isolation.py** - Understand the logic (checks if Write/Edit targets base branch)
2. **Create EnforceWorktreeIsolation.java** - Port the Python logic to Java
3. **Wire up in hooks.json** - Replace `python3 .../enforce-worktree-isolation.py` with `java_runner.sh EnforceWorktreeIsolation`
4. **Test worktree isolation** - Verify it blocks Write/Edit on base branch, allows in worktrees
5. **Read enforce-status-output.py** - Understand the logic (Stop hook behavior)
6. **Create EnforceStatusOutput.java** - Port the Python logic to Java
7. **Wire up in hooks.json** - Replace `python3 .../enforce-status-output.py` with `java_runner.sh EnforceStatusOutput`
8. **Test status output** - Verify output identical to Python version
9. **Run test suite** - `python3 /workspace/run_tests.py`

## Acceptance Criteria
- [ ] EnforceWorktreeIsolation.java blocks Write/Edit on base branch correctly
- [ ] EnforceStatusOutput.java produces identical output to Python
- [ ] Both hooks wired up in hooks.json via java_runner.sh
- [ ] No Python enforce-*.py commands remain in hooks.json
- [ ] All existing tests pass
