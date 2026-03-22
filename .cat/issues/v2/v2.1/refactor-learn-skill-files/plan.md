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

## Execution Steps

### Step 1: Read the files to understand exact retroactive content

Read each of the five files containing retroactive commentary so you can identify exact sentences/sections to remove:
- `plugin/skills/learn/HOOK-WORKAROUNDS.md`
- `plugin/skills/learn/MULTIPLE-MISTAKES.md`
- `plugin/skills/learn/PRIMING-VERIFICATION.md`
- `plugin/skills/learn/RELATED-FILES-CHECK.md`
- `plugin/skills/learn/phase-prevent.md`

Also read the three files with cross-references:
- `plugin/skills/learn/first-use.md`
- `plugin/skills/learn/phase-analyze.md`

(phase-prevent.md was already read above)

### Step 2: Remove retroactive commentary from each file

For each file, remove or rewrite any text that:
- References a mistake ID (Mxxx or A0xx format, e.g., M305, M341, M370, M378, M398, A002, M422)
- Frames current behavioral rules as "this exists because of Mxxx" or "added in response to Mxxx"
- Labels examples with the mistake ID that originated them

Specific patterns to fix by file:
- `HOOK-WORKAROUNDS.md`: Remove "Example - M398" heading/label and any surrounding text that frames the example as originating from M398. Keep the example content itself, just remove the provenance label.
- `MULTIPLE-MISTAKES.md`: Remove any sentence or section referencing M378 historically. Keep the rule/guidance itself.
- `PRIMING-VERIFICATION.md`: Remove any text that says "M370 is why this gate exists" or equivalent. Keep the gate's behavioral description.
- `RELATED-FILES-CHECK.md`: Remove M341 from the title (if present) and from the body. Rename the section/heading to describe what the check does, not why it was added.
- `phase-prevent.md`: Remove all sentences/paragraphs that reference M305, A002, M422 as motivating incidents. Keep the prevention instructions themselves.

Edit each file in-place using the Edit tool. Do NOT remove behavioral content — only the historical provenance framing.

### Step 3: Rename uppercase files to lowercase using git mv

Run from within the worktree directory (`plugin/skills/learn/` subdirectory):

```bash
cd /workspace/.cat/work/worktrees/2.1-refactor-learn-skill-files && \
  git mv plugin/skills/learn/ANTI-PATTERNS.md plugin/skills/learn/anti-patterns.md && \
  git mv plugin/skills/learn/EXAMPLES.md plugin/skills/learn/examples.md && \
  git mv plugin/skills/learn/HOOK-WORKAROUNDS.md plugin/skills/learn/hook-workarounds.md && \
  git mv plugin/skills/learn/MULTIPLE-MISTAKES.md plugin/skills/learn/multiple-mistakes.md && \
  git mv plugin/skills/learn/PRIMING-VERIFICATION.md plugin/skills/learn/priming-verification.md && \
  git mv plugin/skills/learn/RELATED-FILES-CHECK.md plugin/skills/learn/related-files-check.md
```

Note: `git mv` preserves git history. The content edits from Step 2 are staged automatically alongside the rename.

### Step 4: Update cross-references to use lowercase filenames

Update all references in these files to use lowercase filenames:

In `plugin/skills/learn/first-use.md`:
- Change all occurrences of `EXAMPLES.md` → `examples.md`
- Change all occurrences of `ANTI-PATTERNS.md` → `anti-patterns.md`

In `plugin/skills/learn/phase-analyze.md`:
- Change all occurrences of `MULTIPLE-MISTAKES.md` → `multiple-mistakes.md`
- Change all occurrences of `HOOK-WORKAROUNDS.md` → `hook-workarounds.md`

In `plugin/skills/learn/phase-prevent.md`:
- Change all occurrences of `PRIMING-VERIFICATION.md` → `priming-verification.md`
- Change all occurrences of `RELATED-FILES-CHECK.md` → `related-files-check.md`

Also check `plugin/skills/learn/SKILL.md` for any uppercase references and update if found.

### Step 5: Run tests

```bash
cd /workspace/.cat/work/worktrees/2.1-refactor-learn-skill-files && mvn -f client/pom.xml test
```

All tests must pass (exit code 0).

### Step 6: Commit and update index.json

Stage all changes explicitly (no `git add .` or `git add -A`):

```bash
cd /workspace/.cat/work/worktrees/2.1-refactor-learn-skill-files && \
  git add plugin/skills/learn/anti-patterns.md \
          plugin/skills/learn/examples.md \
          plugin/skills/learn/hook-workarounds.md \
          plugin/skills/learn/multiple-mistakes.md \
          plugin/skills/learn/priming-verification.md \
          plugin/skills/learn/related-files-check.md \
          plugin/skills/learn/phase-prevent.md \
          plugin/skills/learn/first-use.md \
          plugin/skills/learn/phase-analyze.md
```

Also stage SKILL.md if it was modified in Step 4, and the index.json:

```bash
cd /workspace/.cat/work/worktrees/2.1-refactor-learn-skill-files && \
  git add plugin/skills/learn/SKILL.md 2>/dev/null || true && \
  git add .cat/issues/v2/v2.1/refactor-learn-skill-files/index.json
```

Update index.json: set `"status": "closed"` and `"progress": 100` in the file at
`.cat/issues/v2/v2.1/refactor-learn-skill-files/index.json`.

Commit with:
```bash
cd /workspace/.cat/work/worktrees/2.1-refactor-learn-skill-files && \
  git commit -m "refactor: remove retroactive commentary and lowercase filenames in learn skill"
```

## Post-conditions

- [ ] No Mxxx provenance markers or "why this gate exists" retrospective framing in any `plugin/skills/learn/` file
- [ ] All previously uppercase filenames in `plugin/skills/learn/` are now lowercase
- [ ] All cross-references within the learn skill updated to lowercase filenames
- [ ] Tests pass (`mvn -f client/pom.xml test` exits 0)
- [ ] E2E: Load `plugin/skills/learn/first-use.md` and confirm it references `examples.md` and `anti-patterns.md` (lowercase)
