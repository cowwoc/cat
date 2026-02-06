# Plan: hide-add-skill-bash-output

## Goal
Reduce visible Bash tool calls in `/cat:add` from ~8 to ~1 by: (1) extending `add_handler.py` to
pre-load version data invisibly at skill load time, and (2) creating a `create-issue.py` script that
consolidates post-interaction file creation + git commit into a single call.

## Satisfies
None - UX polish

## Risk Assessment
- **Risk Level:** MEDIUM
- **Breaking Changes:** Skill behavior changes but output is identical
- **Mitigation:** Test both issue and version creation paths after changes

## Current Visible Bash Calls (Issue Workflow)

| Step | Bash Call | Can Hide? | How |
|------|-----------|-----------|-----|
| `verify` | Check `.claude/cat` exists | Yes | Handler pre-check |
| `task_analyze_versions` | `find` versions, `grep` statuses | Yes | Handler pre-loads |
| `task_validate_version` | Check dir exists, grep status | Yes | Handler provides data |
| `task_validate_name` | Regex + dir existence check | Yes | LLM checks format; handler provides existing names |
| `task_discuss` | `find ... \| wc -l` (count issues) | Yes | Handler provides count |
| `task_create` | `grep` PROJECT.md, `mkdir -p` | Yes | create-issue.py |
| `task_update_parent` | `sed -i` STATE.md | Yes | create-issue.py |
| `task_commit` | `git add`, `git commit` | Yes | create-issue.py |
| `task_done` | `render-add-complete.sh` | Already hidden | Existing script |

## Files to Modify

| File | Change |
|------|--------|
| `plugin/hooks/skill_handlers/add_handler.py` | Extend to pre-load version data, issue counts, branch strategy |
| `plugin/scripts/create-issue.py` | New script: mkdir + write STATE.md + write PLAN.md + update parent STATE.md + git commit |
| `plugin/skills/add/SKILL.md` | Remove inline bash blocks; use HANDLER_DATA for validation; call create-issue.py for creation |

## Acceptance Criteria
- [ ] Functionality works - issues and versions created identically to before
- [ ] Tests passing
- [ ] No regressions - version workflow still works

## Execution Steps

1. **Step 1:** Extend `add_handler.py` to pre-load version data
   - File: `plugin/hooks/skill_handlers/add_handler.py`
   - Add a `_preload_version_data()` method that:
     - Validates `.claude/cat` and `ROADMAP.md` exist (currently `verify` step)
     - Reads all minor version STATE.md files, extracts status, filters closed versions
     - Reads all minor version PLAN.md files, extracts goals/objectives summaries
     - Counts issues per version (currently `task_discuss` step)
     - Reads branch strategy from PROJECT.md and cat-config.json (currently `task_create` step)
     - Reads existing issue names per version (for uniqueness validation)
   - Return all data as `HANDLER_DATA:` JSON block with structure:
     ```json
     {
       "planning_valid": true,
       "versions": [
         {
           "version": "2.1",
           "status": "in-progress",
           "summary": "Pre-Demo Polish",
           "issue_count": 95,
           "existing_issues": ["fix-bug", "add-feature", ...]
         }
       ],
       "branch_strategy": "feature",
       "branch_pattern": null
     }
     ```
   - On error (no planning structure), return error status in JSON

2. **Step 2:** Create `plugin/scripts/create-issue.py`
   - File: `plugin/scripts/create-issue.py`
   - Accept JSON via stdin or `--json` argument with fields:
     - `major`, `minor`, `issue_name`, `issue_type`
     - `dependencies` (list)
     - `state_content` (full STATE.md content)
     - `plan_content` (full PLAN.md content)
     - `commit_description` (one-line description for commit message)
   - Script performs:
     - `mkdir -p` for issue directory
     - Write STATE.md
     - Write PLAN.md
     - Update parent version STATE.md (add to "Tasks Pending" section)
     - `git add` both paths
     - `git commit` with standardized message format
   - Output: JSON result with `{ "success": true, "path": "..." }` or error
   - Make executable: `chmod +x`

3. **Step 3:** Update `plugin/skills/add/SKILL.md` to use handler data
   - File: `plugin/skills/add/SKILL.md`
   - Replace `verify` step bash block with: "Parse HANDLER_DATA from context. If
     `planning_valid` is false, output error and STOP."
   - Replace `task_analyze_versions` step bash blocks with: "Use version data from
     HANDLER_DATA.versions. Filter to non-closed versions. Use summaries for recommendations."
   - Replace `task_validate_version` step bash block with: "Verify selected version exists
     in HANDLER_DATA.versions and status is not 'closed'."
   - Replace `task_validate_name` step bash block with: "Check name format matches regex
     `^[a-z][a-z0-9-]{0,48}[a-z0-9]$`. Check name not in HANDLER_DATA.versions[selected].existing_issues."
   - Replace `task_discuss` step bash block (issue count) with: "Use
     HANDLER_DATA.versions[selected].issue_count."
   - Replace `task_create` + `task_update_parent` + `task_commit` steps with: "After
     generating STATE.md and PLAN.md content, call create-issue.py with JSON input containing
     all parameters. The script handles directory creation, file writing, parent STATE.md
     update, and git commit in one call."
   - Keep `task_done` step as-is (already uses render-add-complete.sh)

4. **Step 4:** Add tests for the extended handler
   - File: `tests/test_add_handler.py`
   - Test `_preload_version_data()` returns correct structure
   - Test error case when planning structure missing
   - Test closed versions are excluded
   - Test issue count accuracy

5. **Step 5:** Add tests for create-issue.py
   - File: `tests/test_create_issue.py`
   - Test successful issue creation
   - Test parent STATE.md update
   - Test git commit message format
   - Test error handling (invalid paths, missing parent)

6. **Step 6:** Run all tests
   - `python3 /workspace/run_tests.py`

7. **Step 7:** Commit changes
   - Commit type: `config:` for handler/skill changes, `feature:` for new script

## Success Criteria
- [ ] `/cat:add` with arguments shows 0-1 visible Bash calls (only create-issue.py)
- [ ] `/cat:add` without arguments shows 0-1 visible Bash calls
- [ ] All handler tests pass
- [ ] All create-issue.py tests pass
- [ ] Existing add_handler display tests still pass
