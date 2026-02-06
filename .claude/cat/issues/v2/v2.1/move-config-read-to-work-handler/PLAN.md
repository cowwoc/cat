# Plan: move-config-read-to-work-handler

## Current State
The `/cat:work` skill (SKILL.md) instructs the LLM to read `cat-config.json` via a visible Bash
call (`jq -r ...`) at the start of execution. This produces user-visible output (the jq results
and surrounding text) that is pure internal bookkeeping with no user value.

## Target State
The `work_handler.py` preprocessing reads `cat-config.json` and injects trust/verify/autoRemove
values into its output. The SKILL.md references these pre-injected values instead of instructing
the LLM to run Bash commands. The user sees the progress banner followed directly by the Task
tool call — no intermediate config-reading noise.

## Satisfies
None (infrastructure/UX improvement)

## Risk Assessment
- **Risk Level:** LOW
- **Breaking Changes:** None — same values are read, just moved to preprocessing
- **Mitigation:** Existing work_handler tests verify handler output; add test for config injection

## Files to Modify
- `plugin/hooks/skill_handlers/work_handler.py` — Add config reading in `handle()` method, inject values into output
- `plugin/skills/work/SKILL.md` — Remove "Configuration" section (lines 71-79), reference pre-injected values

## Execution Steps
1. **Modify work_handler.py `handle()` method:**
   - Read `.claude/cat/cat-config.json` from the project directory
   - Extract `trust` (default "medium"), `verify` (default "changed"), `autoRemoveWorktrees` (default true)
   - The project directory can be determined from the context dict or by using the current working directory
   - Prepend a config section to the handler output before the status boxes:
     ```
     CONFIGURATION:
     TRUST=medium
     VERIFY=all
     AUTO_REMOVE=true
     ```
   - Use json.load to read the config file; if file doesn't exist, use defaults

2. **Modify SKILL.md Configuration section:**
   - Remove the "Read once at start" code block (lines 71-79) that runs jq commands
   - Replace with instruction to use the pre-injected values from preprocessing:
     ```
     ## Configuration

     Values are pre-loaded by handler preprocessing (shown above in CONFIGURATION section).
     Use these values: TRUST, VERIFY, AUTO_REMOVE.
     ```

3. **Update tests:**
   - Add test case in work_handler tests verifying config values appear in handler output
   - Test default values when cat-config.json doesn't exist

## Success Criteria
- [ ] Running `/cat:work` no longer shows a Bash call to read cat-config.json
- [ ] Trust/verify/autoRemove values are correctly available in the skill context
- [ ] All existing tests pass after refactoring
- [ ] Handler output includes CONFIGURATION section with correct values
