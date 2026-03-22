# Plan: verify-pre-conditions-on-work-start

## Goal

Verify pre-conditions in `cat:work-prepare` before starting work on an issue, mirroring how
`cat:verify-implementation` checks post-conditions before closing.

## Parent Requirements

None

## Risk Assessment

- **Risk Level:** LOW
- **Concerns:** Pre-conditions are currently free-form markdown with no consistent format
- **Mitigation:** Match the approach used by `cat:verify-implementation` for post-conditions

## Files to Modify

- `plugin/skills/work-prepare-agent/SKILL.md` — add pre-condition verification step
- `plugin/skills/work-implement-agent/SKILL.md` — add pre-condition check before implementation begins (if applicable)

## Pre-conditions

- [ ] Pre-conditions section format in PLAN.md is understood
- [ ] `cat:verify-implementation` source reviewed to understand the post-condition verification approach

## Post-conditions

- [ ] `cat:work-prepare` reads and evaluates pre-conditions from PLAN.md before starting work
- [ ] Agent warns (non-blocking) when any pre-condition is unchecked
- [ ] Behavior mirrors `cat:verify-implementation` (same evaluation approach, opposite direction)
- [ ] All tests pass (`mvn -f client/pom.xml test`)
- [ ] E2E: Start work on an issue with an explicitly unmet pre-condition and confirm the agent reports it

## Sub-Agent Waves

### Wave 1 — Research

1. Read `plugin/skills/work-prepare-agent/SKILL.md` to understand where to insert the check
2. Read `plugin/skills/work-confirm-agent/SKILL.md` and `plugin/skills/verify-implementation-agent/SKILL.md` to understand how post-condition verification works
3. Identify the pre-conditions format used in existing PLAN.md files

### Wave 2 — Implementation

1. Add a pre-condition verification step to `cat:work-prepare` that:
   - Reads pre-conditions from PLAN.md
   - Evaluates each condition
   - Reports any unmet conditions
   - Blocks or warns as appropriate
2. Verify: Works correctly on an issue with pre-conditions

### Wave 3 — Verification

1. Run all tests: `mvn -f client/pom.xml test`
2. E2E test: work on an issue with unmet pre-conditions and confirm they are reported
3. E2E script test: execute the Step 4 pre-condition check commands against a temporary plan.md with at least one
   unchecked pre-condition and verify the warning output is produced:
   ```bash
   # Create a temporary plan.md with one unmet pre-condition
   TMPDIR=$(mktemp -d)
   PLAN_MD="${TMPDIR}/plan.md"
   cat > "${PLAN_MD}" <<'EOF'
   ## Pre-conditions

   - [ ] Some prerequisite that is not yet satisfied
   - [x] Another prerequisite that is already done
   EOF

   # Extract unchecked items from the ## Pre-conditions section (mirrors Step 4 logic)
   UNMET=$(awk '/^## Pre-conditions/{found=1; next} found && /^## /{found=0} found && /^- \[ \]/{print}' \
     "${PLAN_MD}")

   # Verify the warning would be triggered (UNMET must be non-empty)
   if [[ -z "${UNMET}" ]]; then
     echo "FAIL: Expected unmet pre-conditions to be detected, but none were found"
     rm -rf "${TMPDIR}"
     exit 1
   fi

   echo "PASS: Unmet pre-conditions detected:"
   echo "${UNMET}"

   # Verify the expected warning format is producible
   WARNING=$(printf '⚠ Pre-conditions check: the following conditions were not confirmed:\n%s\n' "${UNMET}")
   echo "${WARNING}" | grep -q "⚠ Pre-conditions check" || {
     echo "FAIL: Warning format does not match expected output"
     rm -rf "${TMPDIR}"
     exit 1
   }

   echo "PASS: Warning format verified"
   rm -rf "${TMPDIR}"
   ```
