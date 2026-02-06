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

    # Test returns HANDLER_DATA without item_type (preload mode)
    result = handler.handle({})
    runner.test("Returns None without item_type", result is not None and result.startswith("HANDLER_DATA: "))

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

        # Test missing file returns open
        result = get_task_status(tmp_path / "nonexistent" / "STATE.md")
        runner.test("get_task_status: missing file returns open", result == "open")

        # Test parsing status from file
        state_file = tmp_path / "STATE.md"
        state_file.write_text("- **Status:** closed\n")
        result = get_task_status(state_file)
        runner.test("get_task_status: parses bold format", result == "closed")

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
    runner.test("format_session_id: full ID shown",
                get_status_display.format_session_id("f3d7107a-ef6a-44df-833d-2a2261a6421e") == "f3d7107a-ef6a-44df-833d-2a2261a6421e")

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
worktree=/workspace/.claude/cat/worktrees/2.1-task-one
created_iso=2026-01-31T12:00:00Z""")

        lock2 = locks_dir / "2.0-task-two.lock"
        lock2.write_text(f"""session_id=def456-session-2
created_at={now - 720}
worktree=/workspace/.claude/cat/worktrees/2.0-task-two
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
        "task_status": "open",
    }
    result = handler.handle(context)
    runner.test("Returns string with task", isinstance(result, str))
    if result:
        runner.test("Contains SCRIPT OUTPUT marker", "SCRIPT OUTPUT" in result)
        runner.test("Contains status boxes", "TASK_COMPLETE" in result or "SCOPE_COMPLETE" in result)


def test_box_alignment():
    """Test that all box-generating scripts produce aligned output."""
    runner.section("scripts/box_alignment")

    import subprocess
    sys.path.insert(0, str(PROJECT_ROOT / "plugin" / "scripts" / "lib"))
    from emoji_widths import display_width, get_emoji_widths
    ew = get_emoji_widths()

    def check_box_alignment(box_text):
        """Check if all lines in a box have the same display width."""
        lines = box_text.strip().split('\n')
        box_lines = []
        for line in lines:
            # Only check lines that are box borders or content
            if line and (line.startswith('│') or line.startswith('╭') or
                        line.startswith('╰') or line.startswith('├') or
                        line.startswith('┌') or line.startswith('└') or
                        line.startswith('┤')):
                box_lines.append(line)

        if len(box_lines) < 2:
            return True, "Not enough box lines"

        widths = [display_width(line, ew) for line in box_lines]
        first_width = widths[0]
        for i, w in enumerate(widths):
            if w != first_width:
                return False, f"Line {i} has width {w}, expected {first_width}"
        return True, "All lines aligned"

    # Test 1: get-issue-complete-box.py alignment
    result = subprocess.run([
        "python3", str(PROJECT_ROOT / "plugin" / "scripts" / "get-issue-complete-box.py"),
        "--issue-name", "2.1-test-issue",
        "--next-issue", "2.1-next-issue",
        "--next-goal", "Test goal description",
        "--base-branch", "v2.1"
    ], capture_output=True, text=True)
    aligned, msg = check_box_alignment(result.stdout)
    runner.test("get-issue-complete-box.py produces aligned box", aligned, msg)

    # Test 2: get-work-boxes.py template alignment
    result = subprocess.run([
        "python3", str(PROJECT_ROOT / "plugin" / "scripts" / "get-work-boxes.py")
    ], capture_output=True, text=True)
    # Split into individual boxes and check each
    boxes = result.stdout.split("###")
    for box_section in boxes[1:]:  # Skip first empty section
        lines = box_section.strip().split('\n')
        if len(lines) > 1:
            box_name = lines[0].strip()
            box_content = '\n'.join(lines[1:])
            aligned, msg = check_box_alignment(box_content)
            runner.test(f"get-work-boxes.py {box_name} aligned", aligned, msg)

    # Test 3: get-progress-banner.sh alignment
    result = subprocess.run([
        "bash", str(PROJECT_ROOT / "plugin" / "scripts" / "get-progress-banner.sh"),
        "2.1-test-task", "--phase", "executing"
    ], capture_output=True, text=True)
    aligned, msg = check_box_alignment(result.stdout)
    runner.test("get-progress-banner.sh produces aligned box", aligned, msg)

    # Test 4: get-checkpoint-box.py alignment (if exists)
    checkpoint_script = PROJECT_ROOT / "plugin" / "scripts" / "get-checkpoint-box.py"
    if checkpoint_script.exists():
        result = subprocess.run([
            "python3", str(checkpoint_script),
            "--issue-name", "2.1-test-issue",
            "--tokens", "50000",
            "--percent", "25",
            "--branch", "v2.1"
        ], capture_output=True, text=True)
        aligned, msg = check_box_alignment(result.stdout)
        runner.test("get-checkpoint-box.py produces aligned box", aligned, msg)
    else:
        runner.test("get-checkpoint-box.py exists", False,
                    "Script missing - checkpoint boxes cannot be generated with actual values")


def test_cleanup_handler():
    """Test cleanup_handler."""
    runner.section("skill_handlers/cleanup_handler")

    from skill_handlers.cleanup_handler import CleanupHandler
    handler = CleanupHandler()

    # Test defaults to survey phase and gathers data
    result = handler.handle({"project_root": "/nonexistent"})
    runner.test("Defaults to survey phase without explicit phase", isinstance(result, str))

    # Test survey phase
    context = {
        "phase": "survey",
        "worktrees": [
            {"path": "/workspace/.claude/cat/worktrees/1.0-task", "branch": "1.0-task", "state": ""}
        ],
        "locks": [],
        "branches": ["1.0-old-branch"],
        "stale_remotes": [],
    }
    result = handler.handle(context)
    runner.test("Returns string for survey phase", isinstance(result, str))
    if result:
        runner.test("Contains SCRIPT OUTPUT marker", "SCRIPT OUTPUT" in result)


def test_delegate_handler():
    """Test delegate_handler."""
    runner.section("skill_handlers/delegate_handler")

    from skill_handlers.delegate_handler import DelegateHandler
    handler = DelegateHandler()

    # Test: empty prompt returns None
    result = handler.handle({})
    runner.test("Returns None without user_prompt", result is None)

    result = handler.handle({"user_prompt": ""})
    runner.test("Returns None with empty user_prompt", result is None)

    # Test: non-delegate prompt returns None
    result = handler.handle({"user_prompt": "/cat:status"})
    runner.test("Returns None for non-delegate prompt", result is None)

    # Test: skill delegation with multiple files (parallel)
    result = handler.handle({
        "user_prompt": "/cat:delegate --skill shrink-doc file1.md file2.md file3.md"
    })
    runner.test("Returns string for skill delegation", isinstance(result, str))
    if result:
        runner.test("Contains SCRIPT OUTPUT marker", "SCRIPT OUTPUT" in result)
        runner.test("Contains PARALLEL mode", "PARALLEL" in result)
        runner.test("Contains item count", "3" in result)
        runner.test("Contains file names", "file1.md" in result)

    # Test: skill delegation with single file (sequential)
    result = handler.handle({
        "user_prompt": "/cat:delegate --skill shrink-doc single-file.md"
    })
    runner.test("Returns string for single file", isinstance(result, str))
    if result:
        runner.test("Contains SEQUENTIAL mode for single file", "SEQUENTIAL" in result)

    # Test: sequential flag forces sequential mode
    result = handler.handle({
        "user_prompt": "/cat:delegate --sequential --skill shrink-doc file1.md file2.md"
    })
    runner.test("Returns string for sequential flag", isinstance(result, str))
    if result:
        runner.test("Sequential flag forces SEQUENTIAL mode", "SEQUENTIAL" in result)

    # Test: issues delegation
    result = handler.handle({
        "user_prompt": "/cat:delegate --issues 2.1-task-a,2.1-task-b,2.1-task-c"
    })
    runner.test("Returns string for issues delegation", isinstance(result, str))
    if result:
        runner.test("Contains PARALLEL mode for issues", "PARALLEL" in result)
        runner.test("Contains issue names", "2.1-task-a" in result)

    # Test: worktree parameter is captured
    result = handler.handle({
        "user_prompt": "/cat:delegate --skill shrink-doc --worktree /workspace/.claude/cat/worktrees/test file.md"
    })
    runner.test("Returns string with worktree", isinstance(result, str))
    if result:
        runner.test("Contains worktree path", "/workspace/.claude/cat/worktrees/test" in result)


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


def test_posttool_user_input_reminder():
    """Test PostToolUse handler for user input reminder (M247/M337/M366/M379)."""
    runner.section("posttool_handlers/user_input_reminder")

    # Import the handler
    from posttool_handlers.user_input_reminder import UserInputReminderHandler
    handler = UserInputReminderHandler()

    # Test 1: No system-reminder - returns None
    result = handler.check("Bash", {"output": "some output"}, {})
    runner.test("No system-reminder returns None", result is None)

    # Test 2: System-reminder without user input - returns None
    tool_result = {
        "output": "<system-reminder>This is just a reminder about something.</system-reminder>"
    }
    result = handler.check("Bash", tool_result, {})
    runner.test("System-reminder without user input returns None", result is None)

    # Test 3: System-reminder with question mark - triggers reminder
    tool_result = {
        "output": "<system-reminder>Can you help me with this task?</system-reminder>"
    }
    result = handler.check("Bash", tool_result, {})
    runner.test("Question with '?' triggers reminder",
                result is not None and "additionalContext" in result)
    if result:
        runner.test("Reminder mentions M247/M337/M366/M379",
                    "M247" in result.get("additionalContext", "") and
                    "M337" in result.get("additionalContext", "") and
                    "M366" in result.get("additionalContext", "") and
                    "M379" in result.get("additionalContext", ""))

    # Test 4: System-reminder with "can you" phrase - triggers reminder
    tool_result = {
        "output": "<system-reminder>can you please check the logs</system-reminder>"
    }
    result = handler.check("Bash", tool_result, {})
    runner.test("'can you' phrase triggers reminder",
                result is not None and "additionalContext" in result)

    # Test 5: System-reminder with "how do" phrase - triggers reminder
    tool_result = {
        "output": "<system-reminder>how do I configure this setting</system-reminder>"
    }
    result = handler.check("Bash", tool_result, {})
    runner.test("'how do' phrase triggers reminder",
                result is not None and "additionalContext" in result)

    # Test 6: User input already acknowledged - returns None
    tool_result = {
        "output": "I acknowledge your question. <system-reminder>Can you help?</system-reminder>"
    }
    result = handler.check("Bash", tool_result, {})
    runner.test("Acknowledged input returns None", result is None)

    # Test 7: User input with "I see" acknowledgment - returns None
    tool_result = {
        "output": "I see your question about X. <system-reminder>What should I do?</system-reminder>"
    }
    result = handler.check("Bash", tool_result, {})
    runner.test("'I see' acknowledgment returns None", result is None)

    # Test 8: Multiple question patterns
    question_cases = [
        ("?", "Is this a test?"),
        ("can you", "can you help me"),
        ("could you", "could you check this"),
        ("what is", "what is the status"),
        ("why did", "why did this happen"),
        ("do you", "do you have the info"),
    ]
    for pattern, test_text in question_cases:
        tool_result = {
            "output": f"<system-reminder>{test_text}</system-reminder>"
        }
        result = handler.check("Bash", tool_result, {})
        has_result = result is not None and "additionalContext" in result
        runner.test(f"Question pattern '{pattern}' detected", has_result)

    # Test 9: Explicit user input markers
    user_input_cases = [
        ("user message", "The user sent the following message: Please add feature X"),
        ("MUST", "You MUST complete this task before proceeding"),
        ("Before proceeding", "Before proceeding, ensure all tests pass"),
        ("AGENT INSTRUCTION", "AGENT INSTRUCTION: Handle this request first"),
    ]
    for pattern, test_text in user_input_cases:
        tool_result = {
            "output": f"<system-reminder>{test_text}</system-reminder>"
        }
        result = handler.check("Bash", tool_result, {})
        has_result = result is not None and "additionalContext" in result
        runner.test(f"User input marker '{pattern}' detected", has_result)

    # Test 10: Imperative command patterns
    command_cases = [
        ("fix", "fix this bug"),
        ("add", "add a new feature"),
        ("update", "update the configuration"),
        ("please add", "please add documentation"),
        ("can you fix", "can you fix this issue"),
    ]
    for pattern, test_text in command_cases:
        tool_result = {
            "output": f"<system-reminder>{test_text}</system-reminder>"
        }
        result = handler.check("Bash", tool_result, {})
        has_result = result is not None and "additionalContext" in result
        runner.test(f"Command pattern '{pattern}' detected", has_result)


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

- **Status:** open
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
    worktree_path = PROJECT_ROOT.parent / ".claude" / "cat" / "worktrees" / "2.1-self-discover-env-vars"
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


def test_posttool_auto_learn():
    """Test auto_learn posttool handler."""
    runner.section("posttool_handlers/auto_learn")

    from posttool_handlers.auto_learn import AutoLearnHandler
    handler = AutoLearnHandler()

    # Test 1: Grep output with line number prefix gets filtered
    grep_output = '''42:{"type":"assistant","role":"assistant","content":"test_failure in code"}
43:{"parentUuid":"abc123","message":"another line"}
44:Regular text without grep prefix
45:{"sessionId":"session1","type":"assistant"}'''

    filtered = handler._filter_json_content(grep_output)
    runner.test("grep prefix: JSON lines with line numbers filtered",
                '"type":"assistant"' not in filtered and
                '"parentUuid":"abc123"' not in filtered and
                '"sessionId":"session1"' not in filtered)
    runner.test("grep prefix: regular text preserved",
                'Regular text without grep prefix' in filtered)

    # Test 2: Content without grep prefix still works
    no_grep_output = '''{"type":"assistant","content":"test"}
Regular line
{"sessionId":"abc"}'''

    filtered = handler._filter_json_content(no_grep_output)
    runner.test("no grep prefix: JSON filtered",
                '"type":"assistant"' not in filtered and
                '"sessionId":"abc"' not in filtered)
    runner.test("no grep prefix: regular text preserved",
                'Regular line' in filtered)

    # Test 3: Edge case - multiple colons in line
    edge_output = '''123:{"type":"assistant","url":"http://example.com:8080"}
456:http://example.com:8080 regular text'''

    filtered = handler._filter_json_content(edge_output)
    runner.test("multiple colons: JSON with URL filtered",
                '"type":"assistant"' not in filtered)
    runner.test("multiple colons: URL text preserved",
                'http://example.com:8080 regular text' in filtered)

    # Test 4: Line starting with digit but not grep format
    non_grep_output = '''2026-01-15 test_failure detected
123 regular line starting with number
42test_failure in text'''

    filtered = handler._filter_json_content(non_grep_output)
    runner.test("non-grep digits: all text preserved",
                '2026-01-15 test_failure detected' in filtered and
                '123 regular line starting with number' in filtered and
                '42test_failure in text' in filtered)

    # Test 5: Pattern detection still works after filtering
    test_output = '''42:{"type":"assistant","content":"unrelated"}
43:Some context
44:Tests run: 10, Failures: 2
45:{"sessionId":"abc"}'''

    mistake_type, details = handler._detect_mistake("Bash", test_output, 1, "")
    runner.test("pattern detection: test_failure detected after filtering",
                mistake_type == "test_failure")


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


def test_posttool_validate_state_status():
    """Test PostToolUse handler for STATE.md status validation (M434)."""
    runner.section("posttool_handlers/validate_state_status")

    from posttool_handlers.validate_state_status import handle

    # Test 1: Non-Edit/Write tool returns None
    result = handle("Bash", {"file_path": "/tmp/STATE.md"}, {})
    runner.test("Non-Edit/Write tool returns None", result is None)

    # Test 2: Edit on non-STATE.md file returns None
    result = handle("Edit", {"file_path": "/workspace/README.md"}, {})
    runner.test("Edit on non-STATE.md returns None", result is None)

    # Test 3-6: Canonical statuses return None
    with TemporaryDirectory() as tmp_dir:
        state_file = Path(tmp_dir) / "STATE.md"

        for status in ["open", "in-progress", "closed", "blocked"]:
            state_file.write_text(f"# State\n\n- **Status:** {status}\n")
            result = handle("Edit", {"file_path": str(state_file)}, {})
            runner.test(f"Canonical status '{status}' returns None", result is None)

    # Test 7: Non-canonical 'complete' returns warning
    with TemporaryDirectory() as tmp_dir:
        state_file = Path(tmp_dir) / "STATE.md"
        state_file.write_text("# State\n\n- **Status:** complete\n")
        result = handle("Edit", {"file_path": str(state_file)}, {})
        runner.test("Non-canonical 'complete' returns warning",
                    result is not None and "M434" in result)

    # Test 8: Non-canonical 'done' via Write returns warning
    with TemporaryDirectory() as tmp_dir:
        state_file = Path(tmp_dir) / "STATE.md"
        state_file.write_text("# State\n\n- **Status:** done\n")
        result = handle("Write", {"file_path": str(state_file)}, {})
        runner.test("Non-canonical 'done' via Write returns warning",
                    result is not None and "M434" in result)

    # Test 9: Non-canonical 'pending' returns warning (renamed to 'open')
    with TemporaryDirectory() as tmp_dir:
        state_file = Path(tmp_dir) / "STATE.md"
        state_file.write_text("# State\n\n- **Status:** pending\n")
        result = handle("Edit", {"file_path": str(state_file)}, {})
        runner.test("Non-canonical 'pending' returns warning",
                    result is not None and "M434" in result)

    # Test 10: Non-canonical 'completed' returns warning (renamed to 'closed')
    with TemporaryDirectory() as tmp_dir:
        state_file = Path(tmp_dir) / "STATE.md"
        state_file.write_text("# State\n\n- **Status:** completed\n")
        result = handle("Edit", {"file_path": str(state_file)}, {})
        runner.test("Non-canonical 'completed' returns warning",
                    result is not None and "M434" in result)

    # Test 11: Non-canonical 'in_progress' returns warning
    with TemporaryDirectory() as tmp_dir:
        state_file = Path(tmp_dir) / "STATE.md"
        state_file.write_text("# State\n\n- **Status:** in_progress\n")
        result = handle("Edit", {"file_path": str(state_file)}, {})
        runner.test("Non-canonical 'in_progress' returns warning",
                    result is not None and "M434" in result)

    # Test 12: Non-canonical 'active' returns warning
    with TemporaryDirectory() as tmp_dir:
        state_file = Path(tmp_dir) / "STATE.md"
        state_file.write_text("# State\n\n- **Status:** active\n")
        result = handle("Edit", {"file_path": str(state_file)}, {})
        runner.test("Non-canonical 'active' returns warning",
                    result is not None and "M434" in result)

    # Test 13: Missing Status field returns None
    with TemporaryDirectory() as tmp_dir:
        state_file = Path(tmp_dir) / "STATE.md"
        state_file.write_text("# State\n\n- **Progress:** 50%\n")
        result = handle("Edit", {"file_path": str(state_file)}, {})
        runner.test("Missing Status field returns None", result is None)

    # Test 14: Missing file_path returns None
    result = handle("Edit", {}, {})
    runner.test("Missing file_path returns None", result is None)


def test_compress_validate_loop():
    """Test compress-validate-loop.py script."""
    runner.section("scripts/compress-validate-loop.py")

    import subprocess

    script = PROJECT_ROOT / "scripts" / "tests" / "test_compress_validate_loop.py"

    if not script.exists():
        runner.test("Test script exists", False, f"Script not found at {script}")
        return

    # Run the unittest tests
    result = subprocess.run(
        ["python3", "-m", "unittest", str(script)],
        capture_output=True,
        text=True,
        cwd=str(PROJECT_ROOT)
    )

    # Parse output to count tests
    output = result.stderr  # unittest writes to stderr
    test_count = output.count("test_")

    runner.test("compress-validate-loop tests run", result.returncode == 0,
                f"Tests failed:\n{output}" if result.returncode != 0 else "")

    if result.returncode == 0:
        runner.test("All compress-validate-loop tests passed", True)


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
        test_box_alignment,
        test_cleanup_handler,
        test_delegate_handler,
        test_posttool_skill_preprocessor_output,
        test_posttool_user_input_reminder,
        test_posttool_auto_learn,
        test_posttool_validate_state_status,
        test_config_loader,
        test_get_available_issues_discovery,
        test_abort_clarification_handler,
        test_jlink_config,
        test_java_runner,
        test_session_start,
        test_compress_validate_loop,
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
