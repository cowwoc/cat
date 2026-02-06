# Plan: wrap-markdown-files-120-chars

## Current State
199 markdown files across the project contain lines exceeding 120 characters.
No line wrapping convention existed until now.

## Target State
All markdown files (excluding backups) respect the 120-character line wrap convention
defined in `.claude/rules/common.md`.

## Satisfies
None

## Risk Assessment
- **Risk Level:** LOW
- **Breaking Changes:** None - content is unchanged, only whitespace reformatting
- **Mitigation:** Verify no markdown tables or code blocks are broken by wrapping.
  Lines inside code blocks (``` fenced) and markdown tables (| delimited) must NOT
  be wrapped as this would break their rendering.

## Files to Modify
199 markdown files across these directories:
- `CLAUDE.md` (project root)
- `.claude/rules/*.md` (project rules)
- `.claude/cat/PROJECT.md` (project config)
- `.claude/cat/conventions/*.md` (language conventions)
- `.claude/cat/issues/v1/**/PLAN.md` and `STATE.md` (v1 issues)
- `.claude/cat/issues/v2/**/PLAN.md`, `STATE.md`, `CHANGELOG.md` (v2 issues)
- `plugin/agents/*.md` (stakeholder agents)
- `plugin/concepts/*.md` (concept docs)
- `plugin/skills/*/*.md` (skill definitions)

Exclude: `.claude/cat/backups/`, `.worktrees/`, `node_modules/`, `.git/`

## Acceptance Criteria
- [ ] All markdown files have no lines exceeding 120 characters
- [ ] Code blocks (``` fenced) are NOT wrapped (would break code)
- [ ] Markdown tables (| delimited rows) are NOT wrapped (would break tables)
- [ ] URLs are NOT broken across lines
- [ ] Content meaning is preserved - only whitespace changes
- [ ] All tests pass
- [ ] No regressions

## Execution Steps

1. **Step 1:** Create a Python script that reformats markdown files to 120 chars
   - Wrap prose lines at 120 characters using word boundaries
   - Preserve code blocks (``` fenced sections) exactly as-is
   - Preserve markdown tables (lines starting with |) exactly as-is
   - Preserve lines that are a single long URL or contain inline URLs
   - Preserve YAML frontmatter (--- delimited sections) exactly as-is
   - Preserve HTML tags and box-drawing characters exactly as-is
   - Handle bullet points and numbered lists (maintain indent on continuation)
   - Files: create `plugin/scripts/wrap-markdown.py`

2. **Step 2:** Run the script on all 199 files
   - Use find to locate all .md files excluding backups, worktrees, node_modules
   - Process each file in-place
   - Files: all 199 markdown files listed above

3. **Step 3:** Validate results
   - Verify no lines exceed 120 chars (except preserved code/tables/URLs)
   - Spot-check a sample of files for correct formatting
   - Run `python3 /workspace/run_tests.py` to verify no regressions
   - Files: none (validation only)

4. **Step 4:** Commit changes
   - Commit the wrapper script and all reformatted files
   - Files: `plugin/scripts/wrap-markdown.py` + all modified .md files

## Success Criteria
- [ ] Zero non-exempt lines exceed 120 characters across all project markdown files
- [ ] All existing tests pass
- [ ] Code blocks, tables, and URLs are intact
