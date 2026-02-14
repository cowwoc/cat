# Copyright (c) 2026 Gili Tzabari. All rights reserved.
#
# Licensed under the CAT Commercial License.
# See LICENSE.md in the project root for license terms.
"""
Shared utilities for STATE.md schema migration scripts.

This module provides common functions for parsing, manipulating, and
discovering STATE.md files across the CAT issue hierarchy.
"""

import subprocess
from datetime import date
from pathlib import Path
from typing import Dict


def parse_state_keys(content: str) -> Dict[str, str]:
    """
    Extract all key-value pairs from STATE.md content.

    Parses lines matching the pattern "- **Key:** value" and returns
    them as a dictionary. Handles multiline values and duplicate keys
    (keeping the first occurrence).

    Args:
        content: The full STATE.md file content

    Returns:
        Dictionary mapping key names to their values
    """
    import re
    keys = {}
    for line in content.split('\n'):
        match = re.match(r'^-\s+\*\*([^:]+):\*\*\s*(.*)', line)
        if match:
            key = match.group(1).strip()
            value = match.group(2).strip()
            # Don't override keys we've already seen (keep first occurrence)
            if key not in keys:
                keys[key] = value
    return keys


def get_git_last_modified(file_path: Path) -> str:
    """
    Get the last modified date of a file from git history.

    Args:
        file_path: Path to the file to check

    Returns:
        ISO date string (YYYY-MM-DD) of last commit touching the file,
        or today's date if git log fails
    """
    try:
        result = subprocess.run(
            ['git', 'log', '-1', '--format=%cs', '--', str(file_path)],
            capture_output=True,
            text=True,
            cwd=file_path.parent
        )
        if result.returncode == 0 and result.stdout.strip():
            return result.stdout.strip()
    except Exception:
        pass
    # Fallback to current date
    return date.today().isoformat()


def find_state_files(issues_dir: Path) -> list[Path]:
    """
    Recursively find all STATE.md files in the issues directory.

    Args:
        issues_dir: Root directory to search (typically .claude/cat/issues)

    Returns:
        List of Path objects for each STATE.md file found
    """
    return list(issues_dir.rglob('STATE.md'))
