# Plan: implement-retrospective-handler

## Goal

Create `GetRetrospectiveOutput.java` implementing `SkillOutput` to produce SCRIPT OUTPUT for the
`/cat:run-retrospective` skill. Wire it via `bindings.json` and update `first-use.md` to reference the binding
variable. This replaces the missing `run_retrospective_handler.py` referenced in the skill.

## Satisfies

None (infrastructure issue - filling a gap where handler was never implemented)

## Risk Assessment

- **Risk Level:** MEDIUM
- **Concerns:** Complex JSON parsing of index.json, mistakes-*.json files; effectiveness evaluation logic
- **Mitigation:** Existing `CheckRetrospectiveDue.java` already reads the same data structures; follow its patterns

## Files to Modify

- `hooks/src/main/java/io/github/cowwoc/cat/hooks/skills/GetRetrospectiveOutput.java` - NEW: Main handler
- `plugin/skills/run-retrospective/bindings.json` - NEW: Wire `CAT_RETROSPECTIVE_OUTPUT` to handler class
- `plugin/skills/run-retrospective/first-use.md` - Update to reference `${CAT_RETROSPECTIVE_OUTPUT}` and remove
  Python handler reference

## Acceptance Criteria

### Handler Structure
- [ ] `GetRetrospectiveOutput` implements `SkillOutput` with constructor accepting `JvmScope`
- [ ] Handler reads `index.json` for config, last_retrospective, mistake_count_since_last, patterns, action_items
- [ ] Handler reads `mistakes-*.json` files listed in `index.json.files.mistakes` array
- [ ] Mistakes are filtered by `mistake.timestamp > index.json.last_retrospective`

### Trigger Logic
- [ ] Time-based trigger: fires when days since `last_retrospective` >= `config.trigger_interval_days` (default 7)
- [ ] Count-based trigger: fires when `mistake_count_since_last` >= `config.mistake_count_threshold` (default 10)
- [ ] First retrospective: fires when `last_retrospective` is null/empty AND any mistakes exist
- [ ] When neither trigger fires: output starts with `SCRIPT OUTPUT RETROSPECTIVE STATUS:`
- [ ] STATUS message shows: days since last retro (X/threshold), mistakes accumulated (Y/threshold)

### Analysis Output Format
- [ ] When any trigger fires: output starts with `SCRIPT OUTPUT RETROSPECTIVE ANALYSIS:`
- [ ] Trigger reason format: "X days since last retrospective (threshold: Y)" or "N mistakes accumulated
  (threshold: M)" matching `CheckRetrospectiveDue.java` style
- [ ] Period analyzed: ISO timestamp range "last_retrospective to now"
- [ ] Mistakes analyzed: count of mistakes in the period
- [ ] Category breakdown: mistakes grouped by `mistake.category`, format "category_name: count" per line
- [ ] Action item effectiveness: REPORTS existing `effectiveness_check.verdict` from index.json for each action item.
  Format: "A00X: verdict" (e.g., "A001: ineffective", "A004: effective", "A009: pending")
- [ ] Pattern status summary: for each pattern with `status != addressed`, show:
  `pattern_id: status (occurrences: total/after_fix)`
- [ ] Open action items: items where `status = open` OR `status = escalated`, sorted by priority (high > medium > low)

### Error Handling
- [ ] Missing retrospectives directory: output `SCRIPT OUTPUT RETROSPECTIVE ERROR:` with path and STOP
- [ ] Missing/malformed index.json: output `SCRIPT OUTPUT RETROSPECTIVE ERROR:` with path and STOP
- [ ] Error messages follow fail-fast principle: identify missing component, no recovery instructions

### Wiring
- [ ] `bindings.json` maps `CAT_RETROSPECTIVE_OUTPUT` to
  `io.github.cowwoc.cat.hooks.skills.GetRetrospectiveOutput`
- [ ] `first-use.md` includes `${CAT_RETROSPECTIVE_OUTPUT}` at the top of Script Output Analysis section
- [ ] `first-use.md` NO SCRIPT OUTPUT error message references Java handler, not Python

### Testing
- [ ] All existing tests pass (`mvn -f hooks/pom.xml test`)
- [ ] Tests cover: trigger not met, time-based trigger, count-based trigger, first retrospective (no last_retro)
- [ ] Tests cover: effectiveness reporting, missing index.json error, empty patterns/action items arrays

## Execution Steps

1. **Read Java conventions:** Read `.claude/cat/conventions/java.md` for code style requirements
2. **Create GetRetrospectiveOutput.java:** Implement `SkillOutput` following the same patterns as
   `GetStatusOutput.java` and `CheckRetrospectiveDue.java`
   - Files: `hooks/src/main/java/io/github/cowwoc/cat/hooks/skills/GetRetrospectiveOutput.java`
3. **Create bindings.json:** Map `CAT_RETROSPECTIVE_OUTPUT` to handler class
   - Files: `plugin/skills/run-retrospective/bindings.json`
4. **Update first-use.md:** Add `${CAT_RETROSPECTIVE_OUTPUT}` reference, remove Python handler mentions
   - Files: `plugin/skills/run-retrospective/first-use.md`
5. **Write unit tests:** Test trigger conditions and output format
   - Files: `hooks/src/test/java/io/github/cowwoc/cat/hooks/skills/GetRetrospectiveOutputTest.java`
6. **Run tests:** Execute `mvn -f hooks/pom.xml test` and verify all pass
