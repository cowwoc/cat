# No Backwards Compatibility

**Policy:** Code in terms of the latest design. Do NOT add backwards-compatibility shims, legacy fallbacks, or
dual-format support. When a data structure, file layout, or API changes, add migration logic to `plugin/migrations/`
and write all code against the new design only.

**Rationale:**
- Backward compatibility code adds complexity and maintenance burden
- CAT is a developer tool where users can re-run migrations easily
- Stale data from old formats should be cleaned up, not silently supported forever
- Legacy support obscures the current design and makes future changes harder

**Migration pattern:**
1. Add a migration script to `plugin/migrations/` that converts old data to the new format
2. Update all writers to use the new format
3. Update all readers to expect ONLY the new format
4. Document the change in the issue's plan.md

**Idempotency:** Migration scripts MUST be idempotent. Running a migration consecutively must be a no-op on the 2nd+
run. Scripts should check current state before making changes (e.g., skip renaming a file that's already renamed, skip
adding a field that already exists).

**Closed issue coverage:** Migration scripts must process all issues regardless of status (open or closed). Closed
issues contain the same file formats as open issues and must be migrated to maintain consistency. The CLAUDE.md rule
about not modifying closed issues applies only to manual agent edits, not automated migrations.

**Planning file schema changes:** When an issue modifies the schema of planning files (index.json, plan.md headings,
field names, section structure), the issue MUST include updating the current version's `plugin/migrations/` script to
transform existing files. The migration is part of the same issue — do not defer it to a separate issue.

**DO NOT:**
- Add "legacy format" branches in readers
- Keep old writers alongside new writers
- Silently fall back to parsing old formats
- Support old file paths or directory structures alongside new ones
- Add compatibility layers that translate between old and new APIs

```cat-rules
- pattern: "\\b(?:FIXME|TODO:[[:space:]]*fix|fallback|workaround)\\b"
  files: "*"
  severity: low
  message: "Comment flag indicates known issue or workaround. Resolve or track as a separate issue. See .claude/rules/backwards-compatibility.md."
```
