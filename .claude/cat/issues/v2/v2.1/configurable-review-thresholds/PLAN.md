# Plan: Configurable Review Thresholds

## Goal

Add a `cat-config.json` option to configure the stakeholder review concern rejection threshold, controlling when the
agent automatically loops back to fix concerns vs proceeds to the user approval gate.

## Satisfies

- None (user-requested enhancement)

## Risk Assessment

- **Risk Level:** LOW
- **Concerns:** Must ensure backward compatibility with existing configs that lack the new field
- **Mitigation:** Default to current behavior (all concerns addressed) when field is absent

## Current Behavior

The stakeholder review decision rules are hardcoded in `plugin/skills/stakeholder-review/first-use.md`:

| Condition | Decision |
|-----------|----------|
| CRITICAL_COUNT > 0 | REJECTED |
| REJECTED_COUNT > 0 | REJECTED |
| HIGH_COUNT >= 3 | REJECTED |
| HIGH_COUNT > 0 | CONCERNS (proceed to user approval) |
| Otherwise | REVIEW_PASSED |

The work-with-issue skill auto-fixes when REJECTED, proceeds to user approval when CONCERNS/APPROVED.

## Proposed Config Schema

Add `reviewThresholds` to `cat-config.json`:

```json
{
  "reviewThresholds": {
    "autofix": "all",
    "proceed": {
      "critical": 0,
      "high": 0,
      "medium": -1,
      "low": -1
    }
  }
}
```

### Fields

- **`autofix`**: When to automatically loop back to fix concerns before asking user. Values:
  - `"all"` (default): Fix all concerns (CRITICAL, HIGH, MEDIUM) before presenting to user
  - `"high+"`: Only auto-fix HIGH and CRITICAL; proceed with MEDIUM
  - `"critical"`: Only auto-fix CRITICAL; proceed with HIGH and MEDIUM
  - `"none"`: Never auto-fix; always proceed to user approval with all concerns listed

- **`proceed`**: Maximum number of concerns allowed at each severity to proceed to user approval
  (after auto-fix attempts). Value of `-1` means unlimited. Value of `0` means none allowed.
  - `critical`: 0 (default - never proceed with CRITICAL)
  - `high`: 0 (default - never proceed with HIGH)
  - `medium`: -1 (default - always proceed with MEDIUM)
  - `low`: -1 (default - always proceed with LOW)

### Example Configurations

**Default (current behavior equivalent):**
```json
{
  "reviewThresholds": {
    "autofix": "high+",
    "proceed": { "critical": 0, "high": 3, "medium": -1, "low": -1 }
  }
}
```

**Fix everything (user's preference):**
```json
{
  "reviewThresholds": {
    "autofix": "all",
    "proceed": { "critical": 0, "high": 0, "medium": 0, "low": -1 }
  }
}
```

**Lenient (proceed with up to 5 HIGH):**
```json
{
  "reviewThresholds": {
    "autofix": "critical",
    "proceed": { "critical": 0, "high": 5, "medium": -1, "low": -1 }
  }
}
```

## Files to Modify

- `plugin/skills/stakeholder-review/first-use.md` - Read thresholds from config, apply to decision rules
- `plugin/skills/work-with-issue/SKILL.md` - Use configured thresholds for auto-fix loop decision
- `client/src/main/java/io/github/cowwoc/cat/hooks/ConfigReader.java` (or equivalent) - Parse new config field

## Post-conditions

- [ ] `reviewThresholds` field is read from `cat-config.json` with sensible defaults when absent
- [ ] `autofix` field controls which severity levels trigger automatic fix loops
- [ ] `proceed` field controls the maximum concern counts at each severity to allow proceeding to user approval
- [ ] Missing config or missing field defaults to current behavior (HIGH_COUNT >= 3 triggers rejection)
- [ ] `/cat:config` wizard includes review threshold configuration
- [ ] E2E: Setting `"autofix": "all"` causes all HIGH and MEDIUM concerns to be auto-fixed before user approval

## Execution Steps

1. **Add config parsing:** Add `reviewThresholds` parsing to the config reader, with defaults matching current behavior
2. **Update stakeholder-review:** Read thresholds and apply to the aggregate decision step
3. **Update work-with-issue:** Use configured thresholds for auto-fix loop decision
4. **Update config wizard:** Add review threshold options to `/cat:config`
5. **Test:** Verify default behavior is unchanged; verify custom thresholds are respected
