#!/bin/bash
# get-available-issues.sh - Find next executable issue for /cat:work
#
# Encapsulates issue discovery logic: argument parsing, version filtering,
# dependency checks, lock checks, and gate evaluation.
#
# Usage:
#   get-available-issues.sh [--scope major|minor|issue|all] [--target VERSION|ISSUE_ID] --session-id ID [--override-gate]
#
# Path Discovery:
#   PROJECT_DIR is auto-discovered via git (finds main workspace, not worktree).
#   This ensures locks and task state are always in the main workspace.
#   SESSION_ID must be passed via --session-id (no way to discover).
#
# Output (JSON):
#   {"status":"found|not_found|all_locked|gate_blocked","issue_id":"2.0-issue-name",...}

set -uo pipefail

# Source shared utilities
# SCRIPT_DIR derived from script location - works regardless of environment variables
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/lib/version-utils.sh"

# =============================================================================
# PATH DISCOVERY
# =============================================================================

# Find the main project directory (not a worktree)
# This is critical because:
# - Locks must be in main workspace to be shared across sessions
# - Task discovery must use main workspace's current state, not worktree snapshot
#
# Uses git to find the common .git directory, then derives main workspace from that.
find_project_dir() {
    local git_common_dir main_workspace

    # Get the common .git directory (shared by all worktrees)
    git_common_dir=$(git rev-parse --git-common-dir 2>/dev/null) || {
        # Not in a git repo - fall back to walking up
        return 1
    }

    # Convert to absolute path if relative
    if [[ "$git_common_dir" != /* ]]; then
        git_common_dir="$(cd "$git_common_dir" 2>/dev/null && pwd)"
    fi

    # Main workspace is the parent of .git
    main_workspace="$(dirname "$git_common_dir")"

    # Verify it has .claude/cat
    if [[ -d "$main_workspace/.claude/cat" ]]; then
        echo "$main_workspace"
        return 0
    fi

    return 1
}

# =============================================================================
# CONFIGURATION
# =============================================================================

PROJECT_DIR=""  # Auto-discovered if not provided
SESSION_ID=""   # Required: must be passed via --session-id
SCOPE="all"     # all | major | minor | issue
TARGET=""
OVERRIDE_GATE=false

# =============================================================================
# ARGUMENT PARSING
# =============================================================================

parse_args() {
    while [[ $# -gt 0 ]]; do
        case "$1" in
            --scope)
                SCOPE="$2"
                shift 2
                ;;
            --target)
                TARGET="$2"
                shift 2
                ;;
            --session-id)
                SESSION_ID="$2"
                shift 2
                ;;
            --override-gate)
                OVERRIDE_GATE=true
                shift
                ;;
            -h|--help)
                show_usage
                exit 0
                ;;
            *)
                # Positional argument - version or issue ID
                if [[ -z "$TARGET" ]]; then
                    TARGET="$1"
                    # Auto-detect scope from target format
                    if [[ "$TARGET" =~ ^[0-9]+$ ]]; then
                        SCOPE="major"
                    elif [[ "$TARGET" =~ ^[0-9]+\.[0-9]+$ ]]; then
                        SCOPE="minor"
                    elif [[ "$TARGET" =~ ^[0-9]+\.[0-9]+-[a-zA-Z0-9_-]+$ ]]; then
                        SCOPE="issue"
                    fi
                fi
                shift
                ;;
        esac
    done

    # Always auto-discover PROJECT_DIR from git common directory
    PROJECT_DIR=$(find_project_dir) || {
        echo '{"status":"error","message":"Could not find project directory. Not in a git repo or no .claude/cat/ in main workspace."}'
        exit 1
    }
}

show_usage() {
    cat << 'EOF'
Usage: get-available-issues.sh [OPTIONS] [VERSION_OR_ISSUE]

Find the next executable issue for /cat:work.

Options:
  --scope SCOPE      Search scope: all, major, minor, issue (auto-detected from target)
  --target TARGET    Version or issue ID to target
  --session-id ID    Session ID for lock acquisition (REQUIRED for locking)
  --override-gate    Skip entry gate evaluation
  VERSION_OR_ISSUE   Version (2, 2.0) or issue ID (2.0-issue-name)

Path Discovery:
  PROJECT_DIR is auto-discovered using git. The script finds the main workspace
  by looking up the git common directory, ensuring it works correctly even when
  called from inside a worktree.

  This is critical because:
  - Locks must be in main workspace to be shared across sessions
  - Task discovery must use main workspace's current state, not worktree snapshot

  SESSION_ID must always be passed via --session-id as it cannot be discovered.

Output (JSON):
  status: found, not_found, all_locked, gate_blocked
  issue_id, major, minor, issue_name, issue_path
  scope, lock_status, blocking_reason
EOF
}

# =============================================================================
# ISSUE DISCOVERY
# =============================================================================

# Parse status from STATE.md
get_issue_status() {
    local state_file="$1"

    if [[ ! -f "$state_file" ]]; then
        echo '{"error":"STATE.md not found","file":"'"$state_file"'"}' >&2
        return 1
    fi

    # Format: "- **Status:** pending"
    local status
    status=$(grep -E "^\- \*\*Status:\*\*" "$state_file" | sed 's/.*\*\*Status:\*\* //' | tr -d ' ')

    if [[ -z "$status" ]]; then
        echo '{"error":"Status field not found","file":"'"$state_file"'"}' >&2
        return 1
    fi

    # Normalize known aliases to canonical values (M253)
    case "$status" in
        complete|done)
            echo "WARNING: Non-canonical status '$status' in $state_file, use 'completed'" >&2
            status="completed"
            ;;
        in_progress|active)
            echo "WARNING: Non-canonical status '$status' in $state_file, use 'in-progress'" >&2
            status="in-progress"
            ;;
    esac

    # Validate against allowed status values (M253: fail-fast on unknown status)
    local valid_statuses="pending in-progress completed blocked"
    local is_valid=false
    for valid in $valid_statuses; do
        if [[ "$status" == "$valid" ]]; then
            is_valid=true
            break
        fi
    done

    if [[ "$is_valid" == "false" ]]; then
        echo "ERROR: Unknown status '$status' in $state_file" >&2
        echo "Valid values: $valid_statuses" >&2
        echo "Common typo: 'complete' should be 'completed'" >&2
        return 1
    fi

    # M279: Validate decomposed parent tasks aren't marked completed prematurely
    if [[ "$status" == "completed" ]]; then
        # Check if this is a decomposed parent task
        if grep -q "^## Decomposed Into" "$state_file" 2>/dev/null; then
            # Extract subtask names from "Decomposed Into" section
            local subtask_names
            subtask_names=$(sed -n '/^## Decomposed Into/,/^##/p' "$state_file" | grep -E '^\- ' | sed 's/^\- //' | cut -d' ' -f1 | tr -d '()')

            if [[ -n "$subtask_names" ]]; then
                local issue_dir parent_version_dir all_subtasks_complete
                issue_dir=$(dirname "$state_file")
                parent_version_dir=$(dirname "$issue_dir")
                all_subtasks_complete=true

                for subtask in $subtask_names; do
                    local subtask_state="${parent_version_dir}/${subtask}/STATE.md"
                    if [[ -f "$subtask_state" ]]; then
                        local subtask_status
                        subtask_status=$(grep -E "^\- \*\*Status:\*\*" "$subtask_state" 2>/dev/null | sed 's/.*\*\*Status:\*\* //' | tr -d ' ')
                        if [[ "$subtask_status" != "completed" ]]; then
                            all_subtasks_complete=false
                            break
                        fi
                    else
                        # Subtask doesn't exist yet - not complete
                        all_subtasks_complete=false
                        break
                    fi
                done

                if [[ "$all_subtasks_complete" == "false" ]]; then
                    echo "ERROR: Decomposed parent task marked 'completed' but subtasks are not all complete in $state_file" >&2
                    echo "Parent tasks with '## Decomposed Into' must stay 'pending' or 'in-progress' until ALL subtasks are completed." >&2
                    echo "See M263 in decompose-task skill for correct lifecycle." >&2
                    return 1
                fi
            fi
        fi
    fi

    echo "$status"
}

# Parse dependencies from STATE.md
get_issue_dependencies() {
    local state_file="$1"

    if [[ ! -f "$state_file" ]]; then
        echo '{"error":"STATE.md not found","file":"'"$state_file"'"}' >&2
        return 1
    fi

    # Format: "- **Dependencies:** [issue1, issue2]"
    local deps_line
    deps_line=$(grep -E "^\- \*\*Dependencies:\*\*" "$state_file" | head -1)

    if [[ -z "$deps_line" ]]; then
        echo '{"error":"Dependencies field not found","file":"'"$state_file"'"}' >&2
        return 1
    fi

    # Empty dependencies is valid
    if [[ "$deps_line" =~ \[\] || "$deps_line" == *"none"* || "$deps_line" == *"None"* ]]; then
        echo "[]"
        return 0
    fi

    # Extract dependency names from array
    echo "$deps_line" | grep -oE '\[.*\]' | tr -d '[]' | tr ',' '\n' | while read -r dep; do
        dep=$(echo "$dep" | tr -d ' "')
        [[ -n "$dep" ]] && echo "$dep"
    done | jq -R -s 'split("\n") | map(select(length > 0))'
}

# Check if a dependency is satisfied (issue completed)
is_dependency_satisfied() {
    local dep_name="$1"

    # Search for the dependency issue in all versions
    local dep_state
    dep_state=$(find "$CAT_DIR" -path "*/$dep_name/STATE.md" 2>/dev/null | head -1)

    if [[ -z "$dep_state" ]]; then
        # Dependency not found - treat as unsatisfied
        echo "false"
        return
    fi

    local status
    status=$(get_issue_status "$dep_state")

    if [[ "$status" == "completed" ]]; then
        echo "true"
    else
        echo "false"
    fi
}

# Check all dependencies for an issue
check_dependencies() {
    local state_file="$1"
    local deps
    deps=$(get_issue_dependencies "$state_file")

    if [[ "$deps" == "[]" ]]; then
        echo '{"satisfied":true,"blocking":[]}'
        return
    fi

    local blocking=()
    while IFS= read -r dep; do
        [[ -z "$dep" ]] && continue
        if [[ $(is_dependency_satisfied "$dep") == "false" ]]; then
            blocking+=("$dep")
        fi
    done < <(echo "$deps" | jq -r '.[]')

    if [[ ${#blocking[@]} -eq 0 ]]; then
        echo '{"satisfied":true,"blocking":[]}'
    else
        printf '{"satisfied":false,"blocking":%s}' "$(printf '%s\n' "${blocking[@]}" | jq -R -s 'split("\n") | map(select(length > 0))')"
    fi
}

# Try to acquire lock for an issue
# Returns the actual status from issue-lock.sh (acquired, locked, or error)
try_acquire_lock() {
    local issue_id="$1"

    if [[ -z "$SESSION_ID" ]]; then
        echo '{"status":"error","message":"No session ID provided"}'
        return 1
    fi

    local result
    # Capture output regardless of exit code - issue-lock.sh returns valid JSON even on failure
    # The JSON contains the actual status (locked, acquired, error) which callers must check
    result=$("${SCRIPT_DIR}/issue-lock.sh" acquire "$PROJECT_DIR" "$issue_id" "$SESSION_ID" 2>/dev/null) || true

    # If result is empty, something went very wrong
    if [[ -z "$result" ]]; then
        echo '{"status":"error","message":"Lock acquisition returned empty result"}'
        return 1
    fi

    echo "$result"
}

# Check if issue is an exit gate issue
is_exit_gate_issue() {
    local version_dir="$1"
    local issue_name="$2"

    local plan_file="$version_dir/PLAN.md"
    [[ ! -f "$plan_file" ]] && echo "false" && return

    # Look for [issue] prefix in Exit section
    if grep -qE "^\- \[issue\] ${issue_name}$" "$plan_file" 2>/dev/null; then
        echo "true"
    else
        echo "false"
    fi
}

# Check if all non-exit-gate issues in version are complete
check_exit_gate_rule() {
    local version_dir="$1"
    local issue_name="$2"

    # Get all exit gate issues
    local plan_file="$version_dir/PLAN.md"
    local exit_issues=()
    if [[ -f "$plan_file" ]]; then
        while IFS= read -r line; do
            local exit_issue
            exit_issue=$(echo "$line" | grep -oP '^\- \[issue\] \K.*$' 2>/dev/null) || continue
            exit_issues+=("$exit_issue")
        done < "$plan_file"
    fi

    # Check all issues in version
    local pending_issues=()
    for issue_dir in "$version_dir"/*/; do
        issue_dir="${issue_dir%/}"  # Strip trailing slash from glob
        [[ ! -d "$issue_dir" ]] && continue
        local this_issue
        this_issue=$(basename "$issue_dir")

        # Skip exit gate issues
        local is_exit=false
        for exit_issue in "${exit_issues[@]}"; do
            [[ "$this_issue" == "$exit_issue" ]] && is_exit=true && break
        done
        $is_exit && continue

        # Check if this non-exit-gate issue is complete
        local status
        status=$(get_issue_status "$issue_dir/STATE.md")
        if [[ "$status" != "completed" ]]; then
            pending_issues+=("$this_issue")
        fi
    done

    if [[ ${#pending_issues[@]} -eq 0 ]]; then
        echo '{"satisfied":true,"pending":[]}'
    else
        printf '{"satisfied":false,"pending":%s}' "$(printf '%s\n' "${pending_issues[@]}" | jq -R -s 'split("\n") | map(select(length > 0))')"
    fi
}

# Find first executable issue in a minor version
find_issue_in_minor() {
    local minor_dir="$1"

    for issue_dir in "$minor_dir"/*/; do
        issue_dir="${issue_dir%/}"  # Strip trailing slash from glob
        [[ ! -d "$issue_dir" ]] && continue
        [[ ! -f "$issue_dir/STATE.md" ]] && continue

        local issue_name
        issue_name=$(basename "$issue_dir")

        # Skip directories that are version subdirectories (not issues)
        [[ "$issue_name" =~ ^v[0-9] ]] && continue

        local status
        status=$(get_issue_status "$issue_dir/STATE.md")

        # Only consider pending or in-progress issues
        if [[ "$status" != "pending" && "$status" != "in-progress" ]]; then
            continue
        fi

        # Skip decomposed parent tasks - their subtasks should be executed instead
        # Parent tasks have "## Decomposed Into" section in STATE.md
        if grep -q "^## Decomposed Into" "$issue_dir/STATE.md" 2>/dev/null; then
            continue
        fi

        # Extract version numbers from path
        local minor_version
        minor_version=$(basename "$minor_dir")
        local major
        major=$(echo "$minor_version" | grep -oP '^v?\K[0-9]+')
        local minor
        minor=$(echo "$minor_version" | grep -oP '\.\K[0-9]+$')
        local issue_id="${major}.${minor}-${issue_name}"

        # Check dependencies
        local dep_result
        dep_result=$(check_dependencies "$issue_dir/STATE.md")
        if [[ $(echo "$dep_result" | jq -r '.satisfied') != "true" ]]; then
            continue
        fi

        # Check if this is an exit gate issue and if rule is satisfied
        if [[ $(is_exit_gate_issue "$minor_dir" "$issue_name") == "true" ]]; then
            local gate_result
            gate_result=$(check_exit_gate_rule "$minor_dir" "$issue_name")
            if [[ $(echo "$gate_result" | jq -r '.satisfied') != "true" ]]; then
                continue
            fi
        fi

        # Check for existing worktree (M237)
        # If worktree exists, assume it's in use by another session - skip this issue
        local worktree_path="$PROJECT_DIR/.worktrees/$issue_id"
        if [[ -d "$worktree_path" ]]; then
            # Skip this issue - worktree indicates another session is working on it
            continue
        fi

        # Try to acquire lock
        local lock_result lock_status
        lock_result=$(try_acquire_lock "$issue_id")
        lock_status=$(echo "$lock_result" | jq -r '.status')
        # Skip if not acquired (locked by another session, or error)
        if [[ "$lock_status" != "acquired" ]]; then
            continue
        fi

        # Found executable issue!
        echo "$issue_id"
        return 0
    done

    echo ""
    return 1
}

# Find first incomplete minor version in a major
find_first_incomplete_minor() {
    local major_dir="$1"

    # Get minors in sorted order
    for minor_dir in $(find "$major_dir" -maxdepth 1 -type d -name "v*.*" 2>/dev/null | sort -V); do
        [[ ! -d "$minor_dir" ]] && continue

        # M282: Check version-level dependencies BEFORE scanning tasks
        # If version STATE.md has dependencies, verify they're satisfied
        local version_state="$minor_dir/STATE.md"
        if [[ -f "$version_state" ]]; then
            local version_dep_result
            version_dep_result=$(check_dependencies "$version_state" 2>/dev/null)
            if [[ $(echo "$version_dep_result" | jq -r '.satisfied' 2>/dev/null) == "false" ]]; then
                # Version dependencies not met - skip this version entirely
                continue
            fi
        fi

        # Check if minor has any pending/in-progress issues
        for issue_dir in "$minor_dir"/*/; do
            issue_dir="${issue_dir%/}"  # Strip trailing slash from glob
            [[ ! -f "$issue_dir/STATE.md" ]] && continue
            local issue_name
            issue_name=$(basename "$issue_dir")
            [[ "$issue_name" =~ ^v[0-9] ]] && continue

            local status
            status=$(get_issue_status "$issue_dir/STATE.md")
            if [[ "$status" == "pending" || "$status" == "in-progress" ]]; then
                echo "$minor_dir"
                return 0
            fi
        done
    done

    echo ""
    return 1
}

# =============================================================================
# MAIN LOGIC
# =============================================================================

find_next_issue() {
    # Specific issue requested
    if [[ "$SCOPE" == "issue" && -n "$TARGET" ]]; then
        # Parse issue ID: major.minor-issue-name
        local major minor issue_name
        major=$(echo "$TARGET" | cut -d. -f1)
        minor=$(echo "$TARGET" | cut -d. -f2 | cut -d- -f1)
        issue_name=$(echo "$TARGET" | sed 's/^[0-9]*\.[0-9]*-//')

        local issue_dir
        issue_dir=$(get_task_dir "${major}.${minor}" "$issue_name" "$CAT_DIR")

        if [[ ! -d "$issue_dir" ]]; then
            echo '{"status":"not_found","message":"Issue directory not found","issue_id":"'"$TARGET"'"}'
            return 1
        fi

        # Check status
        local status
        status=$(get_issue_status "$issue_dir/STATE.md")
        if [[ "$status" != "pending" && "$status" != "in-progress" ]]; then
            echo '{"status":"not_executable","message":"Issue status is '"$status"'","issue_id":"'"$TARGET"'"}'
            return 1
        fi

        # Check if this is a decomposed parent task
        # Decomposed parents cannot be executed directly - their subtasks should be run instead
        if grep -q "^## Decomposed Into" "$issue_dir/STATE.md" 2>/dev/null; then
            echo '{"status":"decomposed","message":"Issue is a decomposed parent task - execute subtasks instead","issue_id":"'"$TARGET"'"}'
            return 1
        fi

        # Check dependencies
        local dep_result
        dep_result=$(check_dependencies "$issue_dir/STATE.md")
        if [[ $(echo "$dep_result" | jq -r '.satisfied') != "true" ]]; then
            local blocking
            blocking=$(echo "$dep_result" | jq -c '.blocking')
            echo '{"status":"blocked","message":"Dependencies not satisfied","issue_id":"'"$TARGET"'","blocking":'"$blocking"'}'
            return 1
        fi

        # Check for existing worktree (M237)
        # If worktree exists, it's in use by another session - report as unavailable
        local worktree_path="$PROJECT_DIR/.worktrees/$TARGET"
        if [[ -d "$worktree_path" ]]; then
            echo '{"status":"existing_worktree","issue_id":"'"$TARGET"'","major":"'"$major"'","minor":"'"$minor"'","issue_name":"'"$issue_name"'","issue_path":"'"$issue_dir"'","worktree_path":"'"$worktree_path"'","message":"Issue has existing worktree - likely in use by another session"}'
            return 1
        fi

        # Try to acquire lock
        local lock_result
        lock_result=$(try_acquire_lock "$TARGET")
        if [[ $(echo "$lock_result" | jq -r '.status') == "locked" ]]; then
            local owner
            owner=$(echo "$lock_result" | jq -r '.owner // "unknown"')
            echo '{"status":"locked","message":"Issue locked by another session","issue_id":"'"$TARGET"'","owner":"'"$owner"'"}'
            return 1
        fi

        # Success!
        echo '{"status":"found","issue_id":"'"$TARGET"'","major":"'"$major"'","minor":"'"$minor"'","issue_name":"'"$issue_name"'","issue_path":"'"$issue_dir"'","scope":"issue","lock_status":"acquired"}'
        return 0
    fi

    # Search for issues based on scope
    local search_dirs=()

    case "$SCOPE" in
        all)
            # Find all major version directories
            for major_dir in "$CAT_DIR"/v*/; do
                [[ -d "$major_dir" ]] && search_dirs+=("$major_dir")
            done
            ;;
        major)
            search_dirs+=("$CAT_DIR/v$TARGET")
            ;;
        minor)
            local major minor
            major=$(echo "$TARGET" | cut -d. -f1)
            minor=$(echo "$TARGET" | cut -d. -f2)
            search_dirs+=("$CAT_DIR/v$major/v${major}.${minor}")
            ;;
    esac

    # Search through directories
    for search_dir in "${search_dirs[@]}"; do
        [[ ! -d "$search_dir" ]] && continue

        # Determine if this is a major or minor directory
        if [[ $(basename "$search_dir") =~ ^v[0-9]+$ ]]; then
            # Major directory - find first incomplete minor
            local minor_dir
            if ! minor_dir=$(find_first_incomplete_minor "$search_dir"); then
                continue
            fi
            [[ -z "$minor_dir" ]] && continue

            local issue_id
            if ! issue_id=$(find_issue_in_minor "$minor_dir"); then
                continue
            fi
            if [[ -n "$issue_id" ]]; then
                # Parse components from issue_id
                local major minor issue_name
                major=$(echo "$issue_id" | cut -d. -f1)
                minor=$(echo "$issue_id" | cut -d. -f2 | cut -d- -f1)
                issue_name=$(echo "$issue_id" | sed 's/^[0-9]*\.[0-9]*-//')
                local issue_dir
                issue_dir=$(get_task_dir "${major}.${minor}" "$issue_name" "$CAT_DIR")

                echo '{"status":"found","issue_id":"'"$issue_id"'","major":"'"$major"'","minor":"'"$minor"'","issue_name":"'"$issue_name"'","issue_path":"'"$issue_dir"'","scope":"'"$SCOPE"'","lock_status":"acquired"}'
                return 0
            fi
        else
            # Minor directory - search directly
            local issue_id
            if ! issue_id=$(find_issue_in_minor "$search_dir"); then
                continue
            fi
            if [[ -n "$issue_id" ]]; then
                local major minor issue_name
                major=$(echo "$issue_id" | cut -d. -f1)
                minor=$(echo "$issue_id" | cut -d. -f2 | cut -d- -f1)
                issue_name=$(echo "$issue_id" | sed 's/^[0-9]*\.[0-9]*-//')
                local issue_dir
                issue_dir=$(get_task_dir "${major}.${minor}" "$issue_name" "$CAT_DIR")

                echo '{"status":"found","issue_id":"'"$issue_id"'","major":"'"$major"'","minor":"'"$minor"'","issue_name":"'"$issue_name"'","issue_path":"'"$issue_dir"'","scope":"'"$SCOPE"'","lock_status":"acquired"}'
                return 0
            fi
        fi
    done

    # No issue found
    echo '{"status":"not_found","message":"No executable issues found","scope":"'"$SCOPE"'"}'
    return 1
}

# =============================================================================
# ENTRY POINT
# =============================================================================

parse_args "$@"

# PROJECT_DIR is now guaranteed to be set by parse_args (auto-discovered if not provided)
# Just verify it's actually a CAT project
if [[ ! -d "$PROJECT_DIR/.claude/cat" ]]; then
    echo '{"status":"error","message":"Not a CAT project: '"$PROJECT_DIR"' (no .claude/cat directory)"}'
    exit 1
fi

# Set derived paths
CAT_DIR="$PROJECT_DIR/.claude/cat/issues"

find_next_issue
