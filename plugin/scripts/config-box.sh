#!/bin/bash
set -euo pipefail

# Config Command Box Generator
#
# Renders various boxes for the /cat:config command with proper
# emoji-aware width calculation.
#
# Usage: config-box.sh BOX_TYPE [ARGS...]
#
# Box types:
#   settings CONTEXT_LIMIT TARGET_USAGE TRUST VERIFY CURIOSITY PATIENCE AUTO_REMOVE
#   behavior TRUST VERIFY CURIOSITY PATIENCE
#   trust CURRENT_LEVEL
#   verify CURRENT_LEVEL
#   curiosity CURRENT_LEVEL
#   patience CURRENT_LEVEL
#   version-gates
#   gates-for VERSION ENTRY_CONDITIONS EXIT_CONDITIONS
#   no-gates VERSION
#   gates-updated VERSION ENTRY_SUMMARY EXIT_SUMMARY
#   setting-updated SETTING OLD_VALUE NEW_VALUE
#   saved CHANGES...
#   no-changes

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/lib/box.sh"

# Config boxes use width 60 (58 interior + 2 borders)
box_init 60

box_settings() {
    local context_limit="${1:-200000}"
    local target_usage="${2:-40}"
    local trust="${3:-medium}"
    local verify="${4:-changed}"
    local curiosity="${5:-low}"
    local patience="${6:-high}"
    local auto_remove="${7:-true}"

    box_top "⚙️ CAT SETTINGS"
    box_empty
    box_line "  🧠 CONTEXT LIMITS"
    box_line "     Window:  ${context_limit} tokens"
    box_line "     Target:  ${target_usage}% before split"
    box_empty
    box_line "  🐱 BEHAVIOR"
    box_line "     Trust:     ${trust}"
    box_line "     Verify:    ${verify}"
    box_line "     Curiosity: ${curiosity}"
    box_line "     Patience:  ${patience}"
    box_empty
    box_line "  🧹 CLEANUP"
    box_line "     Auto-remove: ${auto_remove}"
    box_empty
    box_line "  📊 VERSION GATES"
    box_line "     Configure entry/exit conditions for versions"
    box_empty
    box_bottom
}

box_behavior() {
    local trust="${1:-medium}"
    local verify="${2:-changed}"
    local curiosity="${3:-low}"
    local patience="${4:-high}"

    box_top "🐱 CAT BEHAVIOR"
    box_empty
    box_line "  🤝 Trust:     ${trust}"
    box_line "  ✅ Verify:    ${verify}"
    box_line "  🔍 Curiosity: ${curiosity}"
    box_line "  ⏳ Patience:  ${patience}"
    box_empty
    box_bottom
}

box_trust() {
    local current="${1:-medium}"
    local low_marker="" medium_marker="" high_marker=""
    [[ "$current" == "low" ]] && low_marker="(current)"
    [[ "$current" == "medium" ]] && medium_marker="(current)"
    [[ "$current" == "high" ]] && high_marker="(current)"

    box_top "🤝 TRUST LEVEL"
    box_line "  How much autonomy should your partner have?"
    box_divider
    box_empty
    box_line "  🐱─┈       LOW ${low_marker}"
    box_line "             Low trust. CAT presents options frequently:"
    box_line "             where to place code, which approach to take."
    box_line "             ✦ Best for: Learning, strong preferences"
    box_empty
    box_line "  🐱─ ─ ┈    MEDIUM ${medium_marker}"
    box_line "             Moderate trust. CAT handles routine decisions"
    box_line "             but presents options for meaningful trade-offs."
    box_line "             ✦ Best for: Balanced control and efficiency"
    box_empty
    box_line "  🐱─ ─ ─ ─ ┈ HIGH ${high_marker}"
    box_line "             Full autonomy. CAT runs without stopping."
    box_line "             Makes decisions without asking. Auto-merges."
    box_line "             ✦ Best for: Trusted workflows, batch process."
    box_empty
    box_bottom
}

box_verify() {
    local current="${1:-changed}"
    local none_marker="" changed_marker="" all_marker=""
    [[ "$current" == "none" ]] && none_marker="(current)"
    [[ "$current" == "changed" ]] && changed_marker="(current)"
    [[ "$current" == "all" ]] && all_marker="(current)"

    box_top "✅ VERIFICATION LEVEL"
    box_line "  What does CAT check before commit?"
    box_divider
    box_empty
    box_line "  ⚡ NONE ${none_marker}"
    box_line "     No verification before commit. Fastest iteration"
    box_line "     but won't catch any errors automatically."
    box_line "     ✦ Best for: Rapid prototyping, manual verification"
    box_empty
    box_line "  📦 CHANGED ${changed_marker}"
    box_line "     Verify modified file/module only. Catches most"
    box_line "     regressions without verifying the full project."
    box_line "     ✦ Best for: Most workflows"
    box_empty
    box_line "  🔒 ALL ${all_marker}"
    box_line "     Verify the entire project before each commit."
    box_line "     Slowest but highest confidence."
    box_line "     ✦ Best for: Critical code, integration changes"
    box_empty
    box_bottom
}

box_curiosity() {
    local current="${1:-low}"
    local low_marker="" medium_marker="" high_marker=""
    [[ "$current" == "low" ]] && low_marker="(current)"
    [[ "$current" == "medium" ]] && medium_marker="(current)"
    [[ "$current" == "high" ]] && high_marker="(current)"

    box_top "🔍 CURIOSITY LEVEL"
    box_line "  How much does CAT look beyond the task?"
    box_divider
    box_empty
    box_line "  🎯 LOW ${low_marker}"
    box_line "     Task-only. Complete exactly what's required,"
    box_line "     nothing more. Don't look for improvements."
    box_line "     ✦ Best for: Minimal scope, predictable output"
    box_empty
    box_line "  👀 MEDIUM ${medium_marker}"
    box_line "     Opportunistic. Notice obvious issues encountered"
    box_line "     while working (bugs, deprecated syntax)."
    box_line "     ✦ Best for: Balanced thoroughness"
    box_empty
    box_line "  🔭 HIGH ${high_marker}"
    box_line "     Proactive. Actively examine related code for"
    box_line "     patterns, tech debt, or optimization opportunities."
    box_line "     ✦ Best for: Comprehensive improvement"
    box_empty
    box_bottom
}

box_patience() {
    local current="${1:-high}"
    local low_marker="" medium_marker="" high_marker=""
    [[ "$current" == "low" ]] && low_marker="(current)"
    [[ "$current" == "medium" ]] && medium_marker="(current)"
    [[ "$current" == "high" ]] && high_marker="(current)"

    box_top "⏳ PATIENCE LEVEL"
    box_line "  When does CAT act on what it finds?"
    box_divider
    box_empty
    box_line "  ⚡ LOW ${low_marker}"
    box_line "     Act immediately. Address improvements as part of"
    box_line "     the current task. Scope expands but work is done."
    box_line "     ✦ Best for: Comprehensive fixes, avoiding tech debt"
    box_empty
    box_line "  📋 MEDIUM ${medium_marker}"
    box_line "     Defer to current version. Log improvements as"
    box_line "     separate tasks within the current version."
    box_line "     ✦ Best for: Focused tasks with nearby follow-up"
    box_empty
    box_line "  📅 HIGH ${high_marker}"
    box_line "     Defer by priority. Schedule improvements to future"
    box_line "     versions based on benefit/cost ratio."
    box_line "     ✦ Best for: Surgical tasks, controlled scope"
    box_empty
    box_bottom
}

box_version_gates() {
    box_top "📊 VERSION GATES"
    box_empty
    box_line "  Gates control when work can start and when it's done."
    box_line "  Each version can have entry (start) and exit (done)"
    box_line "  gates. Major gates are inherited by all minor versions."
    box_empty
    box_bottom
}

box_gates_for() {
    local version="${1:-0.0}"
    local entry_conditions="${2:-None configured}"
    local exit_conditions="${3:-None configured}"

    box_top "📊 Gates for v${version}"
    box_empty
    box_line "  ENTRY (when can work start?):"
    # Split entry conditions by | and display each
    IFS='|' read -ra ENTRIES <<< "$entry_conditions"
    for condition in "${ENTRIES[@]}"; do
        box_line "  • ${condition}"
    done
    box_empty
    box_line "  EXIT (when is it done?):"
    # Split exit conditions by | and display each
    IFS='|' read -ra EXITS <<< "$exit_conditions"
    for condition in "${EXITS[@]}"; do
        box_line "  • ${condition}"
    done
    box_empty
    box_bottom
}

box_no_gates() {
    local version="${1:-0.0}"

    box_top "⚠️ No gates configured for v${version}"
    box_empty
    box_line "  Default behavior applies:"
    box_line "  • Entry: Previous version must complete"
    box_line "  • Exit: All tasks must complete"
    box_empty
    box_bottom
}

box_gates_updated() {
    local version="${1:-0.0}"
    local entry_summary="${2:-Default}"
    local exit_summary="${3:-All tasks complete}"

    box_top "✓ Gates updated for v${version}"
    box_empty
    box_line "  Entry: ${entry_summary}"
    box_line "  Exit:  ${exit_summary}"
    box_empty
    box_bottom
}

box_setting_updated() {
    local setting="${1:-setting}"
    local old_value="${2:-old}"
    local new_value="${3:-new}"

    box_top "✓ Setting updated"
    box_empty
    box_line "  ${setting}: ${old_value} → ${new_value}"
    box_empty
    box_bottom
}

box_saved() {
    box_top "✨ CONFIGURATION SAVED"
    box_empty
    box_line "  Changes applied:"
    shift || true
    while [ $# -ge 1 ]; do
        box_line "  • $1"
        shift
    done
    box_empty
    box_line "  Settings updated!"
    box_empty
    box_bottom
}

box_no_changes() {
    box_top
    box_line "  No changes made. Settings unchanged."
    box_bottom
}

# Main dispatcher
BOX_TYPE="${1:-}"
shift || true

case "$BOX_TYPE" in
    settings)
        box_settings "$@"
        ;;
    behavior)
        box_behavior "$@"
        ;;
    trust)
        box_trust "$@"
        ;;
    verify)
        box_verify "$@"
        ;;
    curiosity)
        box_curiosity "$@"
        ;;
    patience)
        box_patience "$@"
        ;;
    version-gates)
        box_version_gates
        ;;
    gates-for)
        box_gates_for "$@"
        ;;
    no-gates)
        box_no_gates "$@"
        ;;
    gates-updated)
        box_gates_updated "$@"
        ;;
    setting-updated)
        box_setting_updated "$@"
        ;;
    saved)
        box_saved "$@"
        ;;
    no-changes)
        box_no_changes
        ;;
    *)
        echo "Usage: config-box.sh BOX_TYPE [ARGS...]" >&2
        echo "" >&2
        echo "Box types:" >&2
        echo "  settings CONTEXT_LIMIT TARGET_USAGE TRUST VERIFY CURIOSITY PATIENCE AUTO_REMOVE" >&2
        echo "  behavior TRUST VERIFY CURIOSITY PATIENCE" >&2
        echo "  trust CURRENT_LEVEL" >&2
        echo "  verify CURRENT_LEVEL" >&2
        echo "  curiosity CURRENT_LEVEL" >&2
        echo "  patience CURRENT_LEVEL" >&2
        echo "  version-gates" >&2
        echo "  gates-for VERSION ENTRY_CONDITIONS EXIT_CONDITIONS" >&2
        echo "  no-gates VERSION" >&2
        echo "  gates-updated VERSION ENTRY_SUMMARY EXIT_SUMMARY" >&2
        echo "  setting-updated SETTING OLD_VALUE NEW_VALUE" >&2
        echo "  saved CHANGES..." >&2
        echo "  no-changes" >&2
        exit 1
        ;;
esac
