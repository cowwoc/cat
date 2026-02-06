# Plan: java-bash-handlers

## Metadata
- **Parent:** migrate-python-to-java
- **Sequence:** 4 of 5 (Wave 3 - concurrent with java-skill-handlers and java-other-handlers)
- **Estimated Tokens:** 25K

## Objective
Create Java equivalents for 3 missing bash handlers and verify all 14 existing Java bash handlers produce identical
output to Python.

## Scope
- 10 Java bash pre-tool handlers already exist - verify output matches Python
- 4 Java bash post-tool handlers already exist - verify output matches Python
- 3 Python bash handlers have NO Java equivalent yet - create them

## Dependencies
- java-core-hooks (entry points must be wired up)

## Existing Java Implementations (verify only)

Pre-tool handlers at `plugin/hooks/src/io/github/cowwoc/cat/hooks/bash/`:

| Python | Java (exists) |
|--------|---------------|
| `bash_handlers/block_lock_manipulation.py` | `BlockLockManipulation.java` |
| `bash_handlers/block_main_rebase.py` | `BlockMainRebase.java` |
| `bash_handlers/block_merge_commits.py` | `BlockMergeCommits.java` |
| `bash_handlers/block_reflog_destruction.py` | `BlockReflogDestruction.java` |
| `bash_handlers/compute_box_lines.py` | `ComputeBoxLines.java` |
| `bash_handlers/remind_git_squash.py` | `RemindGitSquash.java` |
| `bash_handlers/validate_commit_type.py` | `ValidateCommitType.java` |
| `bash_handlers/validate_git_filter_branch.py` | `ValidateGitFilterBranch.java` |
| `bash_handlers/validate_git_operations.py` | `ValidateGitOperations.java` |
| `bash_handlers/warn_file_extraction.py` | `WarnFileExtraction.java` |

Post-tool handlers at `plugin/hooks/src/io/github/cowwoc/cat/hooks/bash/post/`:

| Python | Java (exists) |
|--------|---------------|
| `bash_posttool_handlers/detect_concatenated_commit.py` | `DetectConcatenatedCommit.java` |
| `bash_posttool_handlers/detect_failures.py` | `DetectFailures.java` |
| `bash_posttool_handlers/validate_rebase_target.py` | `ValidateRebaseTarget.java` |
| `bash_posttool_handlers/verify_commit_type.py` | `VerifyCommitType.java` |

## Missing Java Implementations (create new)

| Python | Java (to create) | Location |
|--------|------------------|----------|
| `bash_handlers/detect_shell_operators.py` | `DetectShellOperators.java` | `plugin/hooks/src/io/github/cowwoc/cat/hooks/bash/` |
| `bash_handlers/validate_plan_md.py` | `ValidatePlanMd.java` | `plugin/hooks/src/io/github/cowwoc/cat/hooks/bash/` |
| `bash_handlers/validate_worktree_remove.py` | `ValidateWorktreeRemove.java` | `plugin/hooks/src/io/github/cowwoc/cat/hooks/bash/` |

## Execution Steps
1. **Create DetectShellOperators.java** - Port logic from `detect_shell_operators.py`
2. **Create ValidatePlanMd.java** - Port logic from `validate_plan_md.py`
3. **Create ValidateWorktreeRemove.java** - Port logic from `validate_worktree_remove.py`
4. **Register new handlers** in `BashHandler.java` dispatcher
5. **Verify all 17 handlers** produce identical output to Python equivalents
6. **Run test suite** - `python3 /workspace/run_tests.py` to verify no regressions

## Acceptance Criteria
- [ ] 3 new Java bash handlers created and registered
- [ ] All 17 bash handlers (10 pre + 4 post + 3 new) produce identical decisions to Python
- [ ] Regex patterns behave identically to Python
- [ ] All existing tests pass
