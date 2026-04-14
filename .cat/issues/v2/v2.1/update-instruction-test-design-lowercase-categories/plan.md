# Plan: update-instruction-test-design-lowercase-categories

## Goal

Update `plugin/concepts/instruction-test-design.md` to document lowercase as the canonical category
format, aligning it with `normalize-test-category-casing` which normalizes all test files to lowercase.
Currently the two sources conflict: the concept doc shows uppercase (`REQUIREMENT`, `PROHIBITION`, etc.)
while the normalization issue (and the actual test files) target lowercase.

## Parent Requirements

None

## Risk Assessment

- **Risk Level:** LOW
- **Concerns:** None — documentation-only change
- **Mitigation:** N/A

## Files to Modify

- `plugin/concepts/instruction-test-design.md` — update the category table and all template examples
  to use lowercase values

## Pre-conditions

- [ ] All dependent issues are closed

## Jobs

### Job 1

In `plugin/concepts/instruction-test-design.md`, update all occurrences of uppercase category values
to lowercase:

1. In the **Frontmatter** section, update the valid categories table:

   Before:
   ```
   | `REQUIREMENT` | Verifies a behavior the instruction mandates |
   | `PROHIBITION` | Verifies a behavior the instruction forbids |
   | `CONDITIONAL` | Verifies a behavior that applies only under a specific condition |
   | `SEQUENCE`    | Verifies an ordering or sequencing constraint |
   | `DEPENDENCY`  | Verifies prerequisite or precondition enforcement |
   | `negative`    | Verifies the skill is NOT invoked for an out-of-scope prompt |
   ```

   After:
   ```
   | `requirement` | Verifies a behavior the instruction mandates |
   | `prohibition` | Verifies a behavior the instruction forbids |
   | `conditional` | Verifies a behavior that applies only under a specific condition |
   | `sequence`    | Verifies an ordering or sequencing constraint |
   | `dependency`  | Verifies prerequisite or precondition enforcement |
   | `negative`    | Verifies the skill is NOT invoked for an out-of-scope prompt |
   ```

2. In the **Positive Test Case Template**, update the frontmatter example:

   Before: `category: <CATEGORY>`
   After:  `category: <category>`

3. In the **Negative Test Case Template**, confirm `category: negative` is already lowercase (no change).

4. In the **Example Test Case Files** section, update any example frontmatter that uses uppercase
   category values to use lowercase.

5. Scan the full file for any remaining uppercase `REQUIREMENT`, `PROHIBITION`, `CONDITIONAL`,
   `SEQUENCE`, `DEPENDENCY` in frontmatter context and convert to lowercase.

## Post-conditions

- [ ] The valid categories table in `instruction-test-design.md` shows all lowercase values
- [ ] All template examples in `instruction-test-design.md` use lowercase category values
- [ ] No uppercase category values (`REQUIREMENT`, `PROHIBITION`, `CONDITIONAL`, `SEQUENCE`,
  `DEPENDENCY`) appear in frontmatter context in `instruction-test-design.md`
- [ ] `normalize-test-category-casing` and `instruction-test-design.md` are consistent: both
  document lowercase as canonical
