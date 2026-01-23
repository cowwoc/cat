"""Shared pytest fixtures for handler tests."""

import sys
from pathlib import Path
from typing import Generator

import pytest


@pytest.fixture(scope="session")
def setup_python_path() -> None:
    """Add plugin paths to sys.path for imports."""
    # Get paths relative to this file
    tests_dir = Path(__file__).parent
    project_root = tests_dir.parent.parent
    plugin_hooks = project_root / "plugin" / "hooks"
    plugin_scripts = project_root / "plugin" / "scripts"

    # Add to sys.path if not already there
    for path in [plugin_hooks, plugin_scripts]:
        path_str = str(path)
        if path_str not in sys.path:
            sys.path.insert(0, path_str)


@pytest.fixture
def plugin_root() -> Path:
    """Return the plugin root directory."""
    return Path(__file__).parent.parent.parent / "plugin"


@pytest.fixture
def project_root() -> Path:
    """Return the project root directory."""
    return Path(__file__).parent.parent.parent


@pytest.fixture
def base_context(project_root: Path, plugin_root: Path) -> dict:
    """Create a basic handler context dictionary."""
    return {
        "user_prompt": "",
        "session_id": "test-session-123",
        "project_root": str(project_root),
        "plugin_root": str(plugin_root),
        "hook_data": {},
    }


@pytest.fixture
def context_factory(project_root: Path, plugin_root: Path):
    """Factory fixture for creating custom contexts."""

    def _create_context(**kwargs) -> dict:
        context = {
            "user_prompt": "",
            "session_id": "test-session-123",
            "project_root": str(project_root),
            "plugin_root": str(plugin_root),
            "hook_data": {},
        }
        context.update(kwargs)
        return context

    return _create_context


@pytest.fixture
def mock_cat_structure(tmp_path: Path) -> Path:
    """Create a minimal CAT directory structure for testing.

    Structure:
      .claude/cat/PROJECT.md
      .claude/cat/ROADMAP.md
      .claude/cat/cat-config.json
      .claude/cat/issues/   (version directories go here)
    """
    cat_dir = tmp_path / ".claude" / "cat"
    cat_dir.mkdir(parents=True)

    # Create issues subdirectory for version data
    issues_dir = cat_dir / "issues"
    issues_dir.mkdir()

    # Create PROJECT.md
    project_file = cat_dir / "PROJECT.md"
    project_file.write_text("# Test Project\n\nA test project for unit tests.\n")

    # Create ROADMAP.md
    roadmap_file = cat_dir / "ROADMAP.md"
    roadmap_file.write_text("""# Roadmap

## Version 1: Initial Release

- **1.0:** Core features (2 tasks)
- **1.1:** Bug fixes (1 task)

## Version 2: Enhanced Features

- **2.0:** New features (3 tasks)
""")

    # Create cat-config.json
    config_file = cat_dir / "cat-config.json"
    config_file.write_text('{"trust": "medium", "verify": "changed"}')

    return tmp_path


@pytest.fixture
def mock_version_with_tasks(mock_cat_structure: Path) -> Path:
    """Create a version with sample tasks.

    Structure:
      .claude/cat/issues/v1/v1.0/parse-input/STATE.md
      .claude/cat/issues/v1/v1.0/validate-data/STATE.md
      .claude/cat/issues/v1/v1.0/output-results/STATE.md
    """
    issues_dir = mock_cat_structure / ".claude" / "cat" / "issues"

    # Create v1/v1.0 structure
    v1_dir = issues_dir / "v1"
    v1_dir.mkdir()

    v10_dir = v1_dir / "v1.0"
    v10_dir.mkdir()

    # Create task directories with STATE.md
    task1_dir = v10_dir / "parse-input"
    task1_dir.mkdir()
    (task1_dir / "STATE.md").write_text("Status: completed\n")
    (task1_dir / "PLAN.md").write_text("# Parse Input\n\nParse user input.\n")

    task2_dir = v10_dir / "validate-data"
    task2_dir.mkdir()
    (task2_dir / "STATE.md").write_text("Status: in-progress\n")
    (task2_dir / "PLAN.md").write_text("# Validate Data\n\nValidate parsed data.\n")

    task3_dir = v10_dir / "output-results"
    task3_dir.mkdir()
    (task3_dir / "STATE.md").write_text("Status: pending\n")
    (task3_dir / "PLAN.md").write_text("# Output Results\n\nOutput validated results.\n")

    return mock_cat_structure


@pytest.fixture
def sample_worktree_data() -> list:
    """Sample worktree data for cleanup tests."""
    return [
        {"path": "/workspace/.worktrees/1.0-task-a", "branch": "1.0-task-a", "state": ""},
        {"path": "/workspace/.worktrees/1.0-task-b", "branch": "1.0-task-b", "state": "locked"},
    ]


@pytest.fixture
def sample_lock_data() -> list:
    """Sample lock data for cleanup tests."""
    return [
        {"task_id": "1.0-task-a", "session": "abc123def456", "age": 3600},
        {"task_id": "1.0-task-b", "session": "xyz789uvw012", "age": 7200},
    ]


@pytest.fixture
def sample_branch_data() -> list:
    """Sample branch data for cleanup tests."""
    return ["1.0-task-a", "1.0-task-b", "1.0-task-c"]


@pytest.fixture
def sample_stale_remote_data() -> list:
    """Sample stale remote data for cleanup tests."""
    return [
        {"branch": "origin/1.0-old-task", "author": "user@example.com", "relative": "3 days ago"},
    ]
