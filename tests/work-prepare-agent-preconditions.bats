#!/usr/bin/env bats
# Copyright (c) 2026 Gili Tzabari. All rights reserved.
#
# Licensed under the CAT Commercial License.
# See LICENSE.md in the project root for license terms.
#
# Tests for the pre-condition extraction logic used in work-prepare-agent Step 4.
#
# The awk command under test:
#   UNMET=$(awk '/^## Pre-conditions/{found=1; next} found && /^## /{found=0} found && /^- \[ \]/{print}' "${PLAN_MD}")

setup() {
    TMPDIR="$(mktemp -d)"
    PLAN_MD="${TMPDIR}/plan.md"
}

teardown() {
    rm -rf "${TMPDIR:-}"
}

# Helper: run the awk extraction against PLAN_MD and store result in UNMET.
extract_unmet() {
    UNMET=$(awk '/^## Pre-conditions/{found=1; next} found && /^## /{found=0} found && /^- \[ \]/{print}' "${PLAN_MD}")
}

@test "normal case: one unchecked and one checked pre-condition" {
    cat > "${PLAN_MD}" <<'EOF'
## Pre-conditions

- [ ] Some prerequisite that is not yet satisfied
- [x] Another prerequisite that is already done
EOF
    extract_unmet
    [ -n "${UNMET}" ]
    echo "${UNMET}" | grep -q "Some prerequisite that is not yet satisfied"
    ! echo "${UNMET}" | grep -q "Another prerequisite that is already done"
}

@test "all checked: no unmet pre-conditions reported" {
    cat > "${PLAN_MD}" <<'EOF'
## Pre-conditions

- [x] First prerequisite done
- [x] Second prerequisite done
EOF
    extract_unmet
    [ -z "${UNMET}" ]
}

@test "all unchecked: all items reported as unmet" {
    cat > "${PLAN_MD}" <<'EOF'
## Pre-conditions

- [ ] First prerequisite
- [ ] Second prerequisite
- [ ] Third prerequisite
EOF
    extract_unmet
    [ -n "${UNMET}" ]
    echo "${UNMET}" | grep -q "First prerequisite"
    echo "${UNMET}" | grep -q "Second prerequisite"
    echo "${UNMET}" | grep -q "Third prerequisite"
}

@test "no Pre-conditions section: UNMET is empty" {
    cat > "${PLAN_MD}" <<'EOF'
## Goal

Do something useful.

## Post-conditions

- [ ] Work is done
EOF
    extract_unmet
    [ -z "${UNMET}" ]
}

@test "empty Pre-conditions section: UNMET is empty" {
    cat > "${PLAN_MD}" <<'EOF'
## Pre-conditions

## Post-conditions

- [ ] Work is done
EOF
    extract_unmet
    [ -z "${UNMET}" ]
}

@test "no plan.md file: file guard prevents error" {
    local missing_plan="${TMPDIR}/nonexistent/plan.md"
    if [[ ! -f "${missing_plan}" ]]; then
        # Simulate the file guard from Step 4: skip extraction when file is absent.
        UNMET=""
    else
        UNMET=$(awk '/^## Pre-conditions/{found=1; next} found && /^## /{found=0} found && /^- \[ \]/{print}' \
            "${missing_plan}")
    fi
    [ -z "${UNMET}" ]
}
