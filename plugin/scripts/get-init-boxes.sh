#!/usr/bin/env bash
# get-init-boxes.sh - Generate init box templates
#
# USAGE: get-init-boxes.sh
#
# OUTPUTS: Pre-rendered init box templates for /cat:init
#
# This script is designed to be called via silent preprocessing (!`command`).

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Call the existing Python script with JSON output, then format for skill
python3 "$SCRIPT_DIR/build-init-boxes.py" --format json 2>/dev/null | python3 -c "
import sys
import json

data = json.load(sys.stdin)

if 'error' in data:
    print('**Init boxes unavailable** - ' + data['error'])
    sys.exit(0)

print('## Pre-rendered Init Boxes')
print()
print('Use these box templates EXACTLY as shown. Replace {variables} with actual values.')
print()

boxes = [
    ('default_gates_configured', 'Variables: {N} = version count'),
    ('research_skipped', 'Variables: {version} = example version number'),
    ('choose_your_partner', 'Variables: none (static)'),
    ('cat_initialized', 'Variables: {trust}, {curiosity}, {patience}'),
    ('first_task_walkthrough', 'Variables: none (static)'),
    ('first_task_created', 'Variables: {task-name}'),
    ('all_set', 'Variables: none (static)'),
    ('explore_at_your_own_pace', 'Variables: none (static)'),
]

for name, desc in boxes:
    if name in data:
        print(f'### {name}')
        print(f'{desc}')
        print()
        print(data[name])
        print()

print('---')
print()
print('**INSTRUCTION**: Copy template EXACTLY and only replace {variable} placeholders.')
print('Do NOT recalculate padding or alignment - boxes are pre-computed with correct widths.')
"
