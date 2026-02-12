# Add Git Safety Hook

## Goal

Add a PreToolUse BashHandler that blocks destructive git commands with proper flag normalization, closing the gap between advisory skill documentation and enforced safety.

## Satisfies

None (infrastructure hardening, not tied to v2.3 requirements)

## Background

Blog post analysis (adriangalilea.com/claude-code-permission-bypass) identified that Claude Code deny patterns use prefix matching which breaks when git global flags like `-C` are inserted. Our current git-* skills are advisory-only; the existing BashHandlers cover some operations but have gaps.

### Current Coverage (Already Handled)

| Handler | What It Blocks |
|---------|----------------|
| ValidateGitOperations | `git reset --hard` (with ACKNOWLEDGED bypass), `git push --force main/master` |
| BlockMainRebase | `git rebase` on main/protected branches |
| ValidateGitFilterBranch | `git filter-branch/rebase --all/--branches` |
| BlockReflogDestruction | `git reflog expire --expire=now/all/0`, `git gc --prune=now/all` |
| BlockMergeCommits | `git merge` without `--ff-only` or `--squash` |

### Gaps to Fill

| Command | Gap |
|---------|-----|
| `git push --force` / `git push -f` (non-main branches) | Only main/master blocked; `-f` short form not matched |
| `git branch -D` (version branches) | Not covered at all |
| `git checkout .` / `git restore .` | Not covered (discard working tree changes) |
| `git clean -f` / `git clean -fd` | Not covered |
| `git stash drop --all` | Not covered |
| All handlers | No git global flag normalization (`-C`, `--git-dir`, `--work-tree`) |

## Execution Steps

### Step 1: Create GitCommandNormalizer utility class

Create `hooks/src/main/java/io/github/cowwoc/cat/hooks/bash/GitCommandNormalizer.java`

This class provides a static method that:
1. Takes a raw bash command string
2. Splits on shell operators (`&&`, `||`, `;`, `|`) to isolate individual commands
3. For each command that starts with `git`, strips global flags: `-C <path>`, `--git-dir=<path>`, `--git-dir <path>`, `--work-tree=<path>`, `--work-tree <path>`, `-c <key>=<value>`
4. Returns a list of normalized git command strings (just the subcommand and its args)

Example: `git -C /some/path reset --hard HEAD` -> `git reset --hard HEAD`

Follow Allman braces, 2-space indent, Javadoc on all methods. Use `requireThat()` for parameter validation.

### Step 2: Create BlockDestructiveGitCommands handler

Create `hooks/src/main/java/io/github/cowwoc/cat/hooks/bash/BlockDestructiveGitCommands.java`

Implements `BashHandler`. In its `check()` method:
1. Use `GitCommandNormalizer` to get normalized git commands from the raw command
2. For each normalized command, check against these patterns:
   - `git push --force` or `git push -f` (to ANY branch, not just main) -> BLOCK with message to use `--force-with-lease` or get explicit user approval
   - `git branch -D <branch>` where branch matches `v[0-9]+` or `main` or `master` -> BLOCK
   - `git checkout .` or `git checkout -- .` -> BLOCK with message about data loss
   - `git restore .` or `git restore --staged .` with `--worktree` -> BLOCK
   - `git clean -f` or `git clean -fd` or `git clean` with `-f` flag -> BLOCK
   - `git stash drop --all` or `git stash clear` -> BLOCK
3. All blocks should include a clear reason for the block. Safe alternatives may optionally be included for user context
4. Allow `# ACKNOWLEDGED` comment bypass anywhere in the command string (uses `.contains()` matching, consistent with existing pattern in ValidateGitOperations)

### Step 3: Retrofit GitCommandNormalizer into existing handlers

Update these existing handlers to use `GitCommandNormalizer` instead of matching raw command strings:
- `ValidateGitOperations.java` - update patterns to use normalized commands
- `BlockMainRebase.java` - update patterns to use normalized commands
- `ValidateGitFilterBranch.java` - update patterns to use normalized commands
- `BlockReflogDestruction.java` - update patterns to use normalized commands
- `BlockMergeCommits.java` - update patterns to use normalized commands

This ensures ALL handlers are resistant to `-C` flag bypass.

### Step 4: Register BlockDestructiveGitCommands in GetBashOutput

Edit `hooks/src/main/java/io/github/cowwoc/cat/hooks/GetBashOutput.java`:
- Add `new BlockDestructiveGitCommands()` to the handlers list in the constructor

### Step 5: Write tests

Create test class `hooks/src/test/java/io/github/cowwoc/cat/hooks/test/GitCommandNormalizerTest.java`:
- Test stripping `-C /path` flag
- Test stripping `--git-dir=/path` and `--git-dir /path`
- Test stripping `--work-tree=/path`
- Test stripping `-c key=value`
- Test multiple flags combined
- Test commands chained with `&&`, `||`, `;`
- Test non-git commands pass through unchanged
- Test edge cases: empty string, git command with no subcommand

Create test class `hooks/src/test/java/io/github/cowwoc/cat/hooks/test/BlockDestructiveGitCommandsTest.java`:
- Test each blocked pattern returns `Result.block()`
- Test each pattern with `-C` flag prefix still blocks
- Test `# ACKNOWLEDGED:` bypass allows commands
- Test safe variants are allowed (e.g., `git push --force-with-lease`, `git branch -d` lowercase, `git clean -n`)
- Test `git branch -D feature-branch` is allowed (only version/main branches blocked)

All tests must use TestNG, `requireThat()` assertions, no shared state, `TestJvmScope`.

### Step 6: Update validate-git-safety skill content

Edit `plugin/skills/validate-git-safety/content.md`:
- Add a section noting that destructive commands are now enforced at hook level
- List which commands are blocked and the `# ACKNOWLEDGED:` bypass pattern
- Remove the manual validation bash snippets that are now automated

Update `plugin/skills/validate-git-safety/SKILL.md`:
- Keep `user-invocable: false` (it remains a reference doc, not a user command)

### Step 7: Run tests and verify build

```bash
cd hooks && mvn verify
```

All tests must pass. No checkstyle or PMD violations.

## Success Criteria

- [ ] `GitCommandNormalizer` strips `-C`, `--git-dir`, `--work-tree`, `-c` flags from git commands
- [ ] `BlockDestructiveGitCommands` blocks: force push (any branch), version branch deletion, checkout/restore discard-all, clean -f, stash clear
- [ ] All existing git BashHandlers use `GitCommandNormalizer` (resistant to `-C` bypass)
- [ ] `# ACKNOWLEDGED` bypass works across all handlers (`.contains()` matching anywhere in command string)
- [ ] Tests cover both direct commands and variants with all global flags (`-C`, `--git-dir`, `--work-tree`, `-c`)
- [ ] `mvn verify` passes with no violations
- [ ] `validate-git-safety` skill content updated to reflect hook enforcement