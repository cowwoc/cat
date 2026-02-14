#!/usr/bin/env python3
# Copyright (c) 2026 Gili Tzabari. All rights reserved.
#
# Licensed under the CAT Commercial License.
# See LICENSE.md in the project root for license terms.
"""
Migrate all STATE.md files to the standardized schema.

This is the primary migration script that performs the bulk of schema
standardization work. It should be run first in the migration sequence.

What it fixes:
- Adds missing mandatory keys (Dependencies, Blocks, Last Updated)
- Adds Resolution for closed issues without it
- Renames "Created From" to "Parent"
- Folds "Reason", "Closed Reason", "Obsolete Reason", "Abandoned" into Resolution
- Folds "Duplicate Of" into Resolution as "duplicate (issue-id)"
- Removes non-standard keys (Completed, Version, Tokens Used, etc.)
- Preserves content after the key section

Idempotency: Safe to run multiple times. Only modifies files that need changes.

Execution order: Run this FIRST, before other migration scripts.
"""

import re
from pathlib import Path
from typing import Dict, List, Optional, Tuple

from state_schema_lib import get_git_last_modified, find_state_files


# Keys to remove from STATE.md files
REMOVED_KEYS = {
    'Completed', 'Completed At', 'Version', 'Tokens Used', 'Started',
    'Decomposed At', 'Decomposed', 'Assignee', 'Priority', 'Worktree',
    'Merged', 'Commit', 'Note', 'Scope Note', 'Completion Notes',
    # These are folded into Resolution, then removed
    'Reason', 'Closed Reason', 'Obsolete Reason', 'Abandoned', 'Duplicate Of',
    # Renamed
    'Created From'
}

# Mandatory keys for all issues
MANDATORY_KEYS = {'Status', 'Progress', 'Dependencies', 'Blocks', 'Last Updated'}

# Optional keys
OPTIONAL_KEYS = {'Parent', 'Resolution'}




def parse_state_file(content: str) -> Tuple[Dict[str, str], str]:
    """
    Parse STATE.md file into key-value pairs and remaining content.

    Returns:
        (keys_dict, remaining_content)
    """
    lines = content.split('\n')
    keys = {}
    key_section_end = 0

    # Find the key-value section (starts after "# State" header)
    in_key_section = False
    current_key = None
    current_value_lines = []

    for i, line in enumerate(lines):
        if line.strip() == '# State':
            in_key_section = True
            key_section_end = i + 1
            continue

        if in_key_section:
            # Match pattern: - **Key:** value
            match = re.match(r'^-\s+\*\*([^:]+):\*\*\s*(.*)', line)
            if match:
                # Save previous key-value if exists
                if current_key:
                    keys[current_key] = '\n'.join(current_value_lines).strip()

                # Start new key
                current_key = match.group(1).strip()
                current_value_lines = [match.group(2).strip()]
                key_section_end = i + 1
            elif line.strip() == '':
                # Empty line - end of key section if we have keys
                if current_key:
                    keys[current_key] = '\n'.join(current_value_lines).strip()
                    current_key = None
                    current_value_lines = []
                key_section_end = i + 1
            elif current_key and line.startswith('  '):
                # Continuation of previous value (indented)
                current_value_lines.append(line.strip())
                key_section_end = i + 1
            else:
                # End of key section
                if current_key:
                    keys[current_key] = '\n'.join(current_value_lines).strip()
                break

    # Save last key if still in progress
    if current_key:
        keys[current_key] = '\n'.join(current_value_lines).strip()

    # Everything after the key section is preserved, but skip any remaining key-like lines
    remaining_lines = []
    for line in lines[key_section_end:]:
        # Skip lines that look like keys or their continuations
        if re.match(r'^-\s+\*\*[^:]+:\*\*', line):
            continue
        if line.strip() == '':
            # Keep empty lines that come after we've started collecting content
            if remaining_lines:
                remaining_lines.append(line)
        elif line.startswith('  ') and not remaining_lines:
            # Skip indented continuation lines before real content
            continue
        else:
            remaining_lines.append(line)

    remaining_content = '\n'.join(remaining_lines).strip()

    return keys, remaining_content


def build_resolution(keys: Dict[str, str]) -> Optional[str]:
    """Build Resolution value from existing keys."""
    resolution = keys.get('Resolution', '').strip()

    # Handle Duplicate Of
    if 'Duplicate Of' in keys:
        duplicate_of = keys['Duplicate Of'].strip()
        # Extract issue ID if it's in parentheses or has description
        issue_id_match = re.match(r'^([^\s(]+)', duplicate_of)
        if issue_id_match:
            issue_id = issue_id_match.group(1)
            return f'duplicate ({issue_id})'
        return 'duplicate'

    # Build parenthetical from Reason fields (only if Resolution doesn't already have one)
    reason_parts = []
    for reason_key in ['Reason', 'Closed Reason', 'Obsolete Reason', 'Abandoned']:
        if reason_key in keys:
            reason_value = keys[reason_key].strip()
            # Clean up nested "**Reason:**" prefixes and multi-line artifacts
            reason_value = re.sub(r'^-?\s*\*\*Reason:\*\*\s*', '', reason_value)
            # Take only the first line if multi-line
            reason_value = reason_value.split('\n')[0].strip()
            if reason_value:
                reason_parts.append(reason_value)

    # Combine resolution with reasons
    if resolution:
        # If resolution already has parenthetical, don't add more
        if '(' in resolution and ')' in resolution:
            return resolution

        if reason_parts:
            combined_reason = ' '.join(reason_parts)
            return f'{resolution} ({combined_reason})'
        return resolution

    # No resolution but has Abandoned key
    if 'Abandoned' in keys:
        return 'won\'t-fix (abandoned)'

    # No resolution found
    return None


def build_or_update_resolution(keys: Dict[str, str]) -> None:
    """
    Build or update Resolution field for closed issues.

    Modifies keys dict in-place by setting Resolution if status is closed.
    Uses build_resolution() to construct value from related fields.

    Args:
        keys: Dictionary of STATE.md keys (modified in-place)
    """
    status = keys.get('Status', 'open')
    if status == 'closed':
        if 'Resolution' not in keys or not keys['Resolution'].strip():
            # Try to build from other fields
            resolution = build_resolution(keys)
            if resolution:
                keys['Resolution'] = resolution
            else:
                # Default for closed issues without any resolution info
                keys['Resolution'] = 'implemented'
        else:
            # Update existing resolution with reason fields
            resolution = build_resolution(keys)
            if resolution:
                keys['Resolution'] = resolution


def format_state_content(keys: Dict[str, str], remaining: str) -> str:
    """
    Format STATE.md content from keys and remaining sections.

    Args:
        keys: Dictionary of STATE.md keys
        remaining: Additional content after key section

    Returns:
        Formatted STATE.md content with trailing newline
    """
    new_lines = ['# State', '']

    # Add keys in standard order
    key_order = ['Status', 'Progress', 'Resolution', 'Parent', 'Dependencies', 'Blocks', 'Last Updated']

    for key in key_order:
        if key in keys:
            new_lines.append(f'- **{key}:** {keys[key]}')

    # Add remaining content
    if remaining:
        new_lines.append('')
        new_lines.append(remaining)

    # Write back
    new_content = '\n'.join(new_lines)
    if not new_content.endswith('\n'):
        new_content += '\n'

    return new_content


def migrate_state_file(file_path: Path) -> bool:
    """
    Migrate a single STATE.md file.

    Returns:
        True if file was modified, False otherwise
    """
    content = file_path.read_text()
    keys, remaining = parse_state_file(content)

    original_keys = keys.copy()

    # Step 1: Add missing mandatory keys
    if 'Dependencies' not in keys:
        keys['Dependencies'] = '[]'

    if 'Blocks' not in keys:
        keys['Blocks'] = '[]'

    if 'Last Updated' not in keys:
        keys['Last Updated'] = get_git_last_modified(file_path)

    # Step 2: Handle Resolution for closed issues
    build_or_update_resolution(keys)

    # Step 3: Rename "Created From" to "Parent"
    if 'Created From' in keys:
        keys['Parent'] = keys['Created From']

    # Step 4: Remove all non-standard keys
    for removed_key in REMOVED_KEYS:
        keys.pop(removed_key, None)

    # Check if anything changed
    if keys == original_keys:
        return False

    # Build and write new content
    new_content = format_state_content(keys, remaining)
    file_path.write_text(new_content)
    return True


def main():
    """Main migration function."""
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
            if migrate_state_file(state_file):
                modified_count += 1
                print(f'✓ {state_file.relative_to(issues_dir.parent)}')
        except Exception as e:
            print(f'✗ {state_file.relative_to(issues_dir.parent)}: {e}')
            return 1

    print(f'\nMigration complete: {modified_count}/{len(state_files)} files modified')
    return 0


if __name__ == '__main__':
    exit(main())
