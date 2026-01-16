#!/bin/bash
# Get emoji display width for current terminal environment
#
# Usage: ./get-emoji-width.sh <emoji>
# Usage: ./get-emoji-width.sh --all
#
# Reads from emoji-widths.json and returns the width based on
# detected OS, terminal, and version.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WIDTHS_FILE="${SCRIPT_DIR}/../emoji-widths.json"

if [[ ! -f "$WIDTHS_FILE" ]]; then
    echo "ERROR: emoji-widths.json not found at $WIDTHS_FILE" >&2
    exit 1
fi

# Detect OS
detect_os() {
    uname -s
}

# Detect terminal
detect_terminal() {
    if [[ -n "${TERM_PROGRAM:-}" ]]; then
        echo "$TERM_PROGRAM"
    elif [[ -n "${TERMINAL_EMULATOR:-}" ]]; then
        echo "$TERMINAL_EMULATOR"
    elif [[ -n "${COLORTERM:-}" ]]; then
        echo "$COLORTERM"
    else
        echo "${TERM:-unknown}"
    fi
}

# Detect terminal version
detect_terminal_version() {
    local terminal="$1"

    case "$terminal" in
        iTerm.app|vscode)
            echo "${TERM_PROGRAM_VERSION:-0.0}"
            ;;
        Apple_Terminal)
            sw_vers -productVersion 2>/dev/null || echo "0.0"
            ;;
        *)
            echo "0.0"
            ;;
    esac
}

# Compare versions: returns 0 if version >= min_version
version_gte() {
    local version="$1"
    local min_version="$2"

    # Remove >= prefix if present
    min_version="${min_version#>=}"

    # Simple numeric comparison (handles X.Y.Z format)
    printf '%s\n%s\n' "$min_version" "$version" | sort -V | head -n1 | grep -qF "$min_version"
}

# Get width for emoji
get_width() {
    local emoji="$1"
    local os terminal version

    os=$(detect_os)
    terminal=$(detect_terminal)
    version=$(detect_terminal_version "$terminal")

    # Try OS -> terminal -> version range lookup
    # Fall back to default if not found

    local width

    # Try specific lookup first
    width=$(jq -r --arg os "$os" --arg term "$terminal" --arg emoji "$emoji" '
        .widths[$os][$term] // empty |
        to_entries |
        .[0].value[$emoji] // empty
    ' "$WIDTHS_FILE" 2>/dev/null)

    # Fall back to default
    if [[ -z "$width" ]] || [[ "$width" == "null" ]]; then
        width=$(jq -r --arg emoji "$emoji" '.widths.default[$emoji] // 2' "$WIDTHS_FILE")
    fi

    echo "$width"
}

# Get all widths as JSON
get_all_widths() {
    local os terminal

    os=$(detect_os)
    terminal=$(detect_terminal)

    jq -r --arg os "$os" --arg term "$terminal" '
        (.widths[$os][$term] // empty | to_entries | .[0].value) //
        .widths.default
    ' "$WIDTHS_FILE"
}

# Main
if [[ "${1:-}" == "--all" ]]; then
    get_all_widths
elif [[ -n "${1:-}" ]]; then
    get_width "$1"
else
    echo "Usage: $0 <emoji>    Get width of single emoji"
    echo "       $0 --all      Get all widths as JSON"
    exit 1
fi
