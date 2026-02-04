# Verify Fix Doesn't Introduce Priming (M370)

Lazy-loaded after implementing documentation fixes to verify no new priming patterns introduced.

## When to Use

After editing documentation files in Step 9, verify your fix doesn't introduce priming patterns.

## Check Pattern

```yaml
priming_check:
  # Check each file you edited in Step 9
  for_each_edited_file:
    contains_output_format_example: true|false
    if_true:
      has_concrete_values: true|false  # e.g., "1.0", "0.87", "SUCCESS"
      has_placeholders: true|false     # e.g., "{actual score}", "{status}"

  # BLOCKING: If concrete values found in output format
  if_concrete_values:
    action: "Replace with descriptive placeholders"
    examples:
      wrong: "| file1.md | 1.0 | PASS |"
      right: "| {filename} | {actual score from /compare-docs} | {PASS|FAIL} |"
```

## Common Priming Patterns to Avoid

| Pattern | Risk | Fix |
|---------|------|-----|
| Result table with scores | Agent produces those exact scores | Use `{actual score}` placeholder |
| Status examples like "SUCCESS" | Agent reports success without verification | Use `{status}` |
| Concrete token counts | Agent fabricates similar counts | Use `{count}` |

## Why This Gate Exists

M370: When fixing M369, example result tables were added with concrete values (1.0, 0.87),
which would prime agents to produce those values instead of running actual validation.

**Reference:** See `/cat:skill-builder` § "Priming Prevention Checklist" for complete patterns.
