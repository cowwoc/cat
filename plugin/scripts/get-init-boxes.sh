#!/usr/bin/env bash
# get-init-boxes.sh - Generate init skill box templates
#
# USAGE: get-init-boxes.sh
#
# OUTPUTS: Init box templates (script output)
#
# This script is designed to be called via silent preprocessing (!`command`).

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Call Python to generate boxes with correct alignment
python3 "$SCRIPT_DIR/build-init-boxes.py" --format json | python3 -c "
import json
import sys

data = json.load(sys.stdin)

if 'error' in data:
    print(f\"Error: {data['error']}\", file=sys.stderr)
    sys.exit(1)

print('''SCRIPT OUTPUT INIT BOXES:

Use these box templates EXACTLY as shown. Replace {variables} with actual values at runtime.

=== BOX: default_gates_configured ===
Variables: {N} = version count''')
print(data['default_gates_configured'])
print()

print('''=== BOX: research_skipped ===
Variables: {version} = example version number (shown in help text)''')
print(data['research_skipped'])
print()

print('''=== BOX: choose_your_partner ===
Variables: none (static)''')
print(data['choose_your_partner'])
print()

print('''=== BOX: cat_initialized ===
Variables: {trust}, {curiosity}, {patience} = user preference values''')
print(data['cat_initialized'])
print()

print('''=== BOX: first_task_walkthrough ===
Variables: none (static)''')
print(data['first_task_walkthrough'])
print()

print('''=== BOX: first_task_created ===
Variables: {issue-name} = sanitized issue name from user input''')
print(data['first_task_created'])
print()

print('''=== BOX: all_set ===
Variables: none (static)''')
print(data['all_set'])
print()

print('''=== BOX: explore_at_your_own_pace ===
Variables: none (static)''')
print(data['explore_at_your_own_pace'])
print()

print('''INSTRUCTION: When displaying a box, copy the template EXACTLY and only replace the {variable} placeholders.
Do NOT recalculate padding or alignment - the boxes are pre-computed with correct widths.''')
"
