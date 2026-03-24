#!/usr/bin/env bash
# Copyright (c) 2026 Gili Tzabari. All rights reserved.
#
# Licensed under the CAT Commercial License.
# See LICENSE.md in the project root for license terms.
#
# Shared bash helper functions for skill dependency detection in the add-agent workflow.
# Sourced by plugin/skills/add-agent/first-use.md and plugin/skills/add-agent/tests/skill_dep_detection.bats.
#
# Functions:
#   extract_skill_names   - Parse ISSUE_DESCRIPTION to extract referenced skill names
#   run_detection         - Find all open issues whose plan.md references a given skill
#   update_state_dependency - Update a single issue's index.json to add a dependency entry

# extract_skill_names <description>
#
# Extracts plugin skill names referenced in the description string.
# Prints one skill name per line, deduplicated and sorted.
#
# Matched patterns:
#   Pattern 1: explicit skill path  plugin/skills/<name>/
#   Pattern 2: action phrases like "modify the <name> skill", "update <name> skill",
#              "add a step to <name>", "change <name> first-use.md", "fix <name> skill",
#              "extend <name> skill"
#
# Limitations:
#   - Only detects skill names that appear in the above structured patterns.
#   - Does NOT detect skills mentioned in free-form prose without one of the trigger verbs
#     (e.g., "the add skill is used here" would not be matched by Pattern 2).
#   - Pattern 1 requires the trailing slash: "plugin/skills/add/" matches but "plugin/skills/add"
#     (without slash) does not.
#   - Pattern 2 is case-insensitive for the verb but expects lowercase skill names; mixed-case
#     skill names may not be captured reliably.
#   - Skill names containing characters outside [a-z0-9_-] are not matched.
extract_skill_names() {
    local description="$1"
    local -a found=()

    # Pattern 1: explicit skill path plugin/skills/<name>/
    while IFS= read -r match; do
        [[ -n "$match" ]] && found+=("$match")
    done < <(printf '%s\n' "$description" | grep -oE 'plugin/skills/[a-z0-9_-]+/' | sed 's|plugin/skills/||;s|/||')

    # Pattern 2: "modify the <name> skill", "update <name> skill", "add a step to <name>",
    #             "change <name> first-use.md", "fix <name> skill", "extend <name> skill"
    while IFS= read -r match; do
        [[ -n "$match" ]] && found+=("$match")
    done < <(printf '%s\n' "$description" \
        | grep -oiE '(modify|update|add.*to|change|fix|extend) (the )?([a-z0-9_-]+) (skill|first-use\.md)' \
        | grep -oiE '[a-z0-9_-]+ (skill|first-use\.md)' \
        | grep -oiE '^[a-z0-9_-]+')

    # Deduplicate and print
    printf '%s\n' "${found[@]+"${found[@]}"}" | sort -u
}

# run_detection <skill_name> <current_issue_name>
#
# Scans all index.json files under ISSUES_DIR for open issues whose plan.md references
# plugin/skills/<skill_name>/. Excludes closed issues and the current issue being created.
#
# Outputs one matching issue ID per line.
# Populates (in the caller's scope) MATCHING_ISSUES and MATCHING_ISSUE_PATHS arrays.
#
# Requires: ISSUES_DIR to be set in the calling environment.
run_detection() {
    local skill_name="$1"
    local current_issue_name="$2"

    # NOTE: MATCHING_ISSUES and MATCHING_ISSUE_PATHS are intentionally assigned as globals.
    # This function MUST be called directly (not via process substitution or pipe)
    # to ensure assignments propagate to the caller's scope.
    MATCHING_ISSUES=()
    MATCHING_ISSUE_PATHS=()
    while IFS= read -r state_file; do
        STATUS=$(grep -m1 '^\- \*\*Status:\*\*' "$state_file" | sed 's/^.*Status:\*\* *//;s/[[:space:]]*$//')
        if [[ "$STATUS" == "closed" ]]; then
            continue
        fi
        PLAN_FILE="${state_file%index.json}plan.md"
        if [[ -f "$PLAN_FILE" ]] && grep -qF "plugin/skills/${skill_name}/" "$PLAN_FILE"; then
            local dir_path="${state_file%/index.json}"
            ISSUE_ID="${dir_path##*/}"
            MATCHING_ISSUES+=("$ISSUE_ID")
            MATCHING_ISSUE_PATHS+=("$state_file")
        fi
    done < <(find "$ISSUES_DIR" -name "index.json" -not -path "*/${current_issue_name}/index.json")

    printf '%s\n' "${MATCHING_ISSUES[@]+"${MATCHING_ISSUES[@]}"}"
}

# update_state_dependency <state_file> <new_issue_id>
#
# Appends new_issue_id to the Dependencies list in the given index.json file.
# If the dependency is already present (idempotency guard), this is a no-op.
#
# Handles sed metacharacters in new_issue_id (&, /, \) via escaping.
update_state_dependency() {
    local state_file="$1"
    local new_issue_id="$2"

    # Idempotency guard: check for the full ID in the Dependencies line (not a substring match).
    # Issue IDs only contain [a-zA-Z0-9.-], so bounding by '[', ']', or ', ' is sufficient.
    local deps_line
    deps_line=$(grep '^\- \*\*Dependencies:\*\*' "$state_file")
    if printf '%s' "$deps_line" | grep -qE "(\[|, )${new_issue_id}(\]|,)"; then
        return 0
    fi

    # Escape issue ID for safe use in sed replacement strings
    local escaped_id
    escaped_id=$(printf '%s' "${new_issue_id}" | sed 's/[&/\]/\\&/g')

    # Insert new issue into Dependencies list
    local current
    current=$(grep '^\- \*\*Dependencies:\*\*' "$state_file")
    if echo "$current" | grep -q '\[\]'; then
        sed -i "s/^\(- \*\*Dependencies:\*\*\) \[\]/\1 [${escaped_id}]/" "$state_file"
    else
        sed -i "s/^\(- \*\*Dependencies:\*\*\) \[\(.*\)\]/\1 [\2, ${escaped_id}]/" "$state_file"
    fi
}
