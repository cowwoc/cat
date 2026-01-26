# Plan: java-bash-handlers

## Metadata
- **Parent:** migrate-python-to-java
- **Sequence:** 4 of 5
- **Estimated Tokens:** 25K

## Objective
Migrate bash pre/post tool handlers to Java.

## Scope
- bash_handlers/ - Pre-tool validation for bash commands
- bash_posttool_handlers/ - Post-tool validation for bash commands

## Dependencies
- java-core-hooks (core infrastructure must exist)

## Files to Migrate
| Python | Java |
|--------|------|
| bash_handlers/block_lock_manipulation.py | src/cat/hooks/bash/BlockLockManipulation.java |
| bash_handlers/block_main_rebase.py | src/cat/hooks/bash/BlockMainRebase.java |
| bash_handlers/block_merge_commits.py | src/cat/hooks/bash/BlockMergeCommits.java |
| bash_handlers/block_reflog_destruction.py | src/cat/hooks/bash/BlockReflogDestruction.java |
| bash_handlers/compute_box_lines.py | src/cat/hooks/bash/ComputeBoxLines.java |
| bash_handlers/remind_git_squash.py | src/cat/hooks/bash/RemindGitSquash.java |
| bash_handlers/validate_commit_type.py | src/cat/hooks/bash/ValidateCommitType.java |
| bash_handlers/validate_git_filter_branch.py | src/cat/hooks/bash/ValidateGitFilterBranch.java |
| bash_handlers/validate_git_operations.py | src/cat/hooks/bash/ValidateGitOperations.java |
| bash_handlers/warn_file_extraction.py | src/cat/hooks/bash/WarnFileExtraction.java |
| bash_posttool_handlers/detect_concatenated_commit.py | src/cat/hooks/bash/post/DetectConcatenatedCommit.java |
| bash_posttool_handlers/detect_failures.py | src/cat/hooks/bash/post/DetectFailures.java |
| bash_posttool_handlers/validate_rebase_target.py | src/cat/hooks/bash/post/ValidateRebaseTarget.java |
| bash_posttool_handlers/verify_commit_type.py | src/cat/hooks/bash/post/VerifyCommitType.java |

## Execution Steps
1. Migrate pre-tool bash handlers
2. Migrate post-tool bash handlers
3. Ensure regex patterns work identically
4. Verify validation logic matches Python

## Acceptance Criteria
- [ ] All validation handlers produce identical decisions
- [ ] Regex patterns behave identically to Python
- [ ] Test for validate_commit_type passes
