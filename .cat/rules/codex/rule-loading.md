# Rule Loading

Codex-specific rule stubs include path-scoped rule bodies on demand because Codex does not natively
support `paths` frontmatter and does not currently intercept pre-read hooks for direct file reads.

A parent rule declares:
- `paths`: glob patterns for files covered by the rule.
- `include`: the file containing the full rule body, resolved relative to the parent rule file.

When a parent rule applies this convention:
1. Before reading, searching, or editing a file whose repository-relative path matches any entry in `paths`, read the
   `include` file relative to the parent rule file.
2. Load each `include` file at most once per conversation context. If the conversation is compacted or resumed and
   you are no longer sure the included file is in context, load it again before touching a matching file.
3. Do not load the included file preemptively when no matching file is being inspected or modified.
4. After loading the included file, follow its instructions for all matching files.
