#!/usr/bin/env python3
"""
Unified test runner for CAT plugin (no pytest required).

Runs all tests and reports results. Exit code 0 = all pass, 1 = failures.
"""

import sys
import os
import traceback
from pathlib import Path
from typing import Callable, Any
from tempfile import TemporaryDirectory

# Add paths for imports
PROJECT_ROOT = Path(__file__).parent
sys.path.insert(0, str(PROJECT_ROOT / "plugin" / "hooks"))
sys.path.insert(0, str(PROJECT_ROOT / "plugin" / "scripts"))


class TestRunner:
    """Simple test runner."""

    def __init__(self):
        self.passed = 0
        self.failed = 0
        self.errors = []

    def test(self, name: str, condition: bool, message: str = ""):
        """Record a test result."""
        if condition:
            print(f"  ✓ {name}")
            self.passed += 1
        else:
            print(f"  ✗ {name}: {message}")
            self.failed += 1
            self.errors.append(f"{name}: {message}")

    def run_test_func(self, name: str, func: Callable[[], bool]):
        """Run a test function that returns True/False."""
        try:
            result = func()
            self.test(name, result)
        except Exception as e:
            self.test(name, False, f"Exception: {e}")

    def section(self, name: str):
        """Print a section header."""
        print(f"\n{'='*60}")
        print(f"  {name}")
        print(f"{'='*60}")

    def summary(self) -> bool:
        """Print summary and return True if all passed."""
        print(f"\n{'='*60}")
        print(f"  SUMMARY: {self.passed} passed, {self.failed} failed")
        print(f"{'='*60}")
        return self.failed == 0


runner = TestRunner()


# =============================================================================
# BASH HANDLERS TESTS
# =============================================================================

def test_validate_commit_type():
    """Test validate_commit_type handler."""
    runner.section("bash_handlers/validate_commit_type")

    from bash_handlers.validate_commit_type import ValidateCommitTypeHandler
    handler = ValidateCommitTypeHandler()

    # Test HEREDOC format with valid types
    cmd = '''git commit -m "$(cat <<'EOF'
feature: add new functionality
EOF
)"'''
    result = handler.check(cmd, {})
    runner.test("HEREDOC: valid 'feature' type", result is None)

    cmd = '''git commit -m "$(cat <<'EOF'
bugfix(hooks): fix validation
EOF
)"'''
    result = handler.check(cmd, {})
    runner.test("HEREDOC: valid 'bugfix' with scope", result is None)

    # Test HEREDOC with invalid types
    cmd = '''git commit -m "$(cat <<'EOF'
feat: add feature
EOF
)"'''
    result = handler.check(cmd, {})
    runner.test("HEREDOC: invalid 'feat' blocked",
                result is not None and result.get("decision") == "block")

    cmd = '''git commit -m "$(cat <<'EOF'
fix: resolve bug
EOF
)"'''
    result = handler.check(cmd, {})
    runner.test("HEREDOC: invalid 'fix' blocked",
                result is not None and result.get("decision") == "block")

    # Test simple format
    cmd = 'git commit -m "feature: add login"'
    result = handler.check(cmd, {})
    runner.test("Simple: valid 'feature' type", result is None)

    cmd = 'git commit -m "feat: add feature"'
    result = handler.check(cmd, {})
    runner.test("Simple: invalid 'feat' blocked",
                result is not None and result.get("decision") == "block")

    # Test unparseable formats
    cmd = 'git commit -m $(echo "test")'
    result = handler.check(cmd, {})
    runner.test("Unparseable -m format blocked",
                result is not None and result.get("decision") == "block")

    # Test non-commit commands pass through
    cmd = 'git status'
    result = handler.check(cmd, {})
    runner.test("Non-commit command allowed", result is None)

    cmd = 'git commit'
    result = handler.check(cmd, {})
    runner.test("Interactive commit allowed", result is None)

    # Test all valid types
    for t in ["feature", "bugfix", "docs", "style", "refactor",
              "performance", "test", "config", "planning", "revert"]:
        cmd = f'''git commit -m "$(cat <<'EOF'
{t}: test
EOF
)"'''
        result = handler.check(cmd, {})
        runner.test(f"Valid type '{t}' allowed", result is None)

    # Test all invalid types
    for t in ["feat", "fix", "chore", "build", "ci", "perf"]:
        cmd = f'''git commit -m "$(cat <<'EOF'
{t}: test
EOF
)"'''
        result = handler.check(cmd, {})
        runner.test(f"Invalid type '{t}' blocked",
                    result is not None and result.get("decision") == "block")


# =============================================================================
# SKILL HANDLERS TESTS
# =============================================================================

def test_add_handler():
    """Test add_handler."""
    runner.section("skill_handlers/add_handler")

    from skill_handlers.add_handler import AddHandler
    handler = AddHandler()

    # Test returns None without item_type
    result = handler.handle({})
    runner.test("Returns None without item_type", result is None)

    # Test returns None for invalid item_type
    result = handler.handle({"item_type": "invalid"})
    runner.test("Returns None for invalid item_type", result is None)

    # Test task display
    task_context = {
        "item_type": "task",
        "item_name": "parse-tokens",
        "version": "2.0",
        "task_type": "Feature",
        "dependencies": [],
    }
    result = handler.handle(task_context)
    runner.test("Task returns string", isinstance(result, str))
    runner.test("Task contains OUTPUT TEMPLATE marker", "OUTPUT TEMPLATE ADD DISPLAY" in result)
    runner.test("Task contains task name", "parse-tokens" in result)
    runner.test("Task contains version", "Version: 2.0" in result)
    runner.test("Task contains checkmark", "✅" in result)
    runner.test("Task has box structure", "╭" in result and "╯" in result)

    # Test version display
    version_context = {
        "item_type": "version",
        "item_name": "New Features",
        "version": "2.1",
        "version_type": "minor",
        "parent_info": "v2",
        "path": ".claude/cat/issues/v2/v2.1",
    }
    result = handler.handle(version_context)
    runner.test("Version returns string", isinstance(result, str))
    runner.test("Version contains OUTPUT TEMPLATE marker", "OUTPUT TEMPLATE ADD DISPLAY" in result)
    runner.test("Version contains version name", "v2.1" in result)


def test_display_utils():
    """Test display utility functions in status_handler."""
    runner.section("skill_handlers/status_handler display utils")

    from skill_handlers.status_handler import (
        display_width, build_line, build_border, build_progress_bar, build_inner_box
    )

    # Test display_width
    runner.test("display_width: empty string", display_width("") == 0)
    runner.test("display_width: ASCII", display_width("hello") == 5)
    runner.test("display_width: single emoji", display_width("🐱") == 2)
    runner.test("display_width: emoji with text", display_width("🐱 cat") == 6)
    runner.test("display_width: box chars", display_width("╭╮") == 2)

    # Test build_line
    result = build_line("hello", 10)
    runner.test("build_line: starts with │", result.startswith("│ "))
    runner.test("build_line: ends with │", result.endswith(" │"))
    runner.test("build_line: contains content", "hello" in result)

    result = build_line("hi", 10)
    expected = "│ hi" + " " * 8 + " │"
    runner.test("build_line: correct padding", result == expected)

    # Test build_border
    top = build_border(10, is_top=True)
    runner.test("build_border: top starts with ╭", top.startswith("╭"))
    runner.test("build_border: top ends with ╮", top.endswith("╮"))
    runner.test("build_border: top has dashes", "─" * 12 in top)

    bottom = build_border(10, is_top=False)
    runner.test("build_border: bottom starts with ╰", bottom.startswith("╰"))
    runner.test("build_border: bottom ends with ╯", bottom.endswith("╯"))

    # Test build_progress_bar
    runner.test("progress_bar: 0%", build_progress_bar(0, width=10) == "░" * 10)
    runner.test("progress_bar: 100%", build_progress_bar(100, width=10) == "█" * 10)
    runner.test("progress_bar: 50%", build_progress_bar(50, width=10) == "█" * 5 + "░" * 5)
    runner.test("progress_bar: default width", len(build_progress_bar(0)) == 25)

    # Test build_inner_box
    result = build_inner_box("Header", ["Item 1", "Item 2"])
    runner.test("build_inner_box: returns list", isinstance(result, list))
    runner.test("build_inner_box: correct line count", len(result) == 4)
    runner.test("build_inner_box: header in first line", "Header" in result[0])
    runner.test("build_inner_box: top starts with ╭─", result[0].startswith("╭─"))
    runner.test("build_inner_box: bottom starts with ╰", result[-1].startswith("╰"))


def test_status_handler():
    """Test status_handler."""
    runner.section("skill_handlers/status_handler")

    from skill_handlers.status_handler import StatusHandler, collect_status_data
    handler = StatusHandler()

    # Test returns None without project_root
    result = handler.handle({})
    runner.test("Returns None without project_root", result is None)

    # Test returns None with non-existent path
    result = handler.handle({"project_root": "/nonexistent/path"})
    runner.test("Returns None with invalid path", result is None)

    # Regression test for M223: must use issues/ subdirectory
    # Create temp structure with issues/ subdirectory
    with TemporaryDirectory() as tmp_dir:
        tmp_path = Path(tmp_dir)
        cat_dir = tmp_path / ".claude" / "cat"
        cat_dir.mkdir(parents=True)
        issues_dir = cat_dir / "issues"
        issues_dir.mkdir()

        # Create config files in cat_dir
        (cat_dir / "PROJECT.md").write_text("# Test Project\n")
        (cat_dir / "ROADMAP.md").write_text("# Roadmap\n")

        # Create version structure in issues_dir
        v1_dir = issues_dir / "v1"
        v1_dir.mkdir()
        v10_dir = v1_dir / "v1.0"
        v10_dir.mkdir()
        task_dir = v10_dir / "test-task"
        task_dir.mkdir()
        (task_dir / "STATE.md").write_text("Status: pending\n")
        (task_dir / "PLAN.md").write_text("# Test Task\n")

        # Test collect_status_data finds tasks in issues/
        data = collect_status_data(issues_dir)
        runner.test("M223 regression: finds tasks in issues/ dir",
                    "error" not in data and data.get("overall", {}).get("total", 0) == 1)

        # Test handler uses issues/ subdirectory
        result = handler.handle({"project_root": str(tmp_path)})
        runner.test("M223 regression: handler uses issues/ dir",
                    result is not None and "test-task" in result)


def test_help_handler():
    """Test help_handler."""
    runner.section("skill_handlers/help_handler")

    from skill_handlers.help_handler import HelpHandler
    handler = HelpHandler()

    # Test returns string
    result = handler.handle({})
    runner.test("Returns string", isinstance(result, str))
    runner.test("Contains OUTPUT TEMPLATE marker", "OUTPUT TEMPLATE" in result)
    runner.test("Contains commands", "/cat:" in result)


def test_work_handler():
    """Test work_handler."""
    runner.section("skill_handlers/work_handler")

    from skill_handlers.work_handler import WorkHandler
    handler = WorkHandler()

    # Test with task context - returns template format
    context = {
        "task_id": "2.0-parse-tokens",
        "task_name": "parse-tokens",
        "task_status": "pending",
    }
    result = handler.handle(context)
    runner.test("Returns string with task", isinstance(result, str))
    if result:
        runner.test("Contains OUTPUT TEMPLATE marker", "OUTPUT TEMPLATE" in result)
        runner.test("Contains progress info", "Progress" in result or "Template" in result)


def test_cleanup_handler():
    """Test cleanup_handler."""
    runner.section("skill_handlers/cleanup_handler")

    from skill_handlers.cleanup_handler import CleanupHandler
    handler = CleanupHandler()

    # Test returns None without phase
    result = handler.handle({})
    runner.test("Returns None without phase", result is None)

    # Test survey phase
    context = {
        "phase": "survey",
        "worktrees": [
            {"path": "/workspace/.worktrees/1.0-task", "branch": "1.0-task", "state": ""}
        ],
        "locks": [],
        "branches": ["1.0-old-branch"],
        "stale_remotes": [],
    }
    result = handler.handle(context)
    runner.test("Returns string for survey phase", isinstance(result, str))
    if result:
        runner.test("Contains OUTPUT TEMPLATE marker", "OUTPUT TEMPLATE" in result)


def test_config_loader():
    """Test unified config loader."""
    runner.section("lib/config loader")

    # Import the config module
    import sys
    from pathlib import Path
    lib_path = Path(__file__).parent / "plugin" / "hooks" / "lib"
    if str(lib_path) not in sys.path:
        sys.path.insert(0, str(lib_path))

    from config import load_config, get_config_value, DEFAULTS

    # Test defaults
    runner.test("DEFAULTS has autoRemoveWorktrees", "autoRemoveWorktrees" in DEFAULTS)
    runner.test("DEFAULTS has trust", DEFAULTS.get("trust") == "medium")
    runner.test("DEFAULTS has terminalWidth", DEFAULTS.get("terminalWidth") == 120)

    # Test with temp directory
    with TemporaryDirectory() as tmp_dir:
        tmp_path = Path(tmp_dir)

        # Test: no config returns defaults
        result = load_config(tmp_path)
        runner.test("No config returns defaults", result == DEFAULTS)

        # Create cat dir
        cat_dir = tmp_path / ".claude" / "cat"
        cat_dir.mkdir(parents=True)

        # Test: empty cat dir returns defaults
        result = load_config(tmp_path)
        runner.test("Empty cat dir returns defaults", result == DEFAULTS)

        # Test: base config overrides defaults
        base_config = cat_dir / "cat-config.json"
        base_config.write_text('{"trust": "high"}')
        result = load_config(tmp_path)
        runner.test("Base config overrides defaults", result["trust"] == "high")
        runner.test("Unset keys use defaults", result["verify"] == "changed")

        # Test: local config overrides base
        local_config = cat_dir / "cat-config.local.json"
        local_config.write_text('{"trust": "low", "license": "key123"}')
        result = load_config(tmp_path)
        runner.test("Local config overrides base", result["trust"] == "low")
        runner.test("Local config adds keys", result.get("license") == "key123")

        # Test: invalid local JSON doesn't break loading
        local_config.write_text('{ invalid json }')
        result = load_config(tmp_path)
        runner.test("Invalid local JSON uses base", result["trust"] == "high")

        # Test: get_config_value helper
        local_config.write_text('{"customKey": "customValue"}')
        result = get_config_value("customKey", tmp_path)
        runner.test("get_config_value finds key", result == "customValue")

        result = get_config_value("missing", tmp_path, default="fallback")
        runner.test("get_config_value uses default", result == "fallback")


# =============================================================================
# SCRIPT TESTS (BASH SCRIPTS)
# =============================================================================

def test_get_available_issues_discovery():
    """Test get-available-issues.sh path self-discovery."""
    runner.section("scripts/get-available-issues.sh path discovery")

    import subprocess

    script = PROJECT_ROOT / "plugin" / "scripts" / "get-available-issues.sh"

    # Create a temp CAT project WITH git repo (required for auto-discovery)
    with TemporaryDirectory() as tmp_dir:
        tmp_path = Path(tmp_dir)

        # Initialize git repo first
        subprocess.run(["git", "init", "--quiet"], cwd=str(tmp_path), check=True)
        subprocess.run(["git", "config", "user.email", "test@test.com"], cwd=str(tmp_path))
        subprocess.run(["git", "config", "user.name", "Test"], cwd=str(tmp_path))

        # Create .claude/cat structure
        cat_dir = tmp_path / ".claude" / "cat" / "issues"
        cat_dir.mkdir(parents=True)

        # Create a test task
        task_dir = cat_dir / "v1" / "v1.0" / "test-task"
        task_dir.mkdir(parents=True)
        (task_dir / "STATE.md").write_text("""# State

- **Status:** pending
- **Progress:** 0%
- **Dependencies:** []
- **Last Updated:** 2026-01-01
""")

        # Test 1: Auto-discovery from project root (git repo with .claude/cat)
        result = subprocess.run(
            [str(script), "--session-id", "test-session-1"],
            cwd=str(tmp_path),
            capture_output=True,
            text=True
        )
        runner.test("Auto-discovery from cwd works",
                    "Could not find project" not in result.stdout)

        # Test 2: Auto-discovery from subdirectory
        subdir = tmp_path / "src" / "deep" / "nested"
        subdir.mkdir(parents=True)
        result = subprocess.run(
            [str(script), "--session-id", "test-session-2"],
            cwd=str(subdir),
            capture_output=True,
            text=True
        )
        runner.test("Auto-discovery from subdirectory works",
                    "Could not find project" not in result.stdout)

    # Test 3: Error when not in git repo
    with TemporaryDirectory() as no_git_dir:
        # Create .claude/cat but NO git repo
        (Path(no_git_dir) / ".claude" / "cat").mkdir(parents=True)
        result = subprocess.run(
            [str(script), "--session-id", "test-session-3"],
            cwd=no_git_dir,
            capture_output=True,
            text=True
        )
        runner.test("Error when not in git repo",
                    "Could not find project" in result.stdout)

    # Test 4: Error when no .claude/cat in git repo
    with TemporaryDirectory() as no_cat_dir:
        # Git repo but NO .claude/cat
        subprocess.run(["git", "init", "--quiet"], cwd=no_cat_dir, check=True)
        result = subprocess.run(
            [str(script), "--session-id", "test-session-4"],
            cwd=no_cat_dir,
            capture_output=True,
            text=True
        )
        runner.test("Error when no .claude/cat in git repo",
                    "Could not find project" in result.stdout or
                    "no .claude/cat" in result.stdout)

    # Test 5: From inside a worktree, finds MAIN workspace (not worktree's snapshot)
    # This is critical: locks and task state must be in main workspace
    worktree_path = PROJECT_ROOT.parent / ".worktrees" / "2.1-self-discover-env-vars"
    main_workspace = PROJECT_ROOT.parent  # /workspace
    if worktree_path.exists() and worktree_path != main_workspace:
        # Run from worktree, verify it uses main workspace's .claude/cat
        result = subprocess.run(
            [str(worktree_path / "plugin" / "scripts" / "get-available-issues.sh"),
             "--session-id", "test-session-5"],
            cwd=str(worktree_path),
            capture_output=True,
            text=True
        )
        # Should succeed (not error about missing project)
        # The CAT_DIR should be /workspace/.claude/cat/issues, not worktree's
        runner.test("From worktree: finds main workspace (not worktree snapshot)",
                    "Could not find project" not in result.stdout and
                    result.returncode in [0, 1])
    else:
        runner.test("From worktree: finds main workspace (not worktree snapshot)",
                    True)  # Skip if no worktree exists


# =============================================================================
# MAIN
# =============================================================================

def main():
    """Run all tests."""
    print("\n" + "=" * 60)
    print("  CAT PLUGIN TEST SUITE")
    print("=" * 60)

    # Run all test functions
    test_functions = [
        test_validate_commit_type,
        test_add_handler,
        test_display_utils,
        test_status_handler,
        test_help_handler,
        test_work_handler,
        test_cleanup_handler,
        test_config_loader,
        test_get_available_issues_discovery,
    ]

    for test_func in test_functions:
        try:
            test_func()
        except Exception as e:
            runner.section(f"ERROR in {test_func.__name__}")
            print(f"  Exception: {e}")
            traceback.print_exc()
            runner.failed += 1

    # Print summary
    success = runner.summary()
    sys.exit(0 if success else 1)


if __name__ == "__main__":
    main()
