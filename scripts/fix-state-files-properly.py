#!/usr/bin/env python3
# Copyright (c) 2026 Gili Tzabari. All rights reserved.
#
# Licensed under the CAT Commercial License.
# See LICENSE.md in the project root for license terms.
"""
Comprehensive STATE.md rebuild for complex multi-section files.

This script handles version-level STATE.md files (with ## Status sections)
and rebuilds regular issue STATE.md files from scratch when needed.

What it fixes:
- Handles version-level STATE.md files with ## Status sections
- Ensures mandatory keys exist in all files
- Standardizes Resolution format (removes commit hashes, fixes duplicates)
- Cleans up mangled Resolution values with embedded keys
- Rebuilds file structure while preserving content sections

Idempotency: Safe to run multiple times. Only modifies files that need changes.

Execution order: Run THIRD, after migrate-state-schema.py and fix-remaining-issues.py.
"""

import re
import subprocess
from pathlib import Path
from typing import Dict, Optional

from state_schema_lib import parse_state_keys, get_git_last_modified, find_state_files




def rebuild_state_file(file_path: Path) -> bool:
    """Rebuild STATE.md file with correct structure. Returns True if modified."""
    content = file_path.read_text()
    original_content = content

    # Extract all keys from file
    all_keys = parse_state_keys(content)

    # Check if this is a version-level STATE.md (has ## Status section)
    has_status_section = '## Status' in content

    if has_status_section:
        # This is a version-level STATE.md - preserve its structure
        # Just ensure mandatory keys in top section and clean up ## Status duplicates
        lines = content.split('\n')
        new_lines = []
        in_state_header = False
        in_status_section = False
        state_keys_added = False

        for line in lines:
            if line.strip() == '# State':
                new_lines.append(line)
                new_lines.append('')
                # Add mandatory keys here
                if 'Dependencies' in all_keys:
                    new_lines.append(f'- **Dependencies:** {all_keys["Dependencies"]}')
                else:
                    new_lines.append('- **Dependencies:** []')
                if 'Blocks' in all_keys:
                    new_lines.append(f'- **Blocks:** {all_keys["Blocks"]}')
                else:
                    new_lines.append('- **Blocks:** []')
                if 'Last Updated' in all_keys:
                    new_lines.append(f'- **Last Updated:** {all_keys["Last Updated"]}')
                state_keys_added = True
                continue

            # Skip old keys in # State section
            if state_keys_added and not in_status_section and re.match(r'^-\s+\*\*', line):
                continue

            # Detect ## Status section
            if line.startswith('## Status'):
                in_status_section = True
                new_lines.append(line)
                continue

            # Skip until we're past the keys in ## Status section
            if in_status_section:
                # Check if we've moved to a new section
                if line.startswith('##') and not line.startswith('## Status'):
                    in_status_section = False
                    new_lines.append(line)
                    continue
                # Keep the Status and Progress keys in ## Status section
                if re.match(r'^-\s+\*\*(?:Status|Progress):\*\*', line):
                    new_lines.append(line)
                    continue
                # Skip other keys in ## Status section (Dependencies, Started, etc.)
                if re.match(r'^-\s+\*\*', line):
                    continue

            new_lines.append(line)

        new_content = '\n'.join(new_lines)
    else:
        # This is a regular issue STATE.md - build it from scratch
        mandatory_keys = {'Status', 'Progress', 'Dependencies', 'Blocks', 'Last Updated'}

        # Ensure all mandatory keys exist
        if 'Dependencies' not in all_keys:
            all_keys['Dependencies'] = '[]'
        if 'Blocks' not in all_keys:
            all_keys['Blocks'] = '[]'
        if 'Last Updated' not in all_keys:
            all_keys['Last Updated'] = get_git_last_modified(file_path)

        # Check status for Resolution requirement
        status = all_keys.get('Status', 'open')
        if status == 'closed' and 'Resolution' not in all_keys:
            all_keys['Resolution'] = 'implemented'

        # Standardize Resolution format
        if 'Resolution' in all_keys:
            res = all_keys['Resolution']
            # Fix common non-standard formats
            if res == 'not-applicable':
                all_keys['Resolution'] = 'not-applicable (no longer needed)'
            elif res == 'already-implemented':
                all_keys['Resolution'] = 'implemented'
            elif res == 'duplicate':
                all_keys['Resolution'] = 'duplicate (see parent issue)'
            elif res == 'merged':
                all_keys['Resolution'] = 'duplicate (merged into another issue)'
            # Clean up mangled Resolution values (like "implemented (**Dependencies:** [])")
            elif re.match(r'^(implemented|duplicate|obsolete|won\'t-fix|not-applicable)\s+\(?\*\*', res):
                # Extract just the first word
                match = re.match(r'^(implemented|duplicate|obsolete|won\'t-fix|not-applicable)', res)
                if match:
                    all_keys['Resolution'] = match.group(1)

        # Find content after key section
        lines = content.split('\n')
        remaining_lines = []
        past_header = False
        past_keys = False

        for line in lines:
            if line.strip() == '# State':
                past_header = True
                continue
            if past_header and not past_keys:
                # Skip keys and empty lines after header
                if re.match(r'^-\s+\*\*', line) or line.strip() == '':
                    continue
                # Found first non-key content
                past_keys = True
            if past_keys:
                remaining_lines.append(line)

        remaining_content = '\n'.join(remaining_lines).strip()

        # Build new content
        new_lines = ['# State', '']

        # Add keys in standard order
        key_order = ['Status', 'Progress', 'Resolution', 'Parent', 'Dependencies', 'Blocks', 'Last Updated']

        for key in key_order:
            if key in all_keys:
                new_lines.append(f'- **{key}:** {all_keys[key]}')

        # Add remaining content
        if remaining_content:
            new_lines.append('')
            new_lines.append(remaining_content)

        new_content = '\n'.join(new_lines)

    # Ensure file ends with newline
    if not new_content.endswith('\n'):
        new_content += '\n'

    if new_content != original_content:
        file_path.write_text(new_content)
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
            if rebuild_state_file(state_file):
                modified_count += 1
                print(f'✓ {state_file.relative_to(issues_dir.parent)}')
        except Exception as e:
            print(f'✗ {state_file.relative_to(issues_dir.parent)}: {e}')
            import traceback
            traceback.print_exc()
            return 1

    print(f'\nRebuild complete: {modified_count}/{len(state_files)} files modified')
    return 0


if __name__ == '__main__':
    exit(main())
