# Plan: add-safe-branch-update-cli

## Goal

Add a Java CLI tool (`update-branch`) that performs guarded local branch-pointer updates with a required
fast-forward check (unless `--force` is explicitly provided), and migrate relevant plugin skill command
examples away from unsafe direct ref updates.

## Parent Requirements

None

## Background

The issue is motivated by a real data-loss event where branch-pointer mutation ran despite a failed
fast-forward precheck. Guardrails must be enforced inside the executable itself, not left to ad-hoc shell
chaining.

## Research Findings

- CLI runtime/tooling lives under `client/cli`, not `client/src`.
- Existing utility launchers are registered in `client/cli/build-jlink.sh` via `COMMON_HANDLERS`.
- Existing util commands use package
  `io.github.cowwoc.cat.claude.hook.util` and TestNG tests under
  `client/cli/src/test/java/io/github/cowwoc/cat/client/test`.
- Current tree contains no executable `git update-ref` command usage in skill markdown files under
  `client/plugin/skills/**`; this migration must therefore include a mandatory inventory step and explicit
  zero-match handling rather than assuming call sites exist.

## Approaches

### Option A (Rejected): Keep shell-based call sites but prepend precheck snippets

- **Risk:** HIGH
- **Scope:** moderate
- **Description:** Update each skill snippet to run `merge-base --is-ancestor` before existing pointer update
  commands.
- **Why rejected:** Safety remains optional and fragile; snippet drift can reintroduce unsafe paths.

### Option B (Chosen): Ship dedicated `update-branch` Java launcher and migrate call sites

- **Risk:** MEDIUM
- **Scope:** moderate
- **Description:** Implement a single CLI that verifies fast-forward (unless `--force`) before mutating
  `refs/heads/<branch>`, register launcher in jlink, add tests, then migrate skill snippets that directly
  update local branch refs.
- **Why chosen:** Centralized invariant, testable behavior, and reusable across all skills.

### Option C (Rejected): Block all non-fast-forward updates with no override

- **Risk:** MEDIUM
- **Scope:** minimal
- **Description:** Remove force path entirely.
- **Why rejected:** Some recovery and history-rewrite workflows intentionally require forced pointer updates;
  these need an explicit but available escape hatch.

## Risk Assessment

- **Risk Level:** MEDIUM
- **Concerns:**
  - False negatives/positives in fast-forward detection due to incorrect branch existence handling.
  - Launcher registration mistakes can produce green unit tests but missing runtime binary.
  - Migration work may be skipped if call-site discovery is not explicit and reproducible.
- **Mitigation:**
  - Test both existing and non-existing branch cases in isolated temporary git repositories.
  - Add jlink handler entry and verify launcher is produced during Maven test/verify flow.
  - Make inventory commands part of execution steps and treat zero matches as a documented result, not as
    implicit completion.

## Files to Modify

- `client/cli/src/main/java/io/github/cowwoc/cat/claude/hook/util/UpdateBranch.java` (new)
  - Implement CLI parser and guarded branch update execution.
- `client/cli/build-jlink.sh`
  - Add `update-branch` launcher entry to `COMMON_HANDLERS`.
- `client/cli/src/test/java/io/github/cowwoc/cat/client/test/UpdateBranchTest.java` (new)
  - Add behavioral tests for fast-forward, rejection, force bypass, new-branch creation, and invalid input.
- `client/cli/src/test/java/io/github/cowwoc/cat/client/test/UpdateBranchMainTest.java` (new)
  - Add CLI argument/exit-code/output tests for main/run paths.
- `client/plugin/skills/**/*.md` (conditional, only files returned by inventory)
  - Replace direct local branch-ref mutation patterns with `update-branch` equivalents.

## Pre-conditions

- [ ] All dependent issues are closed

## Jobs

### Job 1: Implement `update-branch` CLI

- Create `UpdateBranch` command in
  `client/cli/src/main/java/io/github/cowwoc/cat/claude/hook/util/UpdateBranch.java`.
  - Parse: `update-branch [--force] <branch> <new-tip-hash>`.
  - Reject unknown flags or missing positional args with usage text and non-zero exit.
  - Resolve current tip with `git rev-parse --verify refs/heads/<branch>`.
  - If branch does not exist, treat as create-path and skip fast-forward check.
  - If branch exists and `--force` is absent:
    - Run `git merge-base --is-ancestor <old-tip> <new-tip-hash>`.
    - On non-zero, print descriptive rejection and exit non-zero.
  - Apply update with `git update-ref refs/heads/<branch> <new-tip-hash>`.
  - Print plain-text success/failure only (no JSON payload).
- Keep implementation consistent with existing util command style:
  - provide a testable execution method separated from `main(String[])`.
  - avoid direct CAT hook block JSON helpers for this command.

### Job 2: Register launcher in runtime images

- Update `client/cli/build-jlink.sh` `COMMON_HANDLERS` with:
  - `update-branch:io.github.cowwoc.cat.claude.hook.util.UpdateBranch`
- Ensure the launcher name is exactly `update-branch` so skills can call it directly.

### Job 3: Add unit and CLI tests

- Add `UpdateBranchTest.java` covering:
  - Fast-forward update succeeds and branch tip changes to requested commit.
  - Non-fast-forward update is rejected without `--force`.
  - `--force` bypass allows non-fast-forward update.
  - Missing local branch is created without fast-forward check.
  - Invalid argument sets (missing branch/hash, unknown flags) fail with usage text.
- Add `UpdateBranchMainTest.java` covering:
  - `run(...)`/`main(...)` argument error behavior, exit codes, and stdout/stderr contract.
  - Output is plain text, never JSON.

### Job 4: Inventory and migrate instruction call sites

- Run deterministic inventory commands over plugin instruction markdown:
  - `rg -n "git update-ref|git push \\. .*--force|git push --force \\.|git branch -f|git checkout -B" client/plugin -g '*.md'`
- For each returned snippet that performs local branch ref mutation:
  - Replace with `update-branch [--force] <branch> <target-hash>` preserving intent.
  - Keep remote-push examples (`origin`, `--force-with-lease`) unchanged unless they are actually local-ref
    mutation workarounds.
- If inventory returns zero local-ref mutation call sites:
  - Leave skill files unchanged and record zero-match result in execution notes/PR summary as explicit
    evidence.

### Job 5: Verification

- Run full required validation:
  - `mvn -f client/pom.xml verify -e`
- If failures occur:
  - Fix implementation/tests and rerun until command exits `0`.

## Post-conditions

- [ ] `update-branch` exists as a Java CLI launcher and enforces fast-forward checks by default.
- [ ] Non-fast-forward updates fail without `--force` and succeed with explicit `--force`.
- [ ] New local branches can be created via `update-branch` when no prior branch tip exists.
- [ ] Launcher is wired into jlink output as `update-branch`.
- [ ] Instruction markdown inventory for local branch-ref mutation patterns was executed and outcomes were handled:
  replacements applied where matches exist, or zero-match evidence documented.
- [ ] All tests and build checks pass via `mvn -f client/pom.xml verify -e`.
