#!/usr/bin/env python3
# Copyright (c) 2026 Gili Tzabari. All rights reserved.
#
# Licensed under the CAT Commercial License.
# See LICENSE.md in the project root for license terms.
"""
Final polishing for acceptance criteria compliance.

This script performs final cleanup tasks to ensure all STATE.md files
meet the standardized schema requirements.

What it fixes:
- Adds missing Progress key (100% for closed, 0% for open/in-progress)
- Removes commit hashes from Resolution values (e.g., "implemented (commit abc123)")
- Standardizes "duplicate of X" to "duplicate (X)" format

Idempotency: Safe to run multiple times. Only modifies files that need changes.

Execution order: Run LAST, after all other migration scripts.
"""

import re
from pathlib import Path

from state_schema_lib import find_state_files


def fix_file(file_path: Path) -> bool:
    """Fix a single STATE.md file. Returns True if modified."""
    content = file_path.read_text()
    original_content = content

    # Parse all keys
    keys = {}
    for line in content.split('\n'):
        match = re.match(r'^-\s+\*\*([^:]+):\*\*\s*(.*)', line)
        if match:
            key = match.group(1).strip()
            value = match.group(2).strip()
            keys[key] = value

    # Fix 1: Add missing Progress key (100% for closed, 0% for open/in-progress without Progress)
    if 'Progress' not in keys:
        status = keys.get('Status', 'open')
        if status == 'closed':
            progress = '100%'
        else:
            progress = '0%'

        # Insert Progress after Status
        content = re.sub(
            r'^(-\s+\*\*Status:\*\*\s+[^\n]+)\n',
            r'\1\n- **Progress:** ' + progress + '\n',
            content,
            flags=re.MULTILINE
        )

    # Fix 2: Fix non-standard Resolution formats
    # Pattern 1: "implemented (commit abc123)"
    content = re.sub(
        r'^(-\s+\*\*Resolution:\*\*\s+)(implemented)\s+\(commit\s+[a-f0-9]+\)$',
        r'\1\2',
        content,
        flags=re.MULTILINE
    )

    # Pattern 2: "duplicate of X" -> "duplicate (X)"
    def fix_duplicate(match):
        prefix = match.group(1)
        target = match.group(2)
        return f'{prefix}duplicate ({target})'

    content = re.sub(
        r'^(-\s+\*\*Resolution:\*\*\s+)duplicate\s+of\s+(.+)$',
        fix_duplicate,
        content,
        flags=re.MULTILINE
    )

    if content != original_content:
        file_path.write_text(content)
        return True
    return False


def main():
    """Main function."""
    issues_dir = Path('.claude/cat/issues')

    # Process all STATE.md files
    state_files = find_state_files(issues_dir)

    modified_count = 0
    for state_file in state_files:
        try:
            if fix_file(state_file):
                modified_count += 1
                print(f'✓ {state_file.relative_to(issues_dir.parent)}')
        except Exception as e:
            print(f'✗ {state_file.relative_to(issues_dir.parent)}: {e}')
            import traceback
            traceback.print_exc()
            return 1

    print(f'\nFinal fixes complete: {modified_count} files modified')
    return 0


if __name__ == '__main__':
    exit(main())
