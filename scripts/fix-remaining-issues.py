#!/usr/bin/env python3
# Copyright (c) 2026 Gili Tzabari. All rights reserved.
#
# Licensed under the CAT Commercial License.
# See LICENSE.md in the project root for license terms.
"""
Fix remaining acceptance criteria issues.

This script handles edge cases and cleanup tasks after the main migration.

What it fixes:
- Removes any remaining "Started" keys from version STATE.md files
- Standardizes non-standard Resolution values (not-applicable, already-implemented, etc.)
- Simplifies verbose Resolution descriptions that exceed 50 characters

Idempotency: Safe to run multiple times. Only modifies files that need changes.

Execution order: Run SECOND, after migrate-state-schema.py.
"""

import re
from pathlib import Path

from state_schema_lib import find_state_files


def fix_state_file(file_path: Path) -> bool:
    """Fix a single STATE.md file. Returns True if modified."""
    content = file_path.read_text()
    original_content = content

    # Fix 1: Remove Started key
    content = re.sub(r'^-\s+\*\*Started:\*\*\s+[^\n]+\n', '', content, flags=re.MULTILINE)

    # Fix 2: Standardize Resolution values
    resolution_fixes = {
        'not-applicable': 'not-applicable (no longer needed)',
        'already-implemented': 'implemented',
        'duplicate': 'duplicate (see parent issue)',
        'merged': 'duplicate (merged into another issue)',
    }

    for old_value, new_value in resolution_fixes.items():
        # Match exact Resolution value (not inside parentheses)
        pattern = r'^(-\s+\*\*Resolution:\*\*\s+)' + re.escape(old_value) + r'(\s*)$'
        content = re.sub(pattern, r'\g<1>' + new_value + r'\g<2>', content, flags=re.MULTILINE)

    # Fix verbose Resolution values - extract meaningful parts
    # Pattern: "Resolution: implemented (verbose description)"
    # Keep only "implemented" if the description is too verbose
    verbose_pattern = r'^(-\s+\*\*Resolution:\*\*\s+)(implemented)\s+-\s+(.+)$'
    def simplify_verbose(match):
        prefix = match.group(1)
        status = match.group(2)
        description = match.group(3)
        # If description is too long (> 50 chars), simplify
        if len(description) > 50:
            return f'{prefix}{status}'
        else:
            return f'{prefix}{status} ({description})'

    content = re.sub(verbose_pattern, simplify_verbose, content, flags=re.MULTILINE)

    if content != original_content:
        file_path.write_text(content)
        return True
    return False


def main():
    """Main function."""
    issues_dir = Path('.claude/cat/issues')

    if not issues_dir.exists():
        print(f'ERROR: Issues directory not found: {issues_dir}')
        return 1

    # Find all STATE.md files
    state_files = find_state_files(issues_dir)
    print(f'Found {len(state_files)} STATE.md files')

    modified_count = 0
    for state_file in state_files:
        try:
            if fix_state_file(state_file):
                modified_count += 1
                print(f'✓ {state_file.relative_to(issues_dir.parent)}')
        except Exception as e:
            print(f'✗ {state_file.relative_to(issues_dir.parent)}: {e}')
            return 1

    print(f'\nFix complete: {modified_count}/{len(state_files)} files modified')
    return 0


if __name__ == '__main__':
    exit(main())
