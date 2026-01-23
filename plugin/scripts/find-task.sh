#!/bin/bash
# find-task.sh - Find next executable task for /cat:work
#
# Encapsulates task discovery logic: argument parsing, version filtering,
# dependency checks, lock checks, and gate evaluation.
#
# Usage:
#   find-task.sh <project-dir> [--scope major|minor|task|all] [--target VERSION|TASK_ID] [--session-id ID] [--override-gate]
#
# Output (JSON):
#   {"status":"found|not_found|all_locked|gate_blocked","task_id":"2.0-task-name",...}

set -uo pipefail

# Source shared utilities
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/lib/version-utils.sh"

# =============================================================================
# CONFIGURATION
# =============================================================================

PROJECT_DIR=""  # Required: set via --project-dir
SESSION_ID=""
SCOPE="all"  # all | major | minor | task
TARGET=""
OVERRIDE_GATE=false

# =============================================================================
# ARGUMENT PARSING
# =============================================================================

parse_args() {
    # First argument must be project-dir
    if [[ $# -lt 1 ]] || [[ "$1" == "-h" ]] || [[ "$1" == "--help" ]]; then
        show_usage
        exit 0
    fi

    PROJECT_DIR="$1"
    shift

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
                # Positional argument - could be version or task ID
                if [[ -z "$TARGET" ]]; then
                    TARGET="$1"
                    # Auto-detect scope from target format
                    if [[ "$TARGET" =~ ^[0-9]+$ ]]; then
                        SCOPE="major"
                    elif [[ "$TARGET" =~ ^[0-9]+\.[0-9]+$ ]]; then
                        SCOPE="minor"
                    elif [[ "$TARGET" =~ ^[0-9]+\.[0-9]+-[a-zA-Z0-9_-]+$ ]]; then
                        SCOPE="task"
                    fi
                fi
                shift
                ;;
        esac
    done
}

show_usage() {
    cat << 'EOF'
Usage: find-task.sh <project-dir> [OPTIONS] [VERSION_OR_TASK]

Find the next executable task for /cat:work.

Arguments:
  project-dir        Project root directory (contains .claude/cat/) - REQUIRED first argument

Options:
  --scope SCOPE      Search scope: all, major, minor, task (auto-detected from target)
  --target TARGET    Version or task ID to target
  --session-id ID    Session ID for lock acquisition
  --override-gate    Skip entry gate evaluation
  VERSION_OR_TASK    Version (2, 2.0) or task ID (2.0-task-name)

Output (JSON):
  status: found, not_found, all_locked, gate_blocked
  task_id, major, minor, task_name, task_path
  scope, lock_status, blocking_reason
EOF
}

# =============================================================================
# TASK DISCOVERY
# =============================================================================

# Parse status from STATE.md
get_task_status() {
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

    echo "$status"
}

# Parse dependencies from STATE.md
get_task_dependencies() {
    local state_file="$1"

    if [[ ! -f "$state_file" ]]; then
        echo '{"error":"STATE.md not found","file":"'"$state_file"'"}' >&2
        return 1
    fi

    # Format: "- **Dependencies:** [task1, task2]"
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

# Check if a dependency is satisfied (task completed)
is_dependency_satisfied() {
    local dep_name="$1"

    # Search for the dependency task in all versions
    local dep_state
    dep_state=$(find "$CAT_DIR" -path "*/$dep_name/STATE.md" 2>/dev/null | head -1)

    if [[ -z "$dep_state" ]]; then
        # Dependency not found - treat as unsatisfied
        echo "false"
        return
    fi

    local status
    status=$(get_task_status "$dep_state")

    if [[ "$status" == "completed" ]]; then
        echo "true"
    else
        echo "false"
    fi
}

# Check all dependencies for a task
check_dependencies() {
    local state_file="$1"
    local deps
    deps=$(get_task_dependencies "$state_file")

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

# Try to acquire lock for a task
# Returns the actual status from task-lock.sh (acquired, locked, or error)
try_acquire_lock() {
    local task_id="$1"

    if [[ -z "$SESSION_ID" ]]; then
        echo '{"status":"error","message":"No session ID provided"}'
        return 1
    fi

    local result
    # Capture output regardless of exit code - task-lock.sh returns valid JSON even on failure
    # The JSON contains the actual status (locked, acquired, error) which callers must check
    result=$("${SCRIPT_DIR}/task-lock.sh" acquire "$PROJECT_DIR" "$task_id" "$SESSION_ID" 2>/dev/null) || true

    # If result is empty, something went very wrong
    if [[ -z "$result" ]]; then
        echo '{"status":"error","message":"Lock acquisition returned empty result"}'
        return 1
    fi

    echo "$result"
}

# Check if task is an exit gate task
is_exit_gate_task() {
    local version_dir="$1"
    local task_name="$2"

    local plan_file="$version_dir/PLAN.md"
    [[ ! -f "$plan_file" ]] && echo "false" && return

    # Look for [task] prefix in Exit section
    if grep -qE "^\- \[task\] ${task_name}$" "$plan_file" 2>/dev/null; then
        echo "true"
    else
        echo "false"
    fi
}

# Check if all non-exit-gate tasks in version are complete
check_exit_gate_rule() {
    local version_dir="$1"
    local task_name="$2"

    # Get all exit gate tasks
    local plan_file="$version_dir/PLAN.md"
    local exit_tasks=()
    if [[ -f "$plan_file" ]]; then
        while IFS= read -r line; do
            local exit_task
            exit_task=$(echo "$line" | grep -oP '^\- \[task\] \K.*$' 2>/dev/null) || continue
            exit_tasks+=("$exit_task")
        done < "$plan_file"
    fi

    # Check all tasks in version
    local pending_tasks=()
    for task_dir in "$version_dir"/*/; do
        task_dir="${task_dir%/}"  # Strip trailing slash from glob
        [[ ! -d "$task_dir" ]] && continue
        local this_task
        this_task=$(basename "$task_dir")

        # Skip exit gate tasks
        local is_exit=false
        for exit_task in "${exit_tasks[@]}"; do
            [[ "$this_task" == "$exit_task" ]] && is_exit=true && break
        done
        $is_exit && continue

        # Check if this non-exit-gate task is complete
        local status
        status=$(get_task_status "$task_dir/STATE.md")
        if [[ "$status" != "completed" ]]; then
            pending_tasks+=("$this_task")
        fi
    done

    if [[ ${#pending_tasks[@]} -eq 0 ]]; then
        echo '{"satisfied":true,"pending":[]}'
    else
        printf '{"satisfied":false,"pending":%s}' "$(printf '%s\n' "${pending_tasks[@]}" | jq -R -s 'split("\n") | map(select(length > 0))')"
    fi
}

# Find first executable task in a minor version
find_task_in_minor() {
    local minor_dir="$1"

    for task_dir in "$minor_dir"/*/; do
        task_dir="${task_dir%/}"  # Strip trailing slash from glob
        [[ ! -d "$task_dir" ]] && continue
        [[ ! -f "$task_dir/STATE.md" ]] && continue

        local task_name
        task_name=$(basename "$task_dir")

        # Skip directories that are version subdirectories (not tasks)
        [[ "$task_name" =~ ^v[0-9] ]] && continue

        local status
        status=$(get_task_status "$task_dir/STATE.md")

        # Only consider pending or in-progress tasks
        if [[ "$status" != "pending" && "$status" != "in-progress" ]]; then
            continue
        fi

        # Extract version numbers from path
        local minor_version
        minor_version=$(basename "$minor_dir")
        local major
        major=$(echo "$minor_version" | grep -oP '^v?\K[0-9]+')
        local minor
        minor=$(echo "$minor_version" | grep -oP '\.\K[0-9]+$')
        local task_id="${major}.${minor}-${task_name}"

        # Check dependencies
        local dep_result
        dep_result=$(check_dependencies "$task_dir/STATE.md")
        if [[ $(echo "$dep_result" | jq -r '.satisfied') != "true" ]]; then
            continue
        fi

        # Check if this is an exit gate task and if rule is satisfied
        if [[ $(is_exit_gate_task "$minor_dir" "$task_name") == "true" ]]; then
            local gate_result
            gate_result=$(check_exit_gate_rule "$minor_dir" "$task_name")
            if [[ $(echo "$gate_result" | jq -r '.satisfied') != "true" ]]; then
                continue
            fi
        fi

        # Try to acquire lock
        local lock_result lock_status
        lock_result=$(try_acquire_lock "$task_id")
        lock_status=$(echo "$lock_result" | jq -r '.status')
        # Skip if not acquired (locked by another session, or error)
        if [[ "$lock_status" != "acquired" ]]; then
            continue
        fi

        # Found executable task!
        echo "$task_id"
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

        # Check if minor has any pending/in-progress tasks
        for task_dir in "$minor_dir"/*/; do
            task_dir="${task_dir%/}"  # Strip trailing slash from glob
            [[ ! -f "$task_dir/STATE.md" ]] && continue
            local task_name
            task_name=$(basename "$task_dir")
            [[ "$task_name" =~ ^v[0-9] ]] && continue

            local status
            status=$(get_task_status "$task_dir/STATE.md")
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

find_next_task() {
    # Specific task requested
    if [[ "$SCOPE" == "task" && -n "$TARGET" ]]; then
        # Parse task ID: major.minor-task-name
        local major minor task_name
        major=$(echo "$TARGET" | cut -d. -f1)
        minor=$(echo "$TARGET" | cut -d. -f2 | cut -d- -f1)
        task_name=$(echo "$TARGET" | sed 's/^[0-9]*\.[0-9]*-//')

        local task_dir
        task_dir=$(get_task_dir "${major}.${minor}" "$task_name" "$CAT_DIR")

        if [[ ! -d "$task_dir" ]]; then
            echo '{"status":"not_found","message":"Task directory not found","task_id":"'"$TARGET"'"}'
            return 1
        fi

        # Check status
        local status
        status=$(get_task_status "$task_dir/STATE.md")
        if [[ "$status" != "pending" && "$status" != "in-progress" ]]; then
            echo '{"status":"not_executable","message":"Task status is '"$status"'","task_id":"'"$TARGET"'"}'
            return 1
        fi

        # Check dependencies
        local dep_result
        dep_result=$(check_dependencies "$task_dir/STATE.md")
        if [[ $(echo "$dep_result" | jq -r '.satisfied') != "true" ]]; then
            local blocking
            blocking=$(echo "$dep_result" | jq -c '.blocking')
            echo '{"status":"blocked","message":"Dependencies not satisfied","task_id":"'"$TARGET"'","blocking":'"$blocking"'}'
            return 1
        fi

        # Try to acquire lock
        local lock_result
        lock_result=$(try_acquire_lock "$TARGET")
        if [[ $(echo "$lock_result" | jq -r '.status') == "locked" ]]; then
            local owner
            owner=$(echo "$lock_result" | jq -r '.owner // "unknown"')
            echo '{"status":"locked","message":"Task locked by another session","task_id":"'"$TARGET"'","owner":"'"$owner"'"}'
            return 1
        fi

        # Success!
        echo '{"status":"found","task_id":"'"$TARGET"'","major":"'"$major"'","minor":"'"$minor"'","task_name":"'"$task_name"'","task_path":"'"$task_dir"'","scope":"task","lock_status":"acquired"}'
        return 0
    fi

    # Search for tasks based on scope
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

            local task_id
            if ! task_id=$(find_task_in_minor "$minor_dir"); then
                continue
            fi
            if [[ -n "$task_id" ]]; then
                # Parse components from task_id
                local major minor task_name
                major=$(echo "$task_id" | cut -d. -f1)
                minor=$(echo "$task_id" | cut -d. -f2 | cut -d- -f1)
                task_name=$(echo "$task_id" | sed 's/^[0-9]*\.[0-9]*-//')
                local task_dir
                task_dir=$(get_task_dir "${major}.${minor}" "$task_name" "$CAT_DIR")

                echo '{"status":"found","task_id":"'"$task_id"'","major":"'"$major"'","minor":"'"$minor"'","task_name":"'"$task_name"'","task_path":"'"$task_dir"'","scope":"'"$SCOPE"'","lock_status":"acquired"}'
                return 0
            fi
        else
            # Minor directory - search directly
            local task_id
            if ! task_id=$(find_task_in_minor "$search_dir"); then
                continue
            fi
            if [[ -n "$task_id" ]]; then
                local major minor task_name
                major=$(echo "$task_id" | cut -d. -f1)
                minor=$(echo "$task_id" | cut -d. -f2 | cut -d- -f1)
                task_name=$(echo "$task_id" | sed 's/^[0-9]*\.[0-9]*-//')
                local task_dir
                task_dir=$(get_task_dir "${major}.${minor}" "$task_name" "$CAT_DIR")

                echo '{"status":"found","task_id":"'"$task_id"'","major":"'"$major"'","minor":"'"$minor"'","task_name":"'"$task_name"'","task_path":"'"$task_dir"'","scope":"'"$SCOPE"'","lock_status":"acquired"}'
                return 0
            fi
        fi
    done

    # No task found
    echo '{"status":"not_found","message":"No executable tasks found","scope":"'"$SCOPE"'"}'
    return 1
}

# =============================================================================
# ENTRY POINT
# =============================================================================

parse_args "$@"

# Validate required arguments
if [[ -z "$PROJECT_DIR" ]]; then
    echo '{"status":"error","message":"--project-dir is required"}'
    exit 1
fi

if [[ ! -d "$PROJECT_DIR/.claude/cat" ]]; then
    echo '{"status":"error","message":"Not a CAT project: '"$PROJECT_DIR"' (no .claude/cat directory)"}'
    exit 1
fi

# Set derived paths
CAT_DIR="$PROJECT_DIR/.claude/cat/issues"

find_next_task
