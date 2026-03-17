# Refactor: Clean Up plugin/skills/learn/ Files

## Goal

Remove retroactive commentary from learn skill files (violates the no-retrospective-commentary rule in `.claude/rules/common.md`) and rename uppercase filenames to lowercase for consistency.

## Background

An analysis of `plugin/skills/learn/` found:
- Five files contain Mxxx provenance markers or "why this gate exists" historical framing: `HOOK-WORKAROUNDS.md`, `MULTIPLE-MISTAKES.md`, `PRIMING-VERIFICATION.md`, `RELATED-FILES-CHECK.md`, `phase-prevent.md`
- Six files use uppercase names: `ANTI-PATTERNS.md`, `EXAMPLES.md`, `HOOK-WORKAROUNDS.md`, `MULTIPLE-MISTAKES.md`, `PRIMING-VERIFICATION.md`, `RELATED-FILES-CHECK.md`
- The `.claude/rules/common.md` § "No retrospective commentary" rule prohibits documenting what was changed/fixed historically

## Files to Modify

### Retroactive commentary removal

| File | Retroactive content to remove |
|------|-------------------------------|
| `plugin/skills/learn/HOOK-WORKAROUNDS.md` | "Example - M398" label and M398 framing |
| `plugin/skills/learn/MULTIPLE-MISTAKES.md` | M378 historical reference |
| `plugin/skills/learn/PRIMING-VERIFICATION.md` | "M370 as why gate exists" framing |
| `plugin/skills/learn/RELATED-FILES-CHECK.md` | M341 in title and body |
| `plugin/skills/learn/phase-prevent.md` | M305, A002, M422 motivating-gate framing |

### Filename renames (uppercase → lowercase)

| Old name | New name |
|----------|----------|
| `ANTI-PATTERNS.md` | `anti-patterns.md` |
| `EXAMPLES.md` | `examples.md` |
| `HOOK-WORKAROUNDS.md` | `hook-workarounds.md` |
| `MULTIPLE-MISTAKES.md` | `multiple-mistakes.md` |
| `PRIMING-VERIFICATION.md` | `priming-verification.md` |
| `RELATED-FILES-CHECK.md` | `related-files-check.md` |

### Cross-reference updates

Files that reference the renamed files and must be updated:
- `plugin/skills/learn/first-use.md` → references `EXAMPLES.md`, `ANTI-PATTERNS.md`
- `plugin/skills/learn/phase-analyze.md` → references `MULTIPLE-MISTAKES.md`, `HOOK-WORKAROUNDS.md`
- `plugin/skills/learn/phase-prevent.md` → references `PRIMING-VERIFICATION.md`, `RELATED-FILES-CHECK.md`

## Execution Plan

### Step 1: Remove retroactive commentary

For each file with retroactive content:
- Remove or rewrite sentences/sections that frame current rules as "this exists because of Mxxx"
- Example fix pattern: "This gate exists because of M305" → Remove the sentence entirely; the gate's purpose should be described by what it does, not why it was added
- Example fix for labeled examples: "Example - M398" → "Example" (keep the example content, drop the mistake ID label)
- Keep the behavioral content; only strip the retrospective framing

### Step 2: Rename files

Use `git mv` to rename each uppercase file to lowercase (preserves git history):
```bash
cd plugin/skills/learn
git mv ANTI-PATTERNS.md anti-patterns.md
git mv EXAMPLES.md examples.md
git mv HOOK-WORKAROUNDS.md hook-workarounds.md
git mv MULTIPLE-MISTAKES.md multiple-mistakes.md
git mv PRIMING-VERIFICATION.md priming-verification.md
git mv RELATED-FILES-CHECK.md related-files-check.md
```

### Step 3: Update cross-references

Update all references in:
- `plugin/skills/learn/first-use.md`
- `plugin/skills/learn/phase-analyze.md`
- `plugin/skills/learn/phase-prevent.md`

### Step 4: Verify and test

- Confirm no uppercase filenames remain in `plugin/skills/learn/`
- Confirm no Mxxx provenance markers remain in learn skill files
- Run `mvn -f client/pom.xml test` and confirm all tests pass
- Confirm cross-references in SKILL.md and phase files point to lowercase filenames

## Post-conditions

- [ ] No Mxxx provenance markers or "why this gate exists" retrospective framing in any `plugin/skills/learn/` file
- [ ] All previously uppercase filenames in `plugin/skills/learn/` are now lowercase
- [ ] All cross-references within the learn skill updated to lowercase filenames
- [ ] Tests pass (`mvn -f client/pom.xml test` exits 0)
- [ ] E2E: Load `plugin/skills/learn/first-use.md` and confirm it references `examples.md` and `anti-patterns.md` (lowercase)
