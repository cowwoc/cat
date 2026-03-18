# Plan: add-rebase-hash-prevention

## Goal

Add prevention guidance to `git-rebase-agent` skill and fix a minor Javadoc issue in `SkillLoader.java`
to prevent recurrence of the M471 mistake (sed pattern using wrong hash length in rebase todo scripts).

## Background

During issue 2.1-fix-add-agent-skill-loading, an interactive rebase failed because the agent-generated
`squash-editor.sh` sed script used a 9-character commit hash (`27200257c`) to match git rebase todo entries,
but git rebase -i uses 7-character abbreviated hashes by default. The pattern never matched, causing a
fixup commit to be silently replayed as a normal pick and squashed into the wrong commit.

Root cause (M471): The agent manually constructed a sed pattern with a full/long hash instead of using
`git log --format='%h'` to obtain the abbreviated hash that git rebase -i actually uses.

## Satisfies

- M471 prevention

## Risk Assessment

- **Risk Level:** LOW
- **Concerns:** None — documentation-only change plus minor Javadoc fix

## Files to Modify

- `plugin/skills/git-rebase-agent/SKILL.md` — add prevention note: when writing sed scripts to transform
  git rebase todo files, always obtain the abbreviated hash via `git log --format='%h' -1 <commit>` rather
  than using long/9-char hashes
- `client/src/main/java/io/github/cowwoc/cat/hooks/util/SkillLoader.java` — fix Javadoc:
  change `@throws IOException if no {@code first-use.md} is found or cannot be read`
  to `@throws IOException if {@code first-use.md} was not found or cannot be read`

## Pre-conditions

- [x] 2.1-fix-add-agent-skill-loading is merged

## Sub-Agent Waves

### Wave 1

- Add prevention note to git-rebase-agent/SKILL.md for abbreviated hash usage
- Fix Javadoc in SkillLoader.java loadRawContent()

## Post-conditions

- [ ] `git-rebase-agent/SKILL.md` contains warning: use `git log --format='%h'` for abbreviated
  hashes in rebase todo sed scripts
- [ ] `SkillLoader.java` Javadoc reads: `@throws IOException if {@code first-use.md} was not found
  or cannot be read`
- [ ] All tests pass
