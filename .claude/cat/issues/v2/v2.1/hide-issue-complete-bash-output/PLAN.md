# Plan: hide-issue-complete-bash-output

## Goal
Hide the noisy bash output (lock release, next task discovery, box rendering) that appears before the Issue Complete
box after merge in /cat:work. Replace with silent preprocessing via delegated skill chaining.

## Satisfies
None

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** Handler registration or argument parsing could fail silently
- **Mitigation:** Fail-fast pattern in skill; existing handler pattern is proven

## Files to Create
- `plugin/scripts/get-next-task-box.py` - Combined script: release lock, find next task, read goal, render box
- `plugin/hooks/skill_handlers/work_complete_handler.py` - Handler that parses JSON args and calls combined script
- `plugin/skills/work-complete/SKILL.md` - Non-user-invocable renderer skill with routing logic

## Files to Modify
- `plugin/hooks/skill_handlers/__init__.py` - Add import for `work_complete_handler`
- `plugin/skills/work/SKILL.md` - Replace "Next Task" section (lines 201-221) with Skill invocation

## Acceptance Criteria
- [ ] No bash tool calls visible before Issue Complete box
- [ ] Issue Complete box renders correctly (alignment, widths)
- [ ] Scope Complete box works when no next task exists
- [ ] Lock released silently after merge
- [ ] All existing tests pass

## Execution Steps

1. **Step 1:** Create `plugin/scripts/get-next-task-box.py`
   - Import box-rendering functions from `get-issue-complete-box.py` (reuse `build_issue_complete_box`,
     `build_scope_complete_box`)
   - Implement `release_lock(project_dir, issue_id, session_id)` - calls `issue-lock.sh release` via subprocess,
     best-effort (ignore failures)
   - Implement `find_next_task(project_dir, session_id, exclude_pattern)` - calls `get-available-issues.sh` via
     subprocess, parses JSON result
   - Implement `read_issue_goal(issue_path)` - reads PLAN.md, extracts text after `## Goal` heading
   - Implement `main()` with argparse: `--completed-issue`, `--base-branch`, `--exclude-pattern`, `--session-id`,
     `--project-dir`
   - Flow: release lock -> find next task -> if found read goal -> render appropriate box -> print to stdout
   - If next task found: call `build_issue_complete_box(completed_issue, next_issue, goal, base_branch)`
   - If no next task: call `build_scope_complete_box(scope_description)`
   - On error: print error message to stderr, output fallback text to stdout
   - Files: `plugin/scripts/get-next-task-box.py`

2. **Step 2:** Create `plugin/hooks/skill_handlers/work_complete_handler.py`
   - Follow exact pattern from `plugin/hooks/skill_handlers/work_with_issue_handler.py`
   - Class `WorkCompleteHandler` with `handle(self, context: dict) -> str | None`
   - Extract JSON args via regex: `r'/?\s*cat:work-complete\s+(\{.*\})'` with `re.DOTALL`
   - Parse JSON to get: `completed_issue`, `base_branch`, `exclude_pattern`, `session_id`, `project_dir`, `trust`
   - Call `get-next-task-box.py` via subprocess with parsed args
   - Return script stdout as additional context, prefixed with `SCRIPT OUTPUT NEXT TASK BOX:\n`
   - Also include `trust` value in output for routing: `TRUST_LEVEL: {trust}\n`
   - Register handler: `register_handler("work-complete", _handler)`
   - Files: `plugin/hooks/skill_handlers/work_complete_handler.py`

3. **Step 3:** Add import to `plugin/hooks/skill_handlers/__init__.py`
   - Add line: `from . import work_complete_handler`
   - Files: `plugin/hooks/skill_handlers/__init__.py`

4. **Step 4:** Create `plugin/skills/work-complete/SKILL.md`
   - Frontmatter: `user-invocable: false`, allowed-tools: none needed (output only)
   - Receive preprocessed box from handler in SCRIPT OUTPUT section
   - Include TRUST_LEVEL from handler output
   - Instructions: Output the preprocessed box VERBATIM
   - Include JSON metadata block after box for parent skill: `{"has_next_task": true/false, "next_issue_id": "..."}`
   - Fail-fast: if no SCRIPT OUTPUT section visible, report error
   - Files: `plugin/skills/work-complete/SKILL.md`

5. **Step 5:** Update `plugin/skills/work/SKILL.md` "Next Task" section (lines 201-221)
   - Replace manual Bash calls with Skill tool invocation of `cat:work-complete`
   - Pass JSON args: `completed_issue`, `base_branch`, `exclude_pattern` (from original ARGUMENTS), `session_id`,
     `project_dir`, `trust`
   - After skill returns: parse the JSON metadata to determine routing
   - If `has_next_task` and trust >= medium: auto-continue to next `/cat:work`
   - If `has_next_task` and trust == low: display low-trust stop message
   - If no next task: stop (scope complete)
   - Files: `plugin/skills/work/SKILL.md`

6. **Step 6:** Run tests to verify no regressions
   - Command: `python3 /workspace/run_tests.py`
   - Files: `run_tests.py`

## Success Criteria
- [ ] No bash tool output visible between merge completion and Issue Complete box
- [ ] Handler correctly parses JSON args and calls combined script
- [ ] Combined script correctly releases lock, discovers next task, renders box
- [ ] All existing tests pass (226+ tests)
