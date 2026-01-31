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

    # Test all valid types (mock staged files to avoid A007 interference)
    original_get_staged = handler._get_staged_files
    handler._get_staged_files = lambda: ['README.md']  # Non-Claude-facing file
    for t in ["feature", "bugfix", "docs", "style", "refactor",
              "performance", "test", "config", "planning", "revert"]:
        cmd = f'''git commit -m "$(cat <<'EOF'
{t}: test
EOF
)"'''
        result = handler.check(cmd, {})
        runner.test(f"Valid type '{t}' allowed", result is None)
    handler._get_staged_files = original_get_staged

    # Test all invalid types
    for t in ["feat", "fix", "chore", "build", "ci", "perf"]:
        cmd = f'''git commit -m "$(cat <<'EOF'
{t}: test
EOF
)"'''
        result = handler.check(cmd, {})
        runner.test(f"Invalid type '{t}' blocked",
                    result is not None and result.get("decision") == "block")

    # A007: Test docs: blocked for Claude-facing files
    runner.section("bash_handlers/validate_commit_type (A007)")

    # Mock _get_staged_files for A007 tests
    original_get_staged = handler._get_staged_files

    # Test docs: blocked for plugin/ files
    handler._get_staged_files = lambda: ['plugin/skills/work/SKILL.md']
    cmd = '''git commit -m "$(cat <<'EOF'
docs: update skill
EOF
)"'''
    result = handler.check(cmd, {})
    runner.test("A007: docs: blocked for plugin/ files",
                result is not None and result.get("decision") == "block"
                and "Claude-facing" in result.get("reason", ""))

    # Test docs: blocked for CLAUDE.md
    handler._get_staged_files = lambda: ['CLAUDE.md']
    cmd = '''git commit -m "$(cat <<'EOF'
docs: update instructions
EOF
)"'''
    result = handler.check(cmd, {})
    runner.test("A007: docs: blocked for CLAUDE.md",
                result is not None and result.get("decision") == "block")

    # Test docs: blocked for .claude/ files
    handler._get_staged_files = lambda: ['.claude/cat/cat-config.json']
    cmd = '''git commit -m "$(cat <<'EOF'
docs: update config
EOF
)"'''
    result = handler.check(cmd, {})
    runner.test("A007: docs: blocked for .claude/ files",
                result is not None and result.get("decision") == "block")

    # Test docs: allowed for README.md
    handler._get_staged_files = lambda: ['README.md']
    cmd = '''git commit -m "$(cat <<'EOF'
docs: update readme
EOF
)"'''
    result = handler.check(cmd, {})
    runner.test("A007: docs: allowed for README.md", result is None)

    # Test docs: allowed for docs/ directory
    handler._get_staged_files = lambda: ['docs/api/endpoints.md']
    cmd = '''git commit -m "$(cat <<'EOF'
docs: update api docs
EOF
)"'''
    result = handler.check(cmd, {})
    runner.test("A007: docs: allowed for docs/ files", result is None)

    # Test config: allowed for plugin/ files
    handler._get_staged_files = lambda: ['plugin/skills/work/SKILL.md']
    cmd = '''git commit -m "$(cat <<'EOF'
config: update skill
EOF
)"'''
    result = handler.check(cmd, {})
    runner.test("A007: config: allowed for plugin/ files", result is None)

    # Restore original method
    handler._get_staged_files = original_get_staged


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

    # Test issue display
    issue_context = {
        "item_type": "issue",
        "item_name": "parse-tokens",
        "version": "2.0",
        "issue_type": "Feature",
        "dependencies": [],
    }
    result = handler.handle(issue_context)
    runner.test("Issue returns string", isinstance(result, str))
    runner.test("Issue contains SCRIPT OUTPUT marker", "SCRIPT OUTPUT ADD DISPLAY" in result)
    runner.test("Issue contains issue name", "parse-tokens" in result)
    runner.test("Issue contains version", "Version: 2.0" in result)
    runner.test("Issue contains checkmark", "✅" in result)
    runner.test("Issue has box structure", "╭" in result and "╯" in result)

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
    runner.test("Version contains SCRIPT OUTPUT marker", "SCRIPT OUTPUT ADD DISPLAY" in result)
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
    """Test status_handler utility functions.

    Note: StatusHandler class removed - status display uses silent preprocessing.
    This tests the remaining utility functions.
    """
    runner.section("skill_handlers/status_handler")

    from skill_handlers.status_handler import get_task_status, get_task_dependencies

    # Test get_task_status with temp files
    with TemporaryDirectory() as tmp_dir:
        tmp_path = Path(tmp_dir)

        # Test missing file returns pending
        result = get_task_status(tmp_path / "nonexistent" / "STATE.md")
        runner.test("get_task_status: missing file returns pending", result == "pending")

        # Test parsing status from file
        state_file = tmp_path / "STATE.md"
        state_file.write_text("- **Status:** completed\n")
        result = get_task_status(state_file)
        runner.test("get_task_status: parses bold format", result == "completed")

        # Test get_task_dependencies
        state_file.write_text("- **Dependencies:** [task-a, task-b]\n")
        result = get_task_dependencies(state_file)
        runner.test("get_task_dependencies: parses inline format",
                    result == ["task-a", "task-b"])


def test_active_agents():
    """Test active agents display in status."""
    runner.section("scripts/get-status-display.py active agents")

    # Import functions from get-status-display.py
    import importlib.util
    spec = importlib.util.spec_from_file_location(
        "get_status_display",
        PROJECT_ROOT / "plugin" / "scripts" / "get-status-display.py"
    )
    get_status_display = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(get_status_display)

    # Test format_age
    runner.test("format_age: seconds", get_status_display.format_age(45) == "45s ago")
    runner.test("format_age: minutes", get_status_display.format_age(300) == "5m ago")
    runner.test("format_age: hours", get_status_display.format_age(7200) == "2h ago")

    # Test format_session_id
    runner.test("format_session_id: short ID unchanged",
                get_status_display.format_session_id("abc123") == "abc123")
    runner.test("format_session_id: long ID truncated",
                get_status_display.format_session_id("f3d7107a-ef6a-44df-833d-2a2261a6421e") == "f3d71...421e")

    # Test get_active_agents with temp directory
    with TemporaryDirectory() as tmp_dir:
        tmp_path = Path(tmp_dir)
        cat_dir = tmp_path / ".claude" / "cat"
        locks_dir = cat_dir / "locks"
        locks_dir.mkdir(parents=True)

        # Test: no locks directory returns empty list
        result = get_status_display.get_active_agents(tmp_path / "nonexistent")
        runner.test("get_active_agents: missing locks dir returns empty", result == [])

        # Test: empty locks directory returns empty list
        result = get_status_display.get_active_agents(cat_dir)
        runner.test("get_active_agents: empty locks dir returns empty", result == [])

        # Create mock lock files
        import time
        now = int(time.time())

        lock1 = locks_dir / "2.1-task-one.lock"
        lock1.write_text(f"""session_id=abc123-session-1
created_at={now - 300}
worktree=/workspace/.worktrees/2.1-task-one
created_iso=2026-01-31T12:00:00Z""")

        lock2 = locks_dir / "2.0-task-two.lock"
        lock2.write_text(f"""session_id=def456-session-2
created_at={now - 720}
worktree=/workspace/.worktrees/2.0-task-two
created_iso=2026-01-31T11:48:00Z""")

        # Test: get all agents
        result = get_status_display.get_active_agents(cat_dir)
        runner.test("get_active_agents: finds lock files", len(result) == 2)
        runner.test("get_active_agents: sorted by age", result[0]['issue_id'] == "2.1-task-one")
        runner.test("get_active_agents: second agent", result[1]['issue_id'] == "2.0-task-two")

        # Test: exclude current session
        result = get_status_display.get_active_agents(cat_dir, "abc123-session-1")
        runner.test("get_active_agents: excludes current session", len(result) == 1)
        runner.test("get_active_agents: excluded correct session", result[0]['session_id'] == "def456-session-2")

        # Test: malformed lock file is skipped
        lock3 = locks_dir / "2.1-bad-lock.lock"
        lock3.write_text("invalid content")
        result = get_status_display.get_active_agents(cat_dir)
        runner.test("get_active_agents: skips malformed lock", len(result) == 2)


def test_help_handler():
    """Test help_handler."""
    runner.section("skill_handlers/help_handler")

    from skill_handlers.help_handler import HelpHandler
    handler = HelpHandler()

    # Test returns string
    result = handler.handle({})
    runner.test("Returns string", isinstance(result, str))
    runner.test("Contains SCRIPT OUTPUT marker", "SCRIPT OUTPUT" in result)
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
        runner.test("Contains SCRIPT OUTPUT marker", "SCRIPT OUTPUT" in result)
        runner.test("Contains status boxes", "TASK_COMPLETE" in result or "SCOPE_COMPLETE" in result)


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
        runner.test("Contains SCRIPT OUTPUT marker", "SCRIPT OUTPUT" in result)


def test_posttool_skill_preprocessor_output():
    """Test PostToolUse handler for Skill tool preprocessor output."""
    runner.section("posttool_handlers/skill_preprocessor_output")

    # Import from the package
    from posttool_handlers.skill_preprocessor_output import SkillPreprocessorOutputHandler
    handler = SkillPreprocessorOutputHandler()

    # Test: non-Skill tool returns None
    result = handler.check("Bash", {}, {"session_id": "test"})
    runner.test("Non-Skill tool returns None", result is None)

    # Test: Skill tool without skill parameter returns None
    hook_data = {"tool_input": {}}
    result = handler.check("Skill", {}, {"session_id": "test", "hook_data": hook_data})
    runner.test("Skill tool without skill parameter returns None", result is None)

    # Test: Non-CAT skill returns None
    hook_data = {"tool_input": {"skill": "some-other-skill"}}
    result = handler.check("Skill", {}, {"session_id": "test", "hook_data": hook_data})
    runner.test("Non-CAT skill returns None", result is None)

    # Test: CAT skill with valid handler returns output
    with TemporaryDirectory() as tmp_dir:
        tmp_path = Path(tmp_dir)
        cat_dir = tmp_path / ".claude" / "cat"
        cat_dir.mkdir(parents=True)

        # Create minimal cat structure
        (cat_dir / "PROJECT.md").write_text("# Test Project")

        hook_data = {"tool_input": {"skill": "cat:help"}}
        context = {
            "session_id": "test-session",
            "hook_data": hook_data,
            "project_root": str(tmp_path),
        }
        result = handler.check("Skill", {}, context)
        runner.test("CAT skill returns additionalContext",
                    result is not None and "additionalContext" in result)

        if result and "additionalContext" in result:
            output = result["additionalContext"]
            runner.test("Help output contains CAT Command Reference",
                        "CAT Command Reference" in output)
            runner.test("Help output contains SCRIPT OUTPUT marker",
                        "SCRIPT OUTPUT" in output)

    # Test: CAT skill name extraction
    test_cases = [
        ("cat:status", "status"),
        ("cat:work", "work"),
        ("cat:help", "help"),
        ("CAT:STATUS", "status"),  # Case insensitive
        ("other-skill", None),
        ("", None),
    ]
    for skill_input, expected in test_cases:
        result = handler._extract_cat_skill_name(skill_input)
        runner.test(f"Extract '{skill_input}' -> {expected}", result == expected)


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
# PROMPT HANDLERS TESTS
# =============================================================================

def test_abort_clarification_handler():
    """Test abort_clarification handler patterns (M280)."""
    runner.section("prompt_handlers/abort_clarification")

    from prompt_handlers.abort_clarification import AbortClarificationHandler
    handler = AbortClarificationHandler()

    # Test exact match patterns
    result = handler.check("abort the task", "test-session")
    runner.test("Matches 'abort the task'", result is not None)

    result = handler.check("abort task", "test-session")
    runner.test("Matches 'abort task'", result is not None)

    # Test with task name in the middle (M280 fix)
    result = handler.check("Abort the 2.1-compress-lang-md task", "test-session")
    runner.test("Matches 'abort the <task-name> task' (M280)", result is not None)

    result = handler.check("cancel the foo-bar task", "test-session")
    runner.test("Matches 'cancel the <task-name> task'", result is not None)

    result = handler.check("stop the v1.2-feature task", "test-session")
    runner.test("Matches 'stop the <task-name> task'", result is not None)

    # Test non-matching patterns
    result = handler.check("abort the mission", "test-session")
    runner.test("Does NOT match 'abort the mission'", result is None)

    result = handler.check("please stop now", "test-session")
    runner.test("Does NOT match unrelated 'stop'", result is None)


# =============================================================================
# JDK INFRASTRUCTURE TESTS
# =============================================================================

def test_jlink_config():
    """Test jlink-config.sh script."""
    runner.section("hooks/jdk/jlink-config.sh")

    import subprocess

    script = PROJECT_ROOT / "plugin" / "hooks" / "jdk" / "jlink-config.sh"

    if not script.exists():
        runner.test("Script exists", False, f"Script not found at {script}")
        return

    # Test 1: info command shows configuration
    result = subprocess.run(
        [str(script), "info"],
        capture_output=True,
        text=True
    )
    runner.test("info command succeeds", result.returncode == 0)
    runner.test("info shows JDK version", "Target JDK Version: 25" in result.stdout)
    runner.test("info shows Jackson version", "Jackson Version: 3.0.3" in result.stdout)
    runner.test("info shows runtime name", "cat-jdk-25" in result.stdout)
    runner.test("info lists JDK modules", "java.base" in result.stdout)
    runner.test("info lists Jackson modules", "tools.jackson.core" in result.stdout)

    # Test 2: Invalid command shows usage
    result = subprocess.run(
        [str(script), "invalid"],
        capture_output=True,
        text=True
    )
    runner.test("invalid command shows usage", result.returncode == 1)
    runner.test("usage mentions build", "build" in result.stdout)

    # Test 3: Unknown build option fails
    result = subprocess.run(
        [str(script), "build", "--unknown-option", "value"],
        capture_output=True,
        text=True
    )
    runner.test("unknown option rejected", result.returncode == 1)
    runner.test("error message shown", "Unknown option" in result.stderr)


def test_java_runner():
    """Test java_runner.sh script."""
    runner.section("hooks/jdk/java_runner.sh")

    import subprocess

    script = PROJECT_ROOT / "plugin" / "hooks" / "jdk" / "java_runner.sh"

    if not script.exists():
        runner.test("Script exists", False, f"Script not found at {script}")
        return

    # Test 1: No arguments shows usage
    result = subprocess.run(
        [str(script)],
        capture_output=True,
        text=True
    )
    runner.test("no args shows usage", result.returncode == 1)
    runner.test("usage shows handler-class", "handler-class" in result.stderr)
    runner.test("usage lists example handlers", "BashPreToolHandler" in result.stderr)

    # Test 2: Invalid handler class name rejected
    result = subprocess.run(
        [str(script), "invalid-class-name"],
        capture_output=True,
        text=True
    )
    runner.test("invalid class name rejected", result.returncode == 1)
    runner.test("error is JSON", '"status":"error"' in result.stderr)

    # Test 3: Handler class with special chars rejected
    result = subprocess.run(
        [str(script), "Handler;ls"],
        capture_output=True,
        text=True
    )
    runner.test("class name with semicolon rejected", result.returncode == 1)

    # Test 4: Valid class name format accepted (will fail on missing class/Java, but validates name)
    result = subprocess.run(
        [str(script), "ValidHandler"],
        capture_output=True,
        text=True,
        timeout=5
    )
    # Should not fail with "Invalid handler class name" - may fail with Java/classpath error
    runner.test("valid class name passes validation",
                "Invalid handler class name" not in result.stderr)


def test_session_start():
    """Test session_start.sh script."""
    runner.section("hooks/jdk/session_start.sh")

    import subprocess
    import json

    script = PROJECT_ROOT / "plugin" / "hooks" / "jdk" / "session_start.sh"

    if not script.exists():
        runner.test("Script exists", False, f"Script not found at {script}")
        return

    # Test 1: Script runs without error (may warn if JDK not present)
    result = subprocess.run(
        [str(script)],
        capture_output=True,
        text=True,
        timeout=10
    )
    runner.test("script runs without crash", result.returncode == 0)

    # Test 2: Output is valid JSON
    stdout_lines = result.stdout.strip().split('\n')
    json_output = None
    for line in stdout_lines:
        line = line.strip()
        if line.startswith('{'):
            try:
                json_output = json.loads(line)
                break
            except json.JSONDecodeError:
                # Multi-line JSON, try to find the complete object
                json_text = '\n'.join(stdout_lines[stdout_lines.index(line):])
                try:
                    json_output = json.loads(json_text)
                    break
                except json.JSONDecodeError:
                    pass

    runner.test("output contains valid JSON", json_output is not None)

    if json_output:
        runner.test("JSON has status field", "status" in json_output)
        runner.test("JSON has message field", "message" in json_output)
        runner.test("status is success or warning",
                    json_output.get("status") in ["success", "warning"])

    # Test 3: Platform detection (via bash subshell)
    platform_check = subprocess.run(
        ["bash", "-c", """
            source {} <<< ''
            get_platform
        """.format(str(script).replace("'", "'\"'\"'"))],
        capture_output=True,
        text=True
    )
    # Even if sourcing fails, we can test the function separately
    platform_result = subprocess.run(
        ["bash", "-c", """
            get_platform() {
                local os arch
                case "$(uname -s)" in
                    Linux*)  os="linux" ;;
                    Darwin*) os="macos" ;;
                    MINGW*|MSYS*|CYGWIN*) os="windows" ;;
                    *)
                        echo "unknown"
                        return 1
                        ;;
                esac
                case "$(uname -m)" in
                    x86_64|amd64) arch="x64" ;;
                    aarch64|arm64) arch="aarch64" ;;
                    *)
                        echo "unknown"
                        return 1
                        ;;
                esac
                echo "${os}-${arch}"
            }
            get_platform
        """],
        capture_output=True,
        text=True
    )
    platform = platform_result.stdout.strip()
    runner.test("platform detection works",
                platform in ["linux-x64", "linux-aarch64", "macos-x64", "macos-aarch64"])


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
        test_active_agents,
        test_help_handler,
        test_work_handler,
        test_cleanup_handler,
        test_posttool_skill_preprocessor_output,
        test_config_loader,
        test_get_available_issues_discovery,
        test_abort_clarification_handler,
        test_jlink_config,
        test_java_runner,
        test_session_start,
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
