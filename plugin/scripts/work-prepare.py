#!/usr/bin/env python3
"""
work-prepare.py - Deterministic preparation phase for /cat:work.

Replaces the work-prepare LLM subagent with a deterministic Python script to reduce
prepare phase latency from ~50s to ~4s. Performs all 10 steps of the prepare phase
that were previously done via LLM round-trips.

Usage:
  work-prepare.py --session-id ID --project-dir DIR [--exclude-pattern GLOB] [--issue-id ID] --trust-level LEVEL

Outputs JSON result matching the work-prepare SKILL.md output contract.
"""

import argparse
import json
import os
import re
import subprocess
import sys
from datetime import datetime
from pathlib import Path
from typing import Dict, List, Optional, Any

# Script directory for calling other scripts
SCRIPT_DIR = Path(__file__).parent

# Compiled regex patterns for STATE.md parsing
STATUS_PATTERN = re.compile(r'\*\*Status:\*\*\s+(\S+)')
PROGRESS_PATTERN = re.compile(r'\*\*Progress:\*\*\s+\d+%')
DEPS_PATTERN = re.compile(r'\*\*Dependencies:\*\*\s+\[([^\]]*)\]')
LAST_UPDATED_PATTERN = re.compile(r'\*\*Last Updated:\*\*\s+.*')


def verify_cat_structure(project_dir: Path) -> bool:
    """
    Step 1: Verify .claude/cat/ structure exists.

    Args:
        project_dir: Project root directory

    Returns:
        True if structure exists, False otherwise
    """
    cat_dir = project_dir / ".claude" / "cat"
    config_file = cat_dir / "cat-config.json"

    if not cat_dir.is_dir():
        return False
    if not config_file.is_file():
        return False

    return True


def call_get_available_issues(
    session_id: str,
    project_dir: Path,
    exclude_pattern: Optional[str],
    issue_id: Optional[str]
) -> Dict[str, Any]:
    """
    Step 2: Call get-available-issues.sh to find next task.

    Args:
        session_id: Current session ID
        project_dir: Project root directory
        exclude_pattern: Optional glob pattern to exclude issues
        issue_id: Optional specific issue ID to select

    Returns:
        Parsed JSON result from get-available-issues.sh
    """
    script = SCRIPT_DIR / "get-available-issues.sh"

    if not script.is_file():
        raise RuntimeError(f"Required script not found: {script}")

    cmd = [
        str(script),
        "--scope", "all",
        "--session-id", session_id
    ]

    if exclude_pattern:
        cmd.extend(["--exclude-pattern", exclude_pattern])

    if issue_id:
        cmd.append(issue_id)

    result = subprocess.run(
        cmd,
        cwd=str(project_dir),
        capture_output=True,
        text=True,
        timeout=30
    )

    # Parse JSON output first - get-available-issues.sh uses JSON status field
    # as semantic result, not exit code (exit 1 used for both "not_found" and errors)
    try:
        parsed = json.loads(result.stdout)
    except json.JSONDecodeError:
        # JSON parse failed - this IS an error
        raise RuntimeError(f"get-available-issues.sh failed: {result.stderr}")

    # If JSON parsed successfully, return it regardless of exit code
    # The status field contains the actual result (found, not_found, locked, etc.)
    return parsed


def gather_diagnostic_info(project_dir: Path) -> Dict[str, Any]:
    """
    Step 3: Gather diagnostic info when no tasks are available.

    Scans issue directories to find:
    - Blocked tasks (with cross-version dependency search)
    - Locked tasks
    - Closed/total counts

    Args:
        project_dir: Project root directory

    Returns:
        Dict with blocked_tasks, locked_tasks, closed_count, total_count
    """
    issues_dir = project_dir / ".claude" / "cat" / "issues"

    blocked_tasks = []
    locked_tasks = []
    closed_count = 0
    total_count = 0

    # Build issue index once for O(1) lookup
    issue_index = {}
    for state_file in issues_dir.rglob("STATE.md"):
        issue_name = state_file.parent.name
        with open(state_file, 'r') as f:
            issue_index[issue_name] = {
                "state_file": state_file,
                "content": f.read()
            }

    # Scan all issues using the index
    for issue_name, issue_data in issue_index.items():
        content = issue_data["content"]
        total_count += 1

        # Extract status
        status_match = STATUS_PATTERN.search(content)
        if not status_match:
            continue

        status = status_match.group(1)

        if status == "closed":
            closed_count += 1
            continue

        if status in ["open", "in-progress"]:
            # Check for dependencies
            deps_match = DEPS_PATTERN.search(content)
            if deps_match:
                deps_str = deps_match.group(1).strip()
                if deps_str:
                    # Parse dependencies
                    deps = [d.strip() for d in deps_str.split(',') if d.strip()]

                    # Check each dependency's status using O(1) lookup
                    unresolved_deps = []
                    for dep in deps:
                        dep_data = issue_index.get(dep)

                        if dep_data:
                            dep_content = dep_data["content"]
                            dep_status_match = STATUS_PATTERN.search(dep_content)
                            if dep_status_match:
                                dep_status = dep_status_match.group(1)
                                if dep_status != "closed":
                                    unresolved_deps.append({
                                        "id": dep,
                                        "status": dep_status
                                    })
                            else:
                                unresolved_deps.append({
                                    "id": dep,
                                    "status": "unknown"
                                })
                        else:
                            # Dependency not found
                            unresolved_deps.append({
                                "id": dep,
                                "status": "not_found"
                            })

                    # If there are unresolved dependencies, add to blocked_tasks
                    if unresolved_deps:
                        blocked_by = [d["id"] for d in unresolved_deps]
                        reason = ", ".join(f"{d['id']} ({d['status']})" for d in unresolved_deps)
                        blocked_tasks.append({
                            "issue_id": issue_name,
                            "blocked_by": blocked_by,
                            "reason": reason
                        })

    # Check for locked tasks
    locks_dir = project_dir / ".claude" / "cat" / "locks"
    if locks_dir.is_dir():
        for lock_file in locks_dir.glob("*.lock"):
            try:
                with open(lock_file, 'r') as f:
                    lock_data = json.load(f)

                issue_id = lock_file.stem
                locked_tasks.append({
                    "issue_id": issue_id,
                    "locked_by": lock_data.get("session_id", "unknown")
                })
            except Exception:
                pass

    return {
        "blocked_tasks": blocked_tasks,
        "locked_tasks": locked_tasks,
        "closed_count": closed_count,
        "total_count": total_count
    }


def estimate_tokens(plan_path: Path) -> int:
    """
    Step 4: Estimate tokens heuristically from PLAN.md.

    Heuristic:
    - Files to create: 5000 tokens each
    - Files to modify: 3000 tokens each
    - Test files: 4000 tokens each
    - Steps: 2000 tokens each

    Args:
        plan_path: Path to PLAN.md file

    Returns:
        Estimated token count
    """
    if not plan_path.is_file():
        return 10000  # Default fallback

    with open(plan_path, 'r') as f:
        content = f.read()

    # Count files to create
    create_section = re.search(r'## Files to Create\s+(.*?)(?=\n##|\Z)', content, re.DOTALL)
    files_to_create = 0
    if create_section:
        # Count bullet points or lines with file paths
        create_lines = [line for line in create_section.group(1).split('\n') if line.strip().startswith('-')]
        files_to_create = len(create_lines)

    # Count files to modify
    modify_section = re.search(r'## Files to Modify\s+(.*?)(?=\n##|\Z)', content, re.DOTALL)
    files_to_modify = 0
    if modify_section:
        modify_lines = [line for line in modify_section.group(1).split('\n') if line.strip().startswith('-')]
        files_to_modify = len(modify_lines)

    # Count test files (in Files to Create or Files to Modify with "test" in name)
    test_files = 0
    if create_section:
        test_files += len([line for line in create_section.group(1).split('\n')
                          if line.strip().startswith('-') and 'test' in line.lower()])
    if modify_section:
        test_files += len([line for line in modify_section.group(1).split('\n')
                          if line.strip().startswith('-') and 'test' in line.lower()])

    # Count execution steps
    steps_section = re.search(r'## Execution Steps\s+(.*?)(?=\n##|\Z)', content, re.DOTALL)
    steps = 0
    if steps_section:
        steps_lines = [line for line in steps_section.group(1).split('\n')
                      if re.match(r'^\s*\d+\.', line)]
        steps = len(steps_lines)

    # Calculate estimate
    estimate = (
        files_to_create * 5000 +
        files_to_modify * 3000 +
        test_files * 4000 +
        steps * 2000
    )

    # Add base overhead
    estimate += 10000

    return estimate


def create_worktree(
    project_dir: Path,
    issue_branch: str,
    base_branch: str
) -> Path:
    """
    Step 5: Create worktree via git worktree add.

    Args:
        project_dir: Project root directory
        issue_branch: Branch name for the issue
        base_branch: Base branch to branch from

    Returns:
        Path to created worktree

    Raises:
        RuntimeError: If worktree creation fails
    """
    worktree_path = project_dir / ".claude" / "cat" / "worktrees" / issue_branch

    # Check if branch already exists (stale from previous session)
    branch_check = subprocess.run(
        ["git", "rev-parse", "--verify", issue_branch],
        cwd=str(project_dir),
        capture_output=True,
        text=True,
        timeout=30
    )

    if branch_check.returncode == 0:
        # Branch exists - delete it first (stale from previous session that crashed without cleanup)
        subprocess.run(
            ["git", "branch", "-D", issue_branch],
            cwd=str(project_dir),
            capture_output=True,
            text=True,
            timeout=30
        )

    # Create worktree
    result = subprocess.run(
        ["git", "worktree", "add", "-b", issue_branch, str(worktree_path), "HEAD"],
        cwd=str(project_dir),
        capture_output=True,
        text=True,
        timeout=30
    )

    if result.returncode != 0:
        raise RuntimeError(f"Failed to create worktree: {result.stderr}")

    # Write cat-base file
    rev_parse_result = subprocess.run(
        ["git", "rev-parse", "--git-common-dir"],
        cwd=str(project_dir),
        capture_output=True,
        text=True,
        timeout=30
    )

    if rev_parse_result.returncode != 0:
        raise RuntimeError(f"git rev-parse failed: {rev_parse_result.stderr}")

    git_common_dir = rev_parse_result.stdout.strip()

    cat_base_file = Path(git_common_dir) / "worktrees" / issue_branch / "cat-base"
    cat_base_file.parent.mkdir(parents=True, exist_ok=True)
    cat_base_file.write_text(base_branch)

    return worktree_path


def verify_worktree_branch(worktree_path: Path, expected_branch: str) -> bool:
    """
    Step 6: Verify worktree branch matches expected (M351).

    Args:
        worktree_path: Path to worktree
        expected_branch: Expected branch name

    Returns:
        True if branch matches, False otherwise
    """
    result = subprocess.run(
        ["git", "branch", "--show-current"],
        cwd=str(worktree_path),
        capture_output=True,
        text=True,
        timeout=30
    )

    if result.returncode != 0:
        return False

    actual_branch = result.stdout.strip()
    return actual_branch == expected_branch


def check_existing_work(worktree_path: Path, base_branch: str) -> Dict[str, Any]:
    """
    Step 7: Call check-existing-work.sh for existing commits (M362/M394).

    Args:
        worktree_path: Path to worktree
        base_branch: Base branch to compare against

    Returns:
        Dict with has_existing_work, existing_commits, commit_summary
    """
    script = SCRIPT_DIR / "check-existing-work.sh"

    if not script.is_file():
        raise RuntimeError(f"Required script not found: {script}")

    result = subprocess.run(
        [str(script), "--worktree", str(worktree_path), "--base-branch", base_branch],
        capture_output=True,
        text=True,
        timeout=10
    )

    if result.returncode != 0:
        raise RuntimeError(f"check-existing-work.sh failed: {result.stderr}")

    return json.loads(result.stdout)


def check_base_branch_commits(
    project_dir: Path,
    base_branch: str,
    issue_name: str
) -> Optional[str]:
    """
    Step 8: Check base branch for suspicious commits mentioning issue name (M394).

    Args:
        project_dir: Project root directory
        base_branch: Base branch to search
        issue_name: Issue name to search for

    Returns:
        Commit summary if found, None otherwise
    """
    result = subprocess.run(
        ["git", "log", "--oneline", f"--grep={issue_name}", base_branch, "-5"],
        cwd=str(project_dir),
        capture_output=True,
        text=True,
        timeout=30
    )

    if result.returncode != 0 or not result.stdout.strip():
        return None

    # Filter out planning commits that just add issue definitions (false positives).
    # These mention the issue name but don't implement it.
    planning_prefixes = ("planning:", "config: add issue", "config: add task", "config: mark", "config: decompose")
    lines = result.stdout.strip().splitlines()
    filtered = []
    for line in lines:
        # Extract commit message (after hash + space)
        msg = line.split(" ", 1)[1] if " " in line else line
        if not msg.lower().startswith(planning_prefixes):
            filtered.append(line)

    return "\n".join(filtered) if filtered else None


def update_state_md(worktree_path: Path, issue_path: Path, project_dir: Path) -> None:
    """
    Step 9: Update STATE.md in worktree to in-progress.

    Args:
        worktree_path: Path to worktree
        issue_path: Path to issue directory (absolute path)
        project_dir: Project root directory
    """
    # STATE.md is in the worktree's copy of the issue directory
    # Use relative_to(project_dir) to get the path relative to project root
    state_file = worktree_path / issue_path.relative_to(project_dir) / "STATE.md"

    if not state_file.is_file():
        raise RuntimeError(f"STATE.md not found in worktree: {state_file}")

    # Read current STATE.md
    with open(state_file, 'r') as f:
        content = f.read()

    # Update status and last updated
    content = STATUS_PATTERN.sub(
        '**Status:** in-progress',
        content
    )
    content = PROGRESS_PATTERN.sub(
        '**Progress:** 0%',
        content
    )
    content = LAST_UPDATED_PATTERN.sub(
        f'**Last Updated:** {datetime.now().strftime("%Y-%m-%d")}',
        content
    )

    # Write back
    with open(state_file, 'w') as f:
        f.write(content)


def release_lock(project_dir: Path, issue_id: str, session_id: str) -> None:
    """
    Release lock on failure (best-effort).

    Args:
        project_dir: Project root directory
        issue_id: Issue ID to release lock for
        session_id: Current session ID
    """
    script = SCRIPT_DIR / "issue-lock.sh"

    if not script.is_file():
        return  # Best-effort - if script doesn't exist, can't release lock

    try:
        subprocess.run(
            [str(script), "release", str(project_dir), issue_id, session_id],
            capture_output=True,
            timeout=10
        )
    except Exception:
        pass  # Best-effort


def cleanup_worktree(project_dir: Path, worktree_path: Path) -> None:
    """
    Remove worktree on failure (best-effort).

    Args:
        project_dir: Project root directory
        worktree_path: Path to worktree to remove
    """
    try:
        subprocess.run(
            ["git", "worktree", "remove", str(worktree_path), "--force"],
            cwd=str(project_dir),
            capture_output=True,
            timeout=10
        )
    except Exception:
        pass  # Best-effort


def read_goal_from_plan(plan_path: Path) -> str:
    """
    Read the goal from PLAN.md.

    Args:
        plan_path: Path to PLAN.md

    Returns:
        Goal text (first paragraph after ## Goal heading)
    """
    if not plan_path.is_file():
        return "No goal found"

    with open(plan_path, 'r') as f:
        lines = f.readlines()

    # Find ## Goal heading
    goal_start = None
    for i, line in enumerate(lines):
        if line.strip().startswith("## Goal"):
            goal_start = i + 1
            break

    if goal_start is None:
        return "No goal found"

    # Extract text until next ## heading or end of file
    goal_lines = []
    for i in range(goal_start, len(lines)):
        line = lines[i]
        if line.strip().startswith("##"):
            break
        goal_lines.append(line.rstrip())

    # Join and strip leading/trailing whitespace
    goal = "\n".join(goal_lines).strip()

    # Return first paragraph (up to blank line)
    paragraphs = goal.split("\n\n")
    if paragraphs:
        return paragraphs[0].strip()
    return goal


def main():
    parser = argparse.ArgumentParser(description="Deterministic preparation phase for /cat:work")
    parser.add_argument("--session-id", required=True, help="Current session ID")
    parser.add_argument("--project-dir", required=True, help="Project root directory")
    parser.add_argument("--exclude-pattern", required=False, help="Glob pattern to exclude issues")
    parser.add_argument("--issue-id", required=False, help="Specific issue ID to select (overrides priority-based selection)")
    parser.add_argument("--trust-level", required=True, choices=["low", "medium", "high"],
                       help="Trust level for execution")

    args = parser.parse_args()

    # Fail-fast: project-dir must not be empty
    if not args.project_dir:
        print(json.dumps({
            "status": "ERROR",
            "message": "project-dir cannot be empty"
        }))
        sys.exit(1)

    project_dir = Path(args.project_dir)

    try:
        # Step 1: Verify CAT structure
        if not verify_cat_structure(project_dir):
            print(json.dumps({
                "status": "ERROR",
                "message": "No .claude/cat/ directory or cat-config.json found"
            }))
            sys.exit(1)

        # Step 2: Find available task
        discovery_result = call_get_available_issues(
            args.session_id,
            project_dir,
            args.exclude_pattern,
            args.issue_id
        )

        status = discovery_result.get("status")

        # Handle non-found statuses
        if status == "locked":
            print(json.dumps({
                "status": "LOCKED",
                "message": discovery_result.get("message", "Task is locked"),
                "issue_id": discovery_result.get("issue_id", ""),
                "locked_by": discovery_result.get("locked_by", "")
            }))
            sys.exit(0)

        if status == "not_found":
            # Step 3: Gather diagnostic info
            diagnostics = gather_diagnostic_info(project_dir)

            result = {
                "status": "NO_TASKS",
                "message": "No executable tasks available",
                "suggestion": "Use /cat:status to see available tasks"
            }

            # Add diagnostic info
            if diagnostics["blocked_tasks"]:
                result["blocked_tasks"] = diagnostics["blocked_tasks"]
            if diagnostics["locked_tasks"]:
                result["locked_tasks"] = diagnostics["locked_tasks"]

            result["closed_count"] = diagnostics["closed_count"]
            result["total_count"] = diagnostics["total_count"]

            print(json.dumps(result, indent=2))
            sys.exit(0)

        if status != "found":
            print(json.dumps({
                "status": "ERROR",
                "message": f"Unexpected discovery status: {status}"
            }))
            sys.exit(1)

        # Extract issue info from discovery result
        issue_id = discovery_result["issue_id"]
        issue_path = Path(discovery_result["issue_path"])
        major = discovery_result.get("major", "")
        minor = discovery_result.get("minor", "")
        issue_name = discovery_result.get("issue_name", "")

        # Get base branch (current branch)
        base_branch_result = subprocess.run(
            ["git", "branch", "--show-current"],
            cwd=str(project_dir),
            capture_output=True,
            text=True,
            timeout=30
        )

        if base_branch_result.returncode != 0:
            raise RuntimeError(f"git branch --show-current failed: {base_branch_result.stderr}")

        base_branch = base_branch_result.stdout.strip()

        # Step 4: Estimate tokens
        plan_path = issue_path / "PLAN.md"
        estimated_tokens = estimate_tokens(plan_path)

        # Check if oversized (hard limit: 160K tokens)
        if estimated_tokens > 160000:
            print(json.dumps({
                "status": "OVERSIZED",
                "message": f"Task estimated at {estimated_tokens} tokens (limit: 160000)",
                "suggestion": "Use /cat:decompose-issue to break into smaller tasks",
                "issue_id": issue_id,
                "estimated_tokens": estimated_tokens
            }))
            sys.exit(0)

        # Step 5: Create worktree
        issue_branch = f"{major}.{minor}-{issue_name}"
        try:
            worktree_path = create_worktree(project_dir, issue_branch, base_branch)
        except Exception as e:
            release_lock(project_dir, issue_id, args.session_id)
            print(json.dumps({
                "status": "ERROR",
                "message": f"Failed to create worktree: {str(e)}"
            }))
            sys.exit(1)

        # Step 6: Verify worktree branch
        if not verify_worktree_branch(worktree_path, issue_branch):
            cleanup_worktree(project_dir, worktree_path)
            release_lock(project_dir, issue_id, args.session_id)
            print(json.dumps({
                "status": "ERROR",
                "message": f"Worktree created on wrong branch (expected: {issue_branch})"
            }))
            sys.exit(1)

        # Step 7: Check for existing work
        try:
            existing_work = check_existing_work(worktree_path, base_branch)
        except Exception as e:
            cleanup_worktree(project_dir, worktree_path)
            release_lock(project_dir, issue_id, args.session_id)
            print(json.dumps({
                "status": "ERROR",
                "message": f"Failed to check existing work: {str(e)}"
            }))
            sys.exit(1)

        # Step 8: Check base branch for suspicious commits
        suspicious_commits = check_base_branch_commits(project_dir, base_branch, issue_name)

        # Step 9: Update STATE.md in worktree
        try:
            update_state_md(worktree_path, issue_path, project_dir)
        except Exception as e:
            cleanup_worktree(project_dir, worktree_path)
            release_lock(project_dir, issue_id, args.session_id)
            print(json.dumps({
                "status": "ERROR",
                "message": f"Failed to update STATE.md: {str(e)}"
            }))
            sys.exit(1)

        # Read goal from PLAN.md
        goal = read_goal_from_plan(plan_path)

        # Step 10: Return READY JSON
        result = {
            "status": "READY",
            "issue_id": issue_id,
            "major": major,
            "minor": minor,
            "issue_name": issue_name,
            "issue_path": str(issue_path),
            "worktree_path": str(worktree_path),
            "branch": issue_branch,
            "base_branch": base_branch,
            "estimated_tokens": estimated_tokens,
            "percent_of_threshold": int((estimated_tokens / 160000) * 100),
            "goal": goal,
            "approach_selected": "auto",
            "lock_acquired": True,
            "has_existing_work": existing_work.get("has_existing_work", False),
            "existing_commits": existing_work.get("existing_commits", 0),
            "commit_summary": existing_work.get("commit_summary", "")
        }

        # Add potentially_complete flag if suspicious commits found
        if suspicious_commits:
            result["potentially_complete"] = True
            result["suspicious_commits"] = suspicious_commits

        print(json.dumps(result, indent=2))
        sys.exit(0)

    except Exception as e:
        # Cleanup on unexpected error
        print(json.dumps({
            "status": "ERROR",
            "message": f"Unexpected error: {str(e)}"
        }), file=sys.stderr)
        sys.exit(1)


if __name__ == "__main__":
    main()
