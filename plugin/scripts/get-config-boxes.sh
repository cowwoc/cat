#!/usr/bin/env bash
# get-config-boxes.sh - Generate all config box templates
#
# USAGE: get-config-boxes.sh --project-dir <dir>
#
# OUTPUTS: Pre-rendered config boxes including current settings and templates
#
# This script is designed to be called via silent preprocessing (!`command`).

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Parse arguments
PROJECT_DIR=""

while [[ $# -gt 0 ]]; do
    case "$1" in
        --project-dir)
            PROJECT_DIR="$2"
            shift 2
            ;;
        *)
            shift
            ;;
    esac
done

# Default to CLAUDE_PROJECT_DIR or current directory
if [[ -z "$PROJECT_DIR" ]]; then
    PROJECT_DIR="${CLAUDE_PROJECT_DIR:-$(pwd)}"
fi

# Check if CAT is initialized
if [[ ! -f "$PROJECT_DIR/.claude/cat/cat-config.json" ]]; then
    echo "**Config not available** - Run /cat:init first"
    exit 0
fi

# Get current settings display
CURRENT_SETTINGS=$("$SCRIPT_DIR/get-config-display.sh" "$PROJECT_DIR" 2>/dev/null || echo "Unable to load current settings")

# Output all boxes
cat << 'BOXES'
## Pre-rendered Config Boxes

**CRITICAL**: Copy-paste the EXACT boxes below. Do NOT reconstruct or retype them.

BOXES

echo "### CURRENT_SETTINGS"
echo ""
echo "$CURRENT_SETTINGS"
echo ""

cat << 'TEMPLATES'
### VERSION_GATES_OVERVIEW

╭─── 📊 VERSION GATES ─────────────────────────╮
│                                              │
│ Entry and exit gates control version         │
│ dependencies.                                │
│                                              │
│ Select a version to configure its gates,    │
│ or choose 'Apply defaults to all'.          │
╰──────────────────────────────────────────────╯

### GATES_FOR_VERSION

╭─── 🚧 GATES FOR {version} ───────────────────╮
│                                              │
│ Entry: {entry-gate-description}              │
│ Exit: {exit-gate-description}                │
╰──────────────────────────────────────────────╯

### GATES_UPDATED

╭─── ✅ GATES UPDATED ─────────────────────────╮
│                                              │
│ Version: {version}                           │
│ Entry: {new-entry-gate}                      │
│ Exit: {new-exit-gate}                        │
╰──────────────────────────────────────────────╯

### SETTING_UPDATED

╭─── ✅ SETTING UPDATED ───────────────────────╮
│                                              │
│ {setting-name}: {old-value} → {new-value}    │
╰──────────────────────────────────────────────╯

### CONFIGURATION_SAVED

╭─── ✅ CONFIGURATION SAVED ───────────────────╮
│                                              │
│ Changes committed to cat-config.json         │
╰──────────────────────────────────────────────╯

### NO_CHANGES

╭─── ℹ️ NO CHANGES ─────────────────────────────╮
│                                              │
│ Configuration unchanged.                     │
╰──────────────────────────────────────────────╯

---

**INSTRUCTION**: Copy-paste box structures VERBATIM, then replace ONLY {placeholder} text inside.
TEMPLATES
