#!/bin/bash
set -euo pipefail

# Init Command Banner Generator
#
# Renders various banners for the /cat:init command with proper
# emoji-aware width calculation.
#
# Usage: init-banner.sh BANNER_TYPE [ARGS...]
#
# Banner types:
#   choose-partner          - "Choose Your Partner" welcome banner
#   gates-configured N      - Default gates configured for N versions
#   research-skipped        - Research skipped informational banner
#   initialized TRUST CUR PAT - CAT initialized with partner profile
#   first-task-walkthrough  - First task walkthrough intro
#   first-task-created NAME PATH - First task created confirmation
#   all-set                 - All set, explore later
#   explore-pace            - Explore at your own pace

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/lib/box.sh"

# Standard banner width: 66 (64 interior + 2 borders)
box_init 66

banner_choose_partner() {
    box_top "🎮 CHOOSE YOUR PARTNER"
    box_empty
    box_line "  Every developer has a style. These questions shape how your"
    box_line "  AI partner approaches the work ahead."
    box_empty
    box_line "  Choose wisely - your preferences guide every decision."
    box_empty
    box_bottom
}

banner_gates_configured() {
    local version_count="${1:-N}"
    box_top "📊 Default gates configured for ${version_count} versions"
    box_empty
    box_line "  Entry gates: Work proceeds sequentially"
    box_line "  • Each minor waits for previous minor to complete"
    box_line "  • Each major waits for previous major to complete"
    box_empty
    box_line "  Exit gates: Standard completion criteria"
    box_line "  • Minor versions: all tasks must complete"
    box_line "  • Major versions: all minor versions must complete"
    box_empty
    box_line "  To customize gates for any version:"
    box_line "  → /cat:config → 📊 Version Gates"
    box_empty
    box_bottom
}

banner_research_skipped() {
    box_top "ℹ️ RESEARCH SKIPPED"
    box_empty
    box_line "  Stakeholder research was skipped during import."
    box_empty
    box_line "  To research a pending version later:"
    box_line "  → /cat:research {version}"
    box_empty
    box_line "  Example: /cat:research 1.2"
    box_empty
    box_bottom
}

banner_initialized() {
    local trust="${1:-medium}"
    local curiosity="${2:-medium}"
    local patience="${3:-high}"

    box_top "🚀 CAT INITIALIZED"
    box_empty
    box_line "  PARTNER PROFILE"
    box_line "  ─────────────────────────────────────────────────────────"
    box_line "  Working Style:  ${trust}"
    box_line "  Exploration:    ${curiosity}"
    box_line "  Opportunity:    ${patience}"
    box_empty
    box_line "  Your partner is ready. Let's build something solid."
    box_line "  Adjust your style anytime: /cat:config"
    box_empty
    box_bottom
}

banner_first_task_walkthrough() {
    box_top "📋 FIRST TASK WALKTHROUGH"
    box_empty
    box_line "  Great! Let's create your first task together."
    box_line "  I'll ask a few questions to understand what you want to build."
    box_empty
    box_bottom
}

banner_first_task_created() {
    local task_name="${1:-task-name}"
    local task_path="${2:-.claude/cat/v0/v0.0/${task_name}/}"

    box_top "✅ FIRST TASK CREATED"
    box_empty
    box_line "  Task: ${task_name}"
    box_line "  Location: ${task_path}"
    box_empty
    box_line "  Files created:"
    box_line "  • PLAN.md - What needs to be done"
    box_line "  • STATE.md - Progress tracking"
    box_empty
    box_bottom
}

banner_all_set() {
    box_top "👋 ALL SET"
    box_empty
    box_line "  Your project is ready. When you want to start:"
    box_empty
    box_line "  → /cat:work         Execute your first task"
    box_line "  → /cat:status       See project overview"
    box_line "  → /cat:add          Add more tasks or versions"
    box_line "  → /cat:help         Full command reference"
    box_empty
    box_bottom
}

banner_explore_pace() {
    box_top "👋 EXPLORE AT YOUR OWN PACE"
    box_empty
    box_line "  Essential commands to get started:"
    box_empty
    box_line "  → /cat:status       See what's happening"
    box_line "  → /cat:add          Add versions and tasks"
    box_line "  → /cat:work         Execute tasks"
    box_line "  → /cat:help         Full command reference"
    box_empty
    box_line "  Tip: Run /cat:status anytime to see suggested next steps."
    box_empty
    box_bottom
}

# Main dispatcher
BANNER_TYPE="${1:-}"
shift || true

case "$BANNER_TYPE" in
    choose-partner)
        banner_choose_partner
        ;;
    gates-configured)
        banner_gates_configured "$@"
        ;;
    research-skipped)
        banner_research_skipped
        ;;
    initialized)
        banner_initialized "$@"
        ;;
    first-task-walkthrough)
        banner_first_task_walkthrough
        ;;
    first-task-created)
        banner_first_task_created "$@"
        ;;
    all-set)
        banner_all_set
        ;;
    explore-pace)
        banner_explore_pace
        ;;
    *)
        echo "Usage: init-banner.sh BANNER_TYPE [ARGS...]" >&2
        echo "" >&2
        echo "Banner types:" >&2
        echo "  choose-partner          - Choose Your Partner welcome" >&2
        echo "  gates-configured N      - Default gates configured" >&2
        echo "  research-skipped        - Research skipped info" >&2
        echo "  initialized T C P       - CAT initialized (trust, curiosity, patience)" >&2
        echo "  first-task-walkthrough  - First task intro" >&2
        echo "  first-task-created N P  - First task created (name, path)" >&2
        echo "  all-set                 - All set banner" >&2
        echo "  explore-pace            - Explore at own pace" >&2
        exit 1
        ;;
esac
