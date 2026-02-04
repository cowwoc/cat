#!/usr/bin/env bash
# render-add-complete.sh - Render issue/version creation completion box
#
# USAGE: render-add-complete.sh --type <issue|version> [options]
#
# Issue options:
#   --name <name>        Issue name (e.g., "parse-tokens")
#   --version <ver>      Version string (e.g., "2.0")
#   --issue-type <type>  Issue type (Feature, Bugfix, etc.)
#   --deps <deps>        Comma-separated dependencies
#
# Version options:
#   --name <name>       Version name/title
#   --version <ver>     Version string (e.g., "2.1")
#   --version-type <t>  Type: major, minor, patch
#   --parent <info>     Parent version info
#   --path <path>       Filesystem path
#
# This script renders completion boxes for /cat:add delegated preprocessing.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Parse arguments
ITEM_TYPE=""
ITEM_NAME=""
VERSION=""
ISSUE_TYPE="Feature"
DEPS=""
VERSION_TYPE="minor"
PARENT_INFO=""
ITEM_PATH=""

while [[ $# -gt 0 ]]; do
    case "$1" in
        --type) ITEM_TYPE="$2"; shift 2 ;;
        --name) ITEM_NAME="$2"; shift 2 ;;
        --version) VERSION="$2"; shift 2 ;;
        --issue-type) ISSUE_TYPE="$2"; shift 2 ;;
        --deps) DEPS="$2"; shift 2 ;;
        --version-type) VERSION_TYPE="$2"; shift 2 ;;
        --parent) PARENT_INFO="$2"; shift 2 ;;
        --path) ITEM_PATH="$2"; shift 2 ;;
        *) shift ;;
    esac
done

if [[ -z "$ITEM_TYPE" ]]; then
    echo "Error: --type required (issue or version)"
    exit 1
fi

# Call Python to render the box with proper alignment
python3 - << PYTHON_EOF
import sys
sys.path.insert(0, "$SCRIPT_DIR/lib")
from emoji_widths import display_width, get_emoji_widths

ew = get_emoji_widths()

def dw(s):
    return display_width(s, ew)

def build_line(content, max_width):
    padding = max_width - dw(content)
    return "│ " + content + " " * padding + " │"

def build_header_box(header, content_items, min_width=40, prefix="─ "):
    # Calculate max width
    content_widths = [dw(c) for c in content_items]
    header_with_prefix = prefix + header
    header_width = dw(header_with_prefix)
    max_width = max(max(content_widths) if content_widths else 0, header_width, min_width)

    # Build box
    suffix_dashes = "─" * (max_width - header_width + 2)
    lines = ["╭" + header_with_prefix + " " + suffix_dashes + "╮"]
    for c in content_items:
        lines.append(build_line(c, max_width))
    lines.append("╰" + "─" * (max_width + 2) + "╯")
    return "\n".join(lines)

item_type = "$ITEM_TYPE"
item_name = "$ITEM_NAME" or "unknown"
version = "$VERSION" or "0.0"
issue_type = "$ISSUE_TYPE" or "Feature"
deps = "$DEPS"
version_type = "$VERSION_TYPE" or "minor"
parent_info = "$PARENT_INFO"
item_path = "$ITEM_PATH"

if item_type == "issue":
    deps_str = deps if deps else "None"
    content = [
        f"{version}-{item_name}",
        f"Type: {issue_type}",
        f"Dependencies: {deps_str}",
    ]
    header = "✅ Issue Created"
    box = build_header_box(header, content, min_width=40, prefix="─ ")
    next_cmd = f"/cat:work {version}-{item_name}"
    print(box)
    print()
    print(f"Next: /clear, then {next_cmd}")
else:
    content = [
        f"v{version}: {item_name}",
        "",
    ]
    if parent_info:
        content.append(f"Parent: {parent_info}")
    if item_path:
        content.append(f"Path: {item_path}")
    header = "✅ Version Created"
    box = build_header_box(header, content, min_width=40, prefix="─ ")
    print(box)
    print()
    print("Next: /clear, then /cat:add (to add issues)")
PYTHON_EOF
