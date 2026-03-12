# Plan: fix-work-prepare-resume-routing

## Problem

When the user invokes `/cat:work resume <issue-id>`, the word `resume` is passed as part of the raw ARGUMENTS string
to work-prepare. The current inclusion filter matching in `work-prepare-agent/first-use.md` Step 2 uses the FULL raw
argument text (including `resume`) as the substring keyword. Since no issue ID contains the literal string
`resume <issue-id>`, the filter produces no match and work-prepare falls through to the next available issue in
discovery order, silently ignoring the user's intent.

## Parent Requirements

None

## Reproduction Code

```
/cat:work resume 2.1-close-prevention-gate-loopholes
# ARGUMENTS passed to work-prepare: "resume 2.1-close-prevention-gate-loopholes"
# Inclusion keyword: "resume 2.1-close-prevention-gate-loopholes" (full raw string)
# No issue ID contains that substring → falls through to next available issue
# Expected: routes to 2.1-close-prevention-gate-loopholes
# Actual: routes to next available issue (e.g. 2.1-fix-merge-cleanup-hook-guidance-missing-squash-rebase)
```

## Expected vs Actual

- **Expected:** `/cat:work resume <issue-id>` routes to the named issue, identical to `/cat:work <issue-id>`
- **Actual:** Silently falls through to the next available issue in discovery order

## Root Cause

`work-prepare-agent/first-use.md` Step 2 filter classification mandates using the raw ARGUMENTS string as the
inclusion keyword without stripping the `resume` qualifier prefix first. The word `resume` is not listed as an
exclusion keyword, so it enters the inclusion branch, but the raw string including `resume` then fails to
substring-match any issue ID.

## Risk Assessment

- **Risk Level:** LOW
- **Regression Risk:** Minimal — only affects argument preprocessing in Step 2; no change to Java work-prepare binary
  or issue discovery logic
- **Mitigation:** Verify that `resume <issue-id>` routes correctly and that bare `<issue-id>` still works unchanged

## Files to Modify

- `plugin/skills/work-prepare-agent/first-use.md` — add Resume Pattern rule at the start of Step 2, applied before
  filter classification

## Test Cases

- [ ] `/cat:work resume 2.1-close-prevention-gate-loopholes` routes to `2.1-close-prevention-gate-loopholes`
- [ ] `/cat:work resume close-prevention-gate-loopholes` (bare name) routes to `close-prevention-gate-loopholes`
- [ ] Additional tokens after the issue name (diagnostic context) are ignored
- [ ] `/cat:work close-prevention-gate-loopholes` (no resume prefix) still works unchanged
- [ ] Exclusion filters (`/cat:work skip compression`) still work unchanged

## Pre-conditions

- [ ] All dependent issues are closed

## Sub-Agent Waves

### Wave 1

- Add a Resume Pattern block to the start of Step 2 in `plugin/skills/work-prepare-agent/first-use.md`:
  - When ARGUMENTS begins with `resume` (case-insensitive), strip the prefix and extract only the first remaining
    token as the inclusion filter keyword
  - Include two bash-comment examples: one with a versioned name, one with a bare name
  - Add a MANDATORY note stating the raw ARGUMENTS string including `resume` must NOT be passed to filter
    classification
  - Files: `plugin/skills/work-prepare-agent/first-use.md`

## Post-conditions

- [ ] `work-prepare-agent/first-use.md` Step 2 begins with a Resume Pattern block applied before filter
  classification
- [ ] The resume pattern strips the leading `resume` prefix and extracts only the first remaining token as the
  inclusion keyword
- [ ] Additional tokens after the issue name are explicitly ignored per the documented rule
- [ ] Code examples illustrate stripping for both versioned (`2.1-foo`) and bare (`foo`) issue names
- [ ] E2E: Invoking `/cat:work resume 2.1-close-prevention-gate-loopholes` routes to that specific issue rather than
  the next available one
