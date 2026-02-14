# Copyright (c) 2026 Gili Tzabari. All rights reserved.
#
# Licensed under the CAT Commercial License.
# See LICENSE.md in the project root for license terms.
"""
Tests for STATE.md migration scripts.

Tests cover the critical migration functions including parsing,
resolution building, and file discovery.
"""

import sys
from pathlib import Path
from tempfile import TemporaryDirectory

import pytest

# Add scripts directory to path for imports
import importlib.util

_scripts_dir = Path(__file__).resolve().parent.parent / 'scripts'
sys.path.insert(0, str(_scripts_dir))


def _import_hyphenated(name, path):
    """Import a module with hyphens in filename."""
    spec = importlib.util.spec_from_file_location(name, path)
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


_migrate = _import_hyphenated('migrate_state_schema', _scripts_dir / 'migrate-state-schema.py')
_fix_properly = _import_hyphenated('fix_state_files_properly', _scripts_dir / 'fix-state-files-properly.py')

parse_state_file = _migrate.parse_state_file
build_resolution = _migrate.build_resolution
migrate_state_file = _migrate.migrate_state_file
rebuild_state_file = _fix_properly.rebuild_state_file

from state_schema_lib import parse_state_keys, find_state_files


class TestParseStateFile:
    """Test parse_state_file() function for multiline values and malformed input."""

    def test_parse_simple_keys(self):
        """Test parsing simple single-line key-value pairs."""
        content = """# State

- **Status:** open
- **Progress:** 50%
- **Dependencies:** []
"""
        keys, remaining = parse_state_file(content)
        assert keys == {
            'Status': 'open',
            'Progress': '50%',
            'Dependencies': '[]'
        }
        assert remaining == ''

    def test_parse_multiline_values(self):
        """Test parsing values that span multiple lines."""
        content = """# State

- **Status:** closed
- **Resolution:** implemented
  This is a continuation
  of the resolution value
- **Dependencies:** []
"""
        keys, remaining = parse_state_file(content)
        assert keys['Status'] == 'closed'
        # Multiline values should be joined with newlines
        assert 'implemented' in keys['Resolution']
        assert 'continuation' in keys['Resolution']

    def test_parse_malformed_keys(self):
        """Test handling of malformed key lines - parser stops at first non-key line."""
        content = """# State

- **Status:** open
- Missing asterisks: value
- **Progress:** 30%
"""
        keys, remaining = parse_state_file(content)
        # Parser stops at first non-key line, so only Status is parsed
        assert 'Status' in keys
        assert 'Missing asterisks' not in keys
        # Malformed line appears in remaining content
        assert 'Missing asterisks' in remaining

    def test_parse_empty_content(self):
        """Test parsing empty or minimal content."""
        content = """# State

"""
        keys, remaining = parse_state_file(content)
        assert keys == {}
        assert remaining == ''

    def test_parse_preserves_remaining_content(self):
        """Test that content after keys is preserved."""
        content = """# State

- **Status:** open
- **Progress:** 0%

## Notes

This is additional content
that should be preserved.
"""
        keys, remaining = parse_state_file(content)
        assert keys == {'Status': 'open', 'Progress': '0%'}
        assert '## Notes' in remaining
        assert 'preserved' in remaining


class TestBuildResolution:
    """Test build_resolution() for various input scenarios."""

    def test_duplicate_of_extraction(self):
        """Test extracting issue ID from Duplicate Of field."""
        keys = {'Duplicate Of': 'v2.1-some-issue'}
        resolution = build_resolution(keys)
        assert resolution == 'duplicate (v2.1-some-issue)'

    def test_duplicate_of_with_description(self):
        """Test Duplicate Of with description after issue ID."""
        keys = {'Duplicate Of': 'v2.1-issue (some description)'}
        resolution = build_resolution(keys)
        assert resolution == 'duplicate (v2.1-issue)'

    def test_reason_folding(self):
        """Test folding Reason fields into Resolution."""
        keys = {
            'Resolution': 'implemented',
            'Reason': 'Feature is now complete'
        }
        resolution = build_resolution(keys)
        assert resolution == 'implemented (Feature is now complete)'

    def test_closed_reason_folding(self):
        """Test folding Closed Reason into Resolution."""
        keys = {
            'Resolution': 'won\'t-fix',
            'Closed Reason': 'Out of scope'
        }
        resolution = build_resolution(keys)
        assert resolution == 'won\'t-fix (Out of scope)'

    def test_resolution_with_existing_parenthetical(self):
        """Test that existing parentheticals are not duplicated."""
        keys = {
            'Resolution': 'duplicate (v2.1-other)',
            'Reason': 'This should not be added'
        }
        resolution = build_resolution(keys)
        # Should not add more parentheticals
        assert resolution == 'duplicate (v2.1-other)'

    def test_abandoned_without_resolution(self):
        """Test handling Abandoned key when no Resolution exists."""
        keys = {'Abandoned': 'true'}
        resolution = build_resolution(keys)
        assert resolution == 'won\'t-fix (abandoned)'

    def test_no_resolution_found(self):
        """Test when no resolution can be built."""
        keys = {'Status': 'open'}
        resolution = build_resolution(keys)
        assert resolution is None


class TestMigrateStateFile:
    """Test migrate_state_file() for mandatory key handling."""

    def test_adds_missing_dependencies(self):
        """Test that missing Dependencies key is added."""
        with TemporaryDirectory() as tmpdir:
            state_file = Path(tmpdir) / 'STATE.md'
            state_file.write_text("""# State

- **Status:** open
- **Progress:** 0%
""")
            modified = migrate_state_file(state_file)
            assert modified is True

            content = state_file.read_text()
            assert '- **Dependencies:** []' in content

    def test_adds_missing_blocks(self):
        """Test that missing Blocks key is added."""
        with TemporaryDirectory() as tmpdir:
            state_file = Path(tmpdir) / 'STATE.md'
            state_file.write_text("""# State

- **Status:** open
- **Progress:** 0%
""")
            modified = migrate_state_file(state_file)
            assert modified is True

            content = state_file.read_text()
            assert '- **Blocks:** []' in content

    def test_renames_created_from_to_parent(self):
        """Test that Created From is renamed to Parent."""
        with TemporaryDirectory() as tmpdir:
            state_file = Path(tmpdir) / 'STATE.md'
            state_file.write_text("""# State

- **Status:** open
- **Progress:** 0%
- **Created From:** v2.1-parent-issue
- **Dependencies:** []
""")
            modified = migrate_state_file(state_file)
            assert modified is True

            content = state_file.read_text()
            assert '- **Parent:** v2.1-parent-issue' in content
            assert 'Created From' not in content

    def test_removes_non_standard_keys(self):
        """Test that non-standard keys are removed."""
        with TemporaryDirectory() as tmpdir:
            state_file = Path(tmpdir) / 'STATE.md'
            state_file.write_text("""# State

- **Status:** open
- **Progress:** 0%
- **Completed:** 2025-01-01
- **Version:** v2.1
- **Dependencies:** []
""")
            modified = migrate_state_file(state_file)
            assert modified is True

            content = state_file.read_text()
            assert 'Completed' not in content
            assert 'Version' not in content

    def test_no_modification_when_already_standard(self):
        """Test that no modification occurs for already-standard files."""
        with TemporaryDirectory() as tmpdir:
            state_file = Path(tmpdir) / 'STATE.md'
            # Create a file that's already in standard format
            state_file.write_text("""# State

- **Status:** open
- **Progress:** 0%
- **Dependencies:** []
- **Blocks:** []
- **Last Updated:** 2025-01-01
""")
            modified = migrate_state_file(state_file)
            assert modified is False


class TestParseStateKeys:
    """Test parse_state_keys() for duplicate key handling."""

    def test_keeps_first_occurrence_of_duplicate(self):
        """Test that first occurrence of duplicate key is kept."""
        content = """# State

- **Status:** open
- **Progress:** 50%
- **Status:** closed
"""
        keys = parse_state_keys(content)
        # Should keep first occurrence
        assert keys['Status'] == 'open'

    def test_parses_all_keys(self):
        """Test that all unique keys are parsed."""
        content = """# State

- **Status:** open
- **Progress:** 30%
- **Dependencies:** [v2.1-other]
- **Blocks:** []
"""
        keys = parse_state_keys(content)
        assert len(keys) == 4
        assert keys['Status'] == 'open'
        assert keys['Progress'] == '30%'
        assert keys['Dependencies'] == '[v2.1-other]'
        assert keys['Blocks'] == '[]'


class TestRebuildStateFile:
    """Test rebuild_state_file() for version-level vs regular files."""

    def test_handles_version_level_file(self):
        """Test handling of version-level STATE.md with ## Status section."""
        with TemporaryDirectory() as tmpdir:
            state_file = Path(tmpdir) / 'STATE.md'
            state_file.write_text("""# State

- **Dependencies:** []

## Status

- **Status:** open
- **Progress:** 50%
- **Started:** 2025-01-01

## Summary

Version 2.1 summary here.
""")
            modified = rebuild_state_file(state_file)
            assert modified is True

            content = state_file.read_text()
            # Should preserve ## Status section
            assert '## Status' in content
            # Should remove Started from ## Status section
            assert 'Started' not in content
            # Should add mandatory keys to # State section
            assert 'Dependencies' in content
            assert 'Blocks' in content

    def test_handles_regular_issue_file(self):
        """Test handling of regular issue STATE.md without sections."""
        with TemporaryDirectory() as tmpdir:
            state_file = Path(tmpdir) / 'STATE.md'
            state_file.write_text("""# State

- **Status:** closed
- **Progress:** 100%
""")
            modified = rebuild_state_file(state_file)
            assert modified is True

            content = state_file.read_text()
            # Should add mandatory keys
            assert 'Dependencies' in content
            assert 'Blocks' in content
            assert 'Last Updated' in content
            # Should add Resolution for closed status
            assert 'Resolution' in content


class TestFindStateFiles:
    """Test find_state_files() for recursive discovery."""

    def test_finds_all_state_files(self):
        """Test that all STATE.md files are found recursively."""
        with TemporaryDirectory() as tmpdir:
            issues_dir = Path(tmpdir) / '.claude' / 'cat' / 'issues'
            issues_dir.mkdir(parents=True)

            # Create version hierarchy with STATE.md files
            (issues_dir / 'v2').mkdir()
            (issues_dir / 'v2' / 'STATE.md').write_text('# State\n')

            (issues_dir / 'v2' / 'v2.1').mkdir()
            (issues_dir / 'v2' / 'v2.1' / 'STATE.md').write_text('# State\n')

            (issues_dir / 'v2' / 'v2.1' / 'some-issue').mkdir()
            (issues_dir / 'v2' / 'v2.1' / 'some-issue' / 'STATE.md').write_text('# State\n')

            state_files = find_state_files(issues_dir)
            assert len(state_files) == 3

    def test_returns_empty_for_nonexistent_dir(self):
        """Test that empty list is returned for nonexistent directory."""
        with TemporaryDirectory() as tmpdir:
            issues_dir = Path(tmpdir) / 'nonexistent'
            state_files = find_state_files(issues_dir)
            assert state_files == []
